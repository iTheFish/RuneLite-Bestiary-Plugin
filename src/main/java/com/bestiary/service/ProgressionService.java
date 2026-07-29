package com.bestiary.service;

import com.bestiary.model.Achievement;
import com.bestiary.model.BestiaryCollection;
import com.bestiary.model.CapturedCreature;
import com.bestiary.model.CreatureRarity;
import com.bestiary.model.DifficultyTier;
import com.bestiary.model.MonsterRoster;
import com.bestiary.util.XpTable;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.NPC;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Manages the Capture Level (1\u00e2\u20ac"100), XP accumulation, and achievement unlocks.
 * State is held in {@link ProgressionState} and persisted by {@link BestiaryDataService}.
 */
@Slf4j
@Singleton
public class ProgressionService {

    /** Mutable progression state \u00e2\u20ac" serialised independently of the collection. */
    public static class ProgressionState {
        public long totalXp = 0;
        public Set<Achievement> unlockedAchievements = EnumSet.noneOf(Achievement.class);
    }

    private ProgressionState state;
    private BestiaryCollection collection;

    /** Grants Bestiary Credits (level-up bounties + achievement rewards). Wired by BestiaryDataService. */
    private java.util.function.LongConsumer creditAwarder = amount -> {};

    /** Credits granted per level gained: {@code level * 10} (reach Lv2 -> 20, Lv99 -> 990). */
    public static long levelUpCredits(int level) {
        return level * 10L;
    }

    @Inject
    public ProgressionService() {
        this.state      = new ProgressionState();
        this.collection = new BestiaryCollection();
    }

    /** Sets the sink used to grant credits for level-ups and achievement unlocks. */
    public void setCreditAwarder(java.util.function.LongConsumer awarder) {
        this.creditAwarder = awarder != null ? awarder : amount -> {};
    }

    /** Called by BestiaryDataService after loading persisted data. */
    public void init(ProgressionState state, BestiaryCollection collection) {
        this.state      = state;
        this.collection = collection;
    }

    // --- XP ---

    /**
     * Awards Bestiary XP for a kill, scaled by the monster's difficulty tier
     * (Beginner 5 → Boss 30). Returns the newly unlocked level if a level-up occurred, or 0.
     */
    public int recordKill(NPC npc) {
        String name = npc.getName() != null ? npc.getName() : "";
        DifficultyTier tier = MonsterRoster.getDifficulty(name, npc.getCombatLevel());
        return addXp(killXp(tier));
    }

    /** Bestiary kill XP by difficulty tier: Beginner 5, Easy 10, Medium 15, Hard 20, Elite 25, Boss 30. */
    public static long killXp(DifficultyTier tier) {
        switch (tier) {
            case BEGINNER: return 5;
            case EASY:     return 10;
            case MEDIUM:   return 15;
            case HARD:     return 20;
            case ELITE:    return 25;
            case BOSS:     return 30;
            default:       return 10;
        }
    }

    /**
     * Awards capture XP: kill XP \u00c3\u2014 rarity multiplier.
     * Returns any newly unlocked achievements (may be empty).
     */
    public List<Achievement> recordCapture(CapturedCreature creature, boolean awardXp) {
        if (awardXp) {
            addXp(captureXp(creature.npcCombatLevel, creature.rarity));
        }
        return checkNewAchievements(creature);
    }

    /**
     * Capture XP = base × rarity multiplier, where base = max(10, combatLevel × 10) with the
     * combat level CAPPED at 100. The cap stops very-high-level monsters (which exist in droves)
     * from awarding runaway XP: nothing exceeds the combat-100 scale
     * (Common 1,000 → Mythic 50,000).
     */
    public static long captureXp(int npcCombatLevel, CreatureRarity rarity) {
        int combatLevel = Math.max(1, Math.min(100, npcCombatLevel));
        long base       = Math.max(10L, (long) combatLevel * 10);
        return Math.round(base * rarity.xpMultiplier);
    }

    /**
     * Awards arbitrary XP (kill or capture XP, including shop boosts already applied by the
     * caller) and returns the new level if a level-up occurred, otherwise 0.
     */
    public int awardXp(long xp) {
        return addXp(xp);
    }

    /** Current Capture Level (1\u00e2\u20ac"100). */
    public int getLevel() {
        return XpTable.levelForXp(state.totalXp);
    }

    /** Total accumulated XP. */
    public long getTotalXp() {
        return state.totalXp;
    }

    /** XP needed to advance from current total to the next level (0 at level 100). */
    public long getXpToNextLevel() {
        return XpTable.xpToNextLevel(state.totalXp);
    }

    public ProgressionState getState() {
        return state;
    }

    // --- Achievements ---

    /**
     * Checks all achievements against the current state and collection.
     * Returns newly unlocked achievements so the plugin can notify the player.
     */
    public List<Achievement> checkNewAchievements(CapturedCreature latestCapture) {
        List<Achievement> newly = new ArrayList<>();
        int level      = getLevel();
        int totalCaps  = collection.totalCaptures();
        int species    = (int) collection.uniqueSpeciesCount();

        for (Achievement a : Achievement.values()) {
            if (state.unlockedAchievements.contains(a)) {
                continue;
            }
            if (isUnlocked(a, latestCapture, totalCaps, species, level)) {
                unlock(a, newly);
            }
        }
        return newly;
    }

    /** Marks an achievement unlocked, grants its credit reward, and records it for notification. */
    private void unlock(Achievement a, List<Achievement> newly) {
        state.unlockedAchievements.add(a);
        newly.add(a);
        if (a.creditReward > 0) creditAwarder.accept(a.creditReward);
        log.info("Achievement unlocked: {} (+{} credits)", a.title, a.creditReward);
    }

    /** Check kill-count achievements after incrementing kill count. Call on client thread. */
    public List<Achievement> checkKillAchievements() {
        int totalKills = collection.totalKills();
        List<Achievement> newly = new ArrayList<>();
        for (Achievement a : new Achievement[]{Achievement.FIVE_HUNDRED_KILLS, Achievement.FIVE_K_KILLS}) {
            if (state.unlockedAchievements.contains(a)) continue;
            boolean reached = (a == Achievement.FIVE_HUNDRED_KILLS && totalKills >= 500)
                          || (a == Achievement.FIVE_K_KILLS && totalKills >= 5000);
            if (reached) {
                unlock(a, newly);
            }
        }
        return newly;
    }

    private boolean isUnlocked(Achievement a, CapturedCreature capture,
                                int totalCaps, int species, int level) {
        switch (a) {
            case FIRST_CATCH:
            case TEN_CATCHES:
            case FIFTY_CATCHES:
            case HUNDRED_CATCHES:
            case TWO_FIFTY_CATCHES:
            case FIVE_HUNDRED_CATCHES:
            case THOUSAND_CATCHES:
                return !a.isSpeciesBased && totalCaps >= a.countThreshold;

            case FIVE_SPECIES:
            case TWENTY_SPECIES:
            case FIFTY_SPECIES:
                return species >= a.countThreshold;

            case UNCOMMON_CATCH:
                return capture != null && capture.rarity.ordinal() >= CreatureRarity.UNCOMMON.ordinal();
            case RARE_CATCH:
                return capture != null && capture.rarity.ordinal() >= CreatureRarity.RARE.ordinal();
            case EPIC_CATCH:
                return capture != null && capture.rarity.ordinal() >= CreatureRarity.EPIC.ordinal();
            case LEGENDARY_CATCH:
                return capture != null && (capture.rarity == CreatureRarity.LEGENDARY
                        || capture.rarity == CreatureRarity.MYTHIC);
            case MYTHIC_CATCH:
                return capture != null && capture.rarity == CreatureRarity.MYTHIC;
            case SHINY_CATCH:
                return capture != null && capture.isShiny();

            case FIVE_HUNDRED_KILLS:
            case FIVE_K_KILLS:
                return false; // handled by checkKillAchievements()

            case LEVEL_5:   return level >= 5;
            case LEVEL_10:  return level >= 10;
            case LEVEL_25:  return level >= 25;
            case LEVEL_30:  return level >= 30;
            case LEVEL_40:  return level >= 40;
            case LEVEL_50:  return level >= 50;
            case LEVEL_60:  return level >= 60;
            case LEVEL_70:  return level >= 70;
            case LEVEL_75:  return level >= 75;
            case LEVEL_80:  return level >= 80;
            case LEVEL_85:  return level >= 85;
            case LEVEL_90:  return level >= 90;
            case LEVEL_92:  return level >= 92;
            case LEVEL_95:  return level >= 95;
            case LEVEL_99:  return level >= 99;

            // Credits earned / spent (lifetime)
            case EARN_1K: case EARN_5K: case EARN_10K: case EARN_50K: case EARN_100K:
                return collection.lifetimeCreditsEarned >= a.countThreshold;
            case SPEND_1K: case SPEND_5K: case SPEND_10K: case SPEND_50K: case SPEND_100K:
                return collection.lifetimeCreditsSpent >= a.countThreshold;

            // Rerolls
            case REROLL_FIRST: case REROLL_5: case REROLL_10: case REROLL_25: case REROLL_50:
                return collection.totalRerolls() >= a.countThreshold;
            case REROLL_RANK_UP:
                return collection.hasRerollRankUp();

            // Collection milestones
            case POWERHOUSE:
                return collection.maxPowerLevel() >= 150;
            case FULL_HOUSE:
                return collection.hasFullRaritySet();
            case CHROMATIC:
                return collection.shinyCount() >= a.countThreshold;
            case COMPLETIONIST:
                return collection.isAlbumComplete();
            case CURATED:
                return collection.hasFavourite();

            default: return false;
        }
    }

    /** Adds XP (capped at the 200M max) and returns the new level if a level-up occurred, otherwise 0. */
    private int addXp(long xp) {
        int before       = getLevel();
        state.totalXp    = Math.min(XpTable.maxXp(), state.totalXp + xp);
        int after        = getLevel();
        // Grant a per-level credit bounty for every level gained (handles multi-level jumps).
        for (int lvl = before + 1; lvl <= after; lvl++) {
            creditAwarder.accept(levelUpCredits(lvl));
        }
        return after > before ? after : 0;
    }
}

