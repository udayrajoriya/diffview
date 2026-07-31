package com.comparetool.app;

import com.comparetool.infra.persist.DefaultRecentsManager;
import com.comparetool.infra.persist.FilterProfile;
import com.comparetool.infra.persist.JacksonFilterProfileRepository;
import com.comparetool.infra.persist.JacksonSettingsRepository;
import com.comparetool.model.AppSettings;
import com.comparetool.model.RecentComparison;
import com.comparetool.model.ThemeMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end persistence round-trip tests (task 17.1).
 *
 * <p>Requirements: 2.x, 8.x
 *
 * <p>Each test simulates a "restart" by creating a second repository instance pointed at
 * the same file or directory written by the first instance, verifying that values survive
 * a full serialize → deserialize cycle.
 */
class PersistenceRoundTripTest {

    // ── REQ-8.x: AppSettings round-trip ──────────────────────────────────────

    @Test
    void settingsRoundTripAcrossSimulatedRestart(@TempDir Path configDir) {
        JacksonSettingsRepository repo1 = new JacksonSettingsRepository(configDir);

        // Persist settings with a non-default theme
        AppSettings original = AppSettings.defaults().withTheme(ThemeMode.DARK);
        repo1.save(original);

        // "Restart": new repository instance pointing at same directory
        JacksonSettingsRepository repo2 = new JacksonSettingsRepository(configDir);
        AppSettings loaded = repo2.load();

        assertThat(loaded.theme()).isEqualTo(ThemeMode.DARK);
        assertThat(loaded.defaultComparison()).isEqualTo(original.defaultComparison());
        assertThat(loaded.defaultFolderOptions()).isEqualTo(original.defaultFolderOptions());
    }

    // ── REQ-8.x: settings with recents survive restart ────────────────────────

    @Test
    void recentsManagerAddAndReload(@TempDir Path configDir) {
        JacksonSettingsRepository repo1 = new JacksonSettingsRepository(configDir);
        DefaultRecentsManager mgr1 = new DefaultRecentsManager(repo1);

        Path left  = Path.of("/tmp/left.txt");
        Path right = Path.of("/tmp/right.txt");
        RecentComparison recent = RecentComparison.of(left, right, false);
        mgr1.addRecent(recent);

        // Simulated restart: new repository and manager
        JacksonSettingsRepository repo2 = new JacksonSettingsRepository(configDir);
        DefaultRecentsManager mgr2 = new DefaultRecentsManager(repo2);

        List<RecentComparison> loaded = mgr2.getRecents();
        assertThat(loaded).hasSize(1);
        assertThat(loaded.get(0).left()).isEqualTo(left);
        assertThat(loaded.get(0).right()).isEqualTo(right);
        assertThat(loaded.get(0).folder()).isFalse();
    }

    // ── REQ-8.x: multiple recents round-trip in order ────────────────────────

    @Test
    void multipleRecentsPreserveInsertionOrder(@TempDir Path configDir) {
        JacksonSettingsRepository repo = new JacksonSettingsRepository(configDir);
        DefaultRecentsManager mgr = new DefaultRecentsManager(repo);

        RecentComparison r1 = RecentComparison.of(Path.of("/a"), Path.of("/b"), false);
        RecentComparison r2 = RecentComparison.of(Path.of("/c"), Path.of("/d"), true);
        mgr.addRecent(r1);
        mgr.addRecent(r2);

        // New instance reads the same config
        DefaultRecentsManager mgr2 = new DefaultRecentsManager(
                new JacksonSettingsRepository(configDir));

        List<RecentComparison> recents = mgr2.getRecents();
        assertThat(recents).hasSize(2);
        // Most-recent first (r2 was added last)
        assertThat(recents.get(0).left()).isEqualTo(Path.of("/c"));
        assertThat(recents.get(1).left()).isEqualTo(Path.of("/a"));
    }

    // ── REQ-8.x: FilterProfile export / import round-trip ───────────────────

    @Test
    void filterProfileRoundTripExportImport(@TempDir Path tmp) {
        JacksonFilterProfileRepository repo = new JacksonFilterProfileRepository();
        Path profileFile = tmp.resolve("my-profile.json");

        FilterProfile original = new FilterProfile(
                "Java Sources",
                List.of("**/*.java", "**/*.kt"),
                List.of("**/build/**", "**/*.class"));

        repo.export(original, profileFile);

        // Simulated restart: fresh repo instance, import the same file
        JacksonFilterProfileRepository repo2 = new JacksonFilterProfileRepository();
        FilterProfile loaded = repo2.importProfile(profileFile);

        assertThat(loaded.name()).isEqualTo("Java Sources");
        assertThat(loaded.includeMasks()).containsExactly("**/*.java", "**/*.kt");
        assertThat(loaded.excludeMasks()).containsExactly("**/build/**", "**/*.class");
    }

    // ── REQ-8.x: corrupt or missing settings file returns defaults ───────────

    @Test
    void missingSettingsFileReturnsDefaults(@TempDir Path configDir) {
        JacksonSettingsRepository repo = new JacksonSettingsRepository(configDir);

        // No file written — should return defaults without throwing
        AppSettings loaded = repo.load();

        assertThat(loaded).isEqualTo(AppSettings.defaults());
    }
}
