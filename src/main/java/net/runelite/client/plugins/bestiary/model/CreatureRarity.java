package net.runelite.client.plugins.bestiary.model;

import java.awt.Color;

public enum CreatureRarity {

    //        prob    colour                        xpMult  statMult  label
    COMMON    (0.750, new Color(180, 180, 180), 1.0,  0.70, "Common"),
    UNCOMMON  (0.170, new Color(80,  200, 80),  2.0,  0.80, "Uncommon"),
    RARE      (0.060, new Color(80,  140, 255),  5.0,  0.91, "Rare"),
    EPIC      (0.015, new Color(190, 80,  220),  10.0, 1.00, "Epic"),
    LEGENDARY (0.004, new Color(255, 165, 0),    25.0, 1.09, "Legendary"),
    MYTHIC    (0.001, new Color(255, 50,  50),   50.0, 1.20, "Mythic");

    /** Weight used by RarityRoller; values sum to 1.0. */
    public final double probability;

    /** Colour used by the UI to tint creature cards. */
    public final Color displayColor;

    /** Multiplier applied on top of the base capture XP award. */
    public final double xpMultiplier;

    /**
     * Multiplier applied to a monster's stat bases to get this rarity's stat centres.
     * Epic = 1.00 (the reviewed base values represent an "average"/Epic card);
     * Legendary +9%, Mythic +20%; Rare/Uncommon/Common scale down (floored at 1 by the roller).
     */
    public final double statMultiplier;

    /** Human-readable label. */
    public final String label;

    CreatureRarity(double probability, Color displayColor, double xpMultiplier,
                   double statMultiplier, String label) {
        this.probability    = probability;
        this.displayColor   = displayColor;
        this.xpMultiplier   = xpMultiplier;
        this.statMultiplier = statMultiplier;
        this.label          = label;
    }

    /** Returns the first rarity whose display label matches (case-insensitive). */
    public static CreatureRarity fromLabel(String label) {
        for (CreatureRarity r : values()) {
            if (r.label.equalsIgnoreCase(label)) return r;
        }
        return COMMON;
    }
}

