package com.diffview.model;

/**
 * Categorised error codes for {@link OperationResult} and {@link ItemError} (REQ-016.1, REQ-016.2).
 *
 * <p>Callers use these codes to branch on the failure category without parsing message strings.
 */
public enum ErrorCode {

    /** File or directory I/O failure — unreadable, locked, or otherwise inaccessible. */
    IO_ERROR,

    /** Access was denied to the path by the operating system. */
    PERMISSION_DENIED,

    /** A required path does not exist on the file system. */
    PATH_NOT_FOUND,

    /** File content is not in the expected or supported format. */
    INVALID_FORMAT,

    /** The operation was explicitly cancelled by the user. */
    CANCELLED,

    /** Catch-all for unexpected errors not covered by other codes. */
    UNKNOWN
}
