package com.fronzec.plugins.ticketbundle.batch;

import static org.assertj.core.api.Assertions.assertThat;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Unit tests for {@link GeneratedFileRowMapper}.
 *
 * <p>Uses an in-memory H2 database and a real {@link java.sql.ResultSet} to avoid needing
 * Mockito (not a dependency of this module).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GeneratedFileRowMapperTest {

    private JdbcTemplate jdbc;
    private final GeneratedFileRowMapper mapper = new GeneratedFileRowMapper();

    @BeforeAll
    void setUp() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:mapper-test-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        jdbc = new JdbcTemplate(ds);

        jdbc.execute(
                "CREATE TABLE generated_files_mapper_test ("
                        + " id BIGINT PRIMARY KEY,"
                        + " ticket_id BIGINT NOT NULL,"
                        + " storage_path VARCHAR(1024) NOT NULL"
                        + ")");
        jdbc.execute("INSERT INTO generated_files_mapper_test VALUES (42, 7, '/data/tickets/7.pdf')");
        jdbc.execute("INSERT INTO generated_files_mapper_test VALUES (1, 2, '/tmp/bundle-test.pdf')");
    }

    @Test
    void mapRow_mapsAllFieldsCorrectly() {
        GeneratedFileRow row = jdbc.queryForObject(
                "SELECT id, ticket_id, storage_path FROM generated_files_mapper_test WHERE id = 42",
                mapper);

        assertThat(row).isNotNull();
        assertThat(row.id()).isEqualTo(42L);
        assertThat(row.ticketId()).isEqualTo(7L);
        assertThat(row.storagePath()).isEqualTo("/data/tickets/7.pdf");
    }

    @Test
    void mapRow_mapsStoragePath_fromStoragePathColumn() {
        GeneratedFileRow row = jdbc.queryForObject(
                "SELECT id, ticket_id, storage_path FROM generated_files_mapper_test WHERE id = 1",
                mapper);

        assertThat(row).isNotNull();
        // Confirm the storage_path column (not zip_path or any other alias) is mapped
        assertThat(row.storagePath()).isEqualTo("/tmp/bundle-test.pdf");
        assertThat(row.ticketId()).isEqualTo(2L);
    }
}
