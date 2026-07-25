package net.runelite.client.plugins.bestiary.util;

import net.runelite.client.plugins.bestiary.model.CapturedCreature;
import net.runelite.client.plugins.bestiary.model.CreatureRarity;
import net.runelite.client.plugins.bestiary.model.DifficultyTier;
import net.runelite.client.plugins.bestiary.model.MonsterRoster;
import net.runelite.client.plugins.bestiary.service.CaptureService;

import java.util.ArrayList;
import java.util.List;

/**
 * Reconstructs the probability of a specific capture from its stored data
 * (capture level, rarity, shiny, per-stat values) using the live formulas:
 *   catch  = {@link CaptureService#calculateCatchRate}
 *   rarity = {@link RarityRoller#rarityChance}
 *   shiny  = {@link CaptureService#shinyChance}
 *   stats  = base × rarity multiplier ± uniform wiggle ({@link RarityRoller#WIGGLE})
 */
public final class OddsCalculator {

    private static final String[] STAT_NAMES = {"Attack", "Strength", "Defence", "Magic", "Ranged", "Agility"};

    private OddsCalculator() {}

    public static final class StatOdds {
        public final String name;
        public final int value;      // the rolled value on the card
        public final int centre;     // base × rarity multiplier (rounded)
        public final double prob;    // P(this stat lands on `value`)
        StatOdds(String name, int value, int centre, double prob) {
            this.name = name; this.value = value; this.centre = centre; this.prob = prob;
        }
    }

    public static final class Result {
        public int level;
        public DifficultyTier difficulty;
        public CreatureRarity rarity;
        public boolean shiny;
        public double catchChance;    // 0..1 — chance the capture happened at all
        public double rarityChance;   // 0..1 — chance of this rarity
        public double shinyChance;    // 0..1 — chance of the shiny outcome (shiny or not)
        public List<StatOdds> stats = new ArrayList<>();
        public double statsCombined;  // product of per-stat probs
        public double overall;        // rarity × shiny × stats (excludes the catch gate)
    }

    public static Result compute(CapturedCreature c) {
        Result r = new Result();
        r.level      = Math.max(1, c.captureLevel);
        r.difficulty = MonsterRoster.getDifficulty(c.npcName, c.npcCombatLevel);
        r.rarity     = c.rarity;
        r.shiny      = c.isShiny();

        r.catchChance  = CaptureService.calculateCatchRate(r.level, r.difficulty);
        r.rarityChance = RarityRoller.rarityChance(r.level, r.rarity);
        double sc      = CaptureService.shinyChance(r.level);
        r.shinyChance  = r.shiny ? sc : (1.0 - sc);

        int[] bases = MonsterRoster.getStatBases(c.npcName, c.npcCombatLevel);
        double mult  = r.rarity.statMultiplier + (r.shiny ? RarityRoller.SHINY_MULT_BONUS : 0.0);
        int[] vals   = {c.quality.attack, c.quality.strength, c.quality.defence,
                        c.quality.magic, c.quality.ranged, c.quality.agility};

        r.statsCombined = 1.0;
        for (int i = 0; i < 6; i++) {
            int centre = (int) Math.round(bases[i] * mult);
            double p = statProb(centre, vals[i], r.shiny);
            r.stats.add(new StatOdds(STAT_NAMES[i], vals[i], centre, p));
            r.statsCombined *= p;
        }

        r.overall = r.rarityChance * (r.shiny ? r.shinyChance : 1.0) * r.statsCombined;
        return r;
    }

    /**
     * P(a stat centred at {@code centre} lands on {@code value}). Shiny stats are
     * deterministic (centre + max wiggle), so their prob is 1 when they match.
     */
    private static double statProb(int centre, int value, boolean shiny) {
        int w = RarityRoller.WIGGLE;
        if (shiny) {
            return clamp(centre + w) == value ? 1.0 : 0.0;
        }
        int hits = 0, total = 2 * w + 1;
        for (int off = -w; off <= w; off++) {
            if (clamp(centre + off) == value) hits++;
        }
        return (double) hits / total;
    }

    private static int clamp(int v) { return Math.max(1, Math.min(99, v)); }

    /** Formats a 0..1 probability as "1 in N" (N comma-grouped). */
    public static String oneIn(double p) {
        if (p <= 0) return "≈ impossible";
        if (p >= 1) return "certain";
        long n = Math.round(1.0 / p);
        return "1 in " + String.format("%,d", n);
    }

    /** Formats a 0..1 probability as a percentage with sensible precision. */
    public static String pct(double p) {
        double v = p * 100.0;
        if (v >= 10)   return String.format("%.0f%%", v);
        if (v >= 1)    return String.format("%.1f%%", v);
        if (v >= 0.01) return String.format("%.2f%%", v);
        return String.format("%.4f%%", v);
    }
}
