package com.comparetool.infra.persist;

import com.comparetool.model.AppSettings;
import com.comparetool.model.ComparisonOptions;
import com.comparetool.model.FolderComparisonOptions;
import com.comparetool.model.HighlightColors;
import com.comparetool.model.RecentComparison;
import com.comparetool.model.ThemeMode;
import com.comparetool.model.WindowLayout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link JacksonSettingsRepository} (task 9.1).
 *
 * <p>All tests inject a {@code @TempDir} so no OS-level config directory is touched.
 */
class SettingsRepositoryTest {

    @TempDir
    Path tempDir;

    private JacksonSettingsRepository repo;

    @BeforeEach
    void setUp() {
        repo = new JacksonSettingsRepository(tempDir);
    }

    // ── missing-file fallback ─────────────────────────────────────────────────

    @Nested
    class MissingFile {

        @Test
        void noFileReturnsDefaults() {
            AppSettings loaded = repo.load();
            assertThat(loaded).isEqualTo(AppSettings.defaults());
        }

        @Test
        void loadDoesNotThrow() {
            assertThatCode(() -> repo.load()).doesNotThrowAnyException();
        }
    }

    // ── round-trip ────────────────────────────────────────────────────────────

    @Nested
    class RoundTrip {

        @Test
        void defaultSettingsRoundTrip() {
            AppSettings original = AppSettings.defaults();
            repo.save(original);
            AppSettings loaded = repo.load();
            assertThat(loaded).isEqualTo(original);
        }

        @Test
        void modifiedThemeRoundTrip() {
            AppSettings original = AppSettings.defaults().withTheme(ThemeMode.DARK);
            repo.save(original);
            AppSettings loaded = repo.load();
            assertThat(loaded.theme()).isEqualTo(ThemeMode.DARK);
        }

        @Test
        void customHighlightColorsRoundTrip() {
            AppSettings original = AppSettings.defaults()
                    .withColors(new HighlightColors("#ff0000", "#00ff00", "#0000ff"));
            repo.save(original);
            AppSettings loaded = repo.load();
            assertThat(loaded.colors().changed()).isEqualTo("#ff0000");
            assertThat(loaded.colors().added()).isEqualTo("#00ff00");
            assertThat(loaded.colors().removed()).isEqualTo("#0000ff");
        }

        @Test
        void customWindowLayoutRoundTrip() {
            AppSettings original = AppSettings.defaults()
                    .withLayout(new WindowLayout(100, 200, 1920, 1080, true, 0.3));
            repo.save(original);
            AppSettings loaded = repo.load();
            assertThat(loaded.layout().x()).isEqualTo(100.0);
            assertThat(loaded.layout().y()).isEqualTo(200.0);
            assertThat(loaded.layout().width()).isEqualTo(1920.0);
            assertThat(loaded.layout().height()).isEqualTo(1080.0);
            assertThat(loaded.layout().maximized()).isTrue();
            assertThat(loaded.layout().splitDividerRatio()).isEqualTo(0.3);
        }

        @Test
        void recentsListRoundTrip() {
            Path left  = Path.of("/projects/left");
            Path right = Path.of("/projects/right");
            RecentComparison recent = new RecentComparison(left, right, false, Instant.ofEpochSecond(1_000_000));

            AppSettings original = AppSettings.defaults()
                    .withRecents(List.of(recent));
            repo.save(original);
            AppSettings loaded = repo.load();

            assertThat(loaded.recents()).hasSize(1);
            RecentComparison loadedRecent = loaded.recents().get(0);
            assertThat(loadedRecent.left()).isEqualTo(left);
            assertThat(loadedRecent.right()).isEqualTo(right);
            assertThat(loadedRecent.folder()).isFalse();
            assertThat(loadedRecent.lastOpened()).isEqualTo(Instant.ofEpochSecond(1_000_000));
        }

        @Test
        void comparisonOptionsWithIgnoreFlagsRoundTrip() {
            ComparisonOptions opts = ComparisonOptions.defaults()
                    .withIgnoreWhitespace(true)
                    .withIgnoreCase(true)
                    .withIgnoreLineEndings(true);
            AppSettings original = AppSettings.defaults().withDefaultComparison(opts);
            repo.save(original);
            AppSettings loaded = repo.load();
            assertThat(loaded.defaultComparison().ignoreWhitespace()).isTrue();
            assertThat(loaded.defaultComparison().ignoreCase()).isTrue();
            assertThat(loaded.defaultComparison().ignoreLineEndings()).isTrue();
        }

        @Test
        void comparisonOptionsWithEncodingOverrideRoundTrip() {
            ComparisonOptions opts = ComparisonOptions.defaults()
                    .withLeftEncodingOverride(StandardCharsets.UTF_16);
            AppSettings original = AppSettings.defaults().withDefaultComparison(opts);
            repo.save(original);
            AppSettings loaded = repo.load();
            assertThat(loaded.defaultComparison().leftEncodingOverride())
                    .isEqualTo(StandardCharsets.UTF_16);
        }

        @Test
        void nullEncodingOverridePreservedRoundTrip() {
            // Defaults have null encoding overrides
            AppSettings original = AppSettings.defaults();
            repo.save(original);
            AppSettings loaded = repo.load();
            assertThat(loaded.defaultComparison().leftEncodingOverride()).isNull();
            assertThat(loaded.defaultComparison().rightEncodingOverride()).isNull();
        }

        @Test
        void folderOptionsWithMasksRoundTrip() {
            FolderComparisonOptions folderOpts = FolderComparisonOptions.defaults()
                    .withIncludeMasks(List.of("*.java", "*.kt"))
                    .withExcludeMasks(List.of("*.class", "build/"));
            AppSettings original = AppSettings.defaults().withDefaultFolderOptions(folderOpts);
            repo.save(original);
            AppSettings loaded = repo.load();
            assertThat(loaded.defaultFolderOptions().includeMasks())
                    .containsExactlyInAnyOrder("*.java", "*.kt");
            assertThat(loaded.defaultFolderOptions().excludeMasks())
                    .containsExactlyInAnyOrder("*.class", "build/");
        }

        @Test
        void folderOptionsWithManualIgnoresRoundTrip() {
            Path ignored = Path.of("path/to/ignored.txt");
            FolderComparisonOptions folderOpts = FolderComparisonOptions.defaults()
                    .withManualIgnores(Set.of(ignored));
            AppSettings original = AppSettings.defaults().withDefaultFolderOptions(folderOpts);
            repo.save(original);
            AppSettings loaded = repo.load();
            assertThat(loaded.defaultFolderOptions().manualIgnores())
                    .containsExactly(ignored);
        }
    }

    // ── corrupt file fallback ─────────────────────────────────────────────────

    @Nested
    class CorruptFile {

        @Test
        void corruptJsonFallsBackToDefaults() throws IOException {
            Files.writeString(tempDir.resolve("settings.json"), "{ this is not valid json !!! }");
            AppSettings loaded = repo.load();
            assertThat(loaded).isEqualTo(AppSettings.defaults());
        }

        @Test
        void emptyFileFallsBackToDefaults() throws IOException {
            Files.writeString(tempDir.resolve("settings.json"), "");
            AppSettings loaded = repo.load();
            assertThat(loaded).isEqualTo(AppSettings.defaults());
        }

        @Test
        void partiallyValidJsonFallsBackToDefaults() throws IOException {
            Files.writeString(tempDir.resolve("settings.json"),
                    "{\"theme\": \"DARK\", \"defaultComparison\": {incomplete");
            AppSettings loaded = repo.load();
            assertThat(loaded).isEqualTo(AppSettings.defaults());
        }

        @Test
        void corruptLoadDoesNotThrow() throws IOException {
            Files.writeString(tempDir.resolve("settings.json"), "GARBAGE");
            assertThatCode(() -> repo.load()).doesNotThrowAnyException();
        }
    }

    // ── save behaviour ────────────────────────────────────────────────────────

    @Nested
    class SaveBehaviour {

        @Test
        void saveCreatesConfigDirectory(@TempDir Path root) {
            Path nested = root.resolve("subdir").resolve("config");
            JacksonSettingsRepository nestedRepo = new JacksonSettingsRepository(nested);
            assertThat(nested).doesNotExist();
            nestedRepo.save(AppSettings.defaults());
            assertThat(nested).isDirectory();
            assertThat(nested.resolve("settings.json")).isRegularFile();
        }

        @Test
        void saveNullThrowsNPE() {
            assertThatNullPointerException()
                    .isThrownBy(() -> repo.save(null));
        }

        @Test
        void savedFileIsValidJson() throws IOException {
            repo.save(AppSettings.defaults());
            String content = Files.readString(tempDir.resolve("settings.json"));
            assertThat(content).contains("{").contains("}");
            assertThat(content).contains("theme");
        }

        @Test
        void overwriteUpdatesFile() {
            repo.save(AppSettings.defaults().withTheme(ThemeMode.LIGHT));
            repo.save(AppSettings.defaults().withTheme(ThemeMode.DARK));
            AppSettings loaded = repo.load();
            assertThat(loaded.theme()).isEqualTo(ThemeMode.DARK);
        }
    }

    // ── default config dir ────────────────────────────────────────────────────

    @Test
    void defaultConfigDirIsNonNull() {
        assertThat(JacksonSettingsRepository.defaultConfigDir()).isNotNull();
    }
}
