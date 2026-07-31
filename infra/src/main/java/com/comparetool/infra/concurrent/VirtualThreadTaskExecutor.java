package com.comparetool.infra.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Production {@link TaskExecutor} backed by a virtual-thread-per-task executor.
 *
 * <p>Each submitted task runs on its own platform-managed virtual thread, keeping
 * the JavaFX Application Thread free and enabling high-concurrency I/O without
 * pooling overhead (REQ-013).
 *
 * <p>Callers should call {@link #shutdown()} when the executor is no longer needed
 * (e.g. on application exit or ViewModel disposal) to release OS resources.
 */
public class VirtualThreadTaskExecutor implements TaskExecutor {

    private final ExecutorService executor;

    /**
     * Creates a new executor backed by {@link Executors#newVirtualThreadPerTaskExecutor()}.
     */
    public VirtualThreadTaskExecutor() {
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
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
