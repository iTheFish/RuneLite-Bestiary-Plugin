package com.bestiary.model;

/**
 * Combat class for a monster. Determines primary (high), secondary (mid), and
 * tertiary (dump) stats across the 6 indices: 0=ATK 1=STR 2=DEF 3=MAG 4=RNG 5=AGI.
 * Any index not listed as primary or tertiary is implicitly secondary.
 * APEX has 3 primaries and only 1 tertiary (AGI), making it the most dominant class.
 */
public enum CombatClass {
    NIMBLE   ("Nimble",   new int[]{1, 5},    new int[]{2, 3}),   // P: STR AGI  | T: DEF MAG
    BRUTE    ("Brute",    new int[]{0, 1},    new int[]{3, 4}),   // P: ATK STR  | T: MAG RNG
    TANK     ("Tank",     new int[]{1, 2},    new int[]{4, 5}),   // P: STR DEF  | T: RNG AGI
    PREDATOR ("Predator", new int[]{0, 5},    new int[]{2, 3}),   // P: ATK AGI  | T: DEF MAG
    MYSTIC   ("Mystic",   new int[]{2, 3},    new int[]{0, 1}),   // P: DEF MAG  | T: ATK STR
    STALKER  ("Stalker",  new int[]{3, 4},    new int[]{0, 2}),   // P: MAG RNG  | T: ATK DEF
    RANGER   ("Ranger",   new int[]{4, 5},    new int[]{1, 3}),   // P: RNG AGI  | T: STR MAG
    TITAN    ("Titan",    new int[]{1, 3},    new int[]{4, 5}),   // P: STR MAG  | T: RNG AGI
    APEX     ("Apex",     new int[]{0, 1, 3, 4}, new int[]{}),    // tribrid: ATK STR MAG RNG (DEF/AGI secondary)
    JUGGERNAUT("Juggernaut", new int[]{0, 1, 2}, new int[]{3, 4}),// P: ATK STR DEF | T: MAG RNG
    ARCHON   ("Archon",   new int[]{2, 3, 4},  new int[]{1}),     // P: DEF MAG RNG | T: STR (legacy)

    // ---- Attack-style x weight taxonomy (#70). AGI is never primary/tertiary here:
    // it is the flavour stat shown in the attribute band, driven by its own base. ----
    WARRIOR  ("Warrior",   new int[]{0, 1},    new int[]{3, 4}),  // melee            | dump MAG RNG
    MAGE     ("Mage",      new int[]{3},       new int[]{0, 1, 4}),// magic           | dump ATK STR RNG
    MARKSMAN ("Marksman",  new int[]{4},       new int[]{0, 1, 3}),// ranged          | dump ATK STR MAG
    BATTLEMAGE("Battlemage", new int[]{0, 1, 3}, new int[]{4}),   // melee+magic      | dump RNG
    WARDEN   ("Warden",    new int[]{0, 1, 4}, new int[]{3}),     // melee+ranged     | dump MAG
    OCCULTIST("Occultist", new int[]{3, 4},    new int[]{0, 1}),  // magic+ranged     | dump ATK STR

    // ---- Profile-derived flavour classes (assigned programmatically, not by attack style):
    // MEATSHIELD = passive/tanky (low damage, often high HP) e.g. cows, crabs.
    // NIMBLE     = fast + fragile (very low HP, high agility) e.g. rats, chickens, spiders. ----
    MEATSHIELD("Meatshield", new int[]{2}, new int[]{3, 4});      // defensive lump    | dump MAG RNG

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
