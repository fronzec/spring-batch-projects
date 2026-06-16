package com.fronzec.plugins.harvester.batch;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.RowMapper;

/**
 * Maps a {@code harvest_source} result-set row to a {@link HarvestRow} record.
 *
 * <p>Expected columns (by name):
 * <ul>
 *   <li>{@code id} — BIGINT primary key</li>
 *   <li>{@code payload} — VARCHAR(2048)</li>
 *   <li>{@code poison_flag} — BOOLEAN</li>
 *   <li>{@code transient_fail_until_attempt} — INT</li>
 *   <li>{@code abort_flag} — BOOLEAN</li>
 * </ul>
 */
public class HarvestRowMapper implements RowMapper<HarvestRow> {

    /**
     * Maps one result-set row to a {@link HarvestRow}.
     *
     * @param rs     the result set positioned at the current row
     * @param rowNum the 0-based row number (unused)
     * @return the mapped {@link HarvestRow}
     * @throws SQLException if a column cannot be read
     */
    @Override
    public HarvestRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new HarvestRow(
                rs.getLong("id"),
                rs.getString("payload"),
                rs.getBoolean("poison_flag"),
                rs.getInt("transient_fail_until_attempt"),
                rs.getBoolean("abort_flag"));
    }
}
