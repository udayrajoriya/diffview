package com.diffview.infra.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * Abstraction over a background task executor, enabling production code to run work
 * off the JavaFX Application Thread while tests inject a synchronous implementation
 * for determinism.
 *
 * <p>The two built-in implementations are:
 * <ul>
 *   <li>{@link PooledTaskExecutor} — production: cached thread pool, one thread borrowed per task.</li>
 *   <li>{@link DirectTaskExecutor} — tests: runs tasks synchronously in the calling thread.</li>
 * </ul>
 */
public interface TaskExecutor {

    /**
     * Submits a value-returning task and returns a {@link Future} representing
     * the pending result. The task may run on a background thread or synchronously,
     * depending on the implementation.
     *
     * @param <T>  return type of the task
     * @param task the task to execute
     * @return a Future whose {@code get()} returns the task result
     */
    <T> Future<T> submit(Callable<T> task);

    /**
     * Submits a fire-and-forget task. Errors are silently swallowed unless the
     * caller also holds the returned Future.
     *
     * @param task the runnable to execute
     * @return a Future that completes when the task finishes
     */
    Future<?> submit(Runnable task);

    /**
     * Initiates an orderly shutdown, allowing previously submitted tasks to complete.
     * No new tasks will be accepted after this call.
     */
    void shutdown();
}
