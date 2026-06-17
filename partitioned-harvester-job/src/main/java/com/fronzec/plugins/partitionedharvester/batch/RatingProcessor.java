package com.fronzec.plugins.partitionedharvester.batch;

import org.springframework.batch.infrastructure.item.ItemProcessor;

/**
 * Applies flat-rate billing to a {@link UsageRecord}, producing an immutable
 * {@link BillingCharge}.
 *
 * <h3>Cost computation</h3>
 * <p>{@code cost = units × rate}, computed with {@link Math#multiplyExact} to detect
 * overflow at processing time rather than silently wrapping into an incorrect value.
 * An {@link ArithmeticException} from {@code multiplyExact} propagates as a non-skippable
 * exception, failing the chunk and the partition immediately — operator intervention is
 * required to fix the source data.
 *
 * <h3>Idempotency</h3>
 * <p>The processor is stateless and side-effect-free. The same input always produces the
 * same output, so re-processing after a restart does not alter cost values.
 *
 * @see BillingCharge
 * @see UsageRecord
 */
public class RatingProcessor implements ItemProcessor<UsageRecord, BillingCharge> {

    /**
     * Converts a {@link UsageRecord} into a {@link BillingCharge}.
     *
     * <p>The {@code sourceId} of the resulting charge equals the {@code id} of the input
     * record — this 1:1 mapping is the key used by {@code BillingChargeWriter} to enforce
     * idempotency via the UNIQUE constraint on {@code billing_charge.source_id}.
     *
     * @param item the usage record to rate
     * @return the corresponding billing charge with {@code costMinor = units × rateMinor}
     * @throws ArithmeticException if {@code units × rateMinor} overflows {@code long}
     */
    @Override
    public BillingCharge process(UsageRecord item) {
        long cost = Math.multiplyExact(item.units(), item.rateMinor());
        return new BillingCharge(
                item.id(),
                item.subscriberId(),
                item.units(),
                item.rateMinor(),
                cost);
    }
}
