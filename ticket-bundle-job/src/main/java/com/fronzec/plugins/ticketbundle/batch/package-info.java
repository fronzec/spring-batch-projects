/**
 * Spring Batch components for the ticket-bundle-job plugin.
 *
 * <p>Contains the chunk-step reader/writer/listener (step 1 — ZIP assembly)
 * and the tasklet step (step 2 — bundle persist), plus supporting domain types
 * and the fail-fast job parameters validator.
 */
package com.fronzec.plugins.ticketbundle.batch;
