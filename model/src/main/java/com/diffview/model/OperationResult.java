package com.comparetool.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents the outcome of a fallible operation — either a {@link Success} holding a value,
 * or a {@link Failure} holding an {@link ErrorCode} and a descriptive message (REQ-016.2).
 *
 * <p>Usage:
 * <pre>{@code
 * OperationResult<String> result = doSomething();
 * if (result.isSuccess()) {
 *     process(result.valueOrThrow());
 * } else {
 *     log.warn("Failed: {} — {}", result.errorCode(), result.errorMessage());
 * }
 * }</pre>
 *
 * @param <T> type of the success value
 */
public sealed interface OperationResult<T>
        permits OperationResult.Success, OperationResult.Failure {

    // ── Factory methods ───────────────────────────────────────────────────────

    /**
     * Returns a successful result wrapping {@code value}.
     *
     * @param value the success value; must not be {@code null}
     */
    static <T> OperationResult<T> success(T value) {
        return new Success<>(value);
    }

    /**
     * Returns a failure result with the given code and message and no underlying cause.
     *
     * @param code    categorised error code; must not be {@code null}
     * @param message human-readable description; must not be {@code null}
     */
    static <T> OperationResult<T> failure(ErrorCode code, String message) {
        return new Failure<>(code, message, null);
    }

    /**
     * Returns a failure result with the given code, message, and underlying cause.
     *
     * @param code    categorised error code; must not be {@code null}
     * @param message human-readable description; must not be {@code null}
     * @param cause   the underlying exception; may be {@code null}
     */
    static <T> OperationResult<T> failure(ErrorCode code, String message, Throwable cause) {
        return new Failure<>(code, message, cause);
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    /** Returns {@code true} if this is a {@link Success}. */
    boolean isSuccess();

    /** Returns {@code true} if this is a {@link Failure}. */
    boolean isFailure();

    /**
     * Returns the success value wrapped in an {@link Optional}, or
     * {@link Optional#empty()} if this is a {@link Failure}.
     */
    Optional<T> value();

    /**
     * Returns the success value directly, or throws {@link OperationFailedException}
     * if this is a {@link Failure}.
     */
    T valueOrThrow();

    /**
     * Returns the error code for a {@link Failure}.
     *
     * @throws UnsupportedOperationException if this is a {@link Success}
     */
    ErrorCode errorCode();

    /**
     * Returns the error message for a {@link Failure}.
     *
     * @throws UnsupportedOperationException if this is a {@link Success}
     */
    String errorMessage();

    // ── Sealed variants ───────────────────────────────────────────────────────

    /**
     * A successful operation result holding a non-null value.
     *
     * @param result the success value; must not be {@code null}
     */
    record Success<T>(T result) implements OperationResult<T> {

        public Success {
            Objects.requireNonNull(result, "result must not be null");
        }

        @Override public boolean isSuccess()   { return true; }
        @Override public boolean isFailure()   { return false; }
        @Override public Optional<T> value()   { return Optional.of(result); }
        @Override public T valueOrThrow()      { return result; }

        @Override
        public ErrorCode errorCode() {
            throw new UnsupportedOperationException("Success result has no error code");
        }

        @Override
        public String errorMessage() {
            throw new UnsupportedOperationException("Success result has no error message");
        }
    }

    /**
     * A failed operation result holding an error code, message, and optional cause.
     *
     * @param code    categorised error code; must not be {@code null}
     * @param message human-readable description; must not be {@code null}
     * @param cause   the underlying exception; may be {@code null}
     */
    record Failure<T>(ErrorCode code, String message, Throwable cause)
            implements OperationResult<T> {

        public Failure {
            Objects.requireNonNull(code,    "code must not be null");
            Objects.requireNonNull(message, "message must not be null");
        }

        @Override public boolean isSuccess()  { return false; }
        @Override public boolean isFailure()  { return true; }
        @Override public Optional<T> value()  { return Optional.empty(); }

        @Override
        public T valueOrThrow() {
            throw new OperationFailedException(code, message, cause);
        }

        @Override public ErrorCode errorCode()  { return code; }
        @Override public String errorMessage()  { return message; }
    }
}
