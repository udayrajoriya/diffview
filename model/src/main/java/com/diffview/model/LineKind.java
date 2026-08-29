package com.comparetool.model;

/**
 * Classification of a single line in a side-by-side file diff.
 *
 * <ul>
 *   <li>{@code UNCHANGED} – line is identical on both sides.</li>
 *   <li>{@code CHANGED}   – line exists on both sides but content differs.</li>
 *   <li>{@code ADDED}     – line exists only on the right side; the left side
 *                           shows a placeholder row (null leftLineNumber).</li>
 *   <li>{@code REMOVED}   – line exists only on the left side; the right side
 *                           shows a placeholder row (null rightLineNumber).</li>
 * </ul>
 */
public enum LineKind {
    UNCHANGED,
    CHANGED,
    ADDED,
    REMOVED
}
