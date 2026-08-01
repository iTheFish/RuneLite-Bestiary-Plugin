package com.bestiary.service;

import com.google.gson.Gson;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
 * <p>Collections are keyed <b>per account</b> (#47): each account has its own file at
 * {@code ~/.runelite/bestiary/accounts/<accountHash>.json} (+ a {@code .bak}), keyed by
 * RuneLite's stable {@code accountHash} (survives name changes). No account is active until
 * {@link #setActiveAccount} is called on login — before that, loads return empty and saves
 * are dropped, so no account's data is ever touched while logged out.
 *
 * <p>The whole collection lives in memory in {@link BestiaryDataService}; this store just
 * loads and saves a single JSON snapshot for the active account. Writes are debounced onto a
 * background thread (bursts of kills/credits coalesce into one write ~1s later) and are
 * crash-safe: each write goes to a temp file, the previous good file is copied to a
 * {@code .bak}, then the temp is atomically renamed into place. Load falls back to the backup
 * if the main file is missing or corrupt.
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
        /** Lifetime captures this account personally made — never decremented by discard/transfer. */
        public long lifetimeCaptures;
        /** Per-species lifetime captures (npcName -> count), same monotonic semantics. */
        public Map<String, Integer> lifetimeCapturesByNpc = new LinkedHashMap<>();
        /** Total cards this account has sent away via transfer. */
        public long lifetimeCardsSent;

        /** Total cards this account has discarded for credits. */
        public long lifetimeCardsDiscarded;
        public long totalXp;
        public List<String> achievements = new ArrayList<>();
        public Map<String, Integer> shopUpgrades = new LinkedHashMap<>();
    }

    /** One row of the account registry ({@code index.json}) — drives the future account switcher (#48). */
    static class AccountEntry {
        public String rsn;
        public long lastActive;
    }

    private final File dir;
    private final File accountsDir;
    private final File indexFile;
    private final Gson gson;

    /** Active account's target files — null until {@link #setActiveAccount} is called on login. */
    private volatile File file;
    private volatile File backup;
    private volatile Long activeHash;

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
        this.dir         = new File(System.getProperty("user.home"),
                ".runelite" + File.separator + "bestiary");
        this.accountsDir = new File(dir, "accounts");
        this.indexFile   = new File(accountsDir, "index.json");
        // Reuse RuneLite's Gson config, adding an Instant<->epoch-second adapter.
        this.gson = gson.newBuilder()
                .registerTypeAdapter(Instant.class, new InstantEpochAdapter())
                .setPrettyPrinting()
                .create();
        archiveLegacyGlobalFile();
    }

    // -------------------------------------------------------------------------
    // Account selection
    // -------------------------------------------------------------------------

    /**
     * Points the store at {@code accountHash}'s file for all subsequent loads/saves. Flushes any
     * pending write for the previously active account to its own file first, so a switch can never
     * spill one account's buffered data into another's file. Records the account in the registry.
     */
    public void setActiveAccount(long accountHash, String rsn) {
        flushPending();                 // write buffered data to the OLD file before repointing
        this.activeHash = accountHash;
        this.file   = new File(accountsDir, accountHash + ".json");
        this.backup = new File(accountsDir, accountHash + ".json.bak");
        recordAccount(accountHash, rsn);
    }

    /** True once an account has been selected (i.e. the player has logged in this session). */
    public boolean hasActiveAccount() {
        return activeHash != null;
    }

    /**
     * Deactivates the current account on logout: flushes any buffered write to its file, then stops
     * targeting any file so subsequent saves are dropped until the next login. Prevents a logged-out
     * client from ever writing to (or being loaded from) an account's file.
     */
    public void clearActiveAccount() {
        flushPending();
        this.activeHash = null;
        this.file = null;
        this.backup = null;
    }

    // -------------------------------------------------------------------------
    // Load
    // -------------------------------------------------------------------------

    /** Reads the active account's collection (main file then backup); empty if none active/usable. */
    public StoreData load() {
        if (file == null) return new StoreData();
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
        if (d.lifetimeCapturesByNpc == null) d.lifetimeCapturesByNpc = new LinkedHashMap<>();
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
        if (file == null) return;   // no active account — nothing to persist
        synchronized (lock) {
            pending = data;
            if (scheduled == null || scheduled.isDone()) {
                scheduled = writer.schedule(this::flush, DEBOUNCE_MS, TimeUnit.MILLISECONDS);
            }
        }
    }

    /** Immediate synchronous save (shutdown / wipe / user-initiated). Cancels any pending debounce. */
    public void saveNow(StoreData data) {
        if (file == null) return;   // no active account — nothing to persist
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

    /** Writes any debounced-but-unwritten snapshot to the CURRENT active file, synchronously. */
    private void flushPending() {
        StoreData d;
        synchronized (lock) {
            if (scheduled != null) scheduled.cancel(false);
            d = pending;
            pending = null;
        }
        if (d != null) write(d);
    }

    private synchronized void write(StoreData d) {
        if (file == null) return;
        writeTo(file, backup, d);
    }

    /** Crash-safe write of {@code d} to {@code target} (temp file → back up previous → atomic rename). */
    private synchronized boolean writeTo(File target, File bak, StoreData d) {
        try {
            Files.createDirectories(accountsDir.toPath());
            Path tmp = target.toPath().resolveSibling(target.getName() + ".tmp");
            Files.write(tmp, gson.toJson(d).getBytes(StandardCharsets.UTF_8));
            if (target.exists()) {
                Files.copy(target.toPath(), bak.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(tmp, target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnsupported) {
                Files.move(tmp, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            log.error("Failed to write bestiary store {}", target, e);
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Arbitrary-account access (for card transfer, #50)
    // -------------------------------------------------------------------------

    /** Reads another account's collection read-only (never the active file's in-memory state). */
    public StoreData readAccount(long accountHash) {
        StoreData d = tryRead(new File(accountsDir, accountHash + ".json"));
        if (d == null) d = tryRead(new File(accountsDir, accountHash + ".json.bak"));
        if (d == null) return new StoreData();
        if (d.captures == null)     d.captures = new ArrayList<>();
        if (d.killCounts == null)   d.killCounts = new LinkedHashMap<>();
        if (d.achievements == null) d.achievements = new ArrayList<>();
        if (d.shopUpgrades == null) d.shopUpgrades = new LinkedHashMap<>();
        if (d.lifetimeCapturesByNpc == null) d.lifetimeCapturesByNpc = new LinkedHashMap<>();
        return d;
    }

    /**
     * Synchronously writes a specific account's file (used to deposit transferred cards, #50). Must
     * only be used for a NON-active account so it can't race the debounced writer for the active file.
     */
    public boolean writeAccountNow(long accountHash, StoreData data) {
        return writeTo(new File(accountsDir, accountHash + ".json"),
                new File(accountsDir, accountHash + ".json.bak"), data);
    }

    /** A known account from the registry — accountHash + last-known RSN + last-active epoch. */
    public static final class AccountRef {
        public final long   hash;
        public final String rsn;
        public final long   lastActive;
        AccountRef(long hash, String rsn, long lastActive) {
            this.hash = hash; this.rsn = rsn; this.lastActive = lastActive;
        }
    }

    /** All accounts recorded in the registry (index.json), most-recently-active first. */
    public List<AccountRef> listAccounts() {
        List<AccountRef> out = new ArrayList<>();
        for (Map.Entry<String, AccountEntry> e : readIndex().entrySet()) {
            try {
                long h = Long.parseLong(e.getKey());
                AccountEntry v = e.getValue();
                out.add(new AccountRef(h, v != null ? v.rsn : null, v != null ? v.lastActive : 0));
            } catch (NumberFormatException ignored) { /* skip malformed key */ }
        }
        out.sort((a, b) -> Long.compare(b.lastActive, a.lastActive));
        return out;
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
    // Migration + registry
    // -------------------------------------------------------------------------

    /**
     * One-time clean-slate migration: the pre-#47 build kept a single global {@code bestiary.json}
     * shared by every account. Rename it (and its backup) to a dated {@code bestiary.legacy-*} file
     * so it is never loaded again but stays recoverable. Because we move it, this runs only once.
     */
    private void archiveLegacyGlobalFile() {
        File legacy = new File(dir, "bestiary.json");
        if (!legacy.exists()) return;
        try {
            String stamp = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            File archived = new File(dir, "bestiary.legacy-" + stamp + ".json");
            for (int n = 1; archived.exists(); n++) {
                archived = new File(dir, "bestiary.legacy-" + stamp + "-" + n + ".json");
            }
            Files.move(legacy.toPath(), archived.toPath());
            File legacyBak = new File(dir, "bestiary.json.bak");
            if (legacyBak.exists()) {
                Files.move(legacyBak.toPath(), new File(dir, archived.getName() + ".bak").toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("Archived legacy global collection to {} (per-account storage is now active)",
                    archived.getName());
        } catch (IOException e) {
            log.error("Failed to archive legacy global bestiary file", e);
        }
    }

    /** Upserts an account into the registry with a fresh {@code lastActive} timestamp. */
    private synchronized void recordAccount(long accountHash, String rsn) {
        try {
            Files.createDirectories(accountsDir.toPath());
            Map<String, AccountEntry> index = readIndex();
            AccountEntry e = index.computeIfAbsent(String.valueOf(accountHash), k -> new AccountEntry());
            if (rsn != null && !rsn.isEmpty()) e.rsn = rsn;
            e.lastActive = Instant.now().getEpochSecond();
            Files.write(indexFile.toPath(), gson.toJson(index).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            log.warn("Failed to update account registry", ex);
        }
    }

    private Map<String, AccountEntry> readIndex() {
        if (!indexFile.exists()) return new LinkedHashMap<>();
        try {
            String json = new String(Files.readAllBytes(indexFile.toPath()), StandardCharsets.UTF_8);
            Type t = new TypeToken<LinkedHashMap<String, AccountEntry>>() {}.getType();
            Map<String, AccountEntry> m = gson.fromJson(json, t);
            return m != null ? m : new LinkedHashMap<>();
        } catch (Exception e) {
            log.warn("Failed to read account registry — starting fresh", e);
            return new LinkedHashMap<>();
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
