package com.comparetool.infra.persist;

import com.comparetool.model.AppSettings;
import com.comparetool.model.RecentComparison;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Default {@link RecentsManager} that reads and writes the recents list through a
 * {@link SettingsRepository}.
 *
 * <p>Each mutating operation performs a read-modify-write cycle on the
 * {@link SettingsRepository}: it loads the current {@link AppSettings}, updates the
 * recents list, and saves.  This is intentionally simple — the recents list is small
 * (max {@link AppSettings#MAX_RECENTS} entries) and settings saves are infrequent.
 */
public final class DefaultRecentsManager implements RecentsManager {

    private final SettingsRepository repository;

    /**
     * @param repository the settings repository used to load and persist recents
     */
    public DefaultRecentsManager(SettingsRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    // ── RecentsManager ────────────────────────────────────────────────────────

    @Override
    public void addRecent(RecentComparison recent) {
        Objects.requireNonNull(recent, "recent");
        AppSettings current = repository.load();
        repository.save(current.withAddedRecent(recent));
    }

    @Override
    public void removeRecent(Path left, Path right) {
        Objects.requireNonNull(left,  "left");
        Objects.requireNonNull(right, "right");
        AppSettings current = repository.load();
        List<RecentComparison> filtered = current.recents().stream()
                .filter(r -> !(r.left().equals(left) && r.right().equals(right)))
                .collect(Collectors.toList());
        repository.save(current.withRecents(filtered));
    }

    @Override
    public List<RecentComparison> getRecents() {
        return repository.load().recents(); // already immutable via AppSettings record
    }

    @Override
    public boolean isAvailable(RecentComparison recent) {
        Objects.requireNonNull(recent, "recent");
        return Files.exists(recent.left()) && Files.exists(recent.right());
    }
}
