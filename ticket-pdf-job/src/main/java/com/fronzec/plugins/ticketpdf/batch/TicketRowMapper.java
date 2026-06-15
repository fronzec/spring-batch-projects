package com.fronzec.plugins.ticketpdf.batch;

import com.fronzec.plugins.ticketpdf.domain.Ticket;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.RowMapper;

/**
 * Maps a row from {@code event_tickets} to a {@link Ticket} POJO.
 *
 * <p>Follows the same convention as {@code EntityPersonV2RowMapper} in the host:
 * {@code rs.getTimestamp("event_datetime").toLocalDateTime()} for TIMESTAMP columns.
 */
public class TicketRowMapper implements RowMapper<Ticket> {

    @Override
    public Ticket mapRow(ResultSet rs, int rowNum) throws SQLException {
        Ticket ticket = new Ticket();
        ticket.setId(rs.getLong("id"));
        ticket.setEventId(rs.getLong("event_id"));
        ticket.setTicketCode(rs.getString("ticket_code"));
        ticket.setHolderName(rs.getString("holder_name"));
        ticket.setEventName(rs.getString("event_name"));
        ticket.setEventLocation(rs.getString("event_location"));
        ticket.setSeat(rs.getString("seat"));
        ticket.setEventDatetime(rs.getTimestamp("event_datetime").toLocalDateTime());
        ticket.setProcessed(rs.getBoolean("processed"));
        return ticket;
    }
}
