package com.bestiary.model;

import java.awt.Color;

public enum CreatureRarity {

    COMMON    (0.750, new Color(180, 180, 180), 1.0,  "Common"),
    UNCOMMON  (0.170, new Color(80,  200, 80),  2.0,  "Uncommon"),
    RARE      (0.060, new Color(80,  140, 255),  5.0,  "Rare"),
    EPIC      (0.015, new Color(190, 80,  220),  10.0, "Epic"),
    LEGENDARY (0.004, new Color(255, 165, 0),    25.0, "Legendary"),
    MYTHIC    (0.001, new Color(255, 50,  50),   50.0, "Mythic");

    /** Weight used by RarityRoller; values sum to 1.0. */
    public final double probability;

    /** Colour used by the UI to tint creature cards. */
    public final Color displayColor;

    /** Multiplier applied on top of the base capture XP award. */
    public final double xpMultiplier;

    /** Human-readable label. */
    public final String label;

    CreatureRarity(double probability, Color displayColor, double xpMultiplier, String label) {
        this.probability  = probability;
        this.displayColor = displayColor;
        this.xpMultiplier = xpMultiplier;
        this.label        = label;
    }

    /** Returns the first rarity whose display label matches (case-insensitive). */
    public static CreatureRarity fromLabel(String label) {
        for (CreatureRarity r : values()) {
            if (r.label.equalsIgnoreCase(label)) return r;
        }
        return COMMON;
    }
}

