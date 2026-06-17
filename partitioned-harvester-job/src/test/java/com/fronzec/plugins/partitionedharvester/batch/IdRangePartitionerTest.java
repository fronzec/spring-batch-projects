package com.fronzec.plugins.partitionedharvester.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Unit tests for {@link IdRangePartitioner} covering all spec scenarios from REQ-01.
 *
 * <p>Uses an H2 in-memory {@code usage_record} table (not mocking the JdbcTemplate) so
 * the actual SQL executes against a real database.
 *
 * <p>Scenarios covered:
 * <ul>
 *   <li>SC-01.1 — standard equal-width split</li>
 *   <li>SC-01.1 remainder — last partition absorbs the remainder</li>
 *   <li>SC-01.2 — empty table (MIN/MAX null)</li>
 *   <li>SC-01.3 — single row</li>
 *   <li>SC-01.4 — gridSize larger than row count</li>
 *   <li>SC-01.5 — id gaps / skew</li>
 *   <li>Range contiguity invariant</li>
 *   <li>ExecutionContext keys present: minId, maxId, partitionName</li>
 * </ul>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IdRangePartitionerTest {

    private JdbcTemplate jdbc;

    @BeforeAll
    void setUpDatabase() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:partitioner-test;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE");
        ds.setUser("sa");
        ds.setPassword("");
        jdbc = new JdbcTemplate(ds);

        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS usage_record ("
                        + "id            BIGINT    PRIMARY KEY AUTO_INCREMENT,"
                        + "subscriber_id BIGINT    NOT NULL,"
                        + "units         BIGINT    NOT NULL,"
                        + "rate          BIGINT    NOT NULL,"
                        + "recorded_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                        + "created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                        + ")");
    }

    @BeforeEach
    void clearTable() {
        jdbc.execute("DELETE FROM usage_record");
    }

    // ── SC-01.1 — Standard equal-width split ─────────────────────────────────

    /**
     * Given MIN=1, MAX=100, gridSize=4:
     * rangeSize = floor((100-1)/4) = floor(99/4) = 24.
     * Expect: [1,24],[25,48],[49,72],[73,100] — last absorbs remainder 3.
     */
    @Test
    void standardSplit_evenDivision() {
        // Insert rows at id=1 and id=100 to set MIN/MAX
        jdbc.update("INSERT INTO usage_record (id, subscriber_id, units, rate) VALUES (1, 1, 1, 1)");
        jdbc.update("INSERT INTO usage_record (id, subscriber_id, units, rate) VALUES (100, 1, 1, 1)");

        IdRangePartitioner partitioner = new IdRangePartitioner(jdbc, 4);
        Map<String, ExecutionContext> partitions = partitioner.partition(4);

        assertThat(partitions).hasSize(4);

        // rangeSize = 24; last partition absorbs remainder
        assertRange(partitions, "partition0", 1L, 24L);
        assertRange(partitions, "partition1", 25L, 48L);
        assertRange(partitions, "partition2", 49L, 72L);
        assertRange(partitions, "partition3", 73L, 100L);
    }

    // ── SC-01.1 remainder — last partition absorbs remainder ─────────────────

    /**
     * Given MIN=1, MAX=101, gridSize=4:
     * span=100, rangeSize = floor(100/4) = 25. Last partition absorbs to MAX=101.
     * Expect: [1,25],[26,50],[51,75],[76,101]
     */
    @Test
    void standardSplit_remainderAbsorbedByLastPartition() {
        jdbc.update("INSERT INTO usage_record (id, subscriber_id, units, rate) VALUES (1, 1, 1, 1)");
        jdbc.update("INSERT INTO usage_record (id, subscriber_id, units, rate) VALUES (101, 1, 1, 1)");

        IdRangePartitioner partitioner = new IdRangePartitioner(jdbc, 4);
        Map<String, ExecutionContext> partitions = partitioner.partition(4);

        assertThat(partitions).hasSize(4);

        // rangeSize = 25 (exact division of span=100)
        assertRange(partitions, "partition0", 1L, 25L);
        assertRange(partitions, "partition1", 26L, 50L);
        assertRange(partitions, "partition2", 51L, 75L);
        // last partition extends all the way to MAX=101
        assertRange(partitions, "partition3", 76L, 101L);
    }

    // ── SC-01.2 — Empty table ─────────────────────────────────────────────────

    /**
     * Given an empty table (MIN/MAX return NULL):
     * expect 1 context with minId=1 and maxId=0 so the worker reads 0 rows; no NPE.
     */
    @Test
    void emptyTable_returnsSingleEmptyContext() {
        // Table is empty (cleared in @BeforeEach)

        IdRangePartitioner partitioner = new IdRangePartitioner(jdbc, 4);
        Map<String, ExecutionContext> partitions = partitioner.partition(4);

        assertThat(partitions).hasSize(1);
        ExecutionContext ctx = partitions.get("partition0");
        assertThat(ctx).isNotNull();
        assertThat(ctx.getLong("minId")).isEqualTo(1L);
        assertThat(ctx.getLong("maxId")).isEqualTo(0L);
        assertThat(ctx.getString("partitionName")).isEqualTo("partition0");
    }

    // ── SC-01.3 — Single row ──────────────────────────────────────────────────

    /**
     * Given a single row with id=42, gridSize=4:
     * expect 4 ranges produced; ranges are valid (non-overlapping, contiguous);
     * exactly one range contains id=42.
     */
    @Test
    void singleRow_producesGridSizeRanges() {
        jdbc.update("INSERT INTO usage_record (id, subscriber_id, units, rate) VALUES (42, 1, 1, 1)");

        IdRangePartitioner partitioner = new IdRangePartitioner(jdbc, 4);
        Map<String, ExecutionContext> partitions = partitioner.partition(4);

        assertThat(partitions).hasSize(4);

        // MIN=MAX=42 → rangeSize = 0; all ranges collapse around 42
        // Verify the union covers [42, 42]
        long globalMin = Long.MAX_VALUE;
        long globalMax = Long.MIN_VALUE;
        for (ExecutionContext ctx : partitions.values()) {
            long min = ctx.getLong("minId");
            long max = ctx.getLong("maxId");
            if (min < globalMin) globalMin = min;
            if (max > globalMax) globalMax = max;
        }
        assertThat(globalMin).isEqualTo(42L);
        assertThat(globalMax).isEqualTo(42L);

        // All partition names are present
        assertThat(partitions).containsKey("partition0");
        assertThat(partitions).containsKey("partition3");
    }

    // ── SC-01.4 — gridSize > row count ───────────────────────────────────────

    /**
     * Given rows at ids {10, 20, 30}, gridSize=10:
     * expect 10 contexts covering [10, 30]; empty trailing ranges are valid (min > max).
     */
    @Test
    void gridSizeLargerThanRowCount_producesGridSizeContexts() {
        jdbc.update("INSERT INTO usage_record (id, subscriber_id, units, rate) VALUES (10, 1, 1, 1)");
        jdbc.update("INSERT INTO usage_record (id, subscriber_id, units, rate) VALUES (20, 1, 1, 1)");
        jdbc.update("INSERT INTO usage_record (id, subscriber_id, units, rate) VALUES (30, 1, 1, 1)");

        IdRangePartitioner partitioner = new IdRangePartitioner(jdbc, 10);
        Map<String, ExecutionContext> partitions = partitioner.partition(10);

        assertThat(partitions).hasSize(10);

        // First partition starts at MIN=10
        assertThat(partitions.get("partition0").getLong("minId")).isEqualTo(10L);
        // Last partition ends at MAX=30
        assertThat(partitions.get("partition9").getLong("maxId")).isEqualTo(30L);
    }

    // ── SC-01.5 — Id gaps / skew ──────────────────────────────────────────────

    /**
     * Given ids {1, 1000, 1001, 1002} (MIN=1, MAX=1002), gridSize=4:
     * arithmetic ranges are based purely on [1, 1002]; gaps are irrelevant.
     * Range union must still cover [1, 1002] contiguously.
     */
    @Test
    void idGaps_arithmeticRangesCoverFullInterval() {
        jdbc.update("INSERT INTO usage_record (id, subscriber_id, units, rate) VALUES (1, 1, 1, 1)");
        jdbc.update("INSERT INTO usage_record (id, subscriber_id, units, rate) VALUES (1000, 1, 1, 1)");
        jdbc.update("INSERT INTO usage_record (id, subscriber_id, units, rate) VALUES (1001, 1, 1, 1)");
        jdbc.update("INSERT INTO usage_record (id, subscriber_id, units, rate) VALUES (1002, 1, 1, 1)");

        IdRangePartitioner partitioner = new IdRangePartitioner(jdbc, 4);
        Map<String, ExecutionContext> partitions = partitioner.partition(4);

        assertThat(partitions).hasSize(4);

        // First range starts at MIN=1
        assertThat(partitions.get("partition0").getLong("minId")).isEqualTo(1L);
        // Last range ends at MAX=1002
        assertThat(partitions.get("partition3").getLong("maxId")).isEqualTo(1002L);

        // Ranges are contiguous
        assertContiguous(partitions, 4);
    }

    // ── Range contiguity invariant ────────────────────────────────────────────

    /**
     * Contiguity invariant: for any valid result with >= 2 ranges,
     * range[i].maxId + 1 == range[i+1].minId for all i in [0, N-2].
     */
    @Test
    void contiguityInvariant_standard() {
        jdbc.update("INSERT INTO usage_record (id, subscriber_id, units, rate) VALUES (1, 1, 1, 1)");
        jdbc.update("INSERT INTO usage_record (id, subscriber_id, units, rate) VALUES (100, 1, 1, 1)");

        IdRangePartitioner partitioner = new IdRangePartitioner(jdbc, 4);
        Map<String, ExecutionContext> partitions = partitioner.partition(4);

        assertContiguous(partitions, 4);
    }

    // ── ExecutionContext keys present ─────────────────────────────────────────

    /**
     * Each context must carry minId, maxId, and partitionName keys.
     */
    @Test
    void executionContextKeys_presentInAllPartitions() {
        jdbc.update("INSERT INTO usage_record (id, subscriber_id, units, rate) VALUES (1, 1, 1, 1)");
        jdbc.update("INSERT INTO usage_record (id, subscriber_id, units, rate) VALUES (50, 1, 1, 1)");

        IdRangePartitioner partitioner = new IdRangePartitioner(jdbc, 3);
        Map<String, ExecutionContext> partitions = partitioner.partition(3);

        for (Map.Entry<String, ExecutionContext> entry : partitions.entrySet()) {
            ExecutionContext ctx = entry.getValue();
            assertThat(ctx.containsKey("minId"))
                    .as("minId key missing in %s", entry.getKey()).isTrue();
            assertThat(ctx.containsKey("maxId"))
                    .as("maxId key missing in %s", entry.getKey()).isTrue();
            assertThat(ctx.containsKey("partitionName"))
                    .as("partitionName key missing in %s", entry.getKey()).isTrue();
            assertThat(ctx.getString("partitionName"))
                    .as("partitionName value mismatch in %s", entry.getKey())
                    .isEqualTo(entry.getKey());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void assertRange(
            Map<String, ExecutionContext> partitions, String key, long expectedMin, long expectedMax) {
        ExecutionContext ctx = partitions.get(key);
        assertThat(ctx).as("partition context for key '%s' is null", key).isNotNull();
        assertThat(ctx.getLong("minId"))
                .as("minId for '%s'", key).isEqualTo(expectedMin);
        assertThat(ctx.getLong("maxId"))
                .as("maxId for '%s'", key).isEqualTo(expectedMax);
    }

    /**
     * Asserts that partitions named partition0..partition{count-1} are contiguous:
     * partition[i].maxId + 1 == partition[i+1].minId.
     */
    private void assertContiguous(Map<String, ExecutionContext> partitions, int count) {
        for (int i = 0; i < count - 1; i++) {
            ExecutionContext curr = partitions.get("partition" + i);
            ExecutionContext next = partitions.get("partition" + (i + 1));
            assertThat(curr).as("partition%d missing", i).isNotNull();
            assertThat(next).as("partition%d missing", i + 1).isNotNull();
            long currMax = curr.getLong("maxId");
            long nextMin = next.getLong("minId");
            assertThat(currMax + 1)
                    .as("partition%d.maxId+1 (%d) should equal partition%d.minId (%d)",
                            i, currMax + 1, i + 1, nextMin)
                    .isEqualTo(nextMin);
        }
    }
}
