package com.fronzec.plugins.partitionedharvester.batch;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.RowMapper;

/**
 * Maps a {@code usage_record} result-set row to a {@link UsageRecord} record.
 *
 * <p>Expected columns (by name, matching V8 DDL):
 * <ul>
 *   <li>{@code id} — BIGINT primary key</li>
 *   <li>{@code subscriber_id} — BIGINT</li>
 *   <li>{@code units} — BIGINT</li>
 *   <li>{@code rate} — BIGINT (minor currency units per usage unit); mapped to
 *       {@link UsageRecord#rateMinor()}</li>
 * </ul>
 *
 * <p>Column mapping is by name, not position, so the column order in the SELECT
 * does not affect correctness.
 */
public class UsageRecordRowMapper implements RowMapper<UsageRecord> {

    /**
     * Maps one result-set row to a {@link UsageRecord}.
     *
     * @param rs     the result set positioned at the current row
     * @param rowNum the 0-based row number (unused)
     * @return the mapped {@link UsageRecord}
     * @throws SQLException if a column cannot be read
     */
    @Override
    public UsageRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new UsageRecord(
                rs.getLong("id"),
                rs.getLong("subscriber_id"),
                rs.getLong("units"),
                rs.getLong("rate"));
    }
}
