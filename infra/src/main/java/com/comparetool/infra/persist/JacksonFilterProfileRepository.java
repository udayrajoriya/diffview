package com.comparetool.infra.persist;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * {@link FilterProfileRepository} implementation backed by Jackson JSON.
 *
 * <p>Profiles are written as pretty-printed JSON for human readability.  Unknown fields
 * are ignored on import for forward compatibility with newer profile formats.
 */
public final class JacksonFilterProfileRepository implements FilterProfileRepository {

    private final ObjectMapper mapper;

    /** Creates a repository with default Jackson configuration. */
    public JacksonFilterProfileRepository() {
        ObjectMapper m = new ObjectMapper();
        m.enable(SerializationFeature.INDENT_OUTPUT);
        m.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.mapper = m;
    }

    // ── FilterProfileRepository ───────────────────────────────────────────────

    @Override
    public void export(FilterProfile profile, Path targetFile) {
        Objects.requireNonNull(profile,    "profile");
        Objects.requireNonNull(targetFile, "targetFile");
        try {
            mapper.writeValue(targetFile.toFile(), profile);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to export filter profile to " + targetFile, e);
        }
    }

    @Override
    public FilterProfile importProfile(Path sourceFile) {
        Objects.requireNonNull(sourceFile, "sourceFile");
        try {
            FilterProfile profile = mapper.readValue(sourceFile.toFile(), FilterProfile.class);
            if (profile == null) {
                throw new IllegalArgumentException(
                        "Profile file is empty or null: " + sourceFile);
            }
            return profile;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to import filter profile from " + sourceFile, e);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Invalid filter profile JSON in: " + sourceFile, e);
        }
    }
}
