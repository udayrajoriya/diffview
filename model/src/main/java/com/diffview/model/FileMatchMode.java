package com.diffview.model;

/**
 * Strategy used to decide whether two paired files are considered identical or different
 * during a folder comparison.
 *
 * <ul>
 *   <li>{@link #SIZE_ONLY}          - compare file size in bytes only (fastest, least accurate).</li>
 *   <li>{@link #SIZE_AND_TIMESTAMP} - compare size and last-modified time within the configured
 *                                     tolerance; used by most file-sync tools as a quick heuristic.</li>
 *   <li>{@link #CONTENT}            - compute and compare full content hashes (slowest, most accurate).</li>
 * </ul>
 */
public enum FileMatchMode {
    SIZE_ONLY,
    SIZE_AND_TIMESTAMP,
    CONTENT
}
