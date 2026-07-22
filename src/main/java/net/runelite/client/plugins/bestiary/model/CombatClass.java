package net.runelite.client.plugins.bestiary.model;

/**
 * Combat class for a monster. Determines primary (high), secondary (mid), and
 * tertiary (dump) stats across the 6 indices: 0=STR 1=SPD 2=END 3=INT 4=STL 5=VIT.
 * Any index not listed as primary or tertiary is implicitly secondary.
 * APEX has 3 primaries and only 1 tertiary (STL), making it the most well-rounded class.
 */
public enum CombatClass {
    NIMBLE   ("Nimble",   new int[]{1, 4},    new int[]{0, 2}),   // P: SPD STL  | T: STR END
    BRUTE    ("Brute",    new int[]{0, 2},    new int[]{3, 4}),   // P: STR END  | T: INT STL
    TANK     ("Tank",     new int[]{2, 5},    new int[]{1, 4}),   // P: END VIT  | T: SPD STL
    PREDATOR ("Predator", new int[]{0, 1},    new int[]{3, 4}),   // P: STR SPD  | T: INT STL
    MYSTIC   ("Mystic",   new int[]{3, 5},    new int[]{0, 1}),   // P: INT VIT  | T: STR SPD
    STALKER  ("Stalker",  new int[]{3, 4},    new int[]{0, 2}),   // P: INT STL  | T: STR END
    TITAN    ("Titan",    new int[]{0, 5},    new int[]{1, 4}),   // P: STR VIT  | T: SPD STL
    APEX     ("Apex",     new int[]{0, 1, 3}, new int[]{4});      // P: STR SPD INT | T: STL

    public final String label;
    public final int[]  primaryIndices;
    public final int[]  tertiaryIndices;

    CombatClass(String label, int[] primaryIndices, int[] tertiaryIndices) {
        this.label            = label;
        this.primaryIndices   = primaryIndices;
        this.tertiaryIndices  = tertiaryIndices;
    }

    public boolean isPrimary(int statIndex) {
        for (int i : primaryIndices)   { if (i == statIndex) return true; }
        return false;
    }

    public boolean isTertiary(int statIndex) {
        for (int i : tertiaryIndices)  { if (i == statIndex) return true; }
        return false;
    }
}
