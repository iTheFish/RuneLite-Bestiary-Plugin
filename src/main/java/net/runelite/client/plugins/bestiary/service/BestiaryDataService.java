package net.runelite.client.plugins.bestiary.service;

import net.runelite.client.plugins.bestiary.model.Achievement;
import net.runelite.client.plugins.bestiary.model.BestiaryCollection;
import net.runelite.client.plugins.bestiary.model.CapturedCreature;
import net.runelite.client.plugins.bestiary.model.CreatureQuality;
import net.runelite.client.plugins.bestiary.model.CreatureRarity;
import net.runelite.client.plugins.bestiary.model.MonsterRoster;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Loads and saves the bestiary collection and progression state via SQLite.
 * Mutations to captures and kill counts are written immediately. Mutable
 * creature fields (favourite, nickname, playerName, regionName) are flushed
 * as a batch by saveNow(), which is called from UI callbacks after changes.
 */
@Slf4j
@Singleton
public class BestiaryDataService {

    private static final String META_CREDITS      = "credits";
    private static final String META_TOTAL_XP     = "total_xp";
    private static final String META_ACHIEVEMENTS = "achievements";

    private final ProgressionService progressionService;
    private final BestiaryDatabase db;

    @Getter
    private BestiaryCollection collection = new BestiaryCollection();
    private ProgressionService.ProgressionState progressionState = new ProgressionService.ProgressionState();

    @Inject
    public BestiaryDataService(ProgressionService progressionService, BestiaryDatabase db) {
        this.progressionService = progressionService;
        this.db = db;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /** Must be called from the client thread during plugin startUp. */
    public void load() {
        db.open();
        collection = new BestiaryCollection();

        List<CapturedCreature> captures = db.loadAllCaptures();
        for (CapturedCreature c : captures) {
            collection.creatures.add(c);
            collection.captureCountByNpc.merge(c.npcName, 1, Integer::sum);
        }

        collection.killCounts = db.loadKillCounts();
        collection.credits    = parseLong(db.getMetadata(META_CREDITS, "0"), 0L);

        progressionState = new ProgressionService.ProgressionState();
        progressionState.totalXp = parseLong(db.getMetadata(META_TOTAL_XP, "0"), 0L);
        progressionState.unlockedAchievements = loadAchievements();

        progressionService.init(progressionState, collection);
        log.info("Bestiary loaded from DB: {} captures", collection.totalCaptures());
    }

    /**
     * Flushes all mutable creature fields and metadata to disk. Called from UI
     * callbacks after favourite/nickname/playerName changes.
     */
    public void saveNow() {
        db.batchUpdateMutable(collection.creatures);
        saveMetadata();
    }

    /** No-op: individual mutations are written immediately. Kept for API compatibility. */
    public void scheduleSave() {
        saveNow();
    }

    /** Permanently deletes all data and resets in-memory state. */
    public void wipeCollection() {
        db.deleteAllCaptures();
        db.deleteAllKillCounts();
        db.deleteAllMetadata();
        collection       = new BestiaryCollection();
        progressionState = new ProgressionService.ProgressionState();
        progressionService.init(progressionState, collection);
        log.info("Bestiary collection wiped");
    }

    /** Closes the database connection. Call from plugin shutDown. */
    public void shutdown() {
        saveNow();
        db.close();
    }

    // -------------------------------------------------------------------------
    // Mutators (call from client thread)
    // -------------------------------------------------------------------------

    public void addCapture(CapturedCreature c) {
        collection.addCapture(c);
        db.insertCapture(c);
        saveMetadata();
    }

    public void incrementKillCount(String npcName) {
        collection.incrementKillCount(npcName);
        db.upsertKillCount(npcName, collection.getKillCount(npcName));
        saveMetadata();
    }

    /**
     * Persists mutable fields of a single creature immediately. Use this after
     * targeted changes (e.g. a single card's favourite or nickname was just changed)
     * when you want to avoid a full batch flush.
     */
    public void updateCapture(CapturedCreature c) {
        db.updateCaptureMutable(c);
    }

    public ProgressionService.ProgressionState getProgressionState() {
        return progressionState;
    }

    public long getCredits() {
        return collection.credits;
    }

    public void awardCredits(long amount) {
        collection.credits += amount;
        db.setMetadata(META_CREDITS, String.valueOf(collection.credits));
    }

    public void saveProgressionState() {
        saveMetadata();
    }

    // -------------------------------------------------------------------------
    // Dev tools
    // -------------------------------------------------------------------------

    /**
     * Wipes the collection and inserts one capture per rarity for every roster
     * monster. Stats are seeded from the monster name so the same data is
     * produced each run. Only call this from a dev build.
     */
    public void seedTestCollection() {
        wipeCollection();
        Random rng = new Random();
        Instant base = Instant.now().minusSeconds(60L * 60 * 24 * 365); // spread over a year
        int idx = 0;
        int[] captureLevels = {1, 20, 40, 60, 80, 95};
        int[] killsBefore   = {3, 12, 30, 80, 200, 500};

        for (String name : MonsterRoster.ROSTER) {
            int combatLevel = combatLevelForSeed(name);
            net.runelite.client.plugins.bestiary.model.CombatClass combatClass =
                MonsterRoster.getCombatClass(name, combatLevel);

            for (int r = 0; r < CreatureRarity.values().length; r++) {
                CreatureRarity rarity = CreatureRarity.values()[r];
                rng.setSeed((long) name.hashCode() * 31 + r);

                int[] statBases = MonsterRoster.getStatBases(name, combatLevel);
                CreatureQuality quality = net.runelite.client.plugins.bestiary.util.RarityRoller
                    .generateQuality(combatClass, rarity, statBases, rng);
                // Seed shiny using the legacy all-primaries-≥95 heuristic so the album
                // still shows some shiny stars for visual testing.
                boolean shiny = quality.isShiny(combatClass);

                CapturedCreature c = CapturedCreature.builder()
                    .npcId(0)
                    .npcName(name)
                    .npcCombatLevel(combatLevel)
                    .rarity(rarity)
                    .quality(quality)
                    .captureTime(base.plusSeconds((long) idx * 600))
                    .regionName("Dev Seed")
                    .captureLevel(captureLevels[r])
                    .killsBeforeCapture(killsBefore[r])
                    .playerName("Dev")
                    .shiny(shiny)
                    .build();

                collection.addCapture(c);
                db.insertCapture(c);
                idx++;
            }
            rng.setSeed(name.hashCode());
            collection.killCounts.put(name, killsBefore[5] + rng.nextInt(200));
            db.upsertKillCount(name, collection.killCounts.get(name));
        }

        // Set Bestiary level to 99 and grant demo credits so the Shop has a balance
        progressionState.totalXp = 13_034_431L;
        collection.credits       = 25_000L;
        saveMetadata();
        log.info("Dev seed complete: {} captures across {} monsters",
            collection.totalCaptures(), MonsterRoster.ROSTER.size());
    }

    private static int combatLevelForSeed(String npcName) {
        switch (MonsterRoster.getDifficulty(npcName, -1)) {
            case BEGINNER: return 5;
            case EASY:     return 30;
            case MEDIUM:   return 80;
            case HARD:     return 150;
            case ELITE:    return 250;
            default:       return 400; // BOSS
        }
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private void saveMetadata() {
        db.setMetadata(META_CREDITS,  String.valueOf(collection.credits));
        db.setMetadata(META_TOTAL_XP, String.valueOf(progressionState.totalXp));
        String achievements = progressionState.unlockedAchievements.stream()
            .map(Enum::name)
            .collect(Collectors.joining(","));
        db.setMetadata(META_ACHIEVEMENTS, achievements);
    }

    private EnumSet<Achievement> loadAchievements() {
        EnumSet<Achievement> set = EnumSet.noneOf(Achievement.class);
        String raw = db.getMetadata(META_ACHIEVEMENTS, "");
        if (!raw.isEmpty()) {
            for (String name : raw.split(",")) {
                try {
                    set.add(Achievement.valueOf(name.trim()));
                } catch (IllegalArgumentException ignored) {
                    log.warn("Unknown achievement in DB: {}", name);
                }
            }
        }
        return set;
    }

    private static long parseLong(String s, long defaultValue) {
        try { return Long.parseLong(s); }
        catch (NumberFormatException e) { return defaultValue; }
    }
}
