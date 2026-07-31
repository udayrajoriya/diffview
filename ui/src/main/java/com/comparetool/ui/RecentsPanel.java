package com.comparetool.ui;

import com.comparetool.model.RecentComparison;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.nio.file.Files;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Panel showing recently opened comparisons (REQ-014.2, REQ-014.3, REQ-014.4).
 *
 * <ul>
 *   <li>Entries are shown in the order supplied (typically most-recent first).</li>
 *   <li>Unavailable paths (where one or both sides no longer exist) are shown greyed
 *       with an "(unavailable)" indicator and are disabled for opening.</li>
 *   <li>Double-clicking — or calling {@link #openSelectedRecent()} — fires the
 *       {@link #setOnOpenRecent(Consumer)} callback for available entries.</li>
 * </ul>
 *
 * <h3>Testability</h3>
 * <p>Callers can inject a custom {@link #setAvailabilityChecker(Predicate)} that bypasses
 * real file-system checks, enabling headless tests without touching the disk.
 */
public class RecentsPanel extends VBox {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    // ── Controls ──────────────────────────────────────────────────────────────
    private final ListView<RecentComparison> listView = new ListView<>();

    // ── State ─────────────────────────────────────────────────────────────────
    private Consumer<RecentComparison> onOpenRecent;
    private Predicate<RecentComparison> availabilityChecker = RecentsPanel::defaultAvailable;

    // ── Constructor ───────────────────────────────────────────────────────────

    public RecentsPanel() {
        listView.setId("recentsList");
        listView.setAccessibleText("Recent comparisons");
        listView.setPrefHeight(160);
        listView.setMaxHeight(Double.MAX_VALUE);
        listView.setCellFactory(lv -> new RecentCell());
        VBox.setVgrow(listView, Priority.ALWAYS);

        // Double-click opens the selected available recent
        listView.setOnMouseClicked(e -> {
            if (e.getClickCount() >= 2) openSelectedRecent();
        });

        Label heading = new Label("Recent Comparisons");
        heading.setStyle("-fx-font-weight: bold;");

        setSpacing(4);
        setPadding(new Insets(6, 8, 6, 8));
        getChildren().addAll(heading, listView);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Populates the list with the supplied recents (replaces any existing items).
     *
     * @param recents list to show; must not be {@code null}
     */
    public void setRecents(List<RecentComparison> recents) {
        listView.setItems(FXCollections.observableArrayList(recents));
    }

    /**
     * Overrides the availability check used to style cells and gate the open action.
     *
     * <p>The default implementation calls {@link Files#exists(java.nio.file.Path)} on both
     * sides.  Tests can inject {@code r -> true} or {@code r -> false} to avoid disk I/O.
     *
     * @param checker predicate; returns {@code true} if the entry is openable
     */
    public void setAvailabilityChecker(Predicate<RecentComparison> checker) {
        this.availabilityChecker = checker;
        listView.refresh(); // re-render all cells with new availability
    }

    /**
     * Registers the callback invoked when an available recent entry is opened.
     *
     * @param callback receives the selected {@link RecentComparison}; may be {@code null}
     *                 to deregister
     */
    public void setOnOpenRecent(Consumer<RecentComparison> callback) {
        this.onOpenRecent = callback;
    }

    /**
     * Opens the currently selected recent entry if it is available.
     *
     * <p>This is the programmatic equivalent of a double-click.  Tests call this after
     * selecting an item via {@code getListView().getSelectionModel().select(index)}.
     */
    public void openSelectedRecent() {
        RecentComparison selected = listView.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        if (availabilityChecker.test(selected) && onOpenRecent != null) {
            onOpenRecent.accept(selected);
        }
    }

    /** Returns the backing {@link ListView} (id: {@code recentsList}). */
    public ListView<RecentComparison> getListView() {
        return listView;
    }

    // ── Cell factory ──────────────────────────────────────────────────────────

    private class RecentCell extends ListCell<RecentComparison> {

        @Override
        protected void updateItem(RecentComparison item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setStyle("");
                setDisable(false);
                return;
            }
            String kind   = item.folder() ? "[Folder]" : "[File]";
            String left   = lastName(item.left().toString());
            String right  = lastName(item.right().toString());
            String opened = DATE_FMT.format(item.lastOpened());

            if (availabilityChecker.test(item)) {
                setText(kind + "  " + left + "  ↔  " + right + "  (" + opened + ")");
                setStyle("");
                setDisable(false);
            } else {
                setText(kind + "  " + left + "  ↔  " + right + "  (unavailable)");
                setStyle("-fx-text-fill: #aaaaaa;");
                setDisable(true);
            }
        }
    }

    // ── Static helpers ────────────────────────────────────────────────────────

    private static boolean defaultAvailable(RecentComparison r) {
        return Files.exists(r.left()) && Files.exists(r.right());
    }

    /** Returns the last path segment, or the full string if no separator is found. */
    private static String lastName(String path) {
        int idx = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return idx >= 0 ? path.substring(idx + 1) : path;
    }
}
