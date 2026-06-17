package com.fronzec.plugins.partitionedharvester.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Unit tests for {@link PartitionedHarvesterReader}.
 *
 * <p>Tests cover: beforeStep range binding, open/read/close delegation, afterStep
 * ThreadLocal cleanup, and thread isolation for concurrent partitions.
 */
class PartitionedHarvesterReaderTest {

    private JdbcDataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        dataSource = new JdbcDataSource();
        // Unique DB per test run; DB_CLOSE_DELAY=-1 keeps it alive across threads
        dataSource.setURL(
                "jdbc:h2:mem:reader-test-"
                        + System.nanoTime()
                        + ";DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        jdbc = new JdbcTemplate(dataSource);

        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS usage_record ("
                        + " id BIGINT PRIMARY KEY,"
                        + " subscriber_id BIGINT NOT NULL,"
                        + " units BIGINT NOT NULL,"
                        + " rate BIGINT NOT NULL,"
                        + " recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                        + " created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                        + ")");
    }

    private void insertRow(long id, long subscriberId, long units, long rate) {
        jdbc.update(
                "INSERT INTO usage_record (id, subscriber_id, units, rate) VALUES (?, ?, ?, ?)",
                id, subscriberId, units, rate);
    }

    private StepExecution stepExecution(long minId, long maxId, String stepName) {
        ExecutionContext ctx = new ExecutionContext();
        ctx.putLong("minId", minId);
        ctx.putLong("maxId", maxId);
        return TestStepExecutionFactory.create(stepName, ctx);
    }

    // ── WU-06-A: beforeStep binds range and reads only rows in range ──────────

    @Test
    void beforeStep_readsOnlyRowsInRange() throws Exception {
        // Insert rows 1-10
        for (long i = 1; i <= 10; i++) {
            insertRow(i, 100L, 5L, 3L);
        }

        PartitionedHarvesterReader reader = new PartitionedHarvesterReader(dataSource);

        StepExecution step = stepExecution(3L, 6L, "usageWorkerStep:partition0");
        reader.beforeStep(step);

        ExecutionContext ctx = new ExecutionContext();
        reader.open(ctx);

        List<Long> ids = new ArrayList<>();
        UsageRecord record;
        while ((record = reader.read()) != null) {
            ids.add(record.id());
        }
        reader.close();

        assertThat(ids).containsExactly(3L, 4L, 5L, 6L);
    }

    // ── WU-06-B: open/read/close delegate to ThreadLocal ─────────────────────

    @Test
    void openReadClose_delegateToThreadLocal() throws Exception {
        insertRow(1L, 100L, 10L, 2L);
        insertRow(2L, 101L, 20L, 3L);

        PartitionedHarvesterReader reader = new PartitionedHarvesterReader(dataSource);
        reader.beforeStep(stepExecution(1L, 2L, "usageWorkerStep:partition0"));

        ExecutionContext ctx = new ExecutionContext();
        reader.open(ctx);

        UsageRecord first = reader.read();
        assertThat(first).isNotNull();
        assertThat(first.id()).isEqualTo(1L);
        assertThat(first.units()).isEqualTo(10L);
        assertThat(first.rateMinor()).isEqualTo(2L);

        UsageRecord second = reader.read();
        assertThat(second).isNotNull();
        assertThat(second.id()).isEqualTo(2L);

        UsageRecord end = reader.read();
        assertThat(end).isNull();

        reader.close();
    }

    // ── WU-06-C: afterStep removes ThreadLocal (leak guard) ──────────────────

    @Test
    void afterStep_removesThreadLocal_readThrowsAfter() throws Exception {
        insertRow(1L, 100L, 5L, 3L);

        PartitionedHarvesterReader reader = new PartitionedHarvesterReader(dataSource);
        reader.beforeStep(stepExecution(1L, 1L, "usageWorkerStep:partition0"));

        ExecutionContext ctx = new ExecutionContext();
        reader.open(ctx);
        reader.read();
        reader.close();

        // afterStep should remove the ThreadLocal
        reader.afterStep(stepExecution(1L, 1L, "usageWorkerStep:partition0"));

        // After cleanup, read() must throw NullPointerException (no delegate)
        assertThatThrownBy(reader::read)
                .isInstanceOf(NullPointerException.class);
    }

    // ── WU-06-D: thread isolation — two threads read disjoint ranges ──────────

    @Test
    void threadIsolation_concurrentReaders_doNotCrossContaminate() throws Exception {
        // Insert rows 1-10
        for (long i = 1; i <= 10; i++) {
            insertRow(i, 200L, 1L, 1L);
        }

        PartitionedHarvesterReader reader = new PartitionedHarvesterReader(dataSource);

        CountDownLatch beforeStepDone = new CountDownLatch(2);
        CountDownLatch startRead = new CountDownLatch(1);
        AtomicReference<List<Long>> thread1Ids = new AtomicReference<>();
        AtomicReference<List<Long>> thread2Ids = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();

        ExecutorService pool = Executors.newFixedThreadPool(2);

        Future<?> f1 = pool.submit(() -> {
            try {
                reader.beforeStep(stepExecution(1L, 5L, "usageWorkerStep:partition0"));
                beforeStepDone.countDown();
                startRead.await();
                ExecutionContext ctx = new ExecutionContext();
                reader.open(ctx);
                List<Long> ids = new ArrayList<>();
                UsageRecord rec;
                while ((rec = reader.read()) != null) {
                    ids.add(rec.id());
                }
                reader.close();
                reader.afterStep(stepExecution(1L, 5L, "usageWorkerStep:partition0"));
                thread1Ids.set(ids);
            } catch (Throwable t) {
                error.set(t);
            }
        });

        Future<?> f2 = pool.submit(() -> {
            try {
                reader.beforeStep(stepExecution(6L, 10L, "usageWorkerStep:partition1"));
                beforeStepDone.countDown();
                startRead.await();
                ExecutionContext ctx = new ExecutionContext();
                reader.open(ctx);
                List<Long> ids = new ArrayList<>();
                UsageRecord rec;
                while ((rec = reader.read()) != null) {
                    ids.add(rec.id());
                }
                reader.close();
                reader.afterStep(stepExecution(6L, 10L, "usageWorkerStep:partition1"));
                thread2Ids.set(ids);
            } catch (Throwable t) {
                error.set(t);
            }
        });

        beforeStepDone.await();
        startRead.countDown();
        f1.get();
        f2.get();
        pool.shutdown();

        assertThat(error.get()).isNull();
        assertThat(thread1Ids.get()).containsExactly(1L, 2L, 3L, 4L, 5L);
        assertThat(thread2Ids.get()).containsExactly(6L, 7L, 8L, 9L, 10L);
    }

    // ── WU-06-E: empty range reads 0 rows ────────────────────────────────────

    @Test
    void emptyRange_readsZeroRows() throws Exception {
        // Table is empty
        PartitionedHarvesterReader reader = new PartitionedHarvesterReader(dataSource);
        reader.beforeStep(stepExecution(1L, 0L, "usageWorkerStep:partition0"));

        ExecutionContext ctx = new ExecutionContext();
        reader.open(ctx);
        assertThat(reader.read()).isNull();
        reader.close();
    }
}
