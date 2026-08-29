package com.diffview.core.merge;

import com.diffview.model.DecodedText;
import com.diffview.model.LineEnding;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A mutable in-memory line buffer representing one side of a text document being
 * edited in the merge view.
 *
 * <p>Tracks whether the document has been modified since the last {@link #markClean()}
 * call, and supports point-in-time {@link Snapshot snapshots} so that
 * {@code MergeManager} can implement undo/redo.
 */
public final class EditableDocument {

    private final List<String> lines;
    private Charset encoding;
    private LineEnding lineEnding;
    private boolean dirty;

    /**
     * Creates a new document pre-populated with {@code lines}.
     *
     * @param lines      content lines, without line terminators; copied defensively
     * @param encoding   character encoding to use when saving
     * @param lineEnding dominant line-ending style to use when saving
     */
    public EditableDocument(List<String> lines, Charset encoding, LineEnding lineEnding) {
        Objects.requireNonNull(lines, "lines must not be null");
        Objects.requireNonNull(encoding, "encoding must not be null");
        Objects.requireNonNull(lineEnding, "lineEnding must not be null");
        this.lines = new ArrayList<>(lines);
        this.encoding = encoding;
        this.lineEnding = lineEnding;
        this.dirty = false;
    }

    /**
     * Convenience factory: creates an {@code EditableDocument} from a {@link DecodedText}
     * produced by {@code FileIOService}.
     */
    public static EditableDocument from(DecodedText decoded) {
        Objects.requireNonNull(decoded, "decoded must not be null");
        return new EditableDocument(decoded.lines(), decoded.encoding(), decoded.lineEnding());
    }

    // ── Accessors ────────────────────────────────────────────────────────────

    /**
     * Returns an unmodifiable copy of the line list.
     * Use {@link #getLine(int)} for index-based access in tight loops.
     */
    public List<String> lines() {
        return List.copyOf(lines);
    }

    /** Returns the number of lines in this document. */
    public int lineCount() {
        return lines.size();
    }

    /**
     * Returns the line at 0-based {@code index}.
     *
     * @throws IndexOutOfBoundsException if {@code index} is out of range
     */
    public String getLine(int index) {
        return lines.get(index);
    }

    /** Returns the character encoding of this document. */
    public Charset encoding() {
        return encoding;
    }

    /** Returns the dominant line-ending style of this document. */
    public LineEnding lineEnding() {
        return lineEnding;
    }

    /** Returns {@code true} if the document has been modified since the last {@link #markClean()} call. */
    public boolean isDirty() {
        return dirty;
    }

    // ── Mutations ────────────────────────────────────────────────────────────

    /**
     * Replaces the line at 0-based {@code index} with {@code text}.
     * Sets the dirty flag.
     */
    public void setLine(int index, String text) {
        Objects.requireNonNull(text, "text must not be null");
        lines.set(index, text);
        dirty = true;
    }

    /**
     * Inserts {@code text} before the line currently at 0-based {@code index}.
     * Passing {@code index == lineCount()} appends at the end.
     * Sets the dirty flag.
     */
    public void insertLine(int index, String text) {
        Objects.requireNonNull(text, "text must not be null");
        lines.add(index, text);
        dirty = true;
    }

    /**
     * Removes the line at 0-based {@code index}.
     * Sets the dirty flag.
     */
    public void deleteLine(int index) {
        lines.remove(index);
        dirty = true;
    }

    /**
     * Replaces the half-open range [{@code fromIndex}, {@code toIndex}) with
     * {@code newLines}. Equivalent to deleting the range then inserting the new
     * content at {@code fromIndex}.
     * Sets the dirty flag.
     */
    public void replaceLines(int fromIndex, int toIndex, List<String> newLines) {
        Objects.requireNonNull(newLines, "newLines must not be null");
        lines.subList(fromIndex, toIndex).clear();
        lines.addAll(fromIndex, newLines);
        dirty = true;
    }

    /**
     * Changes the character encoding.
     * Sets the dirty flag only if the encoding actually changes.
     */
    public void setEncoding(Charset encoding) {
        Objects.requireNonNull(encoding, "encoding must not be null");
        if (!encoding.equals(this.encoding)) {
            this.encoding = encoding;
            dirty = true;
        }
    }

    /**
     * Changes the line-ending style.
     * Sets the dirty flag only if the value actually changes.
     */
    public void setLineEnding(LineEnding lineEnding) {
        Objects.requireNonNull(lineEnding, "lineEnding must not be null");
        if (!lineEnding.equals(this.lineEnding)) {
            this.lineEnding = lineEnding;
            dirty = true;
        }
    }

    /**
     * Resets the dirty flag. Call this after the document has been successfully saved.
     */
    public void markClean() {
        dirty = false;
    }

    // ── Snapshot ─────────────────────────────────────────────────────────────

    /**
     * Captures an immutable snapshot of the current document state.
     * The snapshot is independent of further mutations to this document.
     */
    public Snapshot takeSnapshot() {
        return new Snapshot(lines, encoding, lineEnding);
    }

    /**
     * Restores the document state to the given {@code snapshot}.
     * Sets the dirty flag to {@code true} after restoring (the document now differs
     * from whatever was last saved to disk).
     *
     * @param snapshot a snapshot previously obtained from {@link #takeSnapshot()}
     */
    public void restore(Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        lines.clear();
        lines.addAll(snapshot.lines());
        this.encoding = snapshot.encoding();
        this.lineEnding = snapshot.lineEnding();
        dirty = true;
    }

    // ── Snapshot record ───────────────────────────────────────────────────────

    /**
     * An immutable point-in-time copy of an {@link EditableDocument}'s state.
     * Used by {@code MergeManager} to implement undo/redo.
     */
    public record Snapshot(List<String> lines, Charset encoding, LineEnding lineEnding) {

        /** Defensive copy constructor — the snapshot is always immutable. */
        public Snapshot {
            Objects.requireNonNull(lines, "lines must not be null");
            Objects.requireNonNull(encoding, "encoding must not be null");
            Objects.requireNonNull(lineEnding, "lineEnding must not be null");
            lines = List.copyOf(lines);
        }
    }
}
