package net.runelite.client.plugins.bestiary.util;

import net.runelite.client.plugins.bestiary.model.CombatClass;
import net.runelite.client.plugins.bestiary.model.CreatureQuality;
import net.runelite.client.plugins.bestiary.model.CreatureRarity;

import java.util.Random;

public final class RarityRoller {

    private RarityRoller() {}

    public static CreatureRarity roll(Random rng) {
        return roll(rng, 1);
    }

    /**
     * Rolls a rarity with weights shifted toward rarer outcomes at higher capture levels.
     * At level 1, weights match the base probabilities exactly.
     * At level 99, COMMON weight halves while MYTHIC weight is 12x the base.
     */
    public static CreatureRarity roll(Random rng, int captureLevel) {
        double t = Math.max(0, Math.min(98, captureLevel - 1)) / 98.0;

        double[] multipliers = {
            1.0 + t * (0.50 - 1.0),
            1.0 + t * (1.30 - 1.0),
            1.0 + t * (2.00 - 1.0),
            1.0 + t * (4.00 - 1.0),
            1.0 + t * (8.00 - 1.0),
            1.0 + t * (12.0 - 1.0),
        };

        CreatureRarity[] rarities = CreatureRarity.values();
        double[] weights = new double[rarities.length];
        double total = 0.0;
        for (int i = 0; i < rarities.length; i++) {
            weights[i] = rarities[i].probability * multipliers[i];
            total += weights[i];
        }

        double roll = rng.nextDouble() * total;
        double cumulative = 0.0;
        for (int i = 0; i < rarities.length; i++) {
            cumulative += weights[i];
            if (roll < cumulative) return rarities[i];
        }
        return CreatureRarity.COMMON;
    }

    /** Legacy uniform-floor variant — treats {@code floor} as every stat's base. */
    public static CreatureQuality generateQuality(CombatClass cls, CreatureRarity rarity,
                                                  int floor, Random rng) {
        int[] bases = {floor, floor, floor, floor, floor, floor};
        return generateQuality(cls, rarity, bases, rng, false);
    }

    public static CreatureQuality generateQuality(CombatClass cls, CreatureRarity rarity,
                                                  int[] bases, Random rng) {
        return generateQuality(cls, rarity, bases, rng, false);
    }

    /**
     * Rarity-multiplier stat model.
     *
     * The reviewed per-stat bases (MonsterRoster.STAT_BASES) represent an "average"
     * (Epic) card, so each rarity simply scales them:
     *   centre[i] = base[i] × rarity.statMultiplier   (Common ×0.70 … Epic ×1.0 … Mythic ×1.20)
     * then a small uniform wiggle in [-WIGGLE, +WIGGLE] is added and the result is clamped 1..99.
     *
     * Shiny adds {@link #SHINY_MULT_BONUS} to the multiplier and takes the top of the wiggle
     * band (deterministic best roll). The combat class is no longer used to spike stats — the
     * per-stat bases already encode each monster's offensive profile (it remains a display label).
     */
    public static CreatureQuality generateQuality(CombatClass cls, CreatureRarity rarity,
                                                  int[] bases, Random rng, boolean shiny) {
        double mult = rarity.statMultiplier + (shiny ? SHINY_MULT_BONUS : 0.0);
        int[] stats = new int[6];
        for (int i = 0; i < 6; i++) {
            int centre = (int) Math.round(bases[i] * mult);
            int wiggle = shiny ? WIGGLE : (rng.nextInt(2 * WIGGLE + 1) - WIGGLE);
            stats[i] = Math.max(1, Math.min(99, centre + wiggle));
        }
        return new CreatureQuality(stats[0], stats[1], stats[2], stats[3], stats[4], stats[5]);
    }

    /** Half-width of the uniform RNG wiggle added to each stat centre. */
    public static final int WIGGLE = 3;

    /** Extra multiplier a shiny adds on top of its rarity multiplier. */
    public static final double SHINY_MULT_BONUS = 0.20;
}
