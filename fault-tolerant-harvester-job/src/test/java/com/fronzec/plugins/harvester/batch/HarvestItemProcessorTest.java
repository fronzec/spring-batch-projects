package com.fronzec.plugins.harvester.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.retry.context.RetryContextSupport;
import org.springframework.retry.support.RetrySynchronizationManager;

/**
 * Unit tests for {@link HarvestItemProcessor}.
 *
 * <p>Tests cover all three fault-tolerance paths (abort, poison, transient) plus the happy path.
 * The transient tests exercise both the RetrySynchronizationManager path and the in-memory
 * fallback counter path.
 */
class HarvestItemProcessorTest {

    private HarvestItemProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new HarvestItemProcessor();
    }

    // ── Happy path ────────────────────────────────────────────────────────────────────────────

    @Test
    void normalRow_passesThrough() throws Exception {
        HarvestRow row = new HarvestRow(1L, "payload", false, 0, false);
        assertThat(processor.process(row)).isSameAs(row);
    }

    @Test
    void zeroThreshold_neverThrowsTransient() throws Exception {
        HarvestRow row = new HarvestRow(2L, "payload", false, 0, false);
        assertThat(processor.process(row)).isSameAs(row);
    }

    // ── Abort ─────────────────────────────────────────────────────────────────────────────────

    @Test
    void abortFlag_throwsAbortJobException() {
        HarvestRow row = new HarvestRow(10L, "abort", false, 0, true);
        assertThatThrownBy(() -> processor.process(row))
                .isInstanceOf(AbortJobException.class)
                .hasMessageContaining("10");
    }

    @Test
    void abortFlag_takePrecedenceOverPoisonFlag() {
        // abort + poison — abort wins (checked first)
        HarvestRow row = new HarvestRow(11L, "both", true, 0, true);
        assertThatThrownBy(() -> processor.process(row))
                .isInstanceOf(AbortJobException.class);
    }

    // ── Poison ────────────────────────────────────────────────────────────────────────────────

    @Test
    void poisonFlag_throwsPoisonItemException() {
        HarvestRow row = new HarvestRow(20L, "poison", true, 0, false);
        assertThatThrownBy(() -> processor.process(row))
                .isInstanceOf(PoisonItemException.class)
                .hasMessageContaining("20");
    }

    // ── Transient — in-memory fallback counter (no retry context on thread) ─────────────────

    @Test
    void transient_inMemoryFallback_throwsBelowThreshold() throws Exception {
        // Ensure no retry context is active
        HarvestRow row = new HarvestRow(30L, "transient", false, 2, false);

        // First call: counter=0 < threshold=2 → throw
        assertThatThrownBy(() -> processor.process(row))
                .isInstanceOf(TransientProcessingException.class);
    }

    @Test
    void transient_inMemoryFallback_succeedsAtThreshold() throws Exception {
        HarvestRow row = new HarvestRow(31L, "transient", false, 2, false);

        // First call (counter=0): throws
        assertThatThrownBy(() -> processor.process(row))
                .isInstanceOf(TransientProcessingException.class);
        // Second call (counter=1): throws
        assertThatThrownBy(() -> processor.process(row))
                .isInstanceOf(TransientProcessingException.class);
        // Third call (counter=2 >= threshold=2): succeeds
        HarvestRow result = processor.process(row);
        assertThat(result).isSameAs(row);
    }

    @Test
    void transient_resetAttemptCounters_restartsFromZero() throws Exception {
        HarvestRow row = new HarvestRow(32L, "transient", false, 1, false);

        // First call: throws (counter=0 < threshold=1)
        assertThatThrownBy(() -> processor.process(row))
                .isInstanceOf(TransientProcessingException.class);
        // Second call: succeeds (counter=1 >= threshold=1)
        assertThat(processor.process(row)).isSameAs(row);

        // Reset simulates a new step execution (e.g. restart)
        processor.resetAttemptCounters();

        // After reset: first call throws again
        assertThatThrownBy(() -> processor.process(row))
                .isInstanceOf(TransientProcessingException.class);
    }

    // ── Transient — RetrySynchronizationManager path ─────────────────────────────────────────

    @Test
    void transient_retrySyncManager_throwsBelowThreshold() {
        HarvestRow row = new HarvestRow(40L, "transient", false, 2, false);

        // Simulate retry count = 0 via RetrySynchronizationManager
        Runnable clear = activateRetryContext(0);
        try {
            assertThatThrownBy(() -> processor.process(row))
                    .isInstanceOf(TransientProcessingException.class);
        } finally {
            clear.run();
        }
    }

    @Test
    void transient_retrySyncManager_succeedsAtThreshold() throws Exception {
        HarvestRow row = new HarvestRow(41L, "transient", false, 2, false);

        // Simulate retry count = 2 via RetrySynchronizationManager (>= threshold=2)
        Runnable clear = activateRetryContext(2);
        try {
            HarvestRow result = processor.process(row);
            assertThat(result).isSameAs(row);
        } finally {
            clear.run();
        }
    }

    @Test
    void resolveRetryCount_usesContextWhenPresent() {
        Runnable clear = activateRetryContext(3);
        try {
            assertThat(processor.resolveRetryCount(99L)).isEqualTo(3);
        } finally {
            clear.run();
        }
    }

    @Test
    void resolveRetryCount_usesFallbackCounterWhenContextAbsent() {
        // No active retry context
        assertThat(RetrySynchronizationManager.getContext()).isNull();

        assertThat(processor.resolveRetryCount(50L)).isEqualTo(0); // first call
        assertThat(processor.resolveRetryCount(50L)).isEqualTo(1); // second call
        assertThat(processor.resolveRetryCount(50L)).isEqualTo(2); // third call
        // Different id: starts at 0
        assertThat(processor.resolveRetryCount(51L)).isEqualTo(0);
    }

    // ── Helper ────────────────────────────────────────────────────────────────────────────────

    /**
     * Activates a {@link RetrySynchronizationManager} context with the given retry count.
     * Returns a {@link Runnable} to call in {@code finally} to clear the context.
     */
    private Runnable activateRetryContext(int retryCount) {
        RetryContextSupport ctx = new RetryContextSupport(null);
        // Advance the counter: RetryContextSupport.registerThrowable increments retryCount
        for (int i = 0; i < retryCount; i++) {
            ctx.registerThrowable(new RuntimeException("synthetic"));
        }
        RetrySynchronizationManager.register(ctx);
        return RetrySynchronizationManager::clear;
    }
}
