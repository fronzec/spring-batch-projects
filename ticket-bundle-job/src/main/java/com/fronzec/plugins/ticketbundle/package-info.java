/**
 * Event Ticket Bundle ZIP plugin for the Spring Batch dynamic plugin platform.
 *
 * <p>Given an {@code EVENT_ID} job parameter this plugin reads all already-generated
 * ticket PDF files from {@code generated_files} (joined through {@code event_tickets}),
 * streams them into a single ZIP archive, uploads the archive via a {@code FileStorage}
 * seam, and records one idempotent row in {@code generated_bundles}.
 *
 * <p>The plugin is loaded dynamically by the host service via {@link java.util.ServiceLoader};
 * the implementation class is {@link com.fronzec.plugins.ticketbundle.TicketBundleJobPlugin}.
 */
package com.fronzec.plugins.ticketbundle;
