package com.fronzec.plugins.partitionedharvester.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Unit tests for {@link BillingChargeWriter} using an in-memory H2 database.
 *
 * <p>Covers: single write, idempotency (same source_id twice), batch write,
 * mixed batch (new + duplicate), and frozen-source verification (usage_record not touched).
 */
class BillingChargeWriterTest {

    private JdbcTemplate jdbc;
    private BillingChargeWriter writer;

    @BeforeEach
    void setUp() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:writer-billing-test-"
                + System.nanoTime()
                + ";DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE");
        ds.setUser("sa");
        ds.setPassword("");
        jdbc = new JdbcTemplate(ds);

        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS usage_record ("
                        + " id BIGINT PRIMARY KEY,"
                        + " subscriber_id BIGINT NOT NULL,"
                        + " units BIGINT NOT NULL,"
                        + " rate BIGINT NOT NULL,"
                        + " recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                        + " created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                        + ")");

        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS billing_charge ("
                        + " id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                        + " source_id BIGINT NOT NULL,"
                        + " subscriber_id BIGINT NOT NULL,"
                        + " units BIGINT NOT NULL,"
                        + " rate BIGINT NOT NULL,"
                        + " cost BIGINT NOT NULL,"
                        + " job_execution_id BIGINT NULL,"
                        + " recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                        + " CONSTRAINT uk_billing_charge_source UNIQUE (source_id)"
                        + ")");

        writer = new BillingChargeWriter(jdbc);
    }

    private void insertUsageRecord(long id) {
        jdbc.update(
                "INSERT INTO usage_record (id, subscriber_id, units, rate) VALUES (?, 100, 5, 3)",
                id);
    }

    private int countBillingCharges() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM billing_charge", Integer.class);
        return count != null ? count : 0;
    }

    private long getCostForSourceId(long sourceId) {
        Long cost = jdbc.queryForObject(
                "SELECT cost FROM billing_charge WHERE source_id = ?", Long.class, sourceId);
        return cost != null ? cost : -1L;
    }

    // ── Single write ──────────────────────────────────────────────────────────

    @Test
    void write_singleCharge_insertsOneRow() throws Exception {
        BillingCharge charge = new BillingCharge(1L, 100L, 5L, 3L, 15L);

        writer.write(new Chunk<>(charge));

        assertThat(countBillingCharges()).isEqualTo(1);
        assertThat(getCostForSourceId(1L)).isEqualTo(15L);
    }

    // ── Idempotency — same source_id twice is a no-op ─────────────────────────

    @Test
    void write_sameSourceIdTwice_stillOneRow_costUnchanged() throws Exception {
        BillingCharge first = new BillingCharge(5L, 200L, 10L, 4L, 40L);
        BillingCharge duplicate = new BillingCharge(5L, 200L, 10L, 4L, 40L);

        writer.write(new Chunk<>(first));
        writer.write(new Chunk<>(duplicate));

        assertThat(countBillingCharges()).isEqualTo(1);
        assertThat(getCostForSourceId(5L)).isEqualTo(40L);
    }

    // ── Batch write ───────────────────────────────────────────────────────────

    @Test
    void write_batchOfThree_insertsThreeRows() throws Exception {
        BillingCharge c1 = new BillingCharge(10L, 300L, 2L, 5L, 10L);
        BillingCharge c2 = new BillingCharge(11L, 301L, 3L, 5L, 15L);
        BillingCharge c3 = new BillingCharge(12L, 302L, 4L, 5L, 20L);

        writer.write(new Chunk<>(c1, c2, c3));

        assertThat(countBillingCharges()).isEqualTo(3);
    }

    // ── Mixed batch (1 new + 1 duplicate) ────────────────────────────────────

    @Test
    void write_mixedBatch_duplicateSkipped_newInserted() throws Exception {
        BillingCharge existing = new BillingCharge(20L, 400L, 1L, 1L, 1L);
        writer.write(new Chunk<>(existing));
        assertThat(countBillingCharges()).isEqualTo(1);

        BillingCharge duplicate = new BillingCharge(20L, 400L, 1L, 1L, 1L);
        BillingCharge newCharge = new BillingCharge(21L, 401L, 2L, 2L, 4L);
        writer.write(new Chunk<>(duplicate, newCharge));

        // Total: still 2 rows (not 3)
        assertThat(countBillingCharges()).isEqualTo(2);
        assertThat(getCostForSourceId(20L)).isEqualTo(1L);
        assertThat(getCostForSourceId(21L)).isEqualTo(4L);
    }

    // ── Frozen-source: usage_record is NOT modified ───────────────────────────

    @Test
    void write_doesNotMutateUsageRecord() throws Exception {
        insertUsageRecord(30L);

        BillingCharge charge = new BillingCharge(30L, 100L, 5L, 3L, 15L);
        writer.write(new Chunk<>(charge));

        // usage_record row should still exist and be unchanged
        List<Long> ids = jdbc.queryForList("SELECT id FROM usage_record", Long.class);
        assertThat(ids).containsExactly(30L);
    }

    // ── Empty chunk ───────────────────────────────────────────────────────────

    @Test
    void write_emptyChunk_insertsNothing() throws Exception {
        writer.write(new Chunk<>());

        assertThat(countBillingCharges()).isEqualTo(0);
    }
}
