package com.diffview.core.folder;

import com.diffview.model.FileMeta;
import com.diffview.model.FolderComparisonOptions;
import com.diffview.model.FolderItemStatus;

/**
 * {@link FileMatchCriteria} implementation that considers two files equal when
 * their byte sizes are identical, ignoring timestamps and content.
 */
final class SizeOnlyMatchCriteria implements FileMatchCriteria {

    SizeOnlyMatchCriteria() {}

    @Override
    public FolderItemStatus compare(FileMeta left, FileMeta right, FolderComparisonOptions options) {
        return left.size() == right.size()
                ? FolderItemStatus.IDENTICAL
                : FolderItemStatus.DIFFERENT;
    }
}
