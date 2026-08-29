package com.comparetool.model;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link OperationResult}, {@link OperationFailedException}, and
 * {@link ErrorCode}.
 */
class OperationResultTest {

    // ── Success ───────────────────────────────────────────────────────────────

    @Test
    void successHoldsValue() {
        OperationResult<String> result = OperationResult.success("hello");
        assertThat(result.valueOrThrow()).isEqualTo("hello");
    }

    @Test
    void successIsSuccess() {
        assertThat(OperationResult.success("x").isSuccess()).isTrue();
    }

    @Test
    void successIsNotFailure() {
        assertThat(OperationResult.success("x").isFailure()).isFalse();
    }

    @Test
    void successValueIsPresentInOptional() {
        Optional<String> opt = OperationResult.success("world").value();
        assertThat(opt).isPresent().contains("world");
    }

    @Test
    void successErrorCodeThrowsUnsupportedOperation() {
        OperationResult<String> result = OperationResult.success("v");
        assertThatThrownBy(result::errorCode)
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void successErrorMessageThrowsUnsupportedOperation() {
        OperationResult<String> result = OperationResult.success("v");
        assertThatThrownBy(result::errorMessage)
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ── Failure ───────────────────────────────────────────────────────────────

    @Test
    void failureIsFailure() {
        assertThat(OperationResult.failure(ErrorCode.IO_ERROR, "boom").isFailure()).isTrue();
    }

    @Test
    void failureIsNotSuccess() {
        assertThat(OperationResult.failure(ErrorCode.IO_ERROR, "boom").isSuccess()).isFalse();
    }

    @Test
    void failureValueIsEmpty() {
        Optional<?> opt = OperationResult.failure(ErrorCode.CANCELLED, "cancelled").value();
        assertThat(opt).isEmpty();
    }

    @Test
    void failureValueOrThrowThrowsOperationFailedException() {
        OperationResult<String> result = OperationResult.failure(ErrorCode.PATH_NOT_FOUND, "missing");
        assertThatThrownBy(result::valueOrThrow)
                .isInstanceOf(OperationFailedException.class)
                .hasMessage("missing");
    }

    @Test
    void failureValueOrThrowCarriesCause() {
        IOException cause = new IOException("disk error");
        OperationResult<Integer> result =
                OperationResult.failure(ErrorCode.IO_ERROR, "io fail", cause);
        assertThatThrownBy(result::valueOrThrow)
                .isInstanceOf(OperationFailedException.class)
                .hasCause(cause);
    }

    @Test
    void failureErrorCodeIsReturned() {
        OperationResult<Void> result =
                OperationResult.failure(ErrorCode.PERMISSION_DENIED, "nope");
        assertThat(result.errorCode()).isEqualTo(ErrorCode.PERMISSION_DENIED);
    }

    @Test
    void failureErrorMessageIsReturned() {
        OperationResult<Void> result =
                OperationResult.failure(ErrorCode.UNKNOWN, "something went wrong");
        assertThat(result.errorMessage()).isEqualTo("something went wrong");
    }

    // ── OperationFailedException ──────────────────────────────────────────────

    @Test
    void operationFailedExceptionExposesErrorCode() {
        OperationFailedException ex =
                new OperationFailedException(ErrorCode.INVALID_FORMAT, "bad format", null);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_FORMAT);
        assertThat(ex.getMessage()).isEqualTo("bad format");
    }

    // ── ErrorCode ─────────────────────────────────────────────────────────────

    @Test
    void allErrorCodesAreDistinct() {
        ErrorCode[] values = ErrorCode.values();
        Set<ErrorCode> distinct = new HashSet<>(Arrays.asList(values));
        assertThat(distinct).hasSize(values.length);
    }

    @Test
    void errorCodeEnumHasExpectedEntries() {
        assertThat(ErrorCode.values()).containsExactlyInAnyOrder(
                ErrorCode.IO_ERROR,
                ErrorCode.PERMISSION_DENIED,
                ErrorCode.PATH_NOT_FOUND,
                ErrorCode.INVALID_FORMAT,
                ErrorCode.CANCELLED,
                ErrorCode.UNKNOWN);
    }

    // ── ItemError ─────────────────────────────────────────────────────────────

    @Test
    void itemErrorHoldsAllComponents() {
        java.nio.file.Path rel = java.nio.file.Path.of("some/file.txt");
        ItemError err = new ItemError(rel, ErrorCode.IO_ERROR, "read failed");
        assertThat(err.relativePath()).isEqualTo(rel);
        assertThat(err.code()).isEqualTo(ErrorCode.IO_ERROR);
        assertThat(err.message()).isEqualTo("read failed");
    }

    @Test
    void itemErrorRejectsNullRelativePath() {
        assertThatThrownBy(() -> new ItemError(null, ErrorCode.IO_ERROR, "msg"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void itemErrorRejectsNullCode() {
        assertThatThrownBy(() -> new ItemError(java.nio.file.Path.of("a"), null, "msg"))
                .isInstanceOf(NullPointerException.class);
    }
}
