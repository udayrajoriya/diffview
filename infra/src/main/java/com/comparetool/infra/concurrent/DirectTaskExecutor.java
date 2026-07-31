package com.comparetool.infra.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * Test-only {@link TaskExecutor} that executes every task <em>synchronously</em>
 * on the calling thread before returning.
 *
 * <p>This removes all thread-scheduling non-determinism from unit tests: after
 * {@code submit(task)} returns, the task has already completed and its result or
 * exception is immediately available via the returned {@link Future}.
 *
 * <p>{@link #shutdown()} is a no-op; there is no underlying executor to drain.
 */
public class DirectTaskExecutor implements TaskExecutor {

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        try {
            T result = task.call();
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public Future<?> submit(Runnable task) {
        try {
            task.run();
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public void shutdown() {
        // No-op: synchronous executor has no background resources to release.
    }
}
