package net.runelite.client.plugins.bestiary.util;

import net.runelite.client.plugins.bestiary.model.CreatureRarity;
import net.runelite.client.plugins.bestiary.model.CombatClass;

import java.util.Random;

/**
 * Weighted random rarity selection.
 * Rarities are drawn by walking the probability thresholds in ascending order
 * so that the most common outcomes are tested first.
 */
public final class RarityRoller {

    private RarityRoller() {}

    /**
     * Rolls a rarity using the base probability weights on {@link CreatureRarity}.
     */
    public static CreatureRarity roll(Random rng) {
        return roll(rng, 1);
    }

    /**
     * Rolls a rarity with weights shifted toward rarer outcomes at higher capture levels.
     * At level 1, weights match the base probabilities exactly.
     * At level 99, COMMON weight halves while MYTHIC weight is 12x the base.
     *
     * Level multipliers (linear interpolation between level 1 and level 99):
     *   COMMON: 1.0 → 0.50   UNCOMMON: 1.0 → 1.30
     *   RARE:   1.0 → 2.00   EPIC:     1.0 → 4.00
     *   LEGENDARY: 1.0 → 8.0  MYTHIC: 1.0 → 12.0
     */
    public static CreatureRarity roll(Random rng, int captureLevel) {
        double t = Math.max(0, Math.min(98, captureLevel - 1)) / 98.0; // 0.0 at lvl1, 1.0 at lvl99

        double[] multipliers = {
            1.0 + t * (0.50 - 1.0),  // COMMON:    1.0 → 0.50
            1.0 + t * (1.30 - 1.0),  // UNCOMMON:  1.0 → 1.30
            1.0 + t * (2.00 - 1.0),  // RARE:      1.0 → 2.00
            1.0 + t * (4.00 - 1.0),  // EPIC:      1.0 → 4.00
            1.0 + t * (8.00 - 1.0),  // LEGENDARY: 1.0 → 8.00
            1.0 + t * (12.0 - 1.0),  // MYTHIC:    1.0 → 12.0
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

    /**
     * Generates quality stats shaped by the monster's combat class.
     * Primary stats (per class) pull toward 100; secondary stats pull toward 0.
     * Higher rarities raise the baseline mean and tighten the spread.
     */
    public static net.runelite.client.plugins.bestiary.model.CreatureQuality generateQuality(
            CombatClass combatClass, CreatureRarity rarity, Random rng) {
        double mean, sd;
        switch (rarity) {
            case MYTHIC:     mean = 90; sd =  8; break;
            case LEGENDARY:  mean = 75; sd = 10; break;
            case EPIC:       mean = 62; sd = 12; break;
            case RARE:       mean = 50; sd = 14; break;
            case UNCOMMON:   mean = 38; sd = 15; break;
            default:         mean = 28; sd = 15; break;
        }
        int[] stats = new int[6];
        for (int i = 0; i < 6; i++) {
            double m = combatClass.isPrimary(i)
                    ? mean + 0.3 * (100 - mean)  // pull toward ceiling
                    : mean * 0.6;                 // pull toward floor
            stats[i] = rollStat(rng, m, sd);
        }
        return new net.runelite.client.plugins.bestiary.model.CreatureQuality(
                stats[0], stats[1], stats[2], stats[3], stats[4], stats[5]);
    }

    private static int rollStat(Random rng, double mean, double sd) {
        double value = mean + rng.nextGaussian() * sd;
        return Math.max(1, Math.min(100, (int) Math.round(value)));
    }
}

