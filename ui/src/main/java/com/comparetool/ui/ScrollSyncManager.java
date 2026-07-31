package com.comparetool.ui;

import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollBar;

import java.util.Objects;

/**
 * Manages synchronized vertical scrolling between two {@link ListView}s.
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>Construct with two ListViews (before or after they are shown).</li>
 *   <li>The manager self-initializes when the left ListView's skin is
 *       applied (i.e., after the scene is shown and the first layout pass
 *       completes).  Call {@link #isReady()} to check.</li>
 *   <li>Use {@link #setSynced(boolean)} to enable or disable bidirectional
 *       scrollbar binding at any time.</li>
 * </ol>
 *
 * <h3>Implementation notes</h3>
 * <p>Bidirectional binding between the two vertical {@link ScrollBar}
 * {@code valueProperty}s propagates scroll position in both directions without
 * an infinite-update loop (JavaFX handles this internally).
 */
public class ScrollSyncManager {

    private final ListView<?> leftList;
    private final ListView<?> rightList;

    private ScrollBar leftSB;
    private ScrollBar rightSB;
    private boolean   synced;

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * @param leftList  the left diff ListView
     * @param rightList the right diff ListView
     * @param syncedByDefault {@code true} to start with synchronization enabled
     */
    public ScrollSyncManager(ListView<?> leftList, ListView<?> rightList, boolean syncedByDefault) {
        this.leftList  = Objects.requireNonNull(leftList,  "leftList");
        this.rightList = Objects.requireNonNull(rightList, "rightList");
        this.synced    = syncedByDefault;

        // React to skin changes (skin is created after scene is shown)
        leftList.skinProperty().addListener((obs, old, newSkin) -> {
            if (newSkin != null) {
                // runLater ensures rightList skin is also applied in the same layout pass
                Platform.runLater(this::initScrollBars);
            }
        });

        // If the skin is already applied (e.g., manager created after scene is shown)
        if (leftList.getSkin() != null) {
            Platform.runLater(this::initScrollBars);
        }
    }

    /** Creates a manager with synchronization enabled by default. */
    public ScrollSyncManager(ListView<?> leftList, ListView<?> rightList) {
        this(leftList, rightList, true);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Enables or disables bidirectional scroll synchronization.
     * Safe to call before {@link #isReady()} returns {@code true}; the
     * requested state is remembered and applied once the scrollbars are found.
     */
    public void setSynced(boolean sync) {
        this.synced = sync;
        if (!isReady()) return;
        applyBinding(sync);
    }

    /** Returns {@code true} when scroll synchronization is currently enabled. */
    public boolean isSynced() {
        return synced;
    }

    /**
     * Returns {@code true} when both vertical scrollbars have been located and
     * the manager is fully operational.
     */
    public boolean isReady() {
        return leftSB != null && rightSB != null;
    }

    // ── Package-private accessors (for tests) ─────────────────────────────────

    ScrollBar getLeftScrollBar()  { return leftSB;  }
    ScrollBar getRightScrollBar() { return rightSB; }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void initScrollBars() {
        leftSB  = findVerticalScrollBar(leftList);
        rightSB = findVerticalScrollBar(rightList);

        if (isReady() && synced) {
            applyBinding(true);
        }
    }

    private void applyBinding(boolean bind) {
        if (bind) {
            leftSB.valueProperty().bindBidirectional(rightSB.valueProperty());
        } else {
            leftSB.valueProperty().unbindBidirectional(rightSB.valueProperty());
        }
    }

    private static ScrollBar findVerticalScrollBar(ListView<?> listView) {
        return listView.lookupAll(".scroll-bar").stream()
                .filter(n -> n instanceof ScrollBar)
                .map(n -> (ScrollBar) n)
                .filter(sb -> sb.getOrientation() == Orientation.VERTICAL)
                .findFirst()
                .orElse(null);
    }
}
