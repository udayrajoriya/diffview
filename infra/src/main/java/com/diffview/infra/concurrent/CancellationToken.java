package com.diffview.infra.concurrent;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A simple, thread-safe cancellation flag that workers can poll or throw on.
 *
 * <p>Workers should call {@link #checkCancelled()} at each iteration boundary
 * (e.g. per file processed) to respond promptly to cancellation without busy-polling.
 *
 * <p>Cancellation is one-way: once cancelled, a token cannot be reset.
 */
public class CancellationToken {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    /**
     * Signals cancellation. Thread-safe; idempotent after the first call.
     */
    public void cancel() {
        cancelled.set(true);
    }

    /**
     * Returns {@code true} if {@link #cancel()} has been called.
     */
    public boolean isCancelled() {
        return cancelled.get();
    }

    /**
     * Throws {@link CancellationException} if this token has been cancelled.
     *
     * @throws CancellationException if cancelled
     */
    public void checkCancelled() {
        if (cancelled.get()) {
            throw new CancellationException("Operation cancelled");
        }
    }

    /**
     * Returns a token that is never cancelled (useful as a no-op sentinel to avoid
     * null checks in production code paths that don't need cancellation support).
     */
    public static CancellationToken neverCancelled() {
        return new CancellationToken() {
            @Override
            public void cancel() { /* no-op */ }

            @Override
            public boolean isCancelled() { return false; }

            @Override
            public void checkCancelled() { /* never throws */ }
        };
    }
}
