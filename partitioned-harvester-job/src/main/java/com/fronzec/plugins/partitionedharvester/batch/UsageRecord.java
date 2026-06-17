package com.fronzec.plugins.partitionedharvester.batch;

/**
 * Immutable domain record representing one row from the frozen {@code usage_record} table.
 *
 * <p>All fields are read directly from the database; the source table is never mutated
 * during processing. Cost is computed by the processor as {@code units × rateMinor}.
 *
 * @param id           primary key of the usage_record row; used as the {@code source_id}
 *                     in {@link BillingCharge} for 1:1 idempotency
 * @param subscriberId identifier for the subscriber being billed
 * @param units        number of billable usage units consumed
 * @param rateMinor    flat rate in minor currency units per usage unit (e.g. millis or cents)
 */
public record UsageRecord(
        long id,
        long subscriberId,
        long units,
        long rateMinor) {}
