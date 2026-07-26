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

    /** Credits a card is worth if discarded. */
    public long discardValue(CapturedCreature c) {
        return net.runelite.client.plugins.bestiary.util.CreditCalculator.forDiscard(
                net.runelite.client.plugins.bestiary.model.MonsterRoster.getDifficulty(c.npcName, c.npcCombatLevel),
                c.rarity, c.isShiny());
    }

    /**
     * Discards a card: removes it, credits its discard value, persists. Returns credits
     * awarded, or 0 if the card was already gone (guards against double-discard exploits).
     */
    public long discardCapture(CapturedCreature c) {
        if (!collection.removeCapture(c)) return 0;   // already discarded — award nothing
        db.deleteCapture(c.id);
        long credits = discardValue(c);
        awardCredits(credits);
        return credits;
    }

    /** Discards several cards at once. Returns total credits awarded (only for cards present). */
    public long discardCaptures(java.util.Collection<CapturedCreature> cards) {
        long total = 0;
        for (CapturedCreature c : cards) {
            if (!collection.removeCapture(c)) continue;
            db.deleteCapture(c.id);
            total += discardValue(c);
        }
        awardCredits(total);
        return total;
    }

    public void saveProgressionState() {
        saveMetadata();
    }

    // -------------------------------------------------------------------------
    // Shop (POC)
    // -------------------------------------------------------------------------

    /**
     * Cost of one "Card Reroller" use, scaled by the card's difficulty tier × rarity
     * (shiny does NOT affect cost). Difficulty sets a base (= a Common card's cost) and
     * rarity multiplies it, so rerolling a high-tier rare card costs far more than a
     * beginner common. Anchors: Beginner Common 25 → Mythic 500; Boss Common 200 → Mythic 4000.
     */
    public static long rerollCost(CapturedCreature c) {
        return rerollBaseCost(MonsterRoster.getDifficulty(c.npcName, c.npcCombatLevel))
                * rerollRarityMultiplier(c.rarity);
    }

    /** Per-difficulty base cost — the price of rerolling a Common card of that tier. */
    private static long rerollBaseCost(net.runelite.client.plugins.bestiary.model.DifficultyTier d) {
        switch (d) {
            case BEGINNER: return 25;
            case EASY:     return 40;
            case MEDIUM:   return 60;
            case HARD:     return 90;
            case ELITE:    return 130;
            case BOSS:     return 200;
            default:       return 60;
        }
    }

    /** Rarity multiplier applied to the difficulty base (Common 1× → Mythic 20×). */
    private static long rerollRarityMultiplier(CreatureRarity r) {
        switch (r) {
            case COMMON:    return 1;
            case UNCOMMON:  return 2;
            case RARE:      return 4;
            case EPIC:      return 7;
            case LEGENDARY: return 12;
            case MYTHIC:    return 20;
            default:        return 1;
        }
    }

    /** Base chance a non-Mythic reroll bumps up one rarity (raised later by shop unlocks). */
    public static final double RARITY_UP_CHANCE = 0.05;

    private final Random rerollRng = new Random();

    /** Deducts credits if affordable; persists. Returns false if too poor. */
    public boolean spendCredits(long amount) {
        if (collection.credits < amount) return false;
        collection.credits -= amount;
        db.setMetadata(META_CREDITS, String.valueOf(collection.credits));
        return true;
    }

    /**
     * Card Reroller (shop POC): for {@link #rerollCost} credits, re-rolls a card's stats,
     * prayer and shiny at the SAME rarity/monster (a chance to improve stats or hit shiny).
     * Keeps id + metadata (favourite/nickname/album-cover/observed HP). Returns the new
     * card, or null if the player can't afford it.
     */
    public CapturedCreature rerollCard(CapturedCreature c, int currentLevel) {
        if (!spendCredits(rerollCost(c))) return null;
        // Non-Mythic cards get a small chance to move up a rarity.
        CreatureRarity rarity = c.rarity;
        if (rarity != CreatureRarity.MYTHIC && rerollRng.nextDouble() < RARITY_UP_CHANCE) {
            rarity = CreatureRarity.values()[rarity.ordinal() + 1];
        }
        net.runelite.client.plugins.bestiary.model.CombatClass cls =
                net.runelite.client.plugins.bestiary.model.MonsterRoster.getCombatClass(c.npcName, c.npcCombatLevel);
        int[] bases = net.runelite.client.plugins.bestiary.model.MonsterRoster.getStatBases(c.npcName, c.npcCombatLevel);
        // A shiny stays shiny; a non-shiny gets a fresh shiny roll.
        boolean shiny = c.isShiny() || rerollRng.nextDouble() < CaptureService.shinyChance(currentLevel);
        net.runelite.client.plugins.bestiary.model.CreatureQuality q =
                net.runelite.client.plugins.bestiary.util.RarityRoller.generateQuality(cls, rarity, bases, rerollRng, shiny);
        int prayer = net.runelite.client.plugins.bestiary.util.RarityRoller.rollPrayer(
                net.runelite.client.plugins.bestiary.model.MonsterRoster.getPrayer(c.npcName), rarity, rerollRng, shiny);
        String reroller = c.playerName != null && !c.playerName.isEmpty() ? c.playerName : "Player";
        // Log the card's pre-reroll state, then carry the whole history forward onto the new card.
        java.util.List<CapturedCreature.RerollState> history = new java.util.ArrayList<>(c.rerollHistory);
        history.add(new CapturedCreature.RerollState(
                c.rarity, c.powerLevel(), c.isShiny(), c.prayer, reroller, java.time.Instant.now().getEpochSecond()));
        CapturedCreature nc = CapturedCreature.builder()
                .id(c.id).npcId(c.npcId).npcName(c.npcName).npcCombatLevel(c.npcCombatLevel)
                .rarity(rarity).quality(q).captureTime(c.captureTime).regionName(c.regionName)
                .captureLevel(currentLevel)   // reroll happened now → odds reflect the current level
                .killsBeforeCapture(c.killsBeforeCapture)
                .playerName(c.playerName).shiny(shiny).prayer(prayer).observedHp(c.observedHp)
                .rerolledBy(reroller)
                .rerollHistory(history)
                .build();
        nc.favourite  = c.favourite;
        nc.nickname   = c.nickname;
        nc.albumCover = c.albumCover;
        // Replace in place (by id) so it can't leave a stale/duplicate copy behind.
        if (!collection.replaceCapture(nc)) collection.addCapture(nc);
        db.deleteCapture(c.id);
        db.insertCapture(nc);
        return nc;
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

                int[] statBases = MonsterRoster.getStatBases(name, combatLevel);
                // Roll shiny the same way live captures do: independent roll scaled by the
                // card's capture level, then generate quality with the shiny flag applied.
                // Dev-seed only: 3x boost so the test collection shows ~20 shinies for visual
                // testing (live captures use the real 0.2%-2% rate).
                rng.setSeed((long) name.hashCode() * 31 + r);
                boolean shiny = rng.nextDouble() < CaptureService.shinyChance(captureLevels[r]) * 3.0;
                CreatureQuality quality = net.runelite.client.plugins.bestiary.util.RarityRoller
                    .generateQuality(combatClass, rarity, statBases, rng, shiny);
                int prayer = net.runelite.client.plugins.bestiary.util.RarityRoller
                    .rollPrayer(net.runelite.client.plugins.bestiary.model.MonsterRoster.getPrayer(name),
                            rarity, rng, shiny);

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
                    .prayer(prayer)
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
