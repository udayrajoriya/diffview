package com.diffview.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class AppSettingsTest {

    @Nested
    class Defaults {

        @Test
        void defaultsHaveSystemTheme() {
            assertThat(AppSettings.defaults().theme()).isEqualTo(ThemeMode.SYSTEM);
        }

        @Test
        void defaultsHaveEmptyRecents() {
            assertThat(AppSettings.defaults().recents()).isEmpty();
        }

        @Test
        void defaultsHaveDefaultSubObjects() {
            AppSettings s = AppSettings.defaults();
            assertThat(s.defaultComparison()).isEqualTo(ComparisonOptions.defaults());
            assertThat(s.defaultFolderOptions()).isEqualTo(FolderComparisonOptions.defaults());
            assertThat(s.colors()).isEqualTo(HighlightColors.defaults());
            assertThat(s.layout()).isEqualTo(WindowLayout.defaults());
        }
    }

    @Nested
    class WitherMethods {

        @Test
        void withTheme_setsValue() {
            AppSettings s = AppSettings.defaults().withTheme(ThemeMode.DARK);
            assertThat(s.theme()).isEqualTo(ThemeMode.DARK);
            assertThat(AppSettings.defaults().theme()).isEqualTo(ThemeMode.SYSTEM); // original unchanged
        }

        @Test
        void withColors_setsValue() {
            HighlightColors custom = new HighlightColors("#111", "#222", "#333");
            AppSettings s = AppSettings.defaults().withColors(custom);
            assertThat(s.colors()).isEqualTo(custom);
        }

        @Test
        void withLayout_setsValue() {
            WindowLayout custom = WindowLayout.defaults().withWidth(1920).withHeight(1080);
            AppSettings s = AppSettings.defaults().withLayout(custom);
            assertThat(s.layout().width()).isEqualTo(1920.0);
        }

        @Test
        void withRecents_isImmutableList() {
            AppSettings s = AppSettings.defaults().withRecents(List.of());
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> s.recents().add(
                            RecentComparison.of(Path.of("/a"), Path.of("/b"), false)));
        }
    }

    @Nested
    class WithAddedRecent {

        private RecentComparison recent(String left, String right) {
            return new RecentComparison(Path.of(left), Path.of(right), false,
                    Instant.parse("2024-01-01T00:00:00Z"));
        }

        @Test
        void addsRecentToEmptyList() {
            RecentComparison r = recent("/a", "/b");
            AppSettings s = AppSettings.defaults().withAddedRecent(r);
            assertThat(s.recents()).containsExactly(r);
        }

        @Test
        void prependsRecentToExistingList() {
            RecentComparison r1 = recent("/a", "/b");
            RecentComparison r2 = recent("/c", "/d");
            AppSettings s = AppSettings.defaults()
                    .withAddedRecent(r1)
                    .withAddedRecent(r2);
            assertThat(s.recents().get(0)).isEqualTo(r2);
            assertThat(s.recents().get(1)).isEqualTo(r1);
        }

        @Test
        void deduplicatesByPaths() {
            RecentComparison r1 = recent("/a", "/b");
            RecentComparison r2 = recent("/a", "/b"); // same paths, different timestamp object
            AppSettings s = AppSettings.defaults()
                    .withAddedRecent(r1)
                    .withAddedRecent(r2);
            // second add deduplicates the first, resulting in only one entry
            assertThat(s.recents()).hasSize(1);
        }

        @Test
        void trimsToMaxRecents() {
            AppSettings s = AppSettings.defaults();
            for (int i = 0; i < AppSettings.MAX_RECENTS + 5; i++) {
                s = s.withAddedRecent(recent("/a" + i, "/b" + i));
            }
            assertThat(s.recents()).hasSize(AppSettings.MAX_RECENTS);
        }

        @Test
        void nullRecentThrows() {
            assertThatNullPointerException()
                    .isThrownBy(() -> AppSettings.defaults().withAddedRecent(null));
        }
    }

    @Nested
    class NullGuards {

        @Test
        void nullDefaultComparisonThrows() {
            assertThatNullPointerException()
                    .isThrownBy(() -> AppSettings.defaults().withDefaultComparison(null));
        }

        @Test
        void nullThemeThrows() {
            assertThatNullPointerException()
                    .isThrownBy(() -> AppSettings.defaults().withTheme(null));
        }
    }

    @ParameterizedTest
    @EnumSource(ThemeMode.class)
    void allThemeModesAssignable(ThemeMode mode) {
        AppSettings s = AppSettings.defaults().withTheme(mode);
        assertThat(s.theme()).isEqualTo(mode);
    }
}
