package com.bestiary.service;

import com.google.gson.Gson;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import lombok.extern.slf4j.Slf4j;
import com.bestiary.model.CapturedCreature;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Pure-Java JSON persistence for the bestiary (no native dependencies — Plugin Hub safe).
 *
 * The whole collection lives in memory in {@link BestiaryDataService}; this store just
 * loads and saves a single JSON snapshot. Writes are debounced onto a background thread
 * (bursts of kills/credits coalesce into one write ~1s later) and are crash-safe: each
 * write goes to a temp file, the previous good file is copied to a {@code .bak}, then the
 * temp is atomically renamed into place. Load falls back to the backup if the main file
 * is missing or corrupt.
 */
@Slf4j
@Singleton
public class BestiaryStore {

    /** Bump when the on-disk shape changes incompatibly. */
    public static final int VERSION = 1;

    private static final long DEBOUNCE_MS = 1000;

    /** Serialized snapshot of everything we persist. */
    public static class StoreData {
        public int version = VERSION;
        public List<CapturedCreature> captures = new ArrayList<>();
        public Map<String, Integer> killCounts = new LinkedHashMap<>();
        public long credits;
        public long lifetimeCreditsEarned;
        public long lifetimeCreditsSpent;
        public long totalXp;
        public List<String> achievements = new ArrayList<>();
        public Map<String, Integer> shopUpgrades = new LinkedHashMap<>();
    }

    private final File file;
    private final File backup;
    private final Gson gson;

    private final ScheduledExecutorService writer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "bestiary-store-writer");
        t.setDaemon(true);
        return t;
    });
    private final Object lock = new Object();
    private StoreData pending;              // guarded by lock
    private ScheduledFuture<?> scheduled;   // guarded by lock

    @Inject
    public BestiaryStore(Gson gson) {
        File dir = new File(System.getProperty("user.home"),
                ".runelite" + File.separator + "bestiary");
        this.file   = new File(dir, "bestiary.json");
        this.backup = new File(dir, "bestiary.json.bak");
        // Reuse RuneLite's Gson config, adding an Instant<->epoch-second adapter.
        this.gson = gson.newBuilder()
                .registerTypeAdapter(Instant.class, new InstantEpochAdapter())
                .setPrettyPrinting()
                .create();
    }

    // -------------------------------------------------------------------------
    // Load
    // -------------------------------------------------------------------------

    /** Reads the collection, preferring the main file then the backup; empty if neither is usable. */
    public StoreData load() {
        StoreData d = tryRead(file);
        if (d == null) {
            d = tryRead(backup);
            if (d != null) log.warn("Bestiary main file unreadable — recovered from backup");
        }
        if (d == null) return new StoreData();
        if (d.captures == null)    d.captures = new ArrayList<>();
        if (d.killCounts == null)  d.killCounts = new LinkedHashMap<>();
        if (d.achievements == null) d.achievements = new ArrayList<>();
        if (d.shopUpgrades == null) d.shopUpgrades = new LinkedHashMap<>();
        return d;
    }

    private StoreData tryRead(File f) {
        if (f == null || !f.exists()) return null;
        try {
            String json = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            return gson.fromJson(json, StoreData.class);
        } catch (Exception e) {
            log.error("Failed to read bestiary store {}", f, e);
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Save
    // -------------------------------------------------------------------------

    /** Debounced save — coalesces bursts and writes ~1s after the last change on a background thread. */
    public void save(StoreData data) {
        synchronized (lock) {
            pending = data;
            if (scheduled == null || scheduled.isDone()) {
                scheduled = writer.schedule(this::flush, DEBOUNCE_MS, TimeUnit.MILLISECONDS);
            }
        }
    }

    /** Immediate synchronous save (shutdown / wipe / user-initiated). Cancels any pending debounce. */
    public void saveNow(StoreData data) {
        synchronized (lock) {
            if (scheduled != null) scheduled.cancel(false);
            pending = null;
        }
        write(data);
    }

    private void flush() {
        StoreData d;
        synchronized (lock) {
            d = pending;
            pending = null;
        }
        if (d != null) write(d);
    }

    private synchronized void write(StoreData d) {
        try {
            Path dir = file.toPath().getParent();
            if (dir != null) Files.createDirectories(dir);
            Path tmp = file.toPath().resolveSibling("bestiary.json.tmp");
            Files.write(tmp, gson.toJson(d).getBytes(StandardCharsets.UTF_8));
            if (file.exists()) {
                Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(tmp, file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnsupported) {
                Files.move(tmp, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("Failed to write bestiary store {}", file, e);
        }
    }

    /** Flush any pending write and stop the writer thread. Call from plugin shutDown. */
    public void close() {
        flush();
        writer.shutdown();
        try {
            writer.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // -------------------------------------------------------------------------

    /** Stores/reads {@link Instant} as an epoch-second number (compact + matches the old DB). */
    private static final class InstantEpochAdapter
            implements JsonSerializer<Instant>, JsonDeserializer<Instant> {
        @Override
        public JsonElement serialize(Instant src, Type type, JsonSerializationContext ctx) {
            return new JsonPrimitive(src.getEpochSecond());
        }
        @Override
        public Instant deserialize(JsonElement json, Type type, JsonDeserializationContext ctx) {
            return Instant.ofEpochSecond(json.getAsLong());
        }
    }
}
