package com.diffview.viewmodel;

import com.diffview.infra.persist.SettingsRepository;
import com.diffview.model.AppSettings;
import com.diffview.model.ComparisonOptions;
import com.diffview.model.FolderComparisonOptions;
import com.diffview.model.HighlightColors;
import com.diffview.model.ThemeMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SettingsViewModel} (task 10.3).
 *
 * <ul>
 *   <li>No JavaFX {@code Node} subclasses.</li>
 *   <li>{@link SettingsRepository} is mocked via Mockito.</li>
 *   <li>Property change events are verified by attaching a listener before each mutation.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class SettingsViewModelTest {

    @Mock SettingsRepository repository;

    private SettingsViewModel vm;

    @BeforeEach
    void setUp() {
        // Default load returns AppSettings.defaults()
        when(repository.load()).thenReturn(AppSettings.defaults());
        vm = new SettingsViewModel(repository);
    }

    // ── Construction ──────────────────────────────────────────────────────────

    @Nested
    class Construction {

        @Test
        void loadsSettingsFromRepositoryOnConstruction() {
            verify(repository, times(1)).load();
        }

        @Test
        void settingsPropertyIsNotNullAfterConstruction() {
            assertThat(vm.getSettings()).isNotNull();
        }

        @Test
        void themeDefaultsToSystem() {
            assertThat(vm.getTheme()).isEqualTo(ThemeMode.SYSTEM);
        }

        @Test
        void colorsDefaultsToHighlightColorsDefaults() {
            assertThat(vm.getColors()).isEqualTo(HighlightColors.defaults());
        }

        @Test
        void defaultComparisonDefaultsToComparisonOptionsDefaults() {
            assertThat(vm.getDefaultComparison()).isEqualTo(ComparisonOptions.defaults());
        }

        @Test
        void defaultFolderOptionsDefaultsToFolderOptionsDefaults() {
            assertThat(vm.getDefaultFolderOptions()).isEqualTo(FolderComparisonOptions.defaults());
        }
    }

    // ── setTheme ──────────────────────────────────────────────────────────────

    @Nested
    class SetTheme {

        @Test
        void setThemePersistsToRepository() {
            vm.setTheme(ThemeMode.DARK);
            verify(repository).save(argThat(s -> s.theme() == ThemeMode.DARK));
        }

        @Test
        void setThemeUpdatesThemeProperty() {
            vm.setTheme(ThemeMode.DARK);
            assertThat(vm.getTheme()).isEqualTo(ThemeMode.DARK);
        }

        @Test
        void setThemePropagatesOnThemePropertyChangeEvent() {
            ThemeMode[] captured = {null};
            vm.themeProperty().addListener((obs, oldVal, newVal) -> captured[0] = newVal);

            vm.setTheme(ThemeMode.LIGHT);

            assertThat(captured[0]).isEqualTo(ThemeMode.LIGHT);
        }

        @Test
        void setThemePropagatesOnSettingsPropertyChangeEvent() {
            AppSettings[] captured = {null};
            vm.settingsProperty().addListener((obs, oldVal, newVal) -> captured[0] = newVal);

            vm.setTheme(ThemeMode.DARK);

            assertThat(captured[0]).isNotNull();
            assertThat(captured[0].theme()).isEqualTo(ThemeMode.DARK);
        }

        @Test
        void settingsObjectConsistentWithThemeProperty() {
            vm.setTheme(ThemeMode.DARK);
            assertThat(vm.getSettings().theme()).isEqualTo(vm.getTheme());
        }

        @Test
        void nullThemeThrowsNPE() {
            assertThatNullPointerException()
                    .isThrownBy(() -> vm.setTheme(null));
        }

        @Test
        void setThemeToSameValueStillPersists() {
            // Setting the same value should still trigger save (no equality shortcut)
            vm.setTheme(ThemeMode.SYSTEM);
            verify(repository, times(1)).save(any()); // once from setUp isn't saved; this is first save
        }
    }

    // ── setColors ─────────────────────────────────────────────────────────────

    @Nested
    class SetColors {

        @Test
        void setColorsPersistsToRepository() {
            HighlightColors custom = new HighlightColors("#111", "#222", "#333");
            vm.setColors(custom);
            verify(repository).save(argThat(s -> s.colors().equals(custom)));
        }

        @Test
        void setColorsUpdatesColorsProperty() {
            HighlightColors custom = new HighlightColors("#111", "#222", "#333");
            vm.setColors(custom);
            assertThat(vm.getColors()).isEqualTo(custom);
        }

        @Test
        void setColorsPropagatesChangeEvent() {
            HighlightColors[] captured = {null};
            vm.colorsProperty().addListener((obs, o, n) -> captured[0] = n);

            HighlightColors custom = new HighlightColors("#aaa", "#bbb", "#ccc");
            vm.setColors(custom);

            assertThat(captured[0]).isEqualTo(custom);
        }

        @Test
        void settingsObjectConsistentWithColorsProperty() {
            HighlightColors custom = new HighlightColors("#x", "#y", "#z");
            vm.setColors(custom);
            assertThat(vm.getSettings().colors()).isEqualTo(vm.getColors());
        }

        @Test
        void nullColorsThrowsNPE() {
            assertThatNullPointerException()
                    .isThrownBy(() -> vm.setColors(null));
        }
    }

    // ── setDefaultComparison ──────────────────────────────────────────────────

    @Nested
    class SetDefaultComparison {

        @Test
        void setDefaultComparisonPersistsToRepository() {
            ComparisonOptions opts = ComparisonOptions.defaults().withIgnoreCase(true);
            vm.setDefaultComparison(opts);
            verify(repository).save(argThat(s -> s.defaultComparison().ignoreCase()));
        }

        @Test
        void setDefaultComparisonUpdatesProperty() {
            ComparisonOptions opts = ComparisonOptions.defaults().withIgnoreWhitespace(true);
            vm.setDefaultComparison(opts);
            assertThat(vm.getDefaultComparison().ignoreWhitespace()).isTrue();
        }

        @Test
        void setDefaultComparisonPropagatesChangeEvent() {
            ComparisonOptions[] captured = {null};
            vm.defaultComparisonProperty().addListener((obs, o, n) -> captured[0] = n);

            ComparisonOptions opts = ComparisonOptions.defaults().withIgnoreLineEndings(true);
            vm.setDefaultComparison(opts);

            assertThat(captured[0]).isNotNull();
            assertThat(captured[0].ignoreLineEndings()).isTrue();
        }

        @Test
        void settingsConsistentWithDefaultComparisonProperty() {
            ComparisonOptions opts = ComparisonOptions.defaults().withIgnoreCase(true);
            vm.setDefaultComparison(opts);
            assertThat(vm.getSettings().defaultComparison()).isEqualTo(vm.getDefaultComparison());
        }

        @Test
        void nullDefaultComparisonThrowsNPE() {
            assertThatNullPointerException()
                    .isThrownBy(() -> vm.setDefaultComparison(null));
        }
    }

    // ── setDefaultFolderOptions ───────────────────────────────────────────────

    @Nested
    class SetDefaultFolderOptions {

        @Test
        void setDefaultFolderOptionsPersistsToRepository() {
            FolderComparisonOptions opts = FolderComparisonOptions.defaults()
                    .withShowOnlyDifferences(true);
            vm.setDefaultFolderOptions(opts);
            verify(repository).save(argThat(s -> s.defaultFolderOptions().showOnlyDifferences()));
        }

        @Test
        void setDefaultFolderOptionsUpdatesProperty() {
            FolderComparisonOptions opts = FolderComparisonOptions.defaults()
                    .withShowOnlyDifferences(true);
            vm.setDefaultFolderOptions(opts);
            assertThat(vm.getDefaultFolderOptions().showOnlyDifferences()).isTrue();
        }

        @Test
        void setDefaultFolderOptionsPropagatesChangeEvent() {
            FolderComparisonOptions[] captured = {null};
            vm.defaultFolderOptionsProperty().addListener((obs, o, n) -> captured[0] = n);

            FolderComparisonOptions opts = FolderComparisonOptions.defaults()
                    .withShowOnlyDifferences(true);
            vm.setDefaultFolderOptions(opts);

            assertThat(captured[0]).isNotNull();
            assertThat(captured[0].showOnlyDifferences()).isTrue();
        }

        @Test
        void nullDefaultFolderOptionsThrowsNPE() {
            assertThatNullPointerException()
                    .isThrownBy(() -> vm.setDefaultFolderOptions(null));
        }
    }

    // ── reload ────────────────────────────────────────────────────────────────

    @Nested
    class Reload {

        @Test
        void reloadFetchesFreshSettingsFromRepository() {
            AppSettings updated = AppSettings.defaults().withTheme(ThemeMode.LIGHT);
            when(repository.load()).thenReturn(updated);

            vm.reload();

            assertThat(vm.getTheme()).isEqualTo(ThemeMode.LIGHT);
        }

        @Test
        void reloadPropagatesThemeChangeIfDifferent() {
            ThemeMode[] captured = {null};
            vm.themeProperty().addListener((obs, o, n) -> captured[0] = n);

            when(repository.load())
                    .thenReturn(AppSettings.defaults().withTheme(ThemeMode.DARK));
            vm.reload();

            assertThat(captured[0]).isEqualTo(ThemeMode.DARK);
        }

        @Test
        void reloadDoesNotSaveToRepository() {
            vm.reload();
            // Only the initial load, no save during reload
            verify(repository, never()).save(any());
        }

        @Test
        void settingsConsistentAfterReload() {
            AppSettings updated = AppSettings.defaults()
                    .withTheme(ThemeMode.DARK)
                    .withColors(new HighlightColors("#1", "#2", "#3"));
            when(repository.load()).thenReturn(updated);

            vm.reload();

            assertThat(vm.getSettings().theme()).isEqualTo(vm.getTheme());
            assertThat(vm.getSettings().colors()).isEqualTo(vm.getColors());
        }
    }

    // ── Guard rails ───────────────────────────────────────────────────────────

    @Nested
    class GuardRails {

        @Test
        void nullRepositoryThrowsNPE() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new SettingsViewModel(null));
        }

        @Test
        void repositorySaveFailureDoesNotCorruptState() {
            // If save throws, the existing state should remain unchanged
            doThrow(new java.io.UncheckedIOException(
                    new java.io.IOException("disk full")))
                    .when(repository).save(any());

            ThemeMode before = vm.getTheme();
            assertThatException()
                    .isThrownBy(() -> vm.setTheme(ThemeMode.DARK));
            // State should not have changed since save failed before applySettings
            assertThat(vm.getTheme()).isEqualTo(before);
        }
    }
}
