package com.diffview.model;

import java.util.Objects;

/**
 * CSS color values used for diff highlighting.
 *
 * <p>Each field is a CSS color string (e.g. {@code "#ffcccc"}, {@code "rgba(255,200,200,0.4)"}).
 *
 * @param changed  highlight color for lines/spans that are present on both sides but differ
 * @param added    highlight color for lines/spans present only on the right (added)
 * @param removed  highlight color for lines/spans present only on the left (removed)
 */
public record HighlightColors(String changed, String added, String removed) {

    public HighlightColors {
        Objects.requireNonNull(changed, "changed must not be null");
        Objects.requireNonNull(added, "added must not be null");
        Objects.requireNonNull(removed, "removed must not be null");
    }

    // ── Factory ───────────────────────────────────────────────────────────

    /** Returns the default highlight palette (soft red/green/amber tones). */
    public static HighlightColors defaults() {
        return new HighlightColors(
                "rgba(255, 220,   0, 0.35)",   // changed — amber
                "rgba( 80, 200, 120, 0.30)",   // added   — green
                "rgba(220,  80,  80, 0.30)");  // removed — red
    }

    // ── Wither helpers ────────────────────────────────────────────────────

    public HighlightColors withChanged(String value) {
        return new HighlightColors(value, added, removed);
    }

    public HighlightColors withAdded(String value) {
        return new HighlightColors(changed, value, removed);
    }

    public HighlightColors withRemoved(String value) {
        return new HighlightColors(changed, added, value);
    }
}
