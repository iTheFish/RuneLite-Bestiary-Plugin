package net.runelite.client.plugins.bestiary.model;

/**
 * Combat class for a monster. Determines which of the 6 stats
 * (indices: 0=STR, 1=SPD, 2=END, 3=INT, 4=STL, 5=VIT) are "primary" and roll
 * with a higher mean. Secondary stats roll with a lower mean.
 * APEX has 3 primaries — reserved for the hardest endgame bosses.
 */
public enum CombatClass {
    NIMBLE   ("Nimble",   new int[]{1, 4}),   // SPD, STL
    BRUTE    ("Brute",    new int[]{0, 2}),   // STR, END
    TANK     ("Tank",     new int[]{2, 5}),   // END, VIT
    PREDATOR ("Predator", new int[]{0, 1}),   // STR, SPD
    MYSTIC   ("Mystic",   new int[]{3, 5}),   // INT, VIT
    STALKER  ("Stalker",  new int[]{3, 4}),   // INT, STL
    TITAN    ("Titan",    new int[]{0, 5}),   // STR, VIT
    APEX     ("Apex",     new int[]{0, 1, 3});// STR, SPD, INT

    public final String label;
    public final int[]  primaryIndices;

    CombatClass(String label, int[] primaryIndices) {
        this.label = label;
        this.primaryIndices = primaryIndices;
    }

    public boolean isPrimary(int statIndex) {
        for (int i : primaryIndices) {
            if (i == statIndex) return true;
        }
        return false;
    }
}
