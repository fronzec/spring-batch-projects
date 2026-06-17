package com.fronzec.plugins.partitionedharvester.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RatingProcessor}.
 *
 * <p>Covers: SC-03.1 per-record cost, boundary values, overflow guard via
 * {@link Math#multiplyExact}, and BillingCharge field mapping.
 */
class RatingProcessorTest {

    private final RatingProcessor processor = new RatingProcessor();

    // ── SC-03.1 — Per-record cost correctness ────────────────────────────────

    @Test
    void process_standard_computesCostCorrectly() throws Exception {
        // units=5, rate=3 → cost=15
        UsageRecord record = new UsageRecord(7L, 101L, 5L, 3L);
        BillingCharge charge = processor.process(record);

        assertThat(charge).isNotNull();
        assertThat(charge.costMinor()).isEqualTo(15L);
    }

    @Test
    void process_unitOne_rateOne_costOne() throws Exception {
        UsageRecord record = new UsageRecord(1L, 200L, 1L, 1L);
        BillingCharge charge = processor.process(record);

        assertThat(charge.costMinor()).isEqualTo(1L);
    }

    @Test
    void process_unitZero_costZero() throws Exception {
        UsageRecord record = new UsageRecord(2L, 300L, 0L, 100L);
        BillingCharge charge = processor.process(record);

        assertThat(charge.costMinor()).isEqualTo(0L);
    }

    // ── Overflow guard — Math.multiplyExact ──────────────────────────────────

    @Test
    void process_overflow_throwsArithmeticException() {
        UsageRecord record = new UsageRecord(99L, 999L, Long.MAX_VALUE, 2L);

        assertThatThrownBy(() -> processor.process(record))
                .isInstanceOf(ArithmeticException.class);
    }

    // ── BillingCharge field mapping ───────────────────────────────────────────

    @Test
    void process_sourceIdMatchesUsageRecordId() throws Exception {
        UsageRecord record = new UsageRecord(42L, 500L, 10L, 5L);
        BillingCharge charge = processor.process(record);

        assertThat(charge.sourceId()).isEqualTo(42L);
    }

    @Test
    void process_allFieldsMappedCorrectly() throws Exception {
        UsageRecord record = new UsageRecord(10L, 777L, 20L, 4L);
        BillingCharge charge = processor.process(record);

        assertThat(charge.sourceId()).isEqualTo(10L);
        assertThat(charge.subscriberId()).isEqualTo(777L);
        assertThat(charge.units()).isEqualTo(20L);
        assertThat(charge.rateMinor()).isEqualTo(4L);
        assertThat(charge.costMinor()).isEqualTo(80L);
    }
}
