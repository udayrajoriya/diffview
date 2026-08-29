package com.comparetool.core.folder;

import com.comparetool.model.FileMeta;
import com.comparetool.model.FolderComparisonOptions;
import com.comparetool.model.FolderItemStatus;

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
