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
        public final int base;       // the monster's base value for this stat
        public final int value;      // the rolled value on the card
        public final int centre;     // the rarity's expected value for this stat
        public final int lo, hi;     // the rarity's roll band for this stat (overlaps neighbours)
        StatOdds(String name, int base, int value, int centre, int lo, int hi) {
            this.name = name; this.base = base; this.value = value;
            this.centre = centre; this.lo = lo; this.hi = hi;
        }
    }

    public static final class Result {
        public int level;
        public DifficultyTier difficulty;
        public CreatureRarity rarity;
        public boolean shiny;
        public double catchChance;    // 0..1 — chance a kill yields a capture
        public double rarityChance;   // 0..1 — chance a capture is this rarity
        public double shinyChance;    // 0..1 — chance of the shiny outcome (shiny or not)
        public List<StatOdds> stats = new ArrayList<>();
        // Power Level inputs
        public int statSum;
        public int hp;
        public int prayer;
        public int powerLevel;
        // Combined odds (stat wiggle is NOT a factor)
        public double perCapture;     // rarity × shiny  — "of your captures, how often this card"
        public double perKill;        // catch × rarity × shiny — "how rare was the whole event"
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
        int[] vals  = {c.quality.attack, c.quality.strength, c.quality.defence,
                       c.quality.magic, c.quality.ranged, c.quality.agility};
        // Combat stats ATK..RNG (0-4), then Prayer, then Agility (5) — prayer sits before agility.
        // When shiny, the shown band is the shiny roll range so the quality tag reflects it.
        for (int i = 0; i < 5; i++) {
            int centre = RarityRoller.statCentre(bases[i], r.rarity);
            int[] band = r.shiny ? RarityRoller.shinyBand(bases[i], r.rarity)
                                 : RarityRoller.statBand(bases[i], r.rarity);
            r.stats.add(new StatOdds(STAT_NAMES[i], bases[i], vals[i], centre, band[0], band[1]));
        }
        // Prayer is a rolled stat too (half scale).
        int prayerBase = MonsterRoster.getPrayer(c.npcName);
        int[] pBand = r.shiny ? RarityRoller.shinyPrayerBand(prayerBase, r.rarity)
                             : RarityRoller.prayerBand(prayerBase, r.rarity);
        r.stats.add(new StatOdds("Prayer", prayerBase, c.prayer,
                RarityRoller.prayerCentre(prayerBase, r.rarity), pBand[0], pBand[1]));
        int centreAgi = RarityRoller.statCentre(bases[5], r.rarity);
        int[] bandAgi = r.shiny ? RarityRoller.shinyBand(bases[5], r.rarity)
                               : RarityRoller.statBand(bases[5], r.rarity);
        r.stats.add(new StatOdds(STAT_NAMES[5], bases[5], vals[5], centreAgi, bandAgi[0], bandAgi[1]));

        // Power Level inputs
        r.statSum    = c.quality.statSum();
        r.hp         = c.hitpoints();
        r.prayer     = c.prayer;
        r.powerLevel = c.powerLevel();

        // Stat wiggle is flavour, not part of "how rare is this card" — only rarity (and shiny) count.
        r.perCapture = r.rarityChance * (r.shiny ? r.shinyChance : 1.0);
        r.perKill    = r.catchChance * r.perCapture;
        return r;
    }

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
