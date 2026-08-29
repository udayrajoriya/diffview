package com.diffview.infra.persist;

import com.diffview.model.AppSettings;
import com.diffview.model.RecentComparison;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link DefaultRecentsManager} and {@link FilterProfile} /
 * {@link JacksonFilterProfileRepository} (task 9.2).
 */
class RecentsAndProfileTest {

    @TempDir
    Path tempDir;

    private JacksonSettingsRepository settingsRepo;
    private DefaultRecentsManager     recentsManager;

    @BeforeEach
    void setUp() {
        settingsRepo    = new JacksonSettingsRepository(tempDir);
        recentsManager  = new DefaultRecentsManager(settingsRepo);
    }

    // ── RecentsManager ────────────────────────────────────────────────────────

    @Nested
    class Recents {

        @Test
        void emptyInitially() {
            assertThat(recentsManager.getRecents()).isEmpty();
        }

        @Test
        void addOneRecent() {
            RecentComparison r = recent("/a", "/b");
            recentsManager.addRecent(r);
            assertThat(recentsManager.getRecents()).hasSize(1);
            assertThat(recentsManager.getRecents().get(0)).isEqualTo(r);
        }

        @Test
        void addedRecentsArePrependedMostRecentFirst() {
            RecentComparison first  = recent("/a", "/b");
            RecentComparison second = recent("/c", "/d");

            recentsManager.addRecent(first);
            recentsManager.addRecent(second);

            List<RecentComparison> recents = recentsManager.getRecents();
            assertThat(recents).hasSize(2);
            // Most recently added comes first
            assertThat(recents.get(0)).isEqualTo(second);
            assertThat(recents.get(1)).isEqualTo(first);
        }

        @Test
        void addingDuplicatePathsMoveToFront() {
            RecentComparison original    = recent("/a", "/b");
            RecentComparison duplicate   = recent("/a", "/b"); // same paths, different timestamp

            recentsManager.addRecent(original);
            recentsManager.addRecent(recent("/c", "/d")); // some other entry in between
            recentsManager.addRecent(duplicate);

            List<RecentComparison> recents = recentsManager.getRecents();
            assertThat(recents).hasSize(2);                     // no duplicates
            assertThat(recents.get(0).left()).isEqualTo(Path.of("/a")); // /a:/b now at front
        }

        @Test
        void addingDuplicateDoesNotIncreaseListSize() {
            for (int i = 0; i < 5; i++) {
                recentsManager.addRecent(recent("/same", "/path"));
            }
            assertThat(recentsManager.getRecents()).hasSize(1);
        }

        @Test
        void removeRecentByPaths() {
            recentsManager.addRecent(recent("/a", "/b"));
            recentsManager.addRecent(recent("/c", "/d"));

            recentsManager.removeRecent(Path.of("/a"), Path.of("/b"));

            List<RecentComparison> recents = recentsManager.getRecents();
            assertThat(recents).hasSize(1);
            assertThat(recents.get(0).left()).isEqualTo(Path.of("/c"));
        }

        @Test
        void removeNonExistentIsNoOp() {
            recentsManager.addRecent(recent("/a", "/b"));
            recentsManager.removeRecent(Path.of("/x"), Path.of("/y")); // not in list
            assertThat(recentsManager.getRecents()).hasSize(1);
        }

        @Test
        void listTrimmesToMaxRecents() {
            // Add more than MAX_RECENTS (20) entries
            for (int i = 0; i < AppSettings.MAX_RECENTS + 5; i++) {
                recentsManager.addRecent(recent("/left/" + i, "/right/" + i));
            }
            assertThat(recentsManager.getRecents())
                    .hasSizeLessThanOrEqualTo(AppSettings.MAX_RECENTS);
        }

        // ── availability ──────────────────────────────────────────────────────

        @Test
        void existingPathsAreAvailable(@TempDir Path existingDir) throws IOException {
            Path left  = Files.createFile(existingDir.resolve("left.txt"));
            Path right = Files.createFile(existingDir.resolve("right.txt"));
            RecentComparison r = new RecentComparison(left, right, false, Instant.now());

            assertThat(recentsManager.isAvailable(r)).isTrue();
        }

        @Test
        void missingLeftPathIsUnavailable(@TempDir Path existingDir) throws IOException {
            Path right = Files.createFile(existingDir.resolve("right.txt"));
            Path left  = existingDir.resolve("does-not-exist.txt"); // intentionally missing
            RecentComparison r = new RecentComparison(left, right, false, Instant.now());

            assertThat(recentsManager.isAvailable(r)).isFalse();
        }

        @Test
        void missingRightPathIsUnavailable(@TempDir Path existingDir) throws IOException {
            Path left  = Files.createFile(existingDir.resolve("left.txt"));
            Path right = existingDir.resolve("does-not-exist.txt"); // intentionally missing
            RecentComparison r = new RecentComparison(left, right, false, Instant.now());

            assertThat(recentsManager.isAvailable(r)).isFalse();
        }

        @Test
        void bothMissingPathsAreUnavailable() {
            RecentComparison r = new RecentComparison(
                    Path.of("/non/existent/left"),
                    Path.of("/non/existent/right"),
                    false, Instant.now());

            assertThat(recentsManager.isAvailable(r)).isFalse();
        }

        @Test
        void directoryComparisonPathsAvailableWhenBothExist(@TempDir Path existingDir) throws IOException {
            Path left  = Files.createDirectory(existingDir.resolve("leftDir"));
            Path right = Files.createDirectory(existingDir.resolve("rightDir"));
            RecentComparison r = new RecentComparison(left, right, true, Instant.now());

            assertThat(recentsManager.isAvailable(r)).isTrue();
        }
    }

    // ── FilterProfile model ───────────────────────────────────────────────────

    @Nested
    class FilterProfileModel {

        @Test
        void blankNameThrows() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new FilterProfile("  ", List.of(), List.of()));
        }

        @Test
        void nullNameThrows() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new FilterProfile(null, List.of(), List.of()));
        }

        @Test
        void emptyFactoryMethod() {
            FilterProfile p = FilterProfile.empty("test");
            assertThat(p.name()).isEqualTo("test");
            assertThat(p.includeMasks()).isEmpty();
            assertThat(p.excludeMasks()).isEmpty();
        }

        @Test
        void masksAreImmutable() {
            FilterProfile p = new FilterProfile("p", List.of("*.java"), List.of("*.class"));
            assertThat(p.includeMasks()).containsExactly("*.java");
            assertThat(p.excludeMasks()).containsExactly("*.class");
        }
    }

    // ── FilterProfileRepository ───────────────────────────────────────────────

    @Nested
    class ProfileImportExport {

        private JacksonFilterProfileRepository profileRepo;

        @BeforeEach
        void setUp() {
            profileRepo = new JacksonFilterProfileRepository();
        }

        @Test
        void exportThenImportEqualsOriginal() {
            FilterProfile original = new FilterProfile(
                    "Java Sources",
                    List.of("*.java", "*.kt"),
                    List.of("*.class", "build/", ".gradle/"));

            Path file = tempDir.resolve("profile.json");
            profileRepo.export(original, file);
            FilterProfile imported = profileRepo.importProfile(file);

            assertThat(imported).isEqualTo(original);
        }

        @Test
        void exportedFileExists() {
            Path file = tempDir.resolve("profile.json");
            profileRepo.export(FilterProfile.empty("test"), file);
            assertThat(file).isRegularFile();
        }

        @Test
        void exportedFileContainsProfileName() throws IOException {
            Path file = tempDir.resolve("profile.json");
            profileRepo.export(new FilterProfile("My Profile", List.of(), List.of()), file);
            assertThat(Files.readString(file)).contains("My Profile");
        }

        @Test
        void emptyMasksRoundTrip() {
            FilterProfile original = FilterProfile.empty("Empty");
            Path file = tempDir.resolve("empty.json");
            profileRepo.export(original, file);
            FilterProfile imported = profileRepo.importProfile(file);
            assertThat(imported.includeMasks()).isEmpty();
            assertThat(imported.excludeMasks()).isEmpty();
        }

        @Test
        void importNonExistentFileThrowsUncheckedIO() {
            assertThatThrownBy(() ->
                    profileRepo.importProfile(tempDir.resolve("nonexistent.json"))
            ).isInstanceOf(java.io.UncheckedIOException.class);
        }

        @Test
        void exportNullProfileThrowsNPE() {
            assertThatNullPointerException()
                    .isThrownBy(() -> profileRepo.export(null, tempDir.resolve("x.json")));
        }

        @Test
        void exportNullTargetThrowsNPE() {
            assertThatNullPointerException()
                    .isThrownBy(() -> profileRepo.export(FilterProfile.empty("x"), null));
        }

        @Test
        void importNullSourceThrowsNPE() {
            assertThatNullPointerException()
                    .isThrownBy(() -> profileRepo.importProfile(null));
        }

        @Test
        void overwriteExistingFileOnExport() {
            Path file = tempDir.resolve("profile.json");
            profileRepo.export(new FilterProfile("Old", List.of("*.old"), List.of()), file);
            profileRepo.export(new FilterProfile("New", List.of("*.new"), List.of()), file);
            FilterProfile imported = profileRepo.importProfile(file);
            assertThat(imported.name()).isEqualTo("New");
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static RecentComparison recent(String left, String right) {
        return new RecentComparison(Path.of(left), Path.of(right), false, Instant.now());
    }
}
