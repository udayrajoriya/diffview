package com.diffview.infra.persist;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/**
 * Exports and imports {@link FilterProfile}s as portable JSON files.
 *
 * <p>Profiles are written to user-selected files so they can be shared across
 * machines or checked into source control.
 */
public interface FilterProfileRepository {

    /**
     * Exports {@code profile} to a JSON file at {@code targetFile}.
     *
     * <p>The parent directory must already exist; the file is created or overwritten.
     *
     * @param profile    the profile to export; must not be {@code null}
     * @param targetFile destination path (must not be {@code null})
     * @throws UncheckedIOException if the file cannot be written
     */
    void export(FilterProfile profile, Path targetFile);

    /**
     * Reads a {@link FilterProfile} from a JSON file previously written by {@link #export}.
     *
     * @param sourceFile path to the JSON file; must not be {@code null}
     * @return the imported profile
     * @throws UncheckedIOException if the file cannot be read
     * @throws IllegalArgumentException if the file content is not valid profile JSON
     */
    FilterProfile importProfile(Path sourceFile);
}
