package com.comparetool.infra.concurrent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Task 3.4 — TaskExecutor & cancellation primitives")
class TaskExecutorTest {

    // -----------------------------------------------------------------------
    // CancellationToken
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("CancellationToken")
    class CancellationTokenTests {

        @Test
        @DisplayName("starts not-cancelled")
        void startsNotCancelled() {
            CancellationToken token = new CancellationToken();
            assertThat(token.isCancelled()).isFalse();
        }

        @Test
        @DisplayName("cancel() sets isCancelled() to true")
        void cancelSetsFlag() {
            CancellationToken token = new CancellationToken();
            token.cancel();
            assertThat(token.isCancelled()).isTrue();
        }

        @Test
        @DisplayName("checkCancelled() does not throw before cancellation")
        void checkCancelledDoesNotThrowBeforeCancellation() {
            CancellationToken token = new CancellationToken();
            assertThatCode(token::checkCancelled).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("checkCancelled() throws CancellationException after cancel()")
        void checkCancelledThrowsAfterCancel() {
            CancellationToken token = new CancellationToken();
            token.cancel();
            assertThatExceptionOfType(CancellationException.class)
                    .isThrownBy(token::checkCancelled);
        }

        @Test
        @DisplayName("cancel() is idempotent — calling twice does not throw")
        void cancelIsIdempotent() {
            CancellationToken token = new CancellationToken();
            token.cancel();
            assertThatCode(token::cancel).doesNotThrowAnyException();
            assertThat(token.isCancelled()).isTrue();
        }

        @Test
        @DisplayName("neverCancelled() token is never cancelled")
        void neverCancelledIsNeverCancelled() {
            CancellationToken token = CancellationToken.neverCancelled();
            assertThat(token.isCancelled()).isFalse();
            token.cancel(); // should be a no-op
            assertThat(token.isCancelled()).isFalse();
            assertThatCode(token::checkCancelled).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("cancellation propagates: worker loop exits on cancel()")
        void cancellationPropagates() throws Exception {
            CancellationToken token = new CancellationToken();
            List<Integer> processed = new ArrayList<>();

            // Simulate a worker that checks the token at each iteration.
            Runnable worker = () -> {
                for (int i = 0; i < 100; i++) {
                    if (token.isCancelled()) break;
                    processed.add(i);
                    if (i == 4) token.cancel(); // cancel mid-loop
                }
            };

            worker.run();

            // Worker must have stopped before completing all 100 iterations.
            assertThat(processed).hasSize(5); // 0..4 processed before loop exits
            assertThat(token.isCancelled()).isTrue();
        }

        @Test
        @DisplayName("checkCancelled() propagation: worker throws on cancelled token")
        void checkCancelledPropagation() {
            CancellationToken token = new CancellationToken();
            token.cancel();

            List<Integer> processed = new ArrayList<>();

            assertThatExceptionOfType(CancellationException.class).isThrownBy(() -> {
                for (int i = 0; i < 10; i++) {
                    token.checkCancelled();
                    processed.add(i);
                }
            });

            // No iterations should have completed because the token was already cancelled.
            assertThat(processed).isEmpty();
        }

        @Test
        @DisplayName("thread-safety: cancel() visible across threads")
        void cancelVisibleAcrossThreads() throws Exception {
            CancellationToken token = new CancellationToken();
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch observed = new CountDownLatch(1);

            Thread worker = new Thread(() -> {
                started.countDown();
                while (!token.isCancelled()) {
                    Thread.onSpinWait();
                }
                observed.countDown();
            });
            worker.setDaemon(true);
            worker.start();

            started.await(2, TimeUnit.SECONDS);
            token.cancel();
            boolean latchReached = observed.await(2, TimeUnit.SECONDS);

            assertThat(latchReached).isTrue();
            worker.join(2000);
        }
    }

    // -----------------------------------------------------------------------
    // ProgressReporter
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("ProgressReporter")
    class ProgressReporterTests {

        @Test
        @DisplayName("noOp() does not throw when called")
        void noOpDoesNotThrow() {
            ProgressReporter reporter = ProgressReporter.noOp();
            assertThatCode(() -> reporter.report(5, 10, "step")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("callbacks invoked in order with correct arguments")
        void callbacksInvokedInOrder() {
            record Progress(long current, long total, String message) {}

            List<Progress> calls = new ArrayList<>();
            ProgressReporter reporter = (c, t, m) -> calls.add(new Progress(c, t, m));

            reporter.report(0, 3, "start");
            reporter.report(1, 3, "one");
            reporter.report(2, 3, "two");
            reporter.report(3, 3, "done");

            assertThat(calls).hasSize(4);
            assertThat(calls.get(0)).isEqualTo(new Progress(0, 3, "start"));
            assertThat(calls.get(1)).isEqualTo(new Progress(1, 3, "one"));
            assertThat(calls.get(2)).isEqualTo(new Progress(2, 3, "two"));
            assertThat(calls.get(3)).isEqualTo(new Progress(3, 3, "done"));
        }

        @Test
        @DisplayName("total of -1 is accepted (unknown total)")
        void unknownTotalAccepted() {
            List<Long> totals = new ArrayList<>();
            ProgressReporter reporter = (c, t, m) -> totals.add(t);
            reporter.report(1, -1, "indeterminate");
            assertThat(totals).containsExactly(-1L);
        }
    }

    // -----------------------------------------------------------------------
    // DirectTaskExecutor
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("DirectTaskExecutor (synchronous)")
    class DirectTaskExecutorTests {

        private final TaskExecutor executor = new DirectTaskExecutor();

        @Test
        @DisplayName("submit(Callable) returns result synchronously")
        void submitCallableReturnsSynchronously() throws Exception {
            Future<String> future = executor.submit(() -> "hello");
            // DirectTaskExecutor runs synchronously, so isDone() must be true immediately.
            assertThat(future.isDone()).isTrue();
            assertThat(future.get()).isEqualTo("hello");
        }

        @Test
        @DisplayName("submit(Runnable) completes synchronously")
        void submitRunnableCompletesSynchronously() throws Exception {
            List<String> results = new ArrayList<>();
            Future<?> future = executor.submit(() -> results.add("ran"));
            assertThat(future.isDone()).isTrue();
            assertThat(results).containsExactly("ran");
        }

        @Test
        @DisplayName("failed Callable wraps exception in Future")
        void failedCallableWrapsException() {
            Future<String> future = executor.submit(() -> { throw new IllegalStateException("boom"); });
            assertThat(future.isDone()).isTrue();
            assertThatExceptionOfType(ExecutionException.class)
                    .isThrownBy(future::get)
                    .withCauseInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("failed Runnable wraps exception in Future")
        void failedRunnableWrapsException() {
            Future<?> future = executor.submit((Runnable) () -> { throw new RuntimeException("oops"); });
            assertThat(future.isDone()).isTrue();
            assertThatExceptionOfType(ExecutionException.class)
                    .isThrownBy(future::get)
                    .withCauseInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("sequential tasks run in submission order (deterministic)")
        void sequentialTasksDeterministic() throws Exception {
            List<Integer> order = new ArrayList<>();
            executor.submit(() -> order.add(1));
            executor.submit(() -> order.add(2));
            executor.submit(() -> order.add(3));
            assertThat(order).containsExactly(1, 2, 3);
        }

        @Test
        @DisplayName("shutdown() is a no-op and does not throw")
        void shutdownIsNoOp() {
            DirectTaskExecutor local = new DirectTaskExecutor();
            assertThatCode(local::shutdown).doesNotThrowAnyException();
            // Still usable after shutdown (no real lifecycle).
            assertThatCode(() -> local.submit(() -> "ok")).doesNotThrowAnyException();
        }
    }

    // -----------------------------------------------------------------------
    // PooledTaskExecutor
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("PooledTaskExecutor (cached thread pool)")
    class PooledTaskExecutorTests {

        @Test
        @DisplayName("submit(Callable) eventually returns result")
        void submitCallableReturnsResult() throws Exception {
            PooledTaskExecutor executor = new PooledTaskExecutor();
            try {
                Future<Integer> future = executor.submit(() -> 42);
                assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo(42);
            } finally {
                executor.shutdown();
            }
        }

        @Test
        @DisplayName("submit(Runnable) eventually completes")
        void submitRunnableCompletes() throws Exception {
            PooledTaskExecutor executor = new PooledTaskExecutor();
            CountDownLatch latch = new CountDownLatch(1);
            try {
                executor.submit(latch::countDown);
                assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                executor.shutdown();
            }
        }

        @Test
        @DisplayName("failed Callable wraps exception in Future")
        void failedCallableWrapsException() throws Exception {
            PooledTaskExecutor executor = new PooledTaskExecutor();
            try {
                Future<String> future = executor.submit(() -> { throw new IllegalArgumentException("bad"); });
                assertThatExceptionOfType(ExecutionException.class)
                        .isThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                        .withCauseInstanceOf(IllegalArgumentException.class);
            } finally {
                executor.shutdown();
            }
        }

        @Test
        @DisplayName("cancellation token checked inside virtual-thread task stops work early")
        void cancellationInsideVirtualThread() throws Exception {
            PooledTaskExecutor executor = new PooledTaskExecutor();
            CancellationToken token = new CancellationToken();
            token.cancel();

            List<Integer> processed = new ArrayList<>();
            CountDownLatch done = new CountDownLatch(1);

            try {
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < 100; i++) {
                            token.checkCancelled();
                            processed.add(i);
                        }
                    } catch (CancellationException ignored) {
                        // expected
                    } finally {
                        done.countDown();
                    }
                });
                assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(processed).isEmpty();
            } finally {
                executor.shutdown();
            }
        }

        @Test
        @DisplayName("progress callbacks from virtual thread are received in order")
        void progressCallbacksFromVirtualThread() throws Exception {
            PooledTaskExecutor executor = new PooledTaskExecutor();
            List<Long> received = new CopyOnWriteArrayList<>();
            ProgressReporter reporter = (c, t, m) -> received.add(c);
            CountDownLatch done = new CountDownLatch(1);

            try {
                executor.submit(() -> {
                    try {
                        for (int i = 0; i <= 4; i++) {
                            reporter.report(i, 4, "step " + i);
                        }
                    } finally {
                        done.countDown();
                    }
                });
                assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(received).containsExactly(0L, 1L, 2L, 3L, 4L);
            } finally {
                executor.shutdown();
            }
        }
    }
}
