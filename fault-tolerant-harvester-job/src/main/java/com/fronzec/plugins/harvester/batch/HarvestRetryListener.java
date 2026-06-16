package com.fronzec.plugins.harvester.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;

/**
 * Pedagogical retry listener that logs each retry attempt and backoff for the
 * fault-tolerant harvester step.
 *
 * <p>Registered via {@code FaultTolerantStepBuilder.listener(RetryListener)} which has a
 * dedicated typed overload in Spring Batch 6, so no cast is required.
 *
 * <p>This listener is purely observational — it does not modify retry behaviour.
 * Its purpose is to make the exponential backoff and retry sequence visible in logs
 * when running integration tests or the host service with DEBUG logging enabled.
 *
 * <h3>spring-retry API (RetryListener, spring-retry 2.0.x)</h3>
 * <ul>
 *   <li>{@link #open} — called once before the first attempt; {@code true} continues.</li>
 *   <li>{@link #onError} — called after each failed attempt (before the next retry or
 *       before giving up).</li>
 *   <li>{@link #close} — called once after the final attempt (success or exhaustion).</li>
 * </ul>
 */
public class HarvestRetryListener implements RetryListener {

    private static final Logger log = LoggerFactory.getLogger(HarvestRetryListener.class);

    /**
     * Called before the first attempt. Returns {@code true} to allow the retry template
     * to proceed.
     *
     * @param context  the retry context (retryCount = 0 here)
     * @param callback the operation being retried
     * @param <T>      return type of the callback
     * @param <E>      exception type
     * @return always {@code true}
     */
    @Override
    public <T, E extends Throwable> boolean open(RetryContext context, RetryCallback<T, E> callback) {
        log.debug("Retry template opened: retryCount={}", context.getRetryCount());
        return true;
    }

    /**
     * Called after each failed attempt.
     *
     * @param context   the retry context (retryCount reflects attempts so far)
     * @param callback  the operation being retried
     * @param throwable the exception from this attempt
     * @param <T>       return type of the callback
     * @param <E>       exception type
     */
    @Override
    public <T, E extends Throwable> void onError(RetryContext context,
                                                  RetryCallback<T, E> callback,
                                                  Throwable throwable) {
        log.info("Retry attempt {} failed with {}: {}",
                context.getRetryCount(),
                throwable.getClass().getSimpleName(),
                throwable.getMessage());
    }

    /**
     * Called after the final attempt (success or exhaustion).
     *
     * @param context   the retry context
     * @param callback  the operation being retried
     * @param throwable the final exception ({@code null} if the last attempt succeeded)
     * @param <T>       return type of the callback
     * @param <E>       exception type
     */
    @Override
    public <T, E extends Throwable> void close(RetryContext context,
                                                RetryCallback<T, E> callback,
                                                Throwable throwable) {
        if (throwable == null) {
            log.debug("Retry template closed successfully after {} attempt(s)",
                    context.getRetryCount() + 1);
        } else {
            log.debug("Retry template closed after {} attempt(s) — exhausted: {}",
                    context.getRetryCount() + 1,
                    throwable.getClass().getSimpleName());
        }
    }
}
