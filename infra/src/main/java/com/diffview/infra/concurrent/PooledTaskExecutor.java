package com.comparetool.infra.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Production {@link TaskExecutor} backed by a cached thread pool that grows on demand
 * and reuses idle threads, keeping the JavaFX Application Thread free for background
 * comparison work (REQ-013).
 *
 * <p>Threads are daemon threads so a running task never prevents the JVM from exiting.
 *
 * <p>Callers should call {@link #shutdown()} when the executor is no longer needed
 * (e.g. on application exit or ViewModel disposal) to release OS resources.
 */
public class PooledTaskExecutor implements TaskExecutor {

    private final ExecutorService executor;

    /**
     * Creates a new executor backed by {@link Executors#newCachedThreadPool(ThreadFactory)}.
     */
    public PooledTaskExecutor() {
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "task-executor-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        this.executor = Executors.newCachedThreadPool(threadFactory);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return executor.submit(task);
    }

    @Override
    public Future<?> submit(Runnable task) {
        return executor.submit(task);
    }

    @Override
    public void shutdown() {
        executor.shutdown();
    }
}
