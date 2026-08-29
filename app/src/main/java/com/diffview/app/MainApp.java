package com.comparetool.app;

import com.comparetool.core.diff.LineDiffEngine;
import com.comparetool.core.folder.DefaultFolderDiffEngine;
import com.comparetool.core.service.ComparisonService;
import com.comparetool.core.service.DefaultComparisonService;
import com.comparetool.infra.concurrent.TaskExecutor;
import com.comparetool.infra.concurrent.PooledTaskExecutor;
import com.comparetool.infra.encoding.JUniversalChardetDetector;
import com.comparetool.infra.hash.Sha256HashService;
import com.comparetool.infra.io.FileIOService;
import com.comparetool.infra.io.NioFileIOService;
import com.comparetool.model.ComparisonOptions;
import com.comparetool.model.FolderComparisonOptions;
import com.comparetool.model.ThemeMode;
import com.comparetool.ui.FileComparisonView;
import com.comparetool.ui.FolderComparisonView;
import com.comparetool.ui.SelectionBar;
import com.comparetool.ui.ThemeManager;
import com.comparetool.viewmodel.FileComparisonViewModel;
import com.comparetool.viewmodel.FileDiffRequest;
import com.comparetool.viewmodel.FolderComparisonViewModel;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.net.URL;

/**
 * Application entry point — tab-based shell.
 *
 * <pre>
 * ┌──────────────────────────────────────────────────────────┐
 * │  [+ File Comparison]  [+ Folder Comparison]  (toolbar)  │
 * ├──────────────────────────────────────────────────────────┤
 * │  TabPane                                                 │
 * │  ┌─────────────┐ ┌─────────────┐                        │
 * │  │ File Comp ✕ │ │ Folder  ✕   │  …                     │
 * │  └─────────────┘ └─────────────┘                        │
 * │  ┌────────────────────────────────────────────────────┐  │
 * │  │  SelectionBar (left path | right path | Compare)   │  │
 * │  ├────────────────────────────────────────────────────┤  │
 * │  │  comparison content (empty until Compare is run)   │  │
 * │  └────────────────────────────────────────────────────┘  │
 * └──────────────────────────────────────────────────────────┘
 * </pre>
 *
 * Each tab owns its own ViewModels and Views so comparisons are fully independent.
 * Double-clicking a file inside a Folder Comparison opens a new File Comparison tab.
 */
public class MainApp extends Application {

    // ── Shared services (stateless — safe to share across tabs) ───────────────
    private FileIOService     fileIO;
    private LineDiffEngine    diffEngine;
    private ComparisonService service;
    private TaskExecutor      executor;

    @Override
    public void start(Stage primaryStage) {
        ThemeManager.applyTheme(ThemeMode.LIGHT);

        fileIO     = new NioFileIOService(new JUniversalChardetDetector());
        executor   = new PooledTaskExecutor();
        diffEngine = new LineDiffEngine();
        service    = new DefaultComparisonService(
                diffEngine,
                new DefaultFolderDiffEngine(),
                fileIO,
                new Sha256HashService(),
                executor);

        // ── Tab pane ───────────────────────────────────────────────────────────
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);

        // ── Toolbar ────────────────────────────────────────────────────────────
        Button newFileBtn   = new Button("+ File Comparison");
        Button newFolderBtn = new Button("+ Folder Comparison");
        newFileBtn  .setId("newFileTabButton");
        newFolderBtn.setId("newFolderTabButton");
        newFileBtn  .setOnAction(e -> addTab(tabPane, false));
        newFolderBtn.setOnAction(e -> addTab(tabPane, true));

        HBox toolbar = new HBox(8, newFileBtn, newFolderBtn);
        toolbar.setPadding(new Insets(6, 8, 4, 8));
        toolbar.setAlignment(Pos.CENTER_LEFT);

        BorderPane root = new BorderPane();
        root.setTop(toolbar);
        root.setCenter(tabPane);

        // Open one default file-comparison tab on startup
        addTab(tabPane, false);

        Scene scene = new Scene(root, 1200, 800);
        // diff-colors.css lives in the ui module classpath
        URL cssUrl = getClass().getResource("/css/diff-colors.css");
        if (cssUrl != null) scene.getStylesheets().add(cssUrl.toExternalForm());

        primaryStage.setTitle("Comparison Tool");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    // ── Tab factory ────────────────────────────────────────────────────────────

    /**
     * Creates a new comparison tab, adds it to {@code tabPane}, and selects it.
     * Each tab has its own ViewModels and Views — comparisons are independent.
     *
     * @param folder {@code true} for a Folder Comparison tab, {@code false} for File
     */
    private void addTab(TabPane tabPane, boolean folder) {
        Tab tab = new Tab(folder ? "Folder Comparison" : "File Comparison");

        // Per-tab ViewModels
        FileComparisonViewModel   fileVm   = new FileComparisonViewModel(service, diffEngine, executor, fileIO);
        FolderComparisonViewModel folderVm = new FolderComparisonViewModel(service, executor);

        // Per-tab Views
        FileComparisonView   fileView   = new FileComparisonView();
        FolderComparisonView folderView = new FolderComparisonView();
        fileView.bindViewModel(fileVm);
        folderView.bindViewModel(folderVm);

        // Content area (center of the tab) — filled when Compare is pressed
        BorderPane contentArea = new BorderPane();
        SelectionBar selBar = new SelectionBar();
        if (folder) selBar.setFolderMode(true);

        // Folder drill-down → open a new File Comparison tab
        folderView.setOnFileDiffRequested(req -> {
            if (req instanceof FileDiffRequest.Actual actual) {
                openFileDiffTab(tabPane, actual);
            } else if (req instanceof FileDiffRequest.Placeholder ph) {
                String msg = ph.leftSide()
                        ? "<- Only on left: " + ph.side().getFileName()
                        : "Only on right: " + ph.side().getFileName() + " ->";
                Label lbl = new Label(msg);
                lbl.setStyle("-fx-font-size: 14px; -fx-text-fill: #777;");
                Tab t = new Tab(ph.side().getFileName().toString(), lbl);
                tabPane.getTabs().add(t);
                tabPane.getSelectionModel().select(t);
            }
        });

        selBar.setOnCompare(request -> {
            String ln = request.left() .getFileName().toString();
            String rn = request.right().getFileName().toString();
            if (request.folder()) {
                tab.setText("[Folder] " + ln + " \u2194 " + rn);
                contentArea.setCenter(folderView);
                folderView.startCompare(request.left(), request.right(), FolderComparisonOptions.defaults());
            } else {
                tab.setText("[File] " + ln + " \u2194 " + rn);
                contentArea.setCenter(fileView);
                fileVm.compare(request.left(), request.right(), ComparisonOptions.defaults());
            }
        });

        BorderPane tabRoot = new BorderPane();
        tabRoot.setTop(selBar);
        tabRoot.setCenter(contentArea);
        tab.setContent(tabRoot);

        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
    }

    /**
     * Opens a new File Comparison tab for a file pair drilled into from a Folder Comparison.
     * The tab is selected immediately and the comparison starts right away.
     */
    private void openFileDiffTab(TabPane tabPane, FileDiffRequest.Actual req) {
        FileComparisonViewModel vm   = new FileComparisonViewModel(service, diffEngine, executor, fileIO);
        FileComparisonView      view = new FileComparisonView();
        view.bindViewModel(vm);

        String title = "[File] " + req.left().getFileName() + " \u2194 " + req.right().getFileName();
        Tab tab = new Tab(title, view);
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
        vm.compare(req.left(), req.right(), req.options());
    }

    public static void main(String[] args) {
        // --smoke-test: headless CI validation of the packaged runtime (REQ-17.2).
        for (String arg : args) {
            if ("--smoke-test".equals(arg)) {
                PackagingSmokeTest.run(); // calls System.exit(); never returns
                return;
            }
        }
        launch(args);
    }
}
