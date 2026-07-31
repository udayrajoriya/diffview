package com.comparetool.ui;

import com.comparetool.model.DiffRow;
import com.comparetool.model.InlineSpan;
import com.comparetool.model.LineKind;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.List;

/**
 * A {@link ListCell} that renders one side (left or right) of a {@link DiffRow}.
 *
 * <h3>Layout</h3>
 * <pre>
 * ┌────────────┬──────────────────────────────────────────────┐
 * │ lineNumber │  text content  (with intra-line spans)       │
 * └────────────┴──────────────────────────────────────────────┘
 * </pre>
 *
 * <h3>CSS style classes applied to the cell</h3>
 * <ul>
 *   <li>{@code diff-changed-line}  — {@link LineKind#CHANGED}</li>
 *   <li>{@code diff-added-line}    — {@link LineKind#ADDED}</li>
 *   <li>{@code diff-removed-line}  — {@link LineKind#REMOVED}</li>
 *   <li>{@code diff-placeholder-row} — side with no content (placeholder)</li>
 * </ul>
 *
 * <h3>Intra-line highlighting</h3>
 * <p>{@link InlineSpan} ranges map to {@code Text} nodes with
 * {@code diff-changed-token}, {@code diff-added-token}, or
 * {@code diff-removed-token} CSS classes (defined in {@code diff-colors.css}).
 */
class DiffLineCell extends ListCell<DiffRow> {

    // Fixed-width gutter for line numbers (wide enough for 5-digit line numbers).
    static final double LINE_NUM_WIDTH  = 48.0;
    /** Width of the non-color kind-gutter label (REQ-015.2). */
    static final double KIND_GUTTER_WIDTH = 14.0;

    private final boolean    isLeft;
    private final Label      lineNumberLabel = new Label();
    /** Non-color status gutter: shows +, -, or ~ to supplement the background color cue (REQ-015.2). */
    private final Label      kindLabel       = new Label();
    private final TextFlow   textFlow        = new TextFlow();
    private final HBox       cellBox;

    DiffLineCell(boolean isLeft) {
        this.isLeft = isLeft;

        lineNumberLabel.setMinWidth(LINE_NUM_WIDTH);
        lineNumberLabel.setPrefWidth(LINE_NUM_WIDTH);
        lineNumberLabel.setAlignment(Pos.CENTER_RIGHT);
        lineNumberLabel.getStyleClass().add("diff-line-number");

        kindLabel.setMinWidth(KIND_GUTTER_WIDTH);
        kindLabel.setPrefWidth(KIND_GUTTER_WIDTH);
        kindLabel.setMaxWidth(KIND_GUTTER_WIDTH);
        kindLabel.setAlignment(Pos.CENTER);
        kindLabel.getStyleClass().add("diff-kind-gutter");

        HBox.setHgrow(textFlow, Priority.ALWAYS);

        cellBox = new HBox(4, lineNumberLabel, kindLabel, textFlow);
        cellBox.setStyle("-fx-padding: 1 4 1 4;");
    }

    // ── Cell rendering ────────────────────────────────────────────────────────

    @Override
    protected void updateItem(DiffRow row, boolean empty) {
        super.updateItem(row, empty);

        // Strip all diff-specific style classes before re-applying
        getStyleClass().removeIf(s -> s.startsWith("diff-"));

        if (empty || row == null) {
            setGraphic(null);
            setText(null);
            return;
        }

        boolean placeholder = isLeft ? row.isLeftPlaceholder() : row.isRightPlaceholder();

        if (placeholder) {
            getStyleClass().add("diff-placeholder-row");
            lineNumberLabel.setText("");
            kindLabel.setText("");
            textFlow.getChildren().setAll(new Text(""));
        } else {
            Integer lineNum = isLeft ? row.leftLineNumber() : row.rightLineNumber();
            String  text    = isLeft ? row.leftText()       : row.rightText();
            List<InlineSpan> spans = isLeft ? row.leftSpans() : row.rightSpans();

            // Line-level highlight class
            switch (row.kind()) {
                case CHANGED  -> getStyleClass().add("diff-changed-line");
                case ADDED    -> getStyleClass().add("diff-added-line");
                case REMOVED  -> getStyleClass().add("diff-removed-line");
                case UNCHANGED -> { /* no special background */ }
            }

            kindLabel.setText(kindSymbol(row.kind()));
            lineNumberLabel.setText(lineNum != null ? String.valueOf(lineNum) : "");
            buildTextFlow(text, spans, row.kind());
        }

        setGraphic(cellBox);
        setText(null);
    }

    // ── TextFlow building ─────────────────────────────────────────────────────

    private void buildTextFlow(String text, List<InlineSpan> spans, LineKind kind) {
        textFlow.getChildren().clear();

        if (spans.isEmpty()) {
            textFlow.getChildren().add(new Text(text));
            return;
        }

        String tokenClass = tokenClassFor(kind);
        int pos = 0;
        for (InlineSpan span : spans) {
            int start = Math.max(pos,  span.startOffset());
            int end   = Math.min(text.length(), span.endOffset());
            if (start >= end) continue;

            if (start > pos) {
                textFlow.getChildren().add(new Text(text.substring(pos, start)));
            }
            Text highlighted = new Text(text.substring(start, end));
            if (!tokenClass.isEmpty()) {
                highlighted.getStyleClass().add(tokenClass);
            }
            textFlow.getChildren().add(highlighted);
            pos = end;
        }
        if (pos < text.length()) {
            textFlow.getChildren().add(new Text(text.substring(pos)));
        }
    }

    private static String tokenClassFor(LineKind kind) {
        return switch (kind) {
            case CHANGED  -> "diff-changed-token";
            case ADDED    -> "diff-added-token";
            case REMOVED  -> "diff-removed-token";
            default       -> "";
        };
    }

    /**
     * Returns a text symbol for the given line kind (non-color accessibility cue, REQ-015.2).
     *
     * <p>These symbols supplement the background-color cue so that status is
     * distinguishable without relying on color alone.
     */
    static String kindSymbol(LineKind kind) {
        return switch (kind) {
            case CHANGED   -> "~";
            case ADDED     -> "+";
            case REMOVED   -> "-";
            case UNCHANGED -> "";
        };
    }
}
