package com.comparetool.ui;

import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;

import java.net.URL;

/**
 * Top-level application shell (task 11.1).
 *
 * <pre>
 * ┌─────────────────────────────────────────────────────┐
 * │  SelectionBar  (top)                                │
 * ├─────────────────────────────────────────────────────┤
 * │  contentPane   (center) — switched by routing logic │
 * └─────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h3>Routing</h3>
 * <p>When the user clicks Compare with valid, same-type paths the shell routes to
 * the appropriate view:
 * <ul>
 *   <li>Both paths are <em>files</em> → file comparison view (task 12.x placeholder for now)</li>
 *   <li>Both paths are <em>directories</em> → folder comparison view (task 12.x placeholder)</li>
 * </ul>
 *
 * <h3>ViewModel wiring</h3>
 * <p>Real ViewModel injection is performed in the {@code app} module (task 11.x+).
 * For 11.1 the shell shows placeholder labels in the content pane so routing can
 * be tested without the full view implementations.
 */
public class MainShell extends BorderPane {

    // ── IDs for lookup ────────────────────────────────────────────────────────
    /** fx:id of the content pane — used by TestFX and the routing logic. */
    public static final String CONTENT_PANE_ID = "contentPane";

    private static final String DIFF_CSS_PATH = "/css/diff-colors.css";

    // ── controls ──────────────────────────────────────────────────────────────
    private final SelectionBar selectionBar = new SelectionBar();
    private final StackPane    contentPane  = new StackPane();

    // ── constructor ───────────────────────────────────────────────────────────

    public MainShell() {
        contentPane.setId(CONTENT_PANE_ID);

        setTop(selectionBar);
        setCenter(contentPane);

        // Add diff-colors.css to the scene when this shell is placed into one
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                URL cssUrl = getClass().getResource(DIFF_CSS_PATH);
                if (cssUrl != null) {
                    String cssExt = cssUrl.toExternalForm();
                    if (!newScene.getStylesheets().contains(cssExt)) {
                        newScene.getStylesheets().add(cssExt);
                    }
                }
            }
        });

        selectionBar.setOnCompare(request -> {
            if (request.folder()) {
                showPlaceholder("Folder comparison: " + request.left().getFileName()
                        + " ↔ " + request.right().getFileName(), "folderView");
            } else {
                showPlaceholder("File comparison: " + request.left().getFileName()
                        + " ↔ " + request.right().getFileName(), "fileView");
            }
        });
    }

    // ── public API ────────────────────────────────────────────────────────────

    /** Returns the {@link SelectionBar} at the top of the shell. */
    public SelectionBar getSelectionBar() {
        return selectionBar;
    }

    /** Returns the center content pane whose child changes on routing. */
    public StackPane getContentPane() {
        return contentPane;
    }

    /**
     * Replaces the content area with the given node.
     * Called by routing logic in the {@code app} module when real views are available.
     *
     * @param node   the view to show
     * @param nodeId an fx:id to set on the node for lookup
     */
    public void showContent(javafx.scene.Node node, String nodeId) {
        node.setId(nodeId);
        contentPane.getChildren().setAll(node);
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private void showPlaceholder(String text, String id) {
        Label placeholder = new Label(text);
        placeholder.setStyle("-fx-font-size: 14px; -fx-text-fill: #555;");
        showContent(placeholder, id);
    }
}
