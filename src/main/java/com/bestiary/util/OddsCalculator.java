package com.bestiary.util;

import com.bestiary.model.CapturedCreature;
import com.bestiary.model.CreatureRarity;
import com.bestiary.model.DifficultyTier;
import com.bestiary.model.MonsterRoster;
import com.bestiary.service.CaptureService;

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

    /**
     * Supplies the player's current passive shiny-chance bonus (from the Shiny Charm shop upgrade).
     * The Odds view describes a capture's shiny odds; since the charm raises the shiny roll on both
     * captures and rerolls, those odds should reflect it. Wired once at startup so the many odds
     * call-sites don't each need the data service. Defaults to 0 (no bonus) for tests/headless use.
     */
    private static java.util.function.DoubleSupplier shinyBonus = () -> 0.0;

    public static void setShinyBonusSupplier(java.util.function.DoubleSupplier supplier) {
        shinyBonus = supplier != null ? supplier : () -> 0.0;
    }

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
        public int avgPowerLevel;      // Power Level if every stat (prayer included) rolled its band centre
        public int avgShinyPowerLevel; // ...if every stat rolled the centre of its SHINY band
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
        double sc      = Math.min(1.0, CaptureService.shinyChance(r.level) + Math.max(0.0, shinyBonus.getAsDouble()));
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
        // Prayer is a utility stat (half scale).
        int prayerBase = MonsterRoster.getPrayer(c.npcName);
        int[] pBand = r.shiny ? RarityRoller.shinyUtilityBand(prayerBase, r.rarity)
                             : RarityRoller.utilityBand(prayerBase, r.rarity);
        r.stats.add(new StatOdds("Prayer", prayerBase, c.prayer,
                RarityRoller.utilityCentre(prayerBase, r.rarity), pBand[0], pBand[1]));
        // Agility is a utility stat too — half scale, same as Prayer.
        int centreAgi = RarityRoller.utilityCentre(bases[5], r.rarity);
        int[] bandAgi = r.shiny ? RarityRoller.shinyUtilityBand(bases[5], r.rarity)
                               : RarityRoller.utilityBand(bases[5], r.rarity);
        r.stats.add(new StatOdds(STAT_NAMES[5], bases[5], vals[5], centreAgi, bandAgi[0], bandAgi[1]));

        // Power Level inputs
        r.statSum    = c.quality.statSum();
        r.hp         = c.hitpoints();
        r.prayer     = c.prayer;
        r.powerLevel = c.powerLevel();

        // Average-roll Power Level for this rarity: every stat at its band centre. Agility (5)
        // and Prayer are utility stats (half scale); the other five are full-scale combat stats.
        int avgStatSum = 0;
        for (int i = 0; i < 6; i++) {
            avgStatSum += (i == RarityRoller.AGILITY_INDEX)
                    ? RarityRoller.utilityCentre(bases[i], r.rarity)
                    : RarityRoller.statCentre(bases[i], r.rarity);
        }
        r.avgPowerLevel = Math.round(
                (avgStatSum + RarityRoller.utilityCentre(prayerBase, r.rarity)) / 7f + r.hp / 6f);

        // Average SHINY roll for this rarity: every stat at the centre of its shiny band.
        int avgShinyStatSum = 0;
        for (int i = 0; i < 6; i++) {
            int[] sb = (i == RarityRoller.AGILITY_INDEX)
                    ? RarityRoller.shinyUtilityBand(bases[i], r.rarity)
                    : RarityRoller.shinyBand(bases[i], r.rarity);
            avgShinyStatSum += (sb[0] + sb[1]) / 2;
        }
        int[] pShiny = RarityRoller.shinyUtilityBand(prayerBase, r.rarity);
        r.avgShinyPowerLevel = Math.round(
                (avgShinyStatSum + (pShiny[0] + pShiny[1]) / 2) / 7f + r.hp / 6f);

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
