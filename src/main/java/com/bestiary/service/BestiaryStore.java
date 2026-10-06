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
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /**
     * Bump when the on-disk shape changes incompatibly. A file whose {@code version} doesn't match
     * is discarded on load (clean-slate reset) rather than migrated.
     * v2: Prayer folded into {@link com.bestiary.model.CreatureQuality} as the 7th stat.
     */
    public static final int VERSION = 2;

    private static final long DEBOUNCE_MS = 1000;

    /** Saves at least this big get a safety copy before being overwritten by one under half their size. */
    static final long SHRINK_GUARD_MIN_BYTES = 10 * 1024;

    /** Safety copies kept per account (save + backup copies together); older ones are deleted. */
    static final int KEEP_SAFETY_COPIES = 5;
    private static final String SAFETY = ".safety-";
    private static final int STAMP_LEN = "yyyyMMdd-HHmmss".length();

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
        /** True once a shiny / a Legendary+ / a Mythic has ever been discarded (discard achievements can't backfill). */
        public boolean discardedShiny;
        public boolean discardedLegendary;
        public boolean discardedMythic;
        /** Largest credit haul from a single discard action. */
        public long largestDiscardBatch;
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

    /** RuneLite's shared executor — the client owns its lifecycle, so we never shut it down. */
    private final ScheduledExecutorService executor;
    private final Object lock = new Object();
    private StoreData pending;              // guarded by lock
    // The write target is captured alongside the buffered data at save() time, so a debounced write
    // always lands in the file that was active when the data was buffered — even if the active
    // account is repointed (setActiveAccount) before the background writer runs.
    private File pendingFile;               // guarded by lock
    private File pendingBackup;             // guarded by lock
    private ScheduledFuture<?> scheduled;   // guarded by lock

    @Inject
    public BestiaryStore(Gson gson, ScheduledExecutorService executor) {
        // RuneLite.RUNELITE_DIR is the canonical ~/.runelite location; prefer it over a hand-rolled
        // user.home/.runelite path so we follow the client if it ever relocates the home dir.
        this(gson, executor, net.runelite.client.RuneLite.RUNELITE_DIR);
    }

    /**
     * Visible for testing: lets tests point storage at a temp directory. RUNELITE_DIR is resolved
     * once at class load, so a runtime {@code user.home} override can't redirect it.
     */
    BestiaryStore(Gson gson, ScheduledExecutorService executor, File runeliteDir) {
        this.executor    = executor;
        this.dir         = new File(runeliteDir, "bestiary");
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
        File f = file, b = backup;
        if (f == null) return new StoreData();
        StoreData d = tryRead(f);
        if (d == null) {
            // The save exists but couldn't be read (damaged, or briefly locked by other software).
            // The next save will overwrite it, so keep a safety copy of it first.
            keepSafetyCopy(f);
            d = tryRead(b);
            if (d != null) log.warn("Bestiary main file unreadable; recovered from backup");
        }
        if (d == null) {
            keepSafetyCopy(b);   // backup unusable too: keep it before we start this account empty
            return new StoreData();
        }
        if (d.version != VERSION) {
            keepSafetyCopy(f);
            keepSafetyCopy(b);
            log.info("Bestiary store version {} != {}; starting this account fresh (data reset)",
                    d.version, VERSION);
            return new StoreData();
        }
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
        File f = file, b = backup;   // snapshot the active target as of this call
        if (f == null) return;       // no active account — nothing to persist
        synchronized (lock) {
            pending = data;
            pendingFile = f;
            pendingBackup = b;
            if (scheduled == null || scheduled.isDone()) {
                scheduled = executor.schedule(this::flush, DEBOUNCE_MS, TimeUnit.MILLISECONDS);
            }
        }
    }

    /** Immediate synchronous save (shutdown / wipe / user-initiated). Cancels any pending debounce. */
    public void saveNow(StoreData data) {
        File f = file, b = backup;   // snapshot the active target as of this call
        if (f == null) return;       // no active account — nothing to persist
        synchronized (lock) {
            if (scheduled != null) scheduled.cancel(false);
            pending = null;
            pendingFile = null;
            pendingBackup = null;
        }
        writeTo(f, b, data);
    }

    private void flush() {
        StoreData d; File f, b;
        synchronized (lock) {
            d = pending; f = pendingFile; b = pendingBackup;
            pending = null; pendingFile = null; pendingBackup = null;
        }
        if (d != null && f != null) writeTo(f, b, d);
    }

    /**
     * Writes any debounced-but-unwritten snapshot to the file it was buffered for, synchronously.
     * Called before an account repoint (setActiveAccount) and on logout, so the previous account's
     * buffered data is committed to its own file first.
     */
    private void flushPending() {
        StoreData d; File f, b;
        synchronized (lock) {
            if (scheduled != null) scheduled.cancel(false);
            d = pending; f = pendingFile; b = pendingBackup;
            pending = null; pendingFile = null; pendingBackup = null;
        }
        if (d != null && f != null) writeTo(f, b, d);
    }

    /**
     * Crash-safe write of {@code d} to {@code target} (temp file → back up previous → atomic rename).
     *
     * <p>The temp file and the backup are forced to disk before the rename. Without that, a full
     * system crash (BSOD / power loss) can persist the rename but not the file's contents, leaving
     * an empty or zero-filled save that silently falls back to the older {@code .bak} on next load.
     */
    private synchronized boolean writeTo(File target, File bak, StoreData d) {
        try {
            Files.createDirectories(accountsDir.toPath());
            byte[] bytes = gson.toJson(d).getBytes(StandardCharsets.UTF_8);
            long oldSize = target.length();   // 0 if missing
            if (oldSize >= SHRINK_GUARD_MIN_BYTES && bytes.length < oldSize / 2) {
                // A save that suddenly loses over half its size is almost never wanted (the usual
                // cause is an empty collection loaded after a read failure). Keep the old one first.
                log.warn("Bestiary save {} shrinking {} -> {} bytes; keeping a safety copy",
                        target, oldSize, bytes.length);
                keepSafetyCopy(target);
            }
            Path tmp = target.toPath().resolveSibling(target.getName() + ".tmp");
            writeDurably(tmp, bytes);
            if (target.exists()) {
                Files.copy(target.toPath(), bak.toPath(), StandardCopyOption.REPLACE_EXISTING);
                forceToDisk(bak.toPath());
            }
            try {
                Files.move(tmp, target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnsupported) {
                Files.move(tmp, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            forceDirectoryToDisk(accountsDir.toPath());
            return true;
        } catch (IOException e) {
            log.error("Failed to write bestiary store {}", target, e);
            return false;
        }
    }

    /**
     * Copies {@code f} (an account's save or its backup) to {@code <hash>.safety-<yyyyMMdd-HHmmss>[-n].json}
     * next to it, then keeps only the account's newest {@link #KEEP_SAFETY_COPIES} safety copies. The
     * name ends in {@code .json} so it shows as a plain JSON file, never mistaken for the live save.
     */
    private static void keepSafetyCopy(File f) {
        if (f == null || !f.exists()) return;
        File dir = f.getParentFile();
        String account = accountOf(f.getName());
        try {
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            // Same-second copies get a counter above any existing one, so names always sort in the
            // order they were made (a pruned name is never reused and mistaken for the oldest).
            int next = 0;
            for (File c : safetyCopies(dir, account)) {
                if (safetyStamp(c).equals(stamp)) next = Math.max(next, safetyCounter(c) + 1);
            }
            File copy = new File(dir, account + SAFETY + stamp + (next == 0 ? "" : "-" + next) + ".json");
            Files.copy(f.toPath(), copy.toPath());
            forceToDisk(copy.toPath());
            log.warn("Kept a safety copy of bestiary save {} at {}", f, copy);
        } catch (IOException e) {
            log.error("Failed to keep a safety copy of {}", f, e);
        }
        pruneSafetyCopies(dir, account);
    }

    /** Deletes all but the newest {@link #KEEP_SAFETY_COPIES} safety copies for one account. */
    private static void pruneSafetyCopies(File dir, String account) {
        List<File> copies = safetyCopies(dir, account);
        if (copies.size() <= KEEP_SAFETY_COPIES) return;
        copies.sort(Comparator.comparing(BestiaryStore::safetyStamp)
                .thenComparingInt(BestiaryStore::safetyCounter));
        for (File old : copies.subList(0, copies.size() - KEEP_SAFETY_COPIES)) {
            if (!old.delete()) log.warn("Could not delete old bestiary safety copy {}", old);
        }
    }

    /** {@code "123"} for both {@code 123.json} and {@code 123.json.bak}. */
    private static String accountOf(String fileName) {
        int i = fileName.indexOf('.');
        return i < 0 ? fileName : fileName.substring(0, i);
    }

    private static List<File> safetyCopies(File dir, String account) {
        File[] found = dir.listFiles((d, n) -> n.startsWith(account + SAFETY) && n.endsWith(".json"));
        return found == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(found));
    }

    /** The part of a safety copy's name after {@code .safety-}, without {@code .json}. */
    private static String safetySuffix(File c) {
        String n = c.getName();
        return n.substring(n.indexOf(SAFETY) + SAFETY.length(), n.length() - ".json".length());
    }

    /** The {@code yyyyMMdd-HHmmss} part of a safety copy's name. */
    private static String safetyStamp(File c) {
        String rest = safetySuffix(c);
        return rest.length() > STAMP_LEN ? rest.substring(0, STAMP_LEN) : rest;
    }

    /** The same-second {@code -n} counter of a safety copy's name (0 if none). */
    private static int safetyCounter(File c) {
        String rest = safetySuffix(c);
        if (rest.length() <= STAMP_LEN + 1) return 0;
        try {
            return Integer.parseInt(rest.substring(STAMP_LEN + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Writes {@code bytes} to {@code p} (replacing any leftover content) and forces it to disk. */
    private static void writeDurably(Path p, byte[] bytes) throws IOException {
        try (FileChannel ch = FileChannel.open(p, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buf = ByteBuffer.wrap(bytes);
            while (buf.hasRemaining()) ch.write(buf);
            ch.force(true);
        }
    }

    /** Forces an existing file's contents to disk (opened for write: Windows needs that to flush). */
    private static void forceToDisk(Path p) throws IOException {
        try (FileChannel ch = FileChannel.open(p, StandardOpenOption.WRITE)) {
            ch.force(true);
        }
    }

    /**
     * Best-effort flush of the directory entry so the rename itself survives a crash. Supported on
     * Linux/macOS; Windows can't open a directory as a channel (NTFS journals renames anyway).
     */
    private static void forceDirectoryToDisk(Path dir) {
        try (FileChannel ch = FileChannel.open(dir, StandardOpenOption.READ)) {
            ch.force(true);
        } catch (IOException | UnsupportedOperationException ignored) {
            // Not supported on this platform — nothing more we can do.
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

    /**
     * Commit any debounced-but-unwritten snapshot synchronously. Call from plugin shutDown.
     * The executor is RuneLite's shared one, so we cancel our pending task but never shut it down.
     */
    public void close() {
        flushPending();
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
            log.warn("Failed to read account registry; starting fresh", e);
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
