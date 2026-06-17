package com.fronzec.plugins.partitionedharvester.batch;

import static org.assertj.core.api.Assertions.assertThat;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Unit tests for {@link UsageRecordRowMapper} using a real H2 query result set.
 *
 * <p>Mirrors the pattern from {@code HarvestRowMapperTest}: real H2 result set,
 * no mock ResultSet.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UsageRecordRowMapperTest {

    private JdbcTemplate jdbc;
    private final UsageRecordRowMapper mapper = new UsageRecordRowMapper();

    @BeforeAll
    void setUpDatabase() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:rowmapper-test;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE");
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

    /** All fields from a standard row map correctly to {@link UsageRecord}. */
    @Test
    void mapsAllFields() {
        jdbc.update(
                "INSERT INTO usage_record (id, subscriber_id, units, rate) VALUES (1, 100, 10, 5)");

        UsageRecord row = jdbc.queryForObject(
                "SELECT id, subscriber_id, units, rate FROM usage_record WHERE id = 1",
                mapper);

        assertThat(row).isNotNull();
        assertThat(row.id()).isEqualTo(1L);
        assertThat(row.subscriberId()).isEqualTo(100L);
        assertThat(row.units()).isEqualTo(10L);
        assertThat(row.rateMinor()).isEqualTo(5L);
    }

    /** Column names are mapped by name (not positional) so order does not matter. */
    @Test
    void mapsColumnByName() {
        jdbc.update(
                "INSERT INTO usage_record (id, subscriber_id, units, rate) VALUES (2, 999, 20, 3)");

        UsageRecord row = jdbc.queryForObject(
                "SELECT rate, units, subscriber_id, id FROM usage_record WHERE id = 2",
                mapper);

        assertThat(row).isNotNull();
        assertThat(row.id()).isEqualTo(2L);
        assertThat(row.subscriberId()).isEqualTo(999L);
        assertThat(row.units()).isEqualTo(20L);
        assertThat(row.rateMinor()).isEqualTo(3L);
    }

    /** Boundary: large BIGINT values are preserved without truncation. */
    @Test
    void mapsLargeBigIntValues() {
        long largeUnits = 1_000_000_000L;
        long largeRate = 999_999L;
        jdbc.update(
                "INSERT INTO usage_record (id, subscriber_id, units, rate) VALUES (3, 42, ?, ?)",
                largeUnits, largeRate);

        UsageRecord row = jdbc.queryForObject(
                "SELECT id, subscriber_id, units, rate FROM usage_record WHERE id = 3",
                mapper);

        assertThat(row).isNotNull();
        assertThat(row.units()).isEqualTo(largeUnits);
        assertThat(row.rateMinor()).isEqualTo(largeRate);
    }
}
