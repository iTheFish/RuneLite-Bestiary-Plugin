package com.bestiary.model;

/**
 * Six randomised stats for a captured creature, each in [1, 99].
 * Indices: 0=ATK  1=STR  2=DEF  3=MAG  4=RNG  5=AGI
 * Generated once at capture time; never changes for a given capture record.
 */
public class CreatureQuality {

    public final int attack;
    public final int strength;
    public final int defence;
    public final int magic;
    public final int ranged;
    public final int agility;

    public CreatureQuality(int attack, int strength, int defence,
                           int magic, int ranged, int agility) {
        this.attack   = clamp(attack);
        this.strength = clamp(strength);
        this.defence  = clamp(defence);
        this.magic    = clamp(magic);
        this.ranged   = clamp(ranged);
        this.agility  = clamp(agility);
    }

    /** Sum of all six rolled stats. */
    public int statSum() {
        return attack + strength + defence + magic + ranged + agility;
    }

    /** Average of all six stats, rounded to the nearest integer (pure stat flavour). */
    public int overallRating() {
        return Math.round(statSum() / 6f);
    }

    private static int clamp(int v) {
        return Math.max(1, Math.min(99, v));
    }

    @Override
    public String toString() {
        return String.format("ATK:%d STR:%d DEF:%d MAG:%d RNG:%d AGI:%d",
                attack, strength, defence, magic, ranged, agility);
    }
}
