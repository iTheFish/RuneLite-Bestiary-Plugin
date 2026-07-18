package net.runelite.client.plugins.bestiary.util;

import net.runelite.client.plugins.bestiary.model.CreatureRarity;

import java.util.Random;

/**
 * Weighted random rarity selection.
 * Rarities are drawn by walking the probability thresholds in ascending order
 * so that the most common outcomes are tested first.
 */
public final class RarityRoller {

    private RarityRoller() {}

    /**
     * Rolls a rarity using the probability weights declared on {@link CreatureRarity}.
     * Uses the provided {@link Random} so callers can seed it for deterministic tests.
     */
    public static CreatureRarity roll(Random rng) {
        double roll = rng.nextDouble();
        double cumulative = 0.0;

        // Walk from rarest to most common so thresholds compose naturally.
        // Probabilities declared on the enum sum to 1.0.
        CreatureRarity[] rarities = CreatureRarity.values();
        for (int i = rarities.length - 1; i >= 0; i--) {
            cumulative += rarities[i].probability;
            if (roll >= 1.0 - cumulative) {
                return rarities[i];
            }
        }
        return CreatureRarity.COMMON;
    }

    /**
     * Generates quality stats for a captured creature.
     * Higher rarities produce higher-mean stats with a tighter distribution.
     */
    public static net.runelite.client.plugins.bestiary.model.CreatureQuality generateQuality(CreatureRarity rarity, Random rng) {
        double mean;
        double sd;
        switch (rarity) {
            case MYTHIC:     mean = 90; sd =  8; break;
            case LEGENDARY:  mean = 75; sd = 10; break;
            case EPIC:       mean = 62; sd = 12; break;
            case RARE:       mean = 50; sd = 14; break;
            case UNCOMMON:   mean = 38; sd = 15; break;
            default:         mean = 28; sd = 15; break;
        }
        return new net.runelite.client.plugins.bestiary.model.CreatureQuality(
                rollStat(rng, mean, sd),
                rollStat(rng, mean, sd),
                rollStat(rng, mean, sd),
                rollStat(rng, mean, sd),
                rollStat(rng, mean, sd),
                rollStat(rng, mean, sd));
    }

    private static int rollStat(Random rng, double mean, double sd) {
        double value = mean + rng.nextGaussian() * sd;
        return Math.max(1, Math.min(100, (int) Math.round(value)));
    }
}

