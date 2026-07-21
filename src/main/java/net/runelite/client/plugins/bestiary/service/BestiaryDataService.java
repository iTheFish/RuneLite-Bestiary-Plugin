package net.runelite.client.plugins.bestiary.service;

import net.runelite.client.plugins.bestiary.model.BestiaryCollection;
import net.runelite.client.plugins.bestiary.model.CapturedCreature;
import net.runelite.client.plugins.bestiary.util.InstantAdapter;
import net.runelite.client.plugins.bestiary.util.RegionNames;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Loads and saves the bestiary collection and progression state to
 * {@code ~/.runelite/bestiary/}.  Saves are debounced: the first mutating
 * call after a quiet period schedules a disk write 5 s later.
 */
@Slf4j
@Singleton
public class BestiaryDataService {

    private static final String SUBDIR           = "bestiary";
    private static final String COLLECTION_FILE  = "collection.json";
    private static final String PROGRESS_FILE    = "progress.json";
    private static final long   SAVE_DELAY_SECS  = 5;

    private final ProgressionService progressionService;
    private final Gson gson;
    private final File bestiaryDir;
    private final ScheduledExecutorService executor;

    @Getter
    private BestiaryCollection collection = new BestiaryCollection();
    private ProgressionService.ProgressionState progressionState = new ProgressionService.ProgressionState();

    /** Pending debounced save; replaced on each call to scheduleSave(). */
    private ScheduledFuture<?> pendingSave;

    /** Snapshot captured on the client thread, written to disk on the executor. */
    private volatile String pendingCollectionJson;
    private volatile String pendingProgressJson;

    @Inject
    public BestiaryDataService(ProgressionService progressionService) {
        this.progressionService = progressionService;
        this.gson = new GsonBuilder()
                .registerTypeAdapter(Instant.class, new InstantAdapter())
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create();
        this.bestiaryDir = new File(System.getProperty("user.home"), ".runelite" + File.separator + SUBDIR);
        this.executor    = Executors.newSingleThreadScheduledExecutor(
                r -> { Thread t = new Thread(r, "bestiary-save"); t.setDaemon(true); return t; });
    }

    // --- Lifecycle ---

    /** Must be called from the client thread during plugin startUp. */
    public void load() {
        bestiaryDir.mkdirs();

        collection       = loadJson(new File(bestiaryDir, COLLECTION_FILE),  BestiaryCollection.class,
                                    new BestiaryCollection());
        progressionState = loadJson(new File(bestiaryDir, PROGRESS_FILE),
                                    ProgressionService.ProgressionState.class,
                                    new ProgressionService.ProgressionState());

        // Guard against null maps/lists after Gson deserialisation of empty JSON.
        if (collection.creatures     == null) collection.creatures     = new java.util.ArrayList<>();
        if (collection.killCounts    == null) collection.killCounts    = new java.util.HashMap<>();
        if (collection.captureCountByNpc == null) collection.captureCountByNpc = new java.util.HashMap<>();
        if (progressionState.unlockedAchievements == null) {
            progressionState.unlockedAchievements = java.util.EnumSet.noneOf(net.runelite.client.plugins.bestiary.model.Achievement.class);
        }

        migrateRegionNames();
        progressionService.init(progressionState, collection);
        log.info("Bestiary loaded: {} captures", collection.totalCaptures());
    }

    /**
     * Forces an immediate synchronous write.  Call from plugin shutDown to
     * ensure data is not lost when the client closes.
     */
    public void saveNow() {
        if (pendingSave != null) {
            pendingSave.cancel(false);
            pendingSave = null;
        }
        snapshotAndWrite();
    }

    /** Debounced save \u00e2\u20ac" safe to call after every kill/capture. */
    public void scheduleSave() {
        // Snapshot JSON on the client thread (holds the lock on the data model).
        pendingCollectionJson = gson.toJson(collection);
        pendingProgressJson   = gson.toJson(progressionState);

        if (pendingSave != null && !pendingSave.isDone()) {
            pendingSave.cancel(false);
        }
        pendingSave = executor.schedule(this::writePendingToDisk, SAVE_DELAY_SECS, TimeUnit.SECONDS);
    }

    /** Permanently deletes all capture and progression data. */
    public void wipeCollection() {
        collection       = new BestiaryCollection();
        progressionState = new ProgressionService.ProgressionState();
        progressionService.init(progressionState, collection);
        saveNow();
        log.info("Bestiary collection wiped");
    }

    // --- Mutators (call from client thread) ---

    public void addCapture(CapturedCreature c) {
        collection.addCapture(c);
        scheduleSave();
    }

    public void incrementKillCount(String npcName) {
        collection.incrementKillCount(npcName);
        scheduleSave();
    }

    public ProgressionService.ProgressionState getProgressionState() {
        return progressionState;
    }

    public long getCredits() {
        return collection.credits;
    }

    public void awardCredits(long amount) {
        collection.credits += amount;
        scheduleSave();
    }

    // --- Internal ---

    /** Upgrades captures that stored raw "Region N" IDs before RegionNames existed. */
    private void migrateRegionNames() {
        boolean dirty = false;
        for (CapturedCreature c : collection.creatures) {
            if (c.regionName != null && c.regionName.startsWith("Region ")) {
                try {
                    int id = Integer.parseInt(c.regionName.substring(7).trim());
                    String resolved = RegionNames.get(id);
                    if (!resolved.equals(c.regionName)) {
                        c.regionName = resolved;
                        dirty = true;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (dirty) {
            scheduleSave();
        }
    }

    private void snapshotAndWrite() {
        pendingCollectionJson = gson.toJson(collection);
        pendingProgressJson   = gson.toJson(progressionState);
        writePendingToDisk();
    }

    private void writePendingToDisk() {
        writeToFile(pendingCollectionJson, new File(bestiaryDir, COLLECTION_FILE));
        writeToFile(pendingProgressJson,   new File(bestiaryDir, PROGRESS_FILE));
    }

    private void writeToFile(String json, File file) {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(json);
            log.debug("Saved {}", file.getName());
        } catch (IOException e) {
            log.error("Failed to write {}", file.getAbsolutePath(), e);
        }
    }

    private <T> T loadJson(File file, Class<T> type, T defaultValue) {
        if (!file.exists()) {
            return defaultValue;
        }
        try (FileReader reader = new FileReader(file)) {
            T result = gson.fromJson(reader, type);
            return result != null ? result : defaultValue;
        } catch (Exception e) {
            log.error("Failed to load {}: {}", file.getName(), e.getMessage());
            return defaultValue;
        }
    }
}

