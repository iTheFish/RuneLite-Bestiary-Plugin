package com.bestiary.model;

/**
 * Seven randomised stats for a captured creature, each in [1, 99].
 * Indices: 0=ATK  1=STR  2=DEF  3=MAG  4=RNG  5=AGI  6=PRAYER
 * Agility and Prayer are "utility" stats rolled at half scale; the other five are full-scale
 * combat stats. Generated once at capture time; never changes for a given capture record.
 */
public class CreatureQuality {

    public final int attack;
    public final int strength;
    public final int defence;
    public final int magic;
    public final int ranged;
    public final int agility;
    public final int prayer;

    public CreatureQuality(int attack, int strength, int defence,
                           int magic, int ranged, int agility, int prayer) {
        this.attack   = clamp(attack);
        this.strength = clamp(strength);
        this.defence  = clamp(defence);
        this.magic    = clamp(magic);
        this.ranged   = clamp(ranged);
        this.agility  = clamp(agility);
        this.prayer   = clamp(prayer);
    }

    /** Sum of all seven rolled stats (six combat + Prayer). */
    public int statSum() {
        return attack + strength + defence + magic + ranged + agility + prayer;
    }

    /** Average of all seven stats, rounded to the nearest integer (pure stat flavour). */
    public int overallRating() {
        return Math.round(statSum() / 7f);
    }

    private static int clamp(int v) {
        return Math.max(1, Math.min(99, v));
    }

    @Override
    public String toString() {
        return String.format("ATK:%d STR:%d DEF:%d MAG:%d RNG:%d AGI:%d PRAY:%d",
                attack, strength, defence, magic, ranged, agility, prayer);
    }
}
