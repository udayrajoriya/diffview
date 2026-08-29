package com.diffview.infra.persist;

import com.diffview.model.AppSettings;

/**
 * Loads and saves {@link AppSettings} to a JSON file in the OS app-config directory.
 *
 * <p>Implementations must be defensive on {@link #load()}: any failure (missing file,
 * corrupt JSON, validation error) must return {@link AppSettings#defaults()} without
 * propagating an exception.  {@link #save(AppSettings)} may throw on I/O failure.
 */
public interface SettingsRepository {

    /**
     * Loads the persisted {@link AppSettings}.
     *
     * <p>Returns {@link AppSettings#defaults()} if the settings file does not exist
     * or cannot be parsed (corrupt / invalid).
     *
     * @return the loaded settings, or defaults — never {@code null}
     */
    AppSettings load();

    /**
     * Saves {@code settings} to the persistent store.
     *
     * <p>The config directory is created if it does not yet exist.
     *
     * @param settings the settings to persist; must not be {@code null}
     * @throws java.io.UncheckedIOException if the file cannot be written
     */
    void save(AppSettings settings);
}
