package com.fronzec.plugins.harvester.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Unit tests for {@link HarvestSkipListener}.
 *
 * <p>Key proof: dead-letter INSERT commits independently under REQUIRES_NEW even when the
 * "outer" transaction (simulated via a TransactionTemplate that rolls back) is rolled back.
 */
class HarvestSkipListenerTest {

    private JdbcTemplate jdbc;
    private PlatformTransactionManager txManager;
    private HarvestSkipListener skipListener;

    @BeforeEach
    void setUp() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:skip-listener-test-" + System.nanoTime()
                + ";DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE");
        ds.setUser("sa");
        ds.setPassword("");
        jdbc = new JdbcTemplate(ds);
        txManager = new DataSourceTransactionManager(ds);

        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS harvest_source ("
                        + " id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                        + " payload VARCHAR(2048) NOT NULL,"
                        + " poison_flag BOOLEAN NOT NULL DEFAULT FALSE,"
                        + " transient_fail_until_attempt INT NOT NULL DEFAULT 0,"
                        + " abort_flag BOOLEAN NOT NULL DEFAULT FALSE,"
                        + " processed BOOLEAN NOT NULL DEFAULT FALSE,"
                        + " created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                        + ")");

        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS harvest_dead_letter ("
                        + " id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                        + " source_id BIGINT NULL,"
                        + " raw_payload VARCHAR(2048) NULL,"
                        + " failure_phase VARCHAR(16) NOT NULL,"
                        + " failure_type VARCHAR(16) NOT NULL,"
                        + " exception_class VARCHAR(512) NOT NULL,"
                        + " exception_msg VARCHAR(2048) NULL,"
                        + " attempt_count INT NOT NULL DEFAULT 1,"
                        + " job_execution_id BIGINT NOT NULL,"
                        + " recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                        + ")");

        skipListener = new HarvestSkipListener(jdbc, txManager);
        skipListener.setJobExecutionId(42L);
    }

    // ── onSkipInProcess: PoisonItemException → failure_type='SKIP' ────────────────────────────

    @Test
    void onSkipInProcess_poison_insertsSkipDeadLetter() {
        HarvestRow row = new HarvestRow(1L, "bad-payload", true, 0, false);
        PoisonItemException ex = new PoisonItemException(1L);

        skipListener.onSkipInProcess(row, ex);

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM harvest_dead_letter WHERE source_id = 1");
        assertThat(rows).hasSize(1);
        Map<String, Object> dl = rows.get(0);
        assertThat(dl.get("failure_type")).isEqualTo("SKIP");
        assertThat(dl.get("failure_phase")).isEqualTo("PROCESS");
        assertThat(dl.get("source_id")).isEqualTo(1L);
        assertThat(dl.get("job_execution_id")).isEqualTo(42L);
        assertThat(dl.get("attempt_count")).isEqualTo(1);
        assertThat(dl.get("exception_class")).isEqualTo(PoisonItemException.class.getName());
    }

    // ── onSkipInProcess: TransientProcessingException → failure_type='RETRY_EXHAUSTED' ────────

    @Test
    void onSkipInProcess_transient_insertsRetryExhaustedDeadLetter() {
        HarvestRow row = new HarvestRow(2L, "transient-payload", false, 10, false);
        TransientProcessingException ex = new TransientProcessingException(2L, 3, 10);

        skipListener.onSkipInProcess(row, ex);

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM harvest_dead_letter WHERE source_id = 2");
        assertThat(rows).hasSize(1);
        Map<String, Object> dl = rows.get(0);
        assertThat(dl.get("failure_type")).isEqualTo("RETRY_EXHAUSTED");
        assertThat(dl.get("failure_phase")).isEqualTo("PROCESS");
        assertThat(dl.get("attempt_count")).isEqualTo(4); // retryLimit(3) + 1
        assertThat(dl.get("exception_class")).isEqualTo(TransientProcessingException.class.getName());
    }

    // ── REQUIRES_NEW independence: dead-letter survives outer rollback ────────────────────────

    @Test
    void onSkipInProcess_deadLetterSurvivesOuterRollback() {
        HarvestRow row = new HarvestRow(3L, "rollback-test", true, 0, false);
        PoisonItemException ex = new PoisonItemException(3L);

        // Simulate an outer transaction that will roll back
        TransactionTemplate outerTx = new TransactionTemplate(txManager);
        try {
            outerTx.execute(status -> {
                // Inside a transaction that will be rolled back
                skipListener.onSkipInProcess(row, ex); // this commits independently under REQUIRES_NEW
                status.setRollbackOnly(); // force outer rollback
                return null;
            });
        } catch (Exception ignored) {
            // Outer transaction rolled back — expected
        }

        // Dead-letter record MUST still be present (committed under REQUIRES_NEW)
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM harvest_dead_letter WHERE source_id = 3",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    // ── exception_msg truncation guard (S-01) ─────────────────────────────────────────────────

    @Test
    void truncateMsg_longMessage_isTruncatedTo2048Chars() {
        // Build a string longer than 2048 chars
        String overLong = "x".repeat(3000);
        String result = HarvestSkipListener.truncateMsg(overLong);
        assertThat(result).hasSize(2048);
    }

    @Test
    void truncateMsg_shortMessage_isReturnedUnchanged() {
        String msg = "short message";
        assertThat(HarvestSkipListener.truncateMsg(msg)).isSameAs(msg);
    }

    @Test
    void truncateMsg_null_isReturnedAsNull() {
        assertThat(HarvestSkipListener.truncateMsg(null)).isNull();
    }

    @Test
    void onSkipInProcess_longExceptionMessage_storedTruncated() {
        // S-01: verify that a verbose exception message is truncated in the DB row
        String overLong = "E".repeat(3000);
        HarvestRow row = new HarvestRow(5L, "payload-5", true, 0, false);
        // Craft a PoisonItemException that reports the over-long message
        PoisonItemException ex = new PoisonItemException(overLong); // use String-message ctor

        skipListener.onSkipInProcess(row, ex);

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT exception_msg FROM harvest_dead_letter WHERE source_id = 5");
        assertThat(rows).hasSize(1);
        String storedMsg = (String) rows.get(0).get("exception_msg");
        assertThat(storedMsg).hasSize(2048);
    }

    // ── Unwrapping: wrapped exception still resolves correct failure_type ─────────────────────

    @Test
    void onSkipInProcess_wrappedPoisonException_unwrapsCorrectly() {
        HarvestRow row = new HarvestRow(4L, "wrapped", true, 0, false);
        // Wrapped in a generic RuntimeException
        RuntimeException wrapped = new RuntimeException("wrapper", new PoisonItemException(4L));

        skipListener.onSkipInProcess(row, wrapped);

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM harvest_dead_letter WHERE source_id = 4");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("failure_type")).isEqualTo("SKIP");
        // The recorded exception_class is the inner PoisonItemException
        assertThat(rows.get(0).get("exception_class"))
                .isEqualTo(PoisonItemException.class.getName());
    }
}
