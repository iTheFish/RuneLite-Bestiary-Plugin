package com.bestiary.model;

import java.awt.Color;

/**
 * Global difficulty rating for a monster species — reflects how hard the
 * monster is to kill, not how rare its captures are.
 */
public enum DifficultyTier {
    BEGINNER("Beginner", new Color(105, 105, 105)),
    EASY    ("Easy",     new Color(56,  142,  60)),
    MEDIUM  ("Medium",   new Color(198, 127,   0)),
    HARD    ("Hard",     new Color(194,  57,  57)),
    ELITE   ("Elite",    new Color(121,  40, 176)),
    BOSS    ("Boss",     new Color(165,  20,  20));

    public final String label;
    public final Color  displayColor;

    DifficultyTier(String label, Color displayColor) {
        this.label        = label;
        this.displayColor = displayColor;
    }
}
