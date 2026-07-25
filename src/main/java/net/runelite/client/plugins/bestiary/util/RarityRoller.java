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
    /** Level-scaled rarity weight multipliers (index matches CreatureRarity.ordinal()). */
    private static final double[] LEVEL_TARGET_MULT = {0.50, 1.30, 2.00, 4.00, 8.00, 12.0};

    public static CreatureRarity roll(Random rng, int captureLevel) {
        double[] weights = rarityWeights(captureLevel);
        double total = 0.0;
        for (double w : weights) total += w;

        double roll = rng.nextDouble() * total;
        double cumulative = 0.0;
        CreatureRarity[] rarities = CreatureRarity.values();
        for (int i = 0; i < rarities.length; i++) {
            cumulative += weights[i];
            if (roll < cumulative) return rarities[i];
        }
        return CreatureRarity.COMMON;
    }

    private static double[] rarityWeights(int captureLevel) {
        double t = Math.max(0, Math.min(98, captureLevel - 1)) / 98.0;
        CreatureRarity[] rarities = CreatureRarity.values();
        double[] weights = new double[rarities.length];
        for (int i = 0; i < rarities.length; i++) {
            weights[i] = rarities[i].probability * (1.0 + t * (LEVEL_TARGET_MULT[i] - 1.0));
        }
        return weights;
    }

    /** Probability (0..1) that a rarity roll at the given capture level yields {@code r}. */
    public static double rarityChance(int captureLevel, CreatureRarity r) {
        double[] weights = rarityWeights(captureLevel);
        double total = 0.0;
        for (double w : weights) total += w;
        return weights[r.ordinal()] / total;
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
        int[] stats = new int[6];
        for (int i = 0; i < 6; i++) {
            int centre = statCentre(bases[i], rarity);
            int roll = shiny
                ? SHINY_MIN_BONUS + rng.nextInt(SHINY_MAX_BONUS - SHINY_MIN_BONUS + 1)  // +6..+20 over expected
                : rng.nextInt(2 * WIGGLE + 1) - WIGGLE;                                 // -6..+6
            stats[i] = Math.max(1, Math.min(99, centre + roll));
        }
        return new CreatureQuality(stats[0], stats[1], stats[2], stats[3], stats[4], stats[5]);
    }

    // Stat centre model. Epic = base (the reviewed values represent an "average" card).
    // Below Epic: multiplicative down (a low stat only dips a little, floored at 1).
    // Above Epic: additive lift TOWARD the cap — the absolute lift is a fraction of the
    // headroom (cap − base), so a weak stat (base 1) still climbs a lot at high rarity
    // (fun), while an already-high stat only edges up (nothing auto-maxes to 99).
    private static final double MULT_COMMON = 0.72, MULT_UNCOMMON = 0.82, MULT_RARE = 0.92;
    private static final double LIFT_LEGENDARY = 0.30, LIFT_MYTHIC = 0.60;
    private static final int STAT_CAP = 99;

    /** The "expected" value for a stat at a rarity (before wiggle / shiny bonus). */
    public static int statCentre(int base, CreatureRarity rarity) {
        double c;
        switch (rarity) {
            case COMMON:    c = base * MULT_COMMON;                       break;
            case UNCOMMON:  c = base * MULT_UNCOMMON;                     break;
            case RARE:      c = base * MULT_RARE;                         break;
            case LEGENDARY: c = base + LIFT_LEGENDARY * (STAT_CAP - base); break;
            case MYTHIC:    c = base + LIFT_MYTHIC * (STAT_CAP - base);    break;
            case EPIC:
            default:        c = base;
        }
        return Math.max(1, Math.min(99, (int) Math.round(c)));
    }

    /** Half-width of the uniform RNG wiggle added to each non-shiny stat centre. */
    public static final int WIGGLE = 6;

    /** A shiny stat rolls its expected value plus a uniform bonus in [MIN, MAX]. */
    public static final int SHINY_MIN_BONUS = 6;
    public static final int SHINY_MAX_BONUS = 20;
}
