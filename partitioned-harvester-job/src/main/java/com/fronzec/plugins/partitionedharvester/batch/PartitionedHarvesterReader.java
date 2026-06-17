package com.fronzec.plugins.partitionedharvester.batch;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamException;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import org.springframework.batch.infrastructure.item.database.JdbcCursorItemReader;

/**
 * Per-partition worker reader that isolates state across parallel threads using a
 * {@link ThreadLocal}-scoped {@link JdbcCursorItemReader}.
 *
 * <h3>Why ThreadLocal instead of {@code @StepScope}</h3>
 * <p>Plugin classes are instantiated via {@code getDeclaredConstructor().newInstance()} — no
 * Spring application context, so {@code @StepScope} is unavailable. The
 * {@link org.springframework.batch.core.partition.support.TaskExecutorPartitionHandler}
 * invokes the SAME step object on N threads concurrently. A plain instance-field delegate
 * would cause race conditions between worker threads. A {@link ThreadLocal} guarantees each
 * thread owns its own {@link JdbcCursorItemReader} from {@code beforeStep} through
 * {@code afterStep} — the entire lifecycle runs on a single thread per partition.
 *
 * <h3>Registration</h3>
 * <p>This class must be registered as BOTH the step's {@code reader} AND as a
 * {@code StepExecutionListener} (via {@code .listener((Object) reader)}) on the worker step
 * builder. This ensures {@link #beforeStep(StepExecution)} fires before
 * {@link #open(ExecutionContext)}.
 *
 * <h3>Restart safety</h3>
 * <p>{@code setSaveState(true)} is set on each per-thread delegate. Spring Batch persists
 * {@code currentItemCount} to {@code BATCH_STEP_EXECUTION_CONTEXT} on every chunk commit.
 * On restart, the delegate re-opens the cursor and fast-forwards past already-committed rows
 * using the saved count, so the partition resumes from its last committed offset.
 *
 * <h3>SQL injection safety</h3>
 * <p>The range SQL uses {@code ?} placeholders bound via {@code PreparedStatementSetter}.
 * The {@code minId} and {@code maxId} values are never concatenated into the SQL string.
 *
 * @see ItemStreamReader
 * @see StepExecutionListener
 */
public class PartitionedHarvesterReader
        implements ItemStreamReader<UsageRecord>, StepExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(PartitionedHarvesterReader.class);

    /** SQL: read only rows within the assigned partition range, ordered for stable restart. */
    private static final String RANGE_SQL =
            "SELECT id, subscriber_id, units, rate"
                    + " FROM usage_record"
                    + " WHERE id BETWEEN ? AND ?"
                    + " ORDER BY id";

    private final DataSource dataSource;

    /**
     * Per-thread delegate reader. Each worker thread sets its own delegate in
     * {@link #beforeStep(StepExecution)} and removes it in {@link #afterStep(StepExecution)}.
     */
    private final ThreadLocal<JdbcCursorItemReader<UsageRecord>> threadLocal = new ThreadLocal<>();

    /**
     * Constructs the reader.
     *
     * @param dataSource the DataSource used to open per-thread JDBC cursors
     */
    public PartitionedHarvesterReader(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Invoked by Spring Batch before the step opens the reader. Reads {@code minId} and
     * {@code maxId} from the step's {@link ExecutionContext} (populated by the partitioner),
     * builds a {@link JdbcCursorItemReader} bound to that range, and stores it in
     * the calling thread's {@link ThreadLocal}.
     *
     * <p>Must be called on the same thread that will subsequently call {@link #open},
     * {@link #read}, {@link #update}, and {@link #close}. This is guaranteed by
     * {@code TaskExecutorPartitionHandler}.
     *
     * @param stepExecution the current step execution containing partition range keys
     */
    @Override
    public void beforeStep(StepExecution stepExecution) {
        ExecutionContext ctx = stepExecution.getExecutionContext();
        long minId = ctx.getLong("minId");
        long maxId = ctx.getLong("maxId");
        String stepName = stepExecution.getStepName();

        log.info("beforeStep: thread={} step={} minId={} maxId={}",
                Thread.currentThread().getName(), stepName, minId, maxId);

        JdbcCursorItemReader<UsageRecord> delegate =
                new JdbcCursorItemReader<>(dataSource, RANGE_SQL, new UsageRecordRowMapper());

        // Unique name per partition so Spring Batch stores state under a distinct key
        delegate.setName("usageReader-" + stepName);

        // Persist currentItemCount to BATCH_STEP_EXECUTION_CONTEXT on each chunk commit
        // so the cursor can fast-forward past already-committed rows on restart.
        delegate.setSaveState(true);

        // Bind minId and maxId as PreparedStatement parameters — injection-safe
        delegate.setPreparedStatementSetter(new PreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps) throws SQLException {
                ps.setLong(1, minId);
                ps.setLong(2, maxId);
            }
        });

        threadLocal.set(delegate);
    }

    /**
     * Removes the ThreadLocal delegate after the step completes. Called on the same thread
     * that ran the partition. Prevents memory leaks in thread-pool environments.
     *
     * @param stepExecution the completed step execution (not used)
     * @return {@code null} (no exit-status override required)
     */
    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        log.debug("afterStep: cleaning up ThreadLocal for thread={}", Thread.currentThread().getName());
        threadLocal.remove();
        return null;
    }

    /**
     * Opens the underlying per-thread delegate reader.
     *
     * @param executionContext the step execution context (passed to the delegate for restart state)
     * @throws ItemStreamException if the delegate cannot open its cursor
     */
    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        threadLocal.get().open(executionContext);
    }

    /**
     * Reads the next {@link UsageRecord} from the current thread's partition cursor.
     *
     * @return the next item, or {@code null} if the partition is exhausted
     * @throws NullPointerException if {@link #beforeStep(StepExecution)} was not called on
     *         this thread (or {@link #afterStep(StepExecution)} has already cleaned up)
     */
    @Override
    public UsageRecord read() throws Exception {
        return threadLocal.get().read();
    }

    /**
     * Updates the per-thread delegate's execution context (persists cursor offset for restart).
     *
     * @param executionContext the context to update
     */
    @Override
    public void update(ExecutionContext executionContext) {
        JdbcCursorItemReader<UsageRecord> delegate = threadLocal.get();
        if (delegate != null) {
            delegate.update(executionContext);
        }
    }

    /**
     * Closes the per-thread delegate reader and releases JDBC resources.
     *
     * @throws ItemStreamException if the delegate cannot close cleanly
     */
    @Override
    public void close() throws ItemStreamException {
        JdbcCursorItemReader<UsageRecord> delegate = threadLocal.get();
        if (delegate != null) {
            delegate.close();
        }
    }
}
