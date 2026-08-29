package com.diffview.ui;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.function.BiConsumer;

/**
 * JavaFX-aware global uncaught exception handler (REQ-016.2 — display clear error message,
 * preserve unsaved edits).
 *
 * <p>Installs itself as {@link Thread#setDefaultUncaughtExceptionHandler} via {@link #install()}.
 * When triggered from any thread, it logs the exception to {@code stderr} and shows a
 * non-fatal {@link Alert} on the JavaFX application thread, so the application continues
 * running and unsaved edits are not lost.
 *
 * <p>Call {@link #install()} once at application startup, before showing the primary stage:
 * <pre>{@code
 * GlobalExceptionHandler.install();
 * }</pre>
 *
 * <p>The dialog implementation is injectable via {@link #setDialogFactory(BiConsumer)}
 * so tests can verify invocation without creating a real JavaFX Alert.
 */
public final class GlobalExceptionHandler implements Thread.UncaughtExceptionHandler {

    private BiConsumer<String, String> dialogFactory = this::showSafeAlert;

    // ── Installation ──────────────────────────────────────────────────────────

    /**
     * Sets a new instance of {@code GlobalExceptionHandler} as the default uncaught
     * exception handler for all threads.  Safe to call multiple times (replaces any
     * previous handler).
     */
    public static void install() {
        Thread.setDefaultUncaughtExceptionHandler(new GlobalExceptionHandler());
    }

    // ── UncaughtExceptionHandler ──────────────────────────────────────────────

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        // Always log to stderr so the error is captured even when the dialog cannot open
        System.err.printf(
                "Uncaught exception in thread \"%s\": %s%n",
                thread.getName(), throwable);
        throwable.printStackTrace(System.err);

        String detail = buildDetail(thread, throwable);

        if (Platform.isFxApplicationThread()) {
            dialogFactory.accept("Unexpected Error", detail);
        } else {
            // Must not block this thread — dispatch to FX thread and return
            Platform.runLater(() -> dialogFactory.accept("Unexpected Error", detail));
        }
    }

    // ── Injectable factory (for testing) ──────────────────────────────────────

    /**
     * Replaces the default dialog implementation.
     *
     * <p>The consumer receives {@code (title, detail)} and should display them to the user.
     * In production this shows a JavaFX {@link Alert}; in tests a simple record is sufficient.
     *
     * @param factory non-null consumer; called on the JavaFX application thread
     */
    public void setDialogFactory(BiConsumer<String, String> factory) {
        this.dialogFactory = java.util.Objects.requireNonNull(factory, "factory must not be null");
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void showSafeAlert(String title, String detail) {
        try {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText("An unexpected error occurred.");
            alert.setContentText("The application will continue running. "
                    + "Please save your work and report this error.");

            TextArea textArea = new TextArea(detail);
            textArea.setEditable(false);
            textArea.setWrapText(true);
            textArea.setMaxWidth(Double.MAX_VALUE);
            textArea.setMaxHeight(Double.MAX_VALUE);

            alert.getDialogPane().setExpandableContent(textArea);
            alert.getDialogPane().setExpanded(true);
            alert.showAndWait();
        } catch (Exception ignored) {
            // Never let the handler itself crash; the error has already been logged to stderr
        }
    }

    private static String buildDetail(Thread thread, Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter  pw = new PrintWriter(sw);
        pw.printf("Thread: %s%n%n", thread.getName());
        throwable.printStackTrace(pw);
        return sw.toString();
    }
}
