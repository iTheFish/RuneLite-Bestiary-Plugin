package com.bestiary.model;

import java.awt.Color;

/**
 * Biological/lore type of a monster. Purely cosmetic in v1 — used for
 * album filtering, dashboard breakdowns, and card display. Does not
 * affect stat rolls (CombatClass handles that).
 */
public enum CreatureSpecies {
    ANIMAL   ("Animal",    new Color(80,  165, 80)),
    ARACHNID ("Arachnid",  new Color(150, 110, 30)),
    DAGANNOTH("Dagannoth", new Color(70,  140, 170)),
    DEMON    ("Demon",     new Color(200, 50,  50)),
    DRAGON   ("Dragon",    new Color(210, 90,  30)),
    GIANT    ("Giant",     new Color(160, 110, 60)),
    GOBLINOID("Goblinoid", new Color(130, 160, 60)),
    HUMAN    ("Human",     new Color(200, 185, 155)),
    INSECT   ("Insect",    new Color(170, 150, 40)),
    KALPHITE ("Kalphite",  new Color(200, 170, 80)),
    PLANT    ("Plant",     new Color(90,  175, 100)),
    REPTILE  ("Reptile",   new Color(105, 155, 75)),
    TROLL    ("Troll",     new Color(130, 110, 90)),
    TZHAAR   ("TzHaar",    new Color(220, 100, 40)),
    UNDEAD   ("Undead",    new Color(140, 80,  180)),
    WYRM     ("Wyrm",      new Color(50,  170, 155)),
    OTHER    ("Other",     new Color(110, 110, 110));

    public final String label;
    public final Color  displayColor;

    CreatureSpecies(String label, Color displayColor) {
        this.label        = label;
        this.displayColor = displayColor;
    }
}
