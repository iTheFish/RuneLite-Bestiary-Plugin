package net.runelite.client.plugins.bestiary.service;

import net.runelite.client.plugins.bestiary.model.Achievement;
import net.runelite.client.plugins.bestiary.model.BestiaryCollection;
import net.runelite.client.plugins.bestiary.model.CapturedCreature;
import net.runelite.client.plugins.bestiary.model.CreatureRarity;
import net.runelite.client.plugins.bestiary.util.XpTable;
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

    @Inject
    public ProgressionService() {
        this.state      = new ProgressionState();
        this.collection = new BestiaryCollection();
    }

    /** Called by BestiaryDataService after loading persisted data. */
    public void init(ProgressionState state, BestiaryCollection collection) {
        this.state      = state;
        this.collection = collection;
    }

    // --- XP ---

    /**
     * Awards kill XP: {@code max(10, combatLevel * 10)}.
     * Returns the newly unlocked level if a level-up occurred, or 0.
     */
    public int recordKill(NPC npc) {
        int combatLevel = Math.max(1, npc.getCombatLevel());
        long xp         = Math.max(10L, (long) combatLevel * 10);
        return addXp(xp);
    }

    /**
     * Awards capture XP: kill XP \u00c3\u2014 rarity multiplier.
     * Returns any newly unlocked achievements (may be empty).
     */
    public List<Achievement> recordCapture(CapturedCreature creature, boolean awardXp) {
        if (awardXp) {
            int combatLevel = Math.max(1, creature.npcCombatLevel);
            long killXp     = Math.max(10L, (long) combatLevel * 10);
            long captureXp  = Math.round(killXp * creature.rarity.xpMultiplier);
            addXp(captureXp);
        }
        return checkNewAchievements(creature);
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
                state.unlockedAchievements.add(a);
                newly.add(a);
                log.info("Achievement unlocked: {}", a.title);
            }
        }
        return newly;
    }

    /** Check kill-count achievements after incrementing kill count. Call on client thread. */
    public List<Achievement> checkKillAchievements() {
        int totalKills = collection.totalKills();
        List<Achievement> newly = new ArrayList<>();
        for (Achievement a : new Achievement[]{Achievement.FIVE_HUNDRED_KILLS, Achievement.FIVE_K_KILLS}) {
            if (state.unlockedAchievements.contains(a)) continue;
            boolean unlock = (a == Achievement.FIVE_HUNDRED_KILLS && totalKills >= 500)
                          || (a == Achievement.FIVE_K_KILLS && totalKills >= 5000);
            if (unlock) {
                state.unlockedAchievements.add(a);
                newly.add(a);
                log.info("Achievement unlocked: {}", a.title);
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

            case FIVE_HUNDRED_KILLS:
            case FIVE_K_KILLS:
                return false; // handled by checkKillAchievements()

            case LEVEL_10:  return level >= 10;
            case LEVEL_25:  return level >= 25;
            case LEVEL_50:  return level >= 50;
            case LEVEL_75:  return level >= 75;
            case LEVEL_100: return level >= 100;

            default: return false;
        }
    }

    /** Adds XP and returns the new level if a level-up occurred, otherwise 0. */
    private int addXp(long xp) {
        int before       = getLevel();
        state.totalXp   += xp;
        int after        = getLevel();
        return after > before ? after : 0;
    }
}

