package com.bestiary.util;

import com.bestiary.model.CombatClass;
import com.bestiary.model.CreatureQuality;
import com.bestiary.model.CreatureRarity;

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
            // Agility (index 5), like Prayer, is a utility stat rolled at HALF scale.
            boolean utility = (i == AGILITY_INDEX);
            int[] band = utility ? utilityBand(bases[i], rarity) : statBand(bases[i], rarity);
            if (shiny) {
                // Anchor to the TOP of the band + bonus, so a shiny always beats a
                // same-rarity non-shiny (which can only reach band top). Utility stats
                // get half the bonus, matching their half-scale bands.
                int fullBonus = SHINY_MIN_BONUS + rng.nextInt(SHINY_MAX_BONUS - SHINY_MIN_BONUS + 1); // +6..+20
                stats[i] = clampStat(band[1] + (utility ? fullBonus / 2 : fullBonus));
            } else {
                stats[i] = band[0] + (band[1] > band[0] ? rng.nextInt(band[1] - band[0] + 1) : 0);
            }
        }
        return new CreatureQuality(stats[0], stats[1], stats[2], stats[3], stats[4], stats[5]);
    }

    // Unified stat model. Every rarity rolls a band expressed as a LIFT FRACTION of the
    // headroom (cap − base): stat = base + L × (99 − base), with L uniform in [lo, hi].
    // Because the fraction bands OVERLAP in lift-space (each row's lo < the row above's… i.e.
    // next.lo < cur.hi), the resulting stat bands overlap at EVERY base — a lucky Rare can
    // beat an unlucky Epic, a lucky Legendary an unlucky Mythic, etc. Epic ≈ base (centred on
    // 0). Rare/Uncommon/Common can dip below base (negative lift) but are floored at 1; low
    // rarities still get real upward spread for weak monsters (base 1 Rare ≈ 1–10).
    // Index = CreatureRarity.ordinal(): Common, Uncommon, Rare, Epic, Legendary, Mythic.
    private static final double[] LIFT_LO = {-0.26, -0.18, -0.11, 0.00, 0.12, 0.40};
    private static final double[] LIFT_HI = { 0.01,  0.04,  0.07, 0.22, 0.50, 0.82};
    private static final int STAT_CAP = 99;

    /** The inclusive [lo, hi] range a non-shiny stat rolls in at this rarity (floored at 1). */
    public static int[] statBand(int base, CreatureRarity rarity) {
        int i  = rarity.ordinal();
        int hr = STAT_CAP - base;
        int lo = clampStat(base + LIFT_LO[i] * hr);
        int hi = clampStat(base + LIFT_HI[i] * hr);
        if (hi < lo) hi = lo;
        return new int[]{lo, hi};
    }

    /** The "expected" value for a stat at a rarity (band midpoint). */
    public static int statCentre(int base, CreatureRarity rarity) {
        int i = rarity.ordinal();
        double midLift = (LIFT_LO[i] + LIFT_HI[i]) / 2.0;
        return clampStat(base + midLift * (STAT_CAP - base));
    }

    /** Stats never drop below 1 or exceed 99. */
    private static int clampStat(double v) {
        return Math.max(1, Math.min(99, (int) Math.round(v)));
    }

    /** A shiny stat rolls its expected value plus a uniform bonus in [MIN, MAX]. */
    public static final int SHINY_MIN_BONUS = 6;
    public static final int SHINY_MAX_BONUS = 20;

    /** The [lo, hi] range a SHINY stat rolls in: band top + [MIN, MAX] bonus (clamped). */
    public static int[] shinyBand(int base, CreatureRarity rarity) {
        int hi = statBand(base, rarity)[1];
        return new int[]{clampStat(hi + SHINY_MIN_BONUS), clampStat(hi + SHINY_MAX_BONUS)};
    }

    // Prayer AND Agility are "utility" stats rolled at HALF scale (the other five are full-scale
    // combat stats). They stay ~1 for low-base monsters at low rarity and only climb at high
    // rarity / shiny (base 1 → ~40 at Mythic). Same banded, overlapping model — just halved lift.
    public static final int    AGILITY_INDEX = 5;
    public static final double UTILITY_SCALE = 0.5;

    /** The inclusive [lo, hi] range a non-shiny utility stat (Prayer/Agility) rolls in. */
    public static int[] utilityBand(int base, CreatureRarity rarity) {
        int i = rarity.ordinal();
        int hr = STAT_CAP - base;
        int lo = clampStat(base + UTILITY_SCALE * LIFT_LO[i] * hr);
        int hi = clampStat(base + UTILITY_SCALE * LIFT_HI[i] * hr);
        if (hi < lo) hi = lo;
        return new int[]{lo, hi};
    }

    /** A utility stat's expected value at a rarity (band midpoint). */
    public static int utilityCentre(int base, CreatureRarity rarity) {
        int i = rarity.ordinal();
        double midLift = (LIFT_LO[i] + LIFT_HI[i]) / 2.0;
        return clampStat(base + UTILITY_SCALE * midLift * (STAT_CAP - base));
    }

    /** The [lo, hi] range a SHINY utility stat rolls in: band top + half bonus (clamped). */
    public static int[] shinyUtilityBand(int base, CreatureRarity rarity) {
        int hi = utilityBand(base, rarity)[1];
        return new int[]{clampStat(hi + SHINY_MIN_BONUS / 2), clampStat(hi + SHINY_MAX_BONUS / 2)};
    }

    /** Rolls a capture's prayer: a utility stat (half scale); shiny gets a smaller boost. */
    public static int rollPrayer(int base, CreatureRarity rarity, Random rng, boolean shiny) {
        int[] b = utilityBand(base, rarity);
        if (shiny) {
            int bonus = (SHINY_MIN_BONUS + rng.nextInt(SHINY_MAX_BONUS - SHINY_MIN_BONUS + 1)) / 2;
            return clampStat(b[1] + bonus);   // top of the band + bonus
        }
        return b[0] + (b[1] > b[0] ? rng.nextInt(b[1] - b[0] + 1) : 0);
    }
}
