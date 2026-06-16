package com.fronzec.plugins.harvester.batch;

import static org.assertj.core.api.Assertions.assertThat;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Unit tests for {@link HarvestRowMapper} using a real H2 query result set.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HarvestRowMapperTest {

    private JdbcTemplate jdbc;
    private final HarvestRowMapper mapper = new HarvestRowMapper();

    @BeforeAll
    void setUpDatabase() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:mapper-test;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE");
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
    }

    @Test
    void mapsNormalRow() {
        jdbc.update(
                "INSERT INTO harvest_source (id, payload, poison_flag, transient_fail_until_attempt,"
                        + " abort_flag, processed) VALUES (1, '{\"key\":\"value\"}', FALSE, 2, FALSE, FALSE)");

        HarvestRow row = jdbc.queryForObject(
                "SELECT id, payload, poison_flag, transient_fail_until_attempt, abort_flag"
                        + " FROM harvest_source WHERE id = 1",
                mapper);

        assertThat(row).isNotNull();
        assertThat(row.id()).isEqualTo(1L);
        assertThat(row.payload()).isEqualTo("{\"key\":\"value\"}");
        assertThat(row.poisonFlag()).isFalse();
        assertThat(row.transientFailUntilAttempt()).isEqualTo(2);
        assertThat(row.abortFlag()).isFalse();
    }

    @Test
    void mapsPoisonRow() {
        jdbc.update(
                "INSERT INTO harvest_source (id, payload, poison_flag, transient_fail_until_attempt,"
                        + " abort_flag, processed) VALUES (2, 'bad-payload', TRUE, 0, FALSE, FALSE)");

        HarvestRow row = jdbc.queryForObject(
                "SELECT id, payload, poison_flag, transient_fail_until_attempt, abort_flag"
                        + " FROM harvest_source WHERE id = 2",
                mapper);

        assertThat(row).isNotNull();
        assertThat(row.poisonFlag()).isTrue();
        assertThat(row.abortFlag()).isFalse();
    }

    @Test
    void mapsAbortRow() {
        jdbc.update(
                "INSERT INTO harvest_source (id, payload, poison_flag, transient_fail_until_attempt,"
                        + " abort_flag, processed) VALUES (3, 'abort-payload', FALSE, 0, TRUE, FALSE)");

        HarvestRow row = jdbc.queryForObject(
                "SELECT id, payload, poison_flag, transient_fail_until_attempt, abort_flag"
                        + " FROM harvest_source WHERE id = 3",
                mapper);

        assertThat(row).isNotNull();
        assertThat(row.abortFlag()).isTrue();
        assertThat(row.poisonFlag()).isFalse();
    }
}
