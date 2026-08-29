package com.comparetool.model;

import java.util.Objects;

/**
 * Thrown by {@link OperationResult.Failure#valueOrThrow()} when an operation result is a
 * failure and the caller asked for the success value anyway.
 */
public final class OperationFailedException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * @param code    the categorised error code; must not be {@code null}
     * @param message human-readable description
     * @param cause   the underlying cause; may be {@code null}
     */
    public OperationFailedException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = Objects.requireNonNull(code, "code must not be null");
    }

    /** Returns the categorised error code. */
    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
