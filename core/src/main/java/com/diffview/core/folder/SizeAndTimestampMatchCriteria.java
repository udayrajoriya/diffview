package com.diffview.core.folder;

import com.diffview.model.FileMeta;
import com.diffview.model.FolderComparisonOptions;
import com.diffview.model.FolderItemStatus;

import java.time.Duration;

/**
 * {@link FileMatchCriteria} implementation that considers two files equal when
 * their byte sizes are identical <em>and</em> their last-modified timestamps
 * differ by no more than {@link FolderComparisonOptions#timestampTolerance()}.
 *
 * <p>The tolerance comparison is inclusive: a difference exactly equal to the
 * tolerance is treated as {@link FolderItemStatus#IDENTICAL}.
 */
final class SizeAndTimestampMatchCriteria implements FileMatchCriteria {

    SizeAndTimestampMatchCriteria() {}

    @Override
    public FolderItemStatus compare(FileMeta left, FileMeta right, FolderComparisonOptions options) {
        if (left.size() != right.size()) {
            return FolderItemStatus.DIFFERENT;
        }
        Duration tolerance = options.timestampTolerance();
        Duration delta = Duration.between(left.lastModified(), right.lastModified()).abs();
        return delta.compareTo(tolerance) <= 0
                ? FolderItemStatus.IDENTICAL
                : FolderItemStatus.DIFFERENT;
    }
}
