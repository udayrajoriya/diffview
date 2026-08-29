package com.comparetool.core.folder;

import com.comparetool.infra.hash.HashService;
import com.comparetool.model.FileMatchMode;
import com.comparetool.model.FileMeta;
import com.comparetool.model.FolderComparisonOptions;
import com.comparetool.model.FolderItemStatus;

import java.util.Objects;

/**
 * Pluggable strategy that decides whether two paired (both-sides-present) files are
 * {@link FolderItemStatus#IDENTICAL} or {@link FolderItemStatus#DIFFERENT}.
 *
 * <p>The strategy is selected by {@link com.comparetool.model.FileMatchMode} and is
 * applied only to file nodes — directory nodes derive their status through roll-up.
 */
public interface FileMatchCriteria {

    /**
     * Compares two file entries that exist on both sides.
     *
     * @param left    left-side file metadata
     * @param right   right-side file metadata
     * @param options options supplying the timestamp tolerance and other settings
     * @return {@link FolderItemStatus#IDENTICAL} or {@link FolderItemStatus#DIFFERENT}
     */
    FolderItemStatus compare(FileMeta left, FileMeta right, FolderComparisonOptions options);

    // ── Static factory ────────────────────────────────────────────────────────

    /**
     * Returns the {@code FileMatchCriteria} implementation for the given mode.
     *
     * @param mode        the comparison mode from {@link FolderComparisonOptions}
     * @param hashService the hash service to use when {@code mode == CONTENT};
     *                    may be {@code null} for non-content modes
     * @throws NullPointerException if {@code mode} is null, or if {@code mode == CONTENT}
     *                              and {@code hashService} is null
     */
    static FileMatchCriteria forMode(FileMatchMode mode, HashService hashService) {
        Objects.requireNonNull(mode, "mode must not be null");
        return switch (mode) {
            case SIZE_ONLY          -> new SizeOnlyMatchCriteria();
            case SIZE_AND_TIMESTAMP -> new SizeAndTimestampMatchCriteria();
            case CONTENT            -> new ContentMatchCriteria(hashService);
        };
    }
}
