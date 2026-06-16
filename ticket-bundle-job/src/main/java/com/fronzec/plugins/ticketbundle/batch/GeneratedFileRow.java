package com.fronzec.plugins.ticketbundle.batch;

/**
 * Immutable projection of a row from the {@code generated_files} table, joined through
 * {@code event_tickets} to resolve the event scope.
 *
 * @param id          primary key of the {@code generated_files} row
 * @param ticketId    foreign key back to {@code event_tickets}
 * @param storagePath absolute local path where the generated ticket PDF is stored
 */
public record GeneratedFileRow(long id, long ticketId, String storagePath) {}
