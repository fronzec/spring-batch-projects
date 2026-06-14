package com.fronzec.plugins.ticketpdf.domain;

/**
 * Value object carrying the rendered PDF bytes for a single ticket.
 *
 * <p>Produced by the processor and consumed by the writer.
 *
 * @param ticketId   the source {@code event_tickets.id}
 * @param ticketCode the source {@code event_tickets.ticket_code}
 * @param pdfBytes   raw PDF content
 */
public record TicketDocument(long ticketId, String ticketCode, byte[] pdfBytes) {}
