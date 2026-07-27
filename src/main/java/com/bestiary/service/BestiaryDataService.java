package com.bestiary.service;

import com.bestiary.model.Achievement;
import com.bestiary.model.BestiaryCollection;
import com.bestiary.model.CapturedCreature;
import com.bestiary.model.CreatureQuality;
import com.bestiary.model.CreatureRarity;
import com.bestiary.model.MonsterRoster;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Loads and saves the bestiary collection and progression state as JSON (via
 * {@link BestiaryStore}). The whole collection lives in memory here; mutations update it
 * and then persist a snapshot — debounced for high-frequency events (kills, credits),
 * immediate for valuable/rare ones (captures, rerolls, discards) and lifecycle events.
 */
@Slf4j
@Singleton
public class BestiaryDataService {

    private final ProgressionService progressionService;
    private final BestiaryStore store;

    @Getter
    private BestiaryCollection collection = new BestiaryCollection();
    private ProgressionService.ProgressionState progressionState = new ProgressionService.ProgressionState();

    @Inject
    public BestiaryDataService(ProgressionService progressionService, BestiaryStore store) {
        this.progressionService = progressionService;
        this.store = store;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /** Must be called from the client thread during plugin startUp. */
    public void load() {
        BestiaryStore.StoreData d = store.load();
        collection = new BestiaryCollection();
        for (CapturedCreature c : d.captures) {
            collection.creatures.add(c);
            collection.captureCountByNpc.merge(c.npcName, 1, Integer::sum);
        }
        collection.killCounts   = new HashMap<>(d.killCounts);
        collection.credits      = d.credits;
        collection.shopUpgrades = new HashMap<>(d.shopUpgrades);

        progressionState = new ProgressionService.ProgressionState();
        progressionState.totalXp = d.totalXp;
        progressionState.unlockedAchievements = parseAchievements(d.achievements);

        progressionService.init(progressionState, collection);
        log.info("Bestiary loaded: {} captures", collection.totalCaptures());
    }

    /** Flushes the current state to disk immediately. Called from UI callbacks after edits. */
    public void saveNow() {
        persistNow();
    }

    /** Debounced save. Kept for API compatibility with callers that don't need an immediate flush. */
    public void scheduleSave() {
        persist();
    }

    /** Permanently deletes all data and resets in-memory state. */
    public void wipeCollection() {
        collection       = new BestiaryCollection();
        progressionState = new ProgressionService.ProgressionState();
        progressionService.init(progressionState, collection);
        persistNow();
        log.info("Bestiary collection wiped");
    }

    /** Flushes and stops the store writer. Call from plugin shutDown. */
    public void shutdown() {
        persistNow();
        store.close();
    }

    // -------------------------------------------------------------------------
    // Mutators (call from client thread)
    // -------------------------------------------------------------------------

    public void addCapture(CapturedCreature c) {
        collection.addCapture(c);
        persistNow();   // a capture is rare + valuable — write immediately
    }

    public void incrementKillCount(String npcName) {
        collection.incrementKillCount(npcName);
        persist();      // high-frequency — debounce
    }

    /** Persists mutable fields of a single creature (favourite/nickname/etc. already changed in memory). */
    public void updateCapture(CapturedCreature c) {
        persist();
    }

    public ProgressionService.ProgressionState getProgressionState() {
        return progressionState;
    }

    public long getCredits() {
        return collection.credits;
    }

    public void awardCredits(long amount) {
        collection.credits += amount;
        persist();
    }

    /** Credits a card is worth if discarded. */
    public long discardValue(CapturedCreature c) {
        return com.bestiary.util.CreditCalculator.forDiscard(
                com.bestiary.model.MonsterRoster.getDifficulty(c.npcName, c.npcCombatLevel),
                c.rarity, c.isShiny());
    }

    /**
     * Discards a card: removes it, credits its discard value, persists. Returns credits
     * awarded, or 0 if the card was already gone (guards against double-discard exploits).
     */
    public long discardCapture(CapturedCreature c) {
        if (!collection.removeCapture(c)) return 0;   // already discarded — award nothing
        long credits = discardValue(c);
        collection.credits += credits;
        persistNow();
        return credits;
    }

    /** Discards several cards at once. Returns total credits awarded (only for cards present). */
    public long discardCaptures(java.util.Collection<CapturedCreature> cards) {
        long total = 0;
        for (CapturedCreature c : cards) {
            if (!collection.removeCapture(c)) continue;
            total += discardValue(c);
        }
        collection.credits += total;
        persistNow();
        return total;
    }

    public void saveProgressionState() {
        persist();
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
    private static long rerollBaseCost(com.bestiary.model.DifficultyTier d) {
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
        persist();
        return true;
    }

    // --- Passive shop upgrades (#39) ---

    /** Tiers currently owned of a passive upgrade. */
    public int getUpgradeTier(com.bestiary.model.ShopUpgrade u) {
        return collection.getUpgradeTier(u);
    }

    /** Cost of the next tier, or -1 if the upgrade is already maxed. */
    public long upgradeCost(com.bestiary.model.ShopUpgrade u) {
        int owned = collection.getUpgradeTier(u);
        return owned >= u.maxTier ? -1 : u.costForNextTier(owned);
    }

    /**
     * Buys the next tier of a passive upgrade if it's not maxed and the player can afford it.
     * Returns true on success. Persists immediately (a purchase is rare + valuable).
     */
    public boolean purchaseUpgrade(com.bestiary.model.ShopUpgrade u) {
        int owned = collection.getUpgradeTier(u);
        if (owned >= u.maxTier) return false;
        long cost = u.costForNextTier(owned);
        if (collection.credits < cost) return false;
        collection.credits -= cost;
        collection.shopUpgrades.put(u.name(), owned + 1);
        persistNow();
        return true;
    }

    /** Passive shiny-chance bonus from the Shiny Charm upgrade (added on top of the level-scaled base). */
    public double bonusShinyChance() {
        return com.bestiary.model.ShopUpgrade.SHINY_CHANCE.effectFor(
                collection.getUpgradeTier(com.bestiary.model.ShopUpgrade.SHINY_CHANCE));
    }

    /** Passive reroll rarity-up bonus from the Reroll Fortune upgrade. */
    public double bonusRerollRarityChance() {
        return com.bestiary.model.ShopUpgrade.REROLL_RARITY.effectFor(
                collection.getUpgradeTier(com.bestiary.model.ShopUpgrade.REROLL_RARITY));
    }

    /**
     * Card Reroller (shop POC): for {@link #rerollCost} credits, re-rolls a card's stats,
     * prayer and shiny at the SAME rarity/monster (a chance to improve stats or hit shiny).
     * Keeps id + metadata (favourite/nickname/album-cover/observed HP). Returns the new
     * card, or null if the player can't afford it.
     */
    public CapturedCreature rerollCard(CapturedCreature c, int currentLevel) {
        if (!spendCredits(rerollCost(c))) return null;
        // Non-Mythic cards get a small chance to move up a rarity (raised by the Reroll Fortune shop upgrade).
        CreatureRarity rarity = c.rarity;
        if (rarity != CreatureRarity.MYTHIC
                && rerollRng.nextDouble() < RARITY_UP_CHANCE + bonusRerollRarityChance()) {
            rarity = CreatureRarity.values()[rarity.ordinal() + 1];
        }
        com.bestiary.model.CombatClass cls =
                com.bestiary.model.MonsterRoster.getCombatClass(c.npcName, c.npcCombatLevel);
        int[] bases = com.bestiary.model.MonsterRoster.getStatBases(c.npcName, c.npcCombatLevel);
        // A shiny stays shiny; a non-shiny gets a fresh shiny roll (raised by the Shiny Charm shop upgrade).
        boolean shiny = c.isShiny()
                || rerollRng.nextDouble() < CaptureService.shinyChance(currentLevel) + bonusShinyChance();
        com.bestiary.model.CreatureQuality q =
                com.bestiary.util.RarityRoller.generateQuality(cls, rarity, bases, rerollRng, shiny);
        int prayer = com.bestiary.util.RarityRoller.rollPrayer(
                com.bestiary.model.MonsterRoster.getPrayer(c.npcName), rarity, rerollRng, shiny);
        String reroller = c.playerName != null && !c.playerName.isEmpty() ? c.playerName : "Player";
        // Log the card's pre-reroll state, then carry the whole history forward onto the new card.
        java.util.List<CapturedCreature.RerollState> history = new java.util.ArrayList<>(c.rerollHistory);
        history.add(new CapturedCreature.RerollState(
                c.rarity, c.quality, c.powerLevel(), c.isShiny(), c.prayer, reroller, java.time.Instant.now().getEpochSecond()));
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
        persistNow();
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
            com.bestiary.model.CombatClass combatClass =
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
                CreatureQuality quality = com.bestiary.util.RarityRoller
                    .generateQuality(combatClass, rarity, statBases, rng, shiny);
                int prayer = com.bestiary.util.RarityRoller
                    .rollPrayer(com.bestiary.model.MonsterRoster.getPrayer(name),
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
                idx++;
            }
            rng.setSeed(name.hashCode());
            collection.killCounts.put(name, killsBefore[5] + rng.nextInt(200));
        }

        // Set Bestiary level to 99 and grant demo credits so the Shop has a balance
        progressionState.totalXp = 13_034_431L;
        collection.credits       = 25_000L;
        persistNow();
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

    /** Debounced snapshot write (coalesces high-frequency mutations). */
    private void persist() {
        store.save(snapshot());
    }

    /** Immediate snapshot write. */
    private void persistNow() {
        store.saveNow(snapshot());
    }

    /** Builds a cheap shallow-copy snapshot so the writer thread never races the collection. */
    private BestiaryStore.StoreData snapshot() {
        BestiaryStore.StoreData d = new BestiaryStore.StoreData();
        d.version     = BestiaryStore.VERSION;
        d.captures    = new ArrayList<>(collection.creatures);
        d.killCounts  = new LinkedHashMap<>(collection.killCounts);
        d.credits     = collection.credits;
        d.shopUpgrades = new LinkedHashMap<>(collection.shopUpgrades);
        d.totalXp     = progressionState.totalXp;
        d.achievements = progressionState.unlockedAchievements.stream()
                .map(Enum::name).collect(Collectors.toList());
        return d;
    }

    private EnumSet<Achievement> parseAchievements(List<String> names) {
        EnumSet<Achievement> set = EnumSet.noneOf(Achievement.class);
        if (names != null) {
            for (String name : names) {
                try {
                    set.add(Achievement.valueOf(name.trim()));
                } catch (IllegalArgumentException ignored) {
                    log.warn("Unknown achievement in store: {}", name);
                }
            }
        }
        return set;
    }
}
