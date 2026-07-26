package com.bestiary.service;

import com.bestiary.BestiaryConfig;
import com.bestiary.model.CapturedCreature;
import com.bestiary.model.CreatureQuality;
import com.bestiary.model.CreatureRarity;
import com.bestiary.model.DevCaptureMode;
import com.bestiary.model.DevRarityOverride;
import com.bestiary.model.DifficultyTier;
import com.bestiary.model.MonsterRoster;
import com.bestiary.model.CombatClass;
import com.bestiary.util.RarityRoller;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.Optional;
import java.util.Random;

/**
 * Pure capture logic \u00e2\u20ac" no RuneLite event handling, no UI, no I/O.
 * Depends only on the config and a Random instance, so it is easy to unit test.
 */
@Slf4j
@Singleton
public class CaptureService {

    private final BestiaryConfig config;
    private final Random rng;
    /** Developer-only testing overrides (Force Rarity / Capture Rate / Shiny) are honoured only in dev. */
    private final boolean developerMode;

    @Inject
    public CaptureService(BestiaryConfig config, @Named("developerMode") boolean developerMode) {
        this.config        = config;
        this.developerMode = developerMode;
        this.rng           = new Random();
    }

    /** Constructor for tests where a seeded RNG is required (developer overrides enabled). */
    CaptureService(BestiaryConfig config, Random rng) {
        this.config        = config;
        this.developerMode = true;
        this.rng           = rng;
    }

    /**
     * Attempt to capture an NPC after a confirmed kill.
     *
     * @param npc           the NPC that was killed
     * @param location      where the kill happened
     * @param captureLevel  the player's current Capture Level
     * @param killCount     how many times this species has been killed before (used as metadata)
     * @param regionName    human-readable area name
     * @param playerName    the logged-in player's name
     * @return a populated {@link CapturedCreature} on success, or empty on failure
     */
    public Optional<CapturedCreature> attemptCapture(NPC npc, WorldPoint location,
                                                     int captureLevel, int killCount,
                                                     String regionName, String playerName,
                                                     int observedDamage) {
        if (!config.captureEnabled()) {
            return Optional.empty();
        }

        String npcName = npc.getName() != null ? npc.getName() : "Unknown";
        DifficultyTier difficulty = MonsterRoster.getDifficulty(npcName, npc.getCombatLevel());

        // Dev testing overrides only apply in developer mode — live users always get the real rate.
        DevCaptureMode devMode = developerMode ? config.devCaptureMode() : DevCaptureMode.NORMAL;
        double catchRate = devMode == DevCaptureMode.FORCE_0   ? 0.0
                        : devMode == DevCaptureMode.FORCE_100 ? 1.0
                        : calculateCatchRate(captureLevel, difficulty);
        double roll = rng.nextDouble();

        log.debug("Capture roll for {} [{}]: roll={} catchRate={}", npcName, difficulty.label,
                String.format("%.3f", roll), String.format("%.3f", catchRate));

        if (roll >= catchRate) {
            return Optional.empty();
        }

        DevRarityOverride forceRarity = developerMode ? config.devForceRarity() : DevRarityOverride.NONE;
        CreatureRarity rarity = (forceRarity != null && forceRarity != DevRarityOverride.NONE)
                ? CreatureRarity.fromLabel(forceRarity.name())
                : RarityRoller.roll(rng, captureLevel);
        // Independent shiny roll — orthogonal to rarity. Base 0.2% at Bestiary level 1,
        // scaling linearly to 2% at level 99. Future shop unlocks / passives can multiply this.
        boolean shiny = (developerMode && config.devForceShiny()) || rng.nextDouble() < shinyChance(captureLevel);

        CombatClass combatClass = MonsterRoster.getCombatClass(npcName, npc.getCombatLevel());
        int[] statBases = MonsterRoster.getStatBases(npcName, npc.getCombatLevel());
        CreatureQuality quality = RarityRoller.generateQuality(combatClass, rarity, statBases, rng, shiny);
        int prayer = RarityRoller.rollPrayer(MonsterRoster.getPrayer(npcName), rarity, rng, shiny);

        CapturedCreature creature = CapturedCreature.builder()
                .shiny(shiny)
                .prayer(prayer)
                .observedHp(observedDamage)
                .npcId(npc.getId())
                .npcName(npcName)
                .npcCombatLevel(npc.getCombatLevel())
                .rarity(rarity)
                .quality(quality)
                .captureTime(Instant.now())
                .regionName(regionName)
                .captureLevel(captureLevel)
                .killsBeforeCapture(killCount)
                .playerName(playerName != null ? playerName : "")
                .build();

        log.info("Captured {} [{}] difficulty={}", creature.npcName, creature.rarity.label, difficulty.label);
        return Optional.of(creature);
    }

    /**
     * Catch rate by difficulty tier and capture level.
     *
     * Each tier has a base rate (level 1), a per-level bonus, and a cap (level 99+):
     *   BEGINNER: 20% base, +0.50%/level, 60% cap
     *   EASY:     15% base, +0.40%/level, 50% cap
     *   MEDIUM:   10% base, +0.30%/level, 40% cap
     *   HARD:      6% base, +0.22%/level, 28% cap
     *   ELITE:     3% base, +0.13%/level, 15% cap
     *   BOSS:    1.5% base, +0.07%/level,  8% cap
     */
    public static double calculateCatchRate(int captureLevel, DifficultyTier difficulty) {
        double base, perLevel, cap;
        switch (difficulty) {
            case BEGINNER: base = 0.20; perLevel = 0.0050; cap = 0.60; break;
            case EASY:     base = 0.15; perLevel = 0.0040; cap = 0.50; break;
            case MEDIUM:   base = 0.10; perLevel = 0.0030; cap = 0.40; break;
            case HARD:     base = 0.06; perLevel = 0.0022; cap = 0.28; break;
            case ELITE:    base = 0.03; perLevel = 0.0013; cap = 0.15; break;
            case BOSS:     base = 0.015; perLevel = 0.0007; cap = 0.08; break;
            default:       base = 0.10; perLevel = 0.0030; cap = 0.40; break;
        }
        return Math.min(base + (captureLevel - 1) * perLevel, cap);
    }

    /**
     * Independent shiny chance, scaled by Bestiary level.
     * 0.2% at level 1 → 2% at level 99 (linear). Rarity does not affect this.
     * Static so the dev seed can roll shinies the same way as live captures.
     */
    public static double shinyChance(int captureLevel) {
        double t = Math.max(0, Math.min(98, captureLevel - 1)) / 98.0;
        return 0.002 + t * (0.02 - 0.002);
    }
}

