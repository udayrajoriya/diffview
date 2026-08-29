package com.diffview.core.ignore;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Unit tests for {@link DefaultIgnoreRuleEngine} (task 7.1).
 *
 * <p>Tests cover include/exclude mask evaluation, directory-vs-file mask
 * semantics, precedence rules, and the manual-ignore API.
 */
class DefaultIgnoreRuleEngineTest {

    // ── factory / NPE guards ──────────────────────────────────────────────────

    @Test
    void nullIncludeMasksThrows() {
        assertThatNullPointerException()
                .isThrownBy(() -> new DefaultIgnoreRuleEngine(null, List.of()));
    }

    @Test
    void nullExcludeMasksThrows() {
        assertThatNullPointerException()
                .isThrownBy(() -> new DefaultIgnoreRuleEngine(List.of(), null));
    }

    @Test
    void nullRelativePathThrows() {
        var engine = new DefaultIgnoreRuleEngine(List.of(), List.of());
        assertThatNullPointerException()
                .isThrownBy(() -> engine.isExcluded(null, false));
    }

    // ── empty masks ───────────────────────────────────────────────────────────

    @Nested
    class EmptyMasks {

        private final DefaultIgnoreRuleEngine engine =
                new DefaultIgnoreRuleEngine(List.of(), List.of());

        @Test
        void fileIsNotExcluded() {
            assertThat(engine.isExcluded(Path.of("file.txt"), false)).isFalse();
        }

        @Test
        void directoryIsNotExcluded() {
            assertThat(engine.isExcluded(Path.of("subdir"), true)).isFalse();
        }

        @Test
        void nestedPathIsNotExcluded() {
            assertThat(engine.isExcluded(Path.of("a", "b", "file.txt"), false)).isFalse();
        }
    }

    // ── exclude masks ─────────────────────────────────────────────────────────

    @Nested
    class ExcludeMasks {

        @Test
        void singleExtensionMaskExcludesMatchingFile() {
            var engine = new DefaultIgnoreRuleEngine(List.of(), List.of("*.tmp"));
            assertThat(engine.isExcluded(Path.of("cache.tmp"), false)).isTrue();
            assertThat(engine.isExcluded(Path.of("cache.txt"), false)).isFalse();
        }

        @Test
        void maskAppliesToFilesAtAnyDepth() {
            var engine = new DefaultIgnoreRuleEngine(List.of(), List.of("*.class"));
            // Nested path — filename is Foo.class
            assertThat(engine.isExcluded(Path.of("com", "example", "Foo.class"), false)).isTrue();
            assertThat(engine.isExcluded(Path.of("com", "example", "Foo.java"),  false)).isFalse();
        }

        @Test
        void multipleExcludeMasksAllApply() {
            var engine = new DefaultIgnoreRuleEngine(List.of(), List.of("*.tmp", "*.log", "*.bak"));
            assertThat(engine.isExcluded(Path.of("run.tmp"),    false)).isTrue();
            assertThat(engine.isExcluded(Path.of("app.log"),    false)).isTrue();
            assertThat(engine.isExcluded(Path.of("data.bak"),   false)).isTrue();
            assertThat(engine.isExcluded(Path.of("source.txt"), false)).isFalse();
        }

        @Test
        void exactNameMaskExcludesMatchingEntry() {
            var engine = new DefaultIgnoreRuleEngine(List.of(), List.of("Thumbs.db"));
            assertThat(engine.isExcluded(Path.of("Thumbs.db"),       false)).isTrue();
            assertThat(engine.isExcluded(Path.of("not-Thumbs.db"),   false)).isFalse();
        }
    }

    // ── include masks ─────────────────────────────────────────────────────────

    @Nested
    class IncludeMasks {

        @Test
        void onlyMatchingFilesAreIncluded() {
            var engine = new DefaultIgnoreRuleEngine(List.of("*.java"), List.of());
            assertThat(engine.isExcluded(Path.of("Main.java"),  false)).isFalse(); // included
            assertThat(engine.isExcluded(Path.of("Main.class"), false)).isTrue();  // not in include
        }

        @Test
        void multipleIncludeMasksAnyMatchIncludes() {
            var engine = new DefaultIgnoreRuleEngine(List.of("*.java", "*.kt"), List.of());
            assertThat(engine.isExcluded(Path.of("App.java"), false)).isFalse();
            assertThat(engine.isExcluded(Path.of("App.kt"),   false)).isFalse();
            assertThat(engine.isExcluded(Path.of("App.py"),   false)).isTrue();
        }

        @Test
        void emptyIncludeListIncludesEverything() {
            var engine = new DefaultIgnoreRuleEngine(List.of(), List.of());
            assertThat(engine.isExcluded(Path.of("anything.xyz"), false)).isFalse();
        }
    }

    // ── include/exclude precedence (exclude wins) ─────────────────────────────

    @Nested
    class Precedence {

        @Test
        void excludeBeatsInclude_fileMatchingBoth() {
            // Include all *.java, but exclude Test*.java
            var engine = new DefaultIgnoreRuleEngine(
                    List.of("*.java"), List.of("Test*.java"));
            assertThat(engine.isExcluded(Path.of("Main.java"),     false)).isFalse();
            // Matches include AND exclude → exclude wins
            assertThat(engine.isExcluded(Path.of("TestMain.java"), false)).isTrue();
        }

        @Test
        void excludeOnlyMatchingFile_includeMaskDoesNotProtect() {
            var engine = new DefaultIgnoreRuleEngine(
                    List.of("*.txt"), List.of("temp.txt"));
            // temp.txt matches include (*.txt) but also matches exclude → excluded
            assertThat(engine.isExcluded(Path.of("temp.txt"),  false)).isTrue();
            assertThat(engine.isExcluded(Path.of("notes.txt"), false)).isFalse();
        }

        @Test
        void excludeOnlyOnNonMatchedInclude() {
            // Include *.java, exclude *.class — a *.class file is both not included AND excluded
            // Either rule would exclude it; verify it is indeed excluded
            var engine = new DefaultIgnoreRuleEngine(
                    List.of("*.java"), List.of("*.class"));
            assertThat(engine.isExcluded(Path.of("Compiled.class"), false)).isTrue();
        }
    }

    // ── directory vs file masks ───────────────────────────────────────────────

    @Nested
    class DirectoryVsFileMasks {

        @Test
        void trailingSlashMaskExcludesDirectoryOnly() {
            var engine = new DefaultIgnoreRuleEngine(List.of(), List.of("bin/"));
            assertThat(engine.isExcluded(Path.of("bin"), true)).isTrue();   // dir → excluded
            assertThat(engine.isExcluded(Path.of("bin"), false)).isFalse(); // file → not excluded
        }

        @Test
        void buildDirectoryMaskDoesNotAffectFileNamedBuild() {
            var engine = new DefaultIgnoreRuleEngine(List.of(), List.of("build/"));
            assertThat(engine.isExcluded(Path.of("build"), false)).isFalse();
        }

        @Test
        void maskWithoutSlashMatchesBothFilesAndDirectories() {
            // *.tmp without trailing slash matches files AND dirs with that pattern
            var engine = new DefaultIgnoreRuleEngine(List.of(), List.of("*.tmp"));
            assertThat(engine.isExcluded(Path.of("cache.tmp"), false)).isTrue(); // file
            assertThat(engine.isExcluded(Path.of("cache.tmp"), true)).isTrue();  // dir
        }

        @Test
        void targetDirectoryNameMaskInNestedPath() {
            var engine = new DefaultIgnoreRuleEngine(List.of(), List.of("node_modules/"));
            // Directly named at root
            assertThat(engine.isExcluded(Path.of("node_modules"), true)).isTrue();
            // Nested — filename component is still "node_modules"
            assertThat(engine.isExcluded(Path.of("pkg", "node_modules"), true)).isTrue();
            // File with same name is not excluded
            assertThat(engine.isExcluded(Path.of("node_modules"), false)).isFalse();
        }

        @Test
        void directoryIncludeOnlyMatchesDirectories() {
            // Only include directories named "src"
            var engine = new DefaultIgnoreRuleEngine(List.of("src/"), List.of());
            assertThat(engine.isExcluded(Path.of("src"),     true)).isFalse();  // dir → included
            assertThat(engine.isExcluded(Path.of("src"),     false)).isTrue();  // file → not in include list
            assertThat(engine.isExcluded(Path.of("test"),    true)).isTrue();   // non-matching dir
        }
    }

    // ── manual ignore API (used in task 7.2; basic smoke tests here) ─────────

    @Nested
    class ManualIgnore {

        @Test
        void ignoreItemCausesExclusion() {
            var engine = new DefaultIgnoreRuleEngine(List.of(), List.of());
            Path p = Path.of("special.txt");
            assertThat(engine.isExcluded(p, false)).isFalse();

            engine.ignoreItem(p);

            assertThat(engine.isExcluded(p, false)).isTrue();
            assertThat(engine.isManuallyIgnored(p)).isTrue();
        }

        @Test
        void unignoreItemRestoresInclusion() {
            var engine = new DefaultIgnoreRuleEngine(List.of(), List.of());
            Path p = Path.of("special.txt");
            engine.ignoreItem(p);
            engine.unignoreItem(p);

            assertThat(engine.isExcluded(p, false)).isFalse();
            assertThat(engine.isManuallyIgnored(p)).isFalse();
        }

        @Test
        void initialManualIgnoresAreRespected() {
            Path p = Path.of("pre-ignored.txt");
            var engine = new DefaultIgnoreRuleEngine(List.of(), List.of(), Set.of(p));

            assertThat(engine.isExcluded(p, false)).isTrue();
            assertThat(engine.isManuallyIgnored(p)).isTrue();
        }
    }
}
