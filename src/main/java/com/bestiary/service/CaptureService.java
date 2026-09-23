package com.bestiary.service;

import com.bestiary.model.CapturedCreature;
import com.bestiary.model.CreatureQuality;
import com.bestiary.model.CreatureRarity;
import com.bestiary.model.DifficultyTier;
import com.bestiary.model.MonsterRoster;
import com.bestiary.model.CombatClass;
import com.bestiary.util.RarityRoller;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.Optional;
import java.util.Random;

/**
 * Pure capture logic \u00e2\u20ac" no RuneLite event handling, no UI, no I/O.
 * Depends only on a Random instance, so it is easy to unit test.
 */
@Slf4j
@Singleton
public class CaptureService {

    private final Random rng;

    @Inject
    public CaptureService() {
        this.rng = new Random();
    }

    /** Constructor for tests where a seeded RNG is required. */
    CaptureService(Random rng) {
        this.rng = rng;
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
                                                     int observedDamage, double shinyBonus,
                                                     double rarityUpChance, double doubleRollChance) {
        // First full capture attempt (catch/miss roll + rarity + shiny + quality).
        Optional<CapturedCreature> first = singleAttempt(npc, location, captureLevel, killCount,
                regionName, playerName, observedDamage, shinyBonus, rarityUpChance);

        // Keen Instinct (shop): a chance to run the WHOLE attempt a second time — including the
        // catch/miss roll — and keep the better outcome (a capture beats a miss; between two captures
        // the higher rarity, then Power Level, wins). Only when owned (doubleRollChance > 0); the
        // short-circuit means an unowned upgrade consumes no RNG, so the roll sequence stays stable.
        if (doubleRollChance > 0 && rng.nextDouble() < doubleRollChance) {
            Optional<CapturedCreature> second = singleAttempt(npc, location, captureLevel, killCount,
                    regionName, playerName, observedDamage, shinyBonus, rarityUpChance);
            return keenKeepBetter(first, second);
        }
        return first;
    }

    /** One independent capture attempt: rolls catch/miss, then rarity + Fortune's Favour, shiny, quality. */
    private Optional<CapturedCreature> singleAttempt(NPC npc, WorldPoint location,
                                                     int captureLevel, int killCount,
                                                     String regionName, String playerName,
                                                     int observedDamage, double shinyBonus,
                                                     double rarityUpChance) {
        String npcName = npc.getName() != null ? npc.getName() : "Unknown";
        DifficultyTier difficulty = MonsterRoster.getDifficulty(npcName, npc.getCombatLevel());

        double catchRate = calculateCatchRate(captureLevel, difficulty);
        double roll = rng.nextDouble();

        log.debug("Capture roll for {} [{}]: roll={} catchRate={}", npcName, difficulty.label,
                String.format("%.3f", roll), String.format("%.3f", catchRate));

        if (roll >= catchRate) {
            return Optional.empty();
        }

        CreatureRarity rarity = RarityRoller.roll(rng, captureLevel);
        // Fortune's Favour (shop): a chance to climb one rarity higher than the roll landed. Only
        // happens if the player owns the upgrade (rarityUpChance > 0); Mythic can't climb further.
        boolean fortuneBumped = false;
        if (rarity != CreatureRarity.MYTHIC && rarityUpChance > 0
                && rng.nextDouble() < rarityUpChance) {
            rarity = CreatureRarity.values()[rarity.ordinal() + 1];
            fortuneBumped = true;
        }
        // Independent shiny roll — orthogonal to rarity. Base 0.2% at Bestiary level 1,
        // scaling linearly to 2% at level 99. Future shop unlocks / passives can multiply this.
        boolean shiny = rng.nextDouble() < shinyChance(captureLevel) + shinyBonus;

        CombatClass combatClass = MonsterRoster.getCombatClass(npcName, npc.getCombatLevel());
        int[] statBases = MonsterRoster.getStatBases(npcName, npc.getCombatLevel());
        int prayerBase  = MonsterRoster.getPrayer(npcName);
        CreatureQuality quality = RarityRoller.generateQuality(combatClass, rarity, statBases, prayerBase, rng, shiny);

        CapturedCreature creature = CapturedCreature.builder()
                .shiny(shiny)
                .shinyBonus(shinyBonus)
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
        creature.fortuneBumped = fortuneBumped;

        log.info("Captured {} [{}] difficulty={}{}", creature.npcName, creature.rarity.label,
                difficulty.label, fortuneBumped ? " (Fortune's Favour bumped)" : "");
        return Optional.of(creature);
    }

    /**
     * Keeps the better of two Keen Instinct capture attempts. Ranking: a capture beats a miss; between
     * two captures the higher rarity wins, then the higher Power Level. When the SECOND attempt is the
     * one kept (i.e. Keen Instinct actually improved on the first roll) and it either rescued a would-be
     * miss or landed a strictly higher rarity, the kept card is tagged so the plugin announces the proc.
     */
    private Optional<CapturedCreature> keenKeepBetter(Optional<CapturedCreature> first,
                                                      Optional<CapturedCreature> second) {
        boolean secondBetter;
        if (!second.isPresent()) {
            secondBetter = false;
        } else if (!first.isPresent()) {
            secondBetter = true;
        } else {
            int cmp = Integer.compare(second.get().rarity.ordinal(), first.get().rarity.ordinal());
            if (cmp == 0) cmp = Integer.compare(second.get().powerLevel(), first.get().powerLevel());
            secondBetter = cmp > 0;
        }

        if (secondBetter) {
            CapturedCreature kept = second.get();
            boolean firstMiss = !first.isPresent();
            // Only a rescued miss or a genuine rarity upgrade is worth a chat shout (a mere Power Level
            // tiebreak win is kept silently).
            if (firstMiss || kept.rarity.ordinal() > first.get().rarity.ordinal()) {
                kept.keenInstinctKept = kept.rarity;
                kept.keenInstinctFrom = firstMiss ? null : first.get().rarity;   // null = rescued a miss
            }
            return second;
        }
        return first;
    }

    /**
     * Catch rate by difficulty tier and capture level. Scales linearly from the level-1 base to the
     * level-99 max (base → max):
     *   BEGINNER: 25% → 70%
     *   EASY:     20% → 65%
     *   MEDIUM:   15% → 55%
     *   HARD:     10% → 50%
     *   ELITE:     5% → 35%
     *   BOSS:      3% → 25%
     */
    public static double calculateCatchRate(int captureLevel, DifficultyTier difficulty) {
        double base, max;
        switch (difficulty) {
            case BEGINNER: base = 0.25; max = 0.70; break;
            case EASY:     base = 0.20; max = 0.65; break;
            case MEDIUM:   base = 0.15; max = 0.55; break;
            case HARD:     base = 0.10; max = 0.50; break;
            case ELITE:    base = 0.05; max = 0.35; break;
            case BOSS:     base = 0.03; max = 0.25; break;
            default:       base = 0.15; max = 0.55; break;
        }
        double perLevel = (max - base) / 98.0;   // reaches the max exactly at level 99
        return Math.min(base + (captureLevel - 1) * perLevel, max);
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

