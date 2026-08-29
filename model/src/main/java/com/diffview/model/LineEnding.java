package com.comparetool.model;

/**
 * Line-ending convention used when writing a file or normalizing line comparison.
 */
public enum LineEnding {
    /** Unix-style: {@code \n} (0x0A). */
    LF("\n"),
    /** Windows-style: {@code \r\n} (0x0D 0x0A). */
    CRLF("\r\n"),
    /** Classic Mac-style: {@code \r} (0x0D). */
    CR("\r");

    private final String separator;

    LineEnding(String separator) {
        this.separator = separator;
    }

    /** Returns the actual line-separator string for this convention. */
    public String separator() {
        return separator;
    }

    /**
     * Detects the dominant line ending in the given text.
     * Defaults to {@link #LF} if no line endings are found.
     */
    public static LineEnding detect(String text) {
        if (text == null || text.isEmpty()) {
            return LF;
        }
        int lf = 0, cr = 0, crlf = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\r') {
                if (i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    crlf++;
                    i++; // skip the \n
                } else {
                    cr++;
                }
            } else if (c == '\n') {
                lf++;
            }
        }
        if (crlf > 0 && crlf >= lf && crlf >= cr) return CRLF;
        if (cr > 0 && cr > lf) return CR;
        return LF;
    }
}
