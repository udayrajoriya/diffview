package com.comparetool.model;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Objects;

/**
 * The result of reading a text file: the decoded lines, the detected (or overridden)
 * encoding, whether a BOM was present, and the dominant line-ending style.
 *
 * @param lines       content lines, stripped of their line terminators; never null; may be empty
 * @param encoding    the charset used to decode the file (auto-detected or caller-supplied override)
 * @param hasBom      {@code true} if the file began with a recognised BOM marker
 * @param lineEnding  the dominant line-ending convention found in the file
 */
public record DecodedText(
        List<String> lines,
        Charset encoding,
        boolean hasBom,
        LineEnding lineEnding) {

    public DecodedText {
        Objects.requireNonNull(lines, "lines must not be null");
        Objects.requireNonNull(encoding, "encoding must not be null");
        Objects.requireNonNull(lineEnding, "lineEnding must not be null");
        lines = List.copyOf(lines);
    }

    /**
     * Returns the full content as a single string, lines joined by the detected
     * {@link #lineEnding()} separator.
     */
    public String content() {
        return String.join(lineEnding.separator(), lines);
    }

    /** Convenience: number of lines. */
    public int lineCount() {
        return lines.size();
    }

    /** Returns {@code true} if the file contained no text (zero lines or one empty line). */
    public boolean isEmpty() {
        return lines.isEmpty() || (lines.size() == 1 && lines.get(0).isEmpty());
    }
}
