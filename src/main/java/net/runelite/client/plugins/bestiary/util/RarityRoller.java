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

    /**
     * Generates quality stats using a tripartite model:
     *
     *   Primary   — climbs steeply toward 99 as rarity increases
     *   Secondary — climbs moderately (implicit: any stat not primary or tertiary)
     *   Tertiary  — barely moves; stays near the difficulty floor
     *
     * The floor (from MonsterRoster.getStatFloor) lifts ALL stats for harder
     * monsters, so even a Common Nex card feels more impressive than a Mythic Chicken.
     */
    public static CreatureQuality generateQuality(CombatClass cls, CreatureRarity rarity,
                                                  int floor, Random rng) {
        // boost: 0.0 at COMMON → 1.0 at MYTHIC
        double boost = rarity.ordinal() / (double)(CreatureRarity.values().length - 1);

        // Ceilings: secondary and tertiary are capped so they never outrun primary
        double primaryCeil  = 99;
        double secondCeil   = Math.max(floor + 15, Math.min(80, floor + (99 - floor) * 0.65));
        double tertiCeil    = Math.max(floor + 5,  Math.min(50, floor + (99 - floor) * 0.20));

        int[] stats = new int[6];
        for (int i = 0; i < 6; i++) {
            double target;
            if (cls.isPrimary(i)) {
                target = floor + boost * (primaryCeil - floor);
            } else if (cls.isTertiary(i)) {
                target = floor + boost * (tertiCeil - floor);
            } else {
                target = floor + boost * (secondCeil - floor);
            }
            stats[i] = rollStat(rng, target, 7);
        }
        return new CreatureQuality(stats[0], stats[1], stats[2], stats[3], stats[4], stats[5]);
    }

    private static int rollStat(Random rng, double mean, double sd) {
        double value = mean + rng.nextGaussian() * sd;
        return Math.max(1, Math.min(99, (int) Math.round(value)));
    }
}
