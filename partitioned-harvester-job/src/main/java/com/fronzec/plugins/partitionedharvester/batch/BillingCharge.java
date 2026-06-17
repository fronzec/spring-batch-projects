package com.fronzec.plugins.partitionedharvester.batch;

/**
 * Immutable domain record representing one billing charge derived from a {@link UsageRecord}.
 *
 * <p>Produced by the {@code RatingProcessor} (PR-2). Exactly one {@code BillingCharge} is
 * created per {@code UsageRecord}. {@code costMinor = units × rateMinor}, computed with
 * {@link Math#multiplyExact} to fail fast on overflow instead of silently wrapping.
 *
 * @param sourceId     the {@code id} of the originating {@link UsageRecord}; mapped to
 *                     {@code billing_charge.source_id}; has a UNIQUE constraint for
 *                     1:1 idempotency
 * @param subscriberId identifier for the subscriber being billed
 * @param units        number of billable usage units (copied from {@link UsageRecord})
 * @param rateMinor    flat rate in minor currency units (copied from {@link UsageRecord})
 * @param costMinor    computed cost in minor currency units: {@code units × rateMinor}
 */
public record BillingCharge(
        long sourceId,
        long subscriberId,
        long units,
        long rateMinor,
        long costMinor) {}
