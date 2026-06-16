package com.fronzec.plugins.harvester.batch;

import static org.assertj.core.api.Assertions.assertThat;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Unit tests for {@link HarvestItemWriter} using an in-memory H2 database.
 */
class HarvestItemWriterTest {

    private JdbcTemplate jdbc;
    private HarvestItemWriter writer;

    @BeforeEach
    void setUp() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:writer-test-" + System.nanoTime()
                + ";DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE");
        ds.setUser("sa");
        ds.setPassword("");
        jdbc = new JdbcTemplate(ds);

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

        writer = new HarvestItemWriter(jdbc);
    }

    private long insertRow(long id, String payload) {
        jdbc.update(
                "INSERT INTO harvest_source (id, payload, poison_flag, transient_fail_until_attempt,"
                        + " abort_flag, processed) VALUES (?, ?, FALSE, 0, FALSE, FALSE)",
                id, payload);
        return id;
    }

    private boolean isProcessed(long id) {
        Boolean result = jdbc.queryForObject(
                "SELECT processed FROM harvest_source WHERE id = ?", Boolean.class, id);
        return Boolean.TRUE.equals(result);
    }

    @Test
    void write_marksRowsAsProcessed() throws Exception {
        insertRow(1L, "payload-1");
        insertRow(2L, "payload-2");

        HarvestRow row1 = new HarvestRow(1L, "payload-1", false, 0, false);
        HarvestRow row2 = new HarvestRow(2L, "payload-2", false, 0, false);

        writer.write(new Chunk<>(row1, row2));

        assertThat(isProcessed(1L)).isTrue();
        assertThat(isProcessed(2L)).isTrue();
    }

    @Test
    void write_alreadyProcessedRow_doesNotThrow() throws Exception {
        insertRow(10L, "already-done");
        // Pre-mark as processed
        jdbc.update("UPDATE harvest_source SET processed = TRUE WHERE id = 10");

        HarvestRow row = new HarvestRow(10L, "already-done", false, 0, false);
        writer.write(new Chunk<>(row)); // must not throw

        // Still processed (no change)
        assertThat(isProcessed(10L)).isTrue();
    }

    @Test
    void write_emptyChunk_noRowsUpdated() throws Exception {
        insertRow(20L, "untouched");
        writer.write(new Chunk<>());
        // Row remains unprocessed
        assertThat(isProcessed(20L)).isFalse();
    }
}
