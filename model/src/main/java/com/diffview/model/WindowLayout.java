package com.diffview.model;

import java.util.Objects;

/**
 * Persisted window geometry and layout state.
 *
 * @param x                 window X position on screen (pixels)
 * @param y                 window Y position on screen (pixels)
 * @param width             window width (pixels); must be > 0
 * @param height            window height (pixels); must be > 0
 * @param maximized         whether the window was maximized when last closed
 * @param splitDividerRatio position of the main split divider as a fraction [0.0, 1.0];
 *                          0.5 means equal halves
 */
public record WindowLayout(
        double x,
        double y,
        double width,
        double height,
        boolean maximized,
        double splitDividerRatio) {

    public WindowLayout {
        if (width <= 0) throw new IllegalArgumentException("width must be > 0, got: " + width);
        if (height <= 0) throw new IllegalArgumentException("height must be > 0, got: " + height);
        if (splitDividerRatio < 0.0 || splitDividerRatio > 1.0) {
            throw new IllegalArgumentException(
                    "splitDividerRatio must be in [0.0, 1.0], got: " + splitDividerRatio);
        }
    }

    // ── Factory ───────────────────────────────────────────────────────────

    /** Returns default layout: centred at the origin, 1280×800, not maximised, equal split. */
    public static WindowLayout defaults() {
        return new WindowLayout(0, 0, 1280, 800, false, 0.5);
    }

    // ── Wither helpers ────────────────────────────────────────────────────

    public WindowLayout withX(double value) {
        return new WindowLayout(value, y, width, height, maximized, splitDividerRatio);
    }

    public WindowLayout withY(double value) {
        return new WindowLayout(x, value, width, height, maximized, splitDividerRatio);
    }

    public WindowLayout withWidth(double value) {
        return new WindowLayout(x, y, value, height, maximized, splitDividerRatio);
    }

    public WindowLayout withHeight(double value) {
        return new WindowLayout(x, y, width, value, maximized, splitDividerRatio);
    }

    public WindowLayout withMaximized(boolean value) {
        return new WindowLayout(x, y, width, height, value, splitDividerRatio);
    }

    public WindowLayout withSplitDividerRatio(double value) {
        return new WindowLayout(x, y, width, height, maximized, value);
    }
}
