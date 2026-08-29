package com.diffview.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class FolderComparisonOptionsTest {

    @Nested
    class Defaults {

        @Test
        void defaultsUseSizeAndTimestampMode() {
            assertThat(FolderComparisonOptions.defaults().matchMode())
                    .isEqualTo(FileMatchMode.SIZE_AND_TIMESTAMP);
        }

        @Test
        void defaultsHaveTwoSecondTolerance() {
            assertThat(FolderComparisonOptions.defaults().timestampTolerance())
                    .isEqualTo(Duration.ofSeconds(2));
        }

        @Test
        void defaultsHaveEmptyMasksAndIgnores() {
            FolderComparisonOptions opts = FolderComparisonOptions.defaults();
            assertThat(opts.includeMasks()).isEmpty();
            assertThat(opts.excludeMasks()).isEmpty();
            assertThat(opts.manualIgnores()).isEmpty();
        }

        @Test
        void defaultsShowAllItems() {
            assertThat(FolderComparisonOptions.defaults().showOnlyDifferences()).isFalse();
        }

        @Test
        void defaultsHaveDefaultContentOptions() {
            assertThat(FolderComparisonOptions.defaults().content())
                    .isEqualTo(ComparisonOptions.defaults());
        }
    }

    @Nested
    class WitherMethods {

        @Test
        void withMatchMode_returnsNewInstance() {
            FolderComparisonOptions original = FolderComparisonOptions.defaults();
            FolderComparisonOptions modified = original.withMatchMode(FileMatchMode.CONTENT);
            assertThat(modified.matchMode()).isEqualTo(FileMatchMode.CONTENT);
            assertThat(original.matchMode()).isEqualTo(FileMatchMode.SIZE_AND_TIMESTAMP);
        }

        @Test
        void withShowOnlyDifferences_toggled() {
            FolderComparisonOptions opts = FolderComparisonOptions.defaults()
                    .withShowOnlyDifferences(true);
            assertThat(opts.showOnlyDifferences()).isTrue();
        }

        @Test
        void withIncludeMasks_setsPatterns() {
            FolderComparisonOptions opts = FolderComparisonOptions.defaults()
                    .withIncludeMasks(List.of("*.java", "*.kt"));
            assertThat(opts.includeMasks()).containsExactly("*.java", "*.kt");
        }

        @Test
        void withExcludeMasks_setsPatterns() {
            FolderComparisonOptions opts = FolderComparisonOptions.defaults()
                    .withExcludeMasks(List.of("*.class", "build/**"));
            assertThat(opts.excludeMasks()).containsExactly("*.class", "build/**");
        }

        @Test
        void withManualIgnores_setsIgnores() {
            Set<Path> ignores = Set.of(Path.of("node_modules"), Path.of(".git"));
            FolderComparisonOptions opts = FolderComparisonOptions.defaults()
                    .withManualIgnores(ignores);
            assertThat(opts.manualIgnores()).containsExactlyInAnyOrderElementsOf(ignores);
        }

        @Test
        void withTimestampTolerance_setsValue() {
            FolderComparisonOptions opts = FolderComparisonOptions.defaults()
                    .withTimestampTolerance(Duration.ofSeconds(10));
            assertThat(opts.timestampTolerance()).isEqualTo(Duration.ofSeconds(10));
        }
    }

    @Nested
    class Immutability {

        @Test
        void includeMasksListIsImmutable() {
            FolderComparisonOptions opts = FolderComparisonOptions.defaults()
                    .withIncludeMasks(List.of("*.txt"));
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> opts.includeMasks().add("*.xml"));
        }

        @Test
        void manualIgnoresSetIsImmutable() {
            FolderComparisonOptions opts = FolderComparisonOptions.defaults()
                    .withManualIgnores(Set.of(Path.of("tmp")));
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> opts.manualIgnores().add(Path.of("other")));
        }
    }

    @Nested
    class Validation {

        @Test
        void negativeTimestampToleranceThrows() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> FolderComparisonOptions.defaults()
                            .withTimestampTolerance(Duration.ofSeconds(-1)))
                    .withMessageContaining("negative");
        }

        @Test
        void nullContentThrows() {
            assertThatNullPointerException()
                    .isThrownBy(() -> FolderComparisonOptions.defaults().withContent(null));
        }
    }
}
