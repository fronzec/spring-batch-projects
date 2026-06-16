package com.fronzec.plugins.ticketbundle.batch;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.RowMapper;

/**
 * Maps a row from the joined {@code generated_files / event_tickets} query to a
 * {@link GeneratedFileRow} record.
 *
 * <p>The expected column aliases in the result set are {@code id}, {@code ticket_id},
 * and {@code storage_path} — matching the SELECT in
 * {@link BundleStepListener#buildSql(String)}.
 */
public class GeneratedFileRowMapper implements RowMapper<GeneratedFileRow> {

    @Override
    public GeneratedFileRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new GeneratedFileRow(
                rs.getLong("id"),
                rs.getLong("ticket_id"),
                rs.getString("storage_path"));
    }
}
