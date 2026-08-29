package com.comparetool.infra.concurrent;

/**
 * Callback for reporting incremental progress from a background operation.
 *
 * <p>Implementations are called from worker threads and must be thread-safe.
 * UI implementations should dispatch updates to the JavaFX Application Thread
 * via {@code Platform.runLater()} rather than updating nodes directly.
 *
 * <p>This is a {@link FunctionalInterface} so lambda expressions can be used
 * wherever a {@code ProgressReporter} is expected.
 *
 * @see #noOp()
 */
@FunctionalInterface
public interface ProgressReporter {

    /**
     * Reports progress of a long-running operation.
     *
     * @param current  items completed so far (0-based)
     * @param total    total items to process; may be -1 if the total is unknown
     * @param message  short human-readable description of the current step; never null
     */
    void report(long current, long total, String message);

    /**
     * Returns a no-op reporter that silently discards all progress updates.
     * Useful as a default/sentinel to avoid null checks.
     */
    static ProgressReporter noOp() {
        return (current, total, message) -> { /* discard */ };
    }
}
