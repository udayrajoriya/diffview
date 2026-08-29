package com.diffview.core.merge;

import com.diffview.model.DecodedText;
import com.diffview.model.LineEnding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class EditableDocumentTest {

    private EditableDocument doc;

    @BeforeEach
    void setUp() {
        doc = new EditableDocument(
                List.of("alpha", "beta", "gamma"),
                StandardCharsets.UTF_8,
                LineEnding.LF);
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    void newDocumentIsNotDirty() {
        assertThat(doc.isDirty()).isFalse();
    }

    @Test
    void newDocumentHasCorrectLineCount() {
        assertThat(doc.lineCount()).isEqualTo(3);
    }

    @Test
    void newDocumentReturnsCorrectLines() {
        assertThat(doc.lines()).containsExactly("alpha", "beta", "gamma");
    }

    @Test
    void newDocumentReturnsCorrectEncoding() {
        assertThat(doc.encoding()).isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    void newDocumentReturnsCorrectLineEnding() {
        assertThat(doc.lineEnding()).isEqualTo(LineEnding.LF);
    }

    // ── getLine ───────────────────────────────────────────────────────────────

    @Test
    void getLineReturnsCorrectText() {
        assertThat(doc.getLine(0)).isEqualTo("alpha");
        assertThat(doc.getLine(1)).isEqualTo("beta");
        assertThat(doc.getLine(2)).isEqualTo("gamma");
    }

    @Test
    void getLineThrowsOnOutOfBoundsIndex() {
        assertThatExceptionOfType(IndexOutOfBoundsException.class)
                .isThrownBy(() -> doc.getLine(3));
    }

    // ── lines() unmodifiable ──────────────────────────────────────────────────

    @Test
    void linesReturnsUnmodifiableView() {
        List<String> view = doc.lines();
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> view.add("delta"));
    }

    @Test
    void mutatingDocumentDoesNotAffectPreviouslyReturnedLinesList() {
        List<String> before = doc.lines();
        doc.setLine(0, "changed");
        // The snapshot-copy returned by lines() must be independent
        assertThat(before).containsExactly("alpha", "beta", "gamma");
    }

    // ── setLine ───────────────────────────────────────────────────────────────

    @Test
    void setLineUpdateContent() {
        doc.setLine(1, "BETA");
        assertThat(doc.getLine(1)).isEqualTo("BETA");
    }

    @Test
    void setLineSetsDirty() {
        doc.setLine(0, "changed");
        assertThat(doc.isDirty()).isTrue();
    }

    // ── insertLine ────────────────────────────────────────────────────────────

    @Test
    void insertLineAtIndexInsertsBeforeTarget() {
        doc.insertLine(1, "inserted");
        assertThat(doc.lines()).containsExactly("alpha", "inserted", "beta", "gamma");
    }

    @Test
    void insertLineAtEndAppends() {
        doc.insertLine(doc.lineCount(), "appended");
        assertThat(doc.getLine(3)).isEqualTo("appended");
        assertThat(doc.lineCount()).isEqualTo(4);
    }

    @Test
    void insertLineSetsDirty() {
        doc.insertLine(0, "new");
        assertThat(doc.isDirty()).isTrue();
    }

    // ── deleteLine ────────────────────────────────────────────────────────────

    @Test
    void deleteLineRemovesCorrectEntry() {
        doc.deleteLine(1);
        assertThat(doc.lines()).containsExactly("alpha", "gamma");
    }

    @Test
    void deleteLineSetsDirty() {
        doc.deleteLine(0);
        assertThat(doc.isDirty()).isTrue();
    }

    // ── replaceLines ──────────────────────────────────────────────────────────

    @Test
    void replaceLinesSubstitutesRange() {
        doc.replaceLines(1, 3, List.of("x", "y", "z"));
        assertThat(doc.lines()).containsExactly("alpha", "x", "y", "z");
    }

    @Test
    void replaceLinesWithEmptyListDeletesRange() {
        doc.replaceLines(0, 2, List.of());
        assertThat(doc.lines()).containsExactly("gamma");
    }

    @Test
    void replaceLinesSetsDirty() {
        doc.replaceLines(0, 1, List.of("replaced"));
        assertThat(doc.isDirty()).isTrue();
    }

    // ── setEncoding ───────────────────────────────────────────────────────────

    @Test
    void setEncodingToDifferentValueSetsDirty() {
        doc.setEncoding(StandardCharsets.ISO_8859_1);
        assertThat(doc.isDirty()).isTrue();
        assertThat(doc.encoding()).isEqualTo(StandardCharsets.ISO_8859_1);
    }

    @Test
    void setEncodingToSameValueDoesNotSetDirty() {
        doc.setEncoding(StandardCharsets.UTF_8);
        assertThat(doc.isDirty()).isFalse();
    }

    // ── setLineEnding ─────────────────────────────────────────────────────────

    @Test
    void setLineEndingToDifferentValueSetsDirty() {
        doc.setLineEnding(LineEnding.CRLF);
        assertThat(doc.isDirty()).isTrue();
        assertThat(doc.lineEnding()).isEqualTo(LineEnding.CRLF);
    }

    @Test
    void setLineEndingToSameValueDoesNotSetDirty() {
        doc.setLineEnding(LineEnding.LF);
        assertThat(doc.isDirty()).isFalse();
    }

    // ── markClean ─────────────────────────────────────────────────────────────

    @Test
    void markCleanClearsDirtyAfterEdit() {
        doc.setLine(0, "changed");
        assertThat(doc.isDirty()).isTrue();
        doc.markClean();
        assertThat(doc.isDirty()).isFalse();
    }

    @Test
    void markCleanOnCleanDocumentIsNoOp() {
        doc.markClean();
        assertThat(doc.isDirty()).isFalse();
    }

    // ── takeSnapshot ──────────────────────────────────────────────────────────

    @Test
    void snapshotCapturesCurrentLines() {
        EditableDocument.Snapshot snap = doc.takeSnapshot();
        assertThat(snap.lines()).containsExactly("alpha", "beta", "gamma");
    }

    @Test
    void snapshotCapturesEncoding() {
        assertThat(doc.takeSnapshot().encoding()).isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    void snapshotCapturesLineEnding() {
        assertThat(doc.takeSnapshot().lineEnding()).isEqualTo(LineEnding.LF);
    }

    @Test
    void snapshotIsImmutableAfterDocumentMutation() {
        EditableDocument.Snapshot snap = doc.takeSnapshot();
        doc.setLine(0, "MUTATED");
        // snapshot must still hold the original value
        assertThat(snap.lines()).containsExactly("alpha", "beta", "gamma");
    }

    @Test
    void snapshotLinesListIsUnmodifiable() {
        EditableDocument.Snapshot snap = doc.takeSnapshot();
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> snap.lines().add("delta"));
    }

    // ── restore ───────────────────────────────────────────────────────────────

    @Test
    void restoreReplacesLinesWithSnapshotContent() {
        EditableDocument.Snapshot snap = doc.takeSnapshot();
        doc.setLine(0, "changed");
        doc.insertLine(3, "extra");

        doc.restore(snap);

        assertThat(doc.lines()).containsExactly("alpha", "beta", "gamma");
    }

    @Test
    void restoreRestoresEncoding() {
        doc.setEncoding(StandardCharsets.ISO_8859_1);
        EditableDocument.Snapshot snap = doc.takeSnapshot();

        doc.setEncoding(StandardCharsets.UTF_16);
        doc.restore(snap);

        assertThat(doc.encoding()).isEqualTo(StandardCharsets.ISO_8859_1);
    }

    @Test
    void restoreRestoresLineEnding() {
        doc.setLineEnding(LineEnding.CRLF);
        EditableDocument.Snapshot snap = doc.takeSnapshot();

        doc.setLineEnding(LineEnding.LF);
        doc.restore(snap);

        assertThat(doc.lineEnding()).isEqualTo(LineEnding.CRLF);
    }

    @Test
    void restoreSetsDirty() {
        EditableDocument.Snapshot snap = doc.takeSnapshot();
        doc.markClean();

        doc.restore(snap);

        assertThat(doc.isDirty()).isTrue();
    }

    @Test
    void editsAfterRestoreWorkCorrectly() {
        EditableDocument.Snapshot snap = doc.takeSnapshot();
        doc.deleteLine(0);
        doc.restore(snap);

        // Should be back to original; further edits must work
        doc.setLine(2, "GAMMA");
        assertThat(doc.lines()).containsExactly("alpha", "beta", "GAMMA");
        assertThat(doc.isDirty()).isTrue();
    }

    // ── multiple snapshots ────────────────────────────────────────────────────

    @Test
    void multipleSnapshotsAreIndependent() {
        EditableDocument.Snapshot snap1 = doc.takeSnapshot();

        doc.setLine(0, "step2");
        EditableDocument.Snapshot snap2 = doc.takeSnapshot();

        doc.setLine(0, "step3");

        // Restore to snap1
        doc.restore(snap1);
        assertThat(doc.getLine(0)).isEqualTo("alpha");

        // Restore to snap2
        doc.restore(snap2);
        assertThat(doc.getLine(0)).isEqualTo("step2");
    }

    // ── factory: from(DecodedText) ────────────────────────────────────────────

    @Test
    void fromDecodedTextCreatesEquivalentDocument() {
        DecodedText decoded = new DecodedText(
                List.of("one", "two"),
                StandardCharsets.UTF_16,
                false,
                LineEnding.CRLF);

        EditableDocument fromDecoded = EditableDocument.from(decoded);

        assertThat(fromDecoded.lines()).containsExactly("one", "two");
        assertThat(fromDecoded.encoding()).isEqualTo(StandardCharsets.UTF_16);
        assertThat(fromDecoded.lineEnding()).isEqualTo(LineEnding.CRLF);
        assertThat(fromDecoded.isDirty()).isFalse();
    }

    // ── null guard ────────────────────────────────────────────────────────────

    @Test
    void constructorRejectsNullLines() {
        assertThatNullPointerException().isThrownBy(
                () -> new EditableDocument(null, StandardCharsets.UTF_8, LineEnding.LF));
    }

    @Test
    void constructorRejectsNullEncoding() {
        assertThatNullPointerException().isThrownBy(
                () -> new EditableDocument(List.of(), null, LineEnding.LF));
    }

    @Test
    void constructorRejectsNullLineEnding() {
        assertThatNullPointerException().isThrownBy(
                () -> new EditableDocument(List.of(), StandardCharsets.UTF_8, null));
    }

    @Test
    void restoreRejectsNullSnapshot() {
        assertThatNullPointerException().isThrownBy(() -> doc.restore(null));
    }
}
