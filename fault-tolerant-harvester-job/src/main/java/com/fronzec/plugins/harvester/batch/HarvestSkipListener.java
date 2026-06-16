package com.fronzec.plugins.harvester.batch;

import java.sql.Timestamp;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Writes a {@code harvest_dead_letter} row for every skipped item, under its own independent
 * transaction ({@code PROPAGATION_REQUIRES_NEW}).
 *
 * <h3>Transaction independence</h3>
 * <p>The dead-letter INSERT uses a {@link TransactionTemplate} configured with
 * {@link TransactionDefinition#PROPAGATION_REQUIRES_NEW}. This guarantees that the audit
 * record commits independently of the surrounding chunk transaction, so a chunk rollback
 * cannot erase the dead-letter evidence.
 *
 * <p>An explicit REQUIRES_NEW is used even though Spring Batch 6 calls the skip listener
 * AFTER the chunk is rolled back — the explicit independent transaction is more defensive
 * and avoids relying on listener-call-timing semantics across SB versions.
 *
 * <h3>failure_type distinction</h3>
 * <ul>
 *   <li>{@link PoisonItemException} → {@code failure_type='SKIP'}</li>
 *   <li>{@link TransientProcessingException} reaching skip (retry exhausted) →
 *       {@code failure_type='RETRY_EXHAUSTED'}</li>
 *   <li>Any other throwable in process → {@code failure_type='SKIP'} (generic skip)</li>
 * </ul>
 *
 * <h3>attempt_count</h3>
 * <p>For {@code RETRY_EXHAUSTED} the stored {@code attempt_count} is {@code RETRY_LIMIT + 1 = 4},
 * representing the TOTAL number of processing calls: 1 initial attempt + 3 retries = 4 total.
 * This is intentional — total calls is more useful for diagnostics than the raw retry-limit constant.
 * For {@code SKIP} items (no retry) the count is always 1.
 * See also the README "attempt_count semantics" note.
 *
 * <h3>exception_msg truncation</h3>
 * <p>The {@code exception_msg} value is truncated to 2048 characters before insertion to guard
 * against column overflow on MySQL (VARCHAR(2048)) when non-domain exceptions (e.g. JDBC errors
 * with verbose messages) reach the skip listener via {@code onSkipInRead}. Domain exceptions
 * ({@link PoisonItemException}, {@link TransientProcessingException}) have bounded messages and
 * will never approach the limit, but defensive truncation is applied uniformly.
 *
 * <h3>job_execution_id</h3>
 * <p>Captured via {@link #setJobExecutionId(long)} called from
 * {@link FaultTolerantHarvesterJobPlugin#configureJob} via a step-execution listener, or
 * falls back to {@code -1} when not set (should not happen in normal operation).
 */
public class HarvestSkipListener implements SkipListener<HarvestRow, HarvestRow> {

    private static final Logger log = LoggerFactory.getLogger(HarvestSkipListener.class);

    /**
     * Number of retries configured on the step (retryLimit=3). Used to compute attempt_count
     * for RETRY_EXHAUSTED entries (total calls = retryLimit + 1 = 4).
     */
    private static final int RETRY_LIMIT = 3;

    /**
     * Maximum length of {@code exception_msg} stored in harvest_dead_letter.
     * Matches the MySQL column definition (VARCHAR(2048)). Messages longer than this are
     * truncated to avoid column-overflow exceptions on verbose non-domain exceptions
     * (e.g. JDBC errors in onSkipInRead).
     */
    private static final int MAX_MSG_LENGTH = 2048;

    private static final String INSERT_SQL =
            "INSERT INTO harvest_dead_letter"
                    + " (source_id, raw_payload, failure_phase, failure_type,"
                    + "  exception_class, exception_msg, attempt_count,"
                    + "  job_execution_id, recorded_at)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate requiresNewTx;

    /** Job execution id captured from the step; set before any skip callback fires. */
    private volatile long jobExecutionId = -1L;

    /**
     * Constructs the skip listener.
     *
     * @param jdbcTemplate       JDBC template backed by the host's DataSource
     * @param transactionManager the platform transaction manager used to create the
     *                           REQUIRES_NEW transaction template
     */
    public HarvestSkipListener(JdbcTemplate jdbcTemplate,
                                PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Sets the job execution id to record in dead-letter rows.
     *
     * <p>Called from {@code configureJob} via a {@code StepExecutionListener.beforeStep}
     * callback so the value is available before any chunk runs.
     *
     * @param jobExecutionId the {@code BATCH_JOB_EXECUTION.id} for the current run
     */
    public void setJobExecutionId(long jobExecutionId) {
        this.jobExecutionId = jobExecutionId;
    }

    /**
     * Called when the processor skips an item.
     *
     * <p>Writes a dead-letter record under REQUIRES_NEW. Distinguishes
     * {@link PoisonItemException} ({@code failure_type='SKIP'}) from
     * {@link TransientProcessingException} ({@code failure_type='RETRY_EXHAUSTED'}).
     *
     * @param item      the skipped harvest row
     * @param throwable the exception that caused the skip (may be the original or a wrapper)
     */
    @Override
    public void onSkipInProcess(HarvestRow item, Throwable throwable) {
        // Unwrap if the exception is wrapped by a retry framework.
        Throwable cause = unwrap(throwable);

        String failureType;
        int attemptCount;

        if (cause instanceof TransientProcessingException) {
            failureType = "RETRY_EXHAUSTED";
            attemptCount = RETRY_LIMIT + 1; // initial attempt + retryLimit retries
        } else {
            // PoisonItemException or any other skippable exception
            failureType = "SKIP";
            attemptCount = 1;
        }

        log.warn("Skip in process: id={} failureType={} exception={}",
                item.id(), failureType, cause.getClass().getSimpleName());

        insertDeadLetter(item, "PROCESS", failureType, cause, attemptCount);
    }

    /**
     * Called when the reader skips an item. Writes a dead-letter record with
     * {@code failure_phase='READ'} and {@code source_id=NULL} (no item available).
     *
     * @param throwable the exception that caused the skip during reading
     */
    @Override
    public void onSkipInRead(Throwable throwable) {
        Throwable cause = unwrap(throwable);
        log.warn("Skip in read: exception={}", cause.getClass().getSimpleName());

        String failureType = (cause instanceof TransientProcessingException)
                ? "RETRY_EXHAUSTED" : "SKIP";
        int attemptCount = (cause instanceof TransientProcessingException) ? RETRY_LIMIT + 1 : 1;

        requiresNewTx.execute(status -> {
            jdbcTemplate.update(INSERT_SQL,
                    null,                                    // source_id — unknown on read skip
                    null,                                    // raw_payload — unknown
                    "READ",
                    failureType,
                    cause.getClass().getName(),
                    truncateMsg(cause.getMessage()),         // guard: VARCHAR(2048)
                    attemptCount,
                    jobExecutionId,
                    Timestamp.from(Instant.now()));
            return null;
        });
    }

    /**
     * Called when the writer skips an item. Writes a dead-letter record with
     * {@code failure_phase='WRITE'}.
     *
     * @param item      the skipped harvest row
     * @param throwable the exception that caused the skip during writing
     */
    @Override
    public void onSkipInWrite(HarvestRow item, Throwable throwable) {
        Throwable cause = unwrap(throwable);
        log.warn("Skip in write: id={} exception={}", item.id(), cause.getClass().getSimpleName());

        String failureType = (cause instanceof TransientProcessingException)
                ? "RETRY_EXHAUSTED" : "SKIP";
        int attemptCount = (cause instanceof TransientProcessingException) ? RETRY_LIMIT + 1 : 1;

        insertDeadLetter(item, "WRITE", failureType, cause, attemptCount);
    }

    // ── Private helpers ───────────────────────────────────────────────────────────────────────

    /**
     * Inserts one dead-letter record under a REQUIRES_NEW transaction.
     *
     * @param item         the harvested row (provides source_id and raw_payload)
     * @param failurePhase READ, PROCESS, or WRITE
     * @param failureType  SKIP or RETRY_EXHAUSTED
     * @param cause        the actual (unwrapped) exception
     * @param attemptCount total number of processing attempts for this item
     */
    private void insertDeadLetter(HarvestRow item,
                                   String failurePhase,
                                   String failureType,
                                   Throwable cause,
                                   int attemptCount) {
        requiresNewTx.execute(status -> {
            jdbcTemplate.update(INSERT_SQL,
                    item.id(),
                    item.payload(),
                    failurePhase,
                    failureType,
                    cause.getClass().getName(),
                    truncateMsg(cause.getMessage()),         // guard: VARCHAR(2048)
                    attemptCount,
                    jobExecutionId,
                    Timestamp.from(Instant.now()));
            return null;
        });
    }

    /**
     * Unwraps a single layer of exception wrapping if the direct throwable is not one of the
     * domain exceptions. Spring Batch may wrap the original cause in some scenarios.
     *
     * @param throwable the potentially wrapped exception
     * @return the original domain exception if the outer exception was a wrapper, otherwise
     *         the throwable itself
     */
    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof PoisonItemException
                || throwable instanceof TransientProcessingException
                || throwable instanceof AbortJobException) {
            return throwable;
        }
        Throwable cause = throwable.getCause();
        return (cause != null) ? cause : throwable;
    }

    /**
     * Truncates a string to {@link #MAX_MSG_LENGTH} to prevent column-overflow on MySQL
     * VARCHAR(2048) when verbose non-domain exception messages reach the dead-letter insert.
     *
     * @param msg the raw message string; may be null
     * @return the original string if its length is within the limit, a truncated substring
     *         otherwise; null is returned unchanged
     */
    static String truncateMsg(String msg) {
        if (msg == null || msg.length() <= MAX_MSG_LENGTH) {
            return msg;
        }
        return msg.substring(0, MAX_MSG_LENGTH);
    }
}
