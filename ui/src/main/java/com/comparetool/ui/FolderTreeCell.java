package com.comparetool.ui;

import com.comparetool.model.DiffTreeNode;
import com.comparetool.model.FileMeta;
import com.comparetool.model.FolderItemStatus;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Custom {@link ListCell} for {@link DiffTreeNode} items displayed in a
 * {@link FolderTreePane} (task 13.1).
 *
 * <p>Each cell renders:
 * <ul>
 *   <li><strong>Indentation</strong> — proportional to {@code relativePath.getNameCount() - 1}</li>
 *   <li><strong>Expand/collapse arrow</strong> — visible only for directory nodes</li>
 *   <li><strong>Name</strong> — the last component of the relative path, or {@code "—"} for
 *       placeholder cells (the item exists only on the other side)</li>
 *   <li><strong>Size</strong> — formatted file size (right-aligned, 80 px), empty for directories</li>
 *   <li><strong>Last-modified</strong> — {@code yyyy-MM-dd HH:mm}, 140 px</li>
 * </ul>
 *
 * <p>Which {@link FileMeta} is used (left or right) is controlled by the {@code leftSide} flag
 * passed to the constructor.
 */
class FolderTreeCell extends ListCell<DiffTreeNode> {

    // ── Layout constants ──────────────────────────────────────────────────────
    private static final double INDENT_PER_DEPTH = 16.0;
    private static final double ARROW_WIDTH      = 24.0;    private static final double BADGE_WIDTH       = 22.0;    private static final double SIZE_WIDTH        = 80.0;
    private static final double MODIFIED_WIDTH    = 140.0;
    private static final String PLACEHOLDER       = "—";

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // ── Injected behaviour ────────────────────────────────────────────────────
    private final boolean               leftSide;
    private final Predicate<Path>       isCollapsed;
    private final Consumer<DiffTreeNode> onToggleExpand;
    /** Invoked with a double-click on any non-directory cell (REQ-9.1 / REQ-9.2). */
    private final Consumer<DiffTreeNode> onOpen;

    // ── Reusable UI nodes ─────────────────────────────────────────────────────
    private final Region  indentRegion = new Region();
    private final Button  arrowButton  = new Button();    /** Status badge: text = non-color cue symbol; inline style = color cue (REQ-8.2, REQ-15.2). */
    private final Label   statusBadge  = new Label();    private final Label   nameLabel    = new Label();
    private final Label   sizeLabel    = new Label();
    private final Label   modLabel     = new Label();
    private final HBox    container;

    // ── Constructor ───────────────────────────────────────────────────────────

    FolderTreeCell(boolean leftSide,
                   Predicate<Path>        isCollapsed,
                   Consumer<DiffTreeNode> onToggleExpand,
                   Consumer<DiffTreeNode> onOpen) {
        this.leftSide       = leftSide;
        this.isCollapsed    = isCollapsed;
        this.onToggleExpand = onToggleExpand;
        this.onOpen         = onOpen;

        // Arrow button (shown only for directories)
        arrowButton.setMinWidth(ARROW_WIDTH);
        arrowButton.setPrefWidth(ARROW_WIDTH);
        arrowButton.setMaxWidth(ARROW_WIDTH);
        arrowButton.setPadding(Insets.EMPTY);
        arrowButton.getStyleClass().add("folder-arrow-button");
        arrowButton.setFocusTraversable(false);

        // Status badge: fixed width, centred text — carries both color AND symbol (REQ-15.2)
        statusBadge.setMinWidth(BADGE_WIDTH);
        statusBadge.setPrefWidth(BADGE_WIDTH);
        statusBadge.setMaxWidth(BADGE_WIDTH);
        statusBadge.setAlignment(Pos.CENTER);
        statusBadge.setStyle("-fx-font-weight: bold;");

        // Name grows to fill remaining space
        HBox.setHgrow(nameLabel, Priority.ALWAYS);
        nameLabel.setMaxWidth(Double.MAX_VALUE);

        // Size: right-aligned fixed width
        sizeLabel.setMinWidth(SIZE_WIDTH);
        sizeLabel.setPrefWidth(SIZE_WIDTH);
        sizeLabel.setMaxWidth(SIZE_WIDTH);
        sizeLabel.setAlignment(Pos.CENTER_RIGHT);

        // Last-modified: fixed width
        modLabel.setMinWidth(MODIFIED_WIDTH);
        modLabel.setPrefWidth(MODIFIED_WIDTH);
        modLabel.setMaxWidth(MODIFIED_WIDTH);

        container = new HBox(4, indentRegion, arrowButton, statusBadge, nameLabel, sizeLabel, modLabel);
        container.setAlignment(Pos.CENTER_LEFT);
        setPadding(Insets.EMPTY);
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    protected void updateItem(DiffTreeNode item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setText(null);
            setGraphic(null);
            return;
        }

        // ── Indentation ─────────────────────────────────────────────────────
        int depth = Math.max(0, item.relativePath().getNameCount() - 1);
        double indentW = depth * INDENT_PER_DEPTH;
        indentRegion.setMinWidth(indentW);
        indentRegion.setPrefWidth(indentW);
        indentRegion.setMaxWidth(indentW);

        // ── Expand / collapse arrow ──────────────────────────────────────────
        if (item.directory()) {
            boolean collapsed = isCollapsed.test(item.relativePath());
            arrowButton.setText(collapsed ? "\u25B6" : "\u25BC"); // ▶ or ▼
            arrowButton.setVisible(true);
            arrowButton.setManaged(true);
            DiffTreeNode captured = item;
            arrowButton.setOnAction(e -> {
                if (onToggleExpand != null) onToggleExpand.accept(captured);
            });
        } else {
            arrowButton.setText("");
            arrowButton.setVisible(false);
            arrowButton.setManaged(false);
        }
        // ── Status badge (non-color cue + color cue, REQ-8.2 / REQ-15.2) ───────────────
        statusBadge.setText(statusSymbol(item.status()));
        statusBadge.setStyle("-fx-font-weight: bold; -fx-text-fill: " + statusColor(item.status()) + ";");
        // ── Name and metadata ────────────────────────────────────────────────
        FileMeta meta = leftSide ? item.left() : item.right();

        if (meta == null) {
            // Placeholder: item exists only on the other side
            nameLabel.setText(PLACEHOLDER);
            nameLabel.setOpacity(0.4);
            sizeLabel.setText("");
            modLabel.setText("");
        } else {
            // Real entry: show name from relative path
            Path fn = item.relativePath().getFileName();
            nameLabel.setText(fn != null ? fn.toString() : item.relativePath().toString());
            nameLabel.setOpacity(1.0);
            sizeLabel.setText(item.directory() ? "" : formatSize(meta.size()));
            modLabel.setText(formatDateTime(meta.lastModified()));
        }

        setText(null);
        setGraphic(container);

        // Double-click on a file node (paired or one-sided) triggers drill-down (REQ-9.1, REQ-9.2)
        if (!item.directory()) {
            DiffTreeNode captured = item;
            setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && onOpen != null) {
                    onOpen.accept(captured);
                }
            });
        } else {
            setOnMouseClicked(null);
        }
    }
    // ── Status helpers ───────────────────────────────────────────────────

    /**
     * Returns a Unicode symbol for the given status (non-color accessibility cue, REQ-15.2).
     */
    static String statusSymbol(FolderItemStatus status) {
        return switch (status) {
            case IDENTICAL  -> "=";
            case DIFFERENT  -> "≠";  // U+2260 ≠
            case LEFT_ONLY  -> "◄";  // U+25C4 ◄
            case RIGHT_ONLY -> "►";  // U+25BA ►
            case IGNORED    -> "~";
        };
    }

    /**
     * Returns an inline CSS color for the given status (color cue, REQ-8.2).
     */
    static String statusColor(FolderItemStatus status) {
        return switch (status) {
            case IDENTICAL  -> "#388e3c";  // Material green 700
            case DIFFERENT  -> "#f57c00";  // Material orange 700
            case LEFT_ONLY  -> "#1565c0";  // Material blue 800
            case RIGHT_ONLY -> "#6a1b9a";  // Material purple 800
            case IGNORED    -> "#757575";  // Material grey 600
        };
    }
    // ── Formatting helpers ────────────────────────────────────────────────────

    static String formatSize(long bytes) {
        if (bytes < 1_024L)             return bytes + " B";
        double kb = bytes / 1_024.0;
        if (kb < 1_024)                 return String.format("%.1f KB", kb);
        double mb = kb / 1_024;
        if (mb < 1_024)                 return String.format("%.1f MB", mb);
        return                                 String.format("%.1f GB", mb / 1_024);
    }

    static String formatDateTime(Instant instant) {
        return DATE_FMT.format(instant.atZone(ZoneId.systemDefault()));
    }
}
