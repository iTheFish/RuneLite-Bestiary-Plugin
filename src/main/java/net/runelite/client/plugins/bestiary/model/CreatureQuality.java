package net.runelite.client.plugins.bestiary.model;

/**
 * Six randomized stats for a captured creature, each in the range [1, 100].
 * Generated once at capture time; never changes for a given capture record.
 */
public class CreatureQuality {

    public final int strength;
    public final int speed;
    public final int endurance;
    public final int intelligence;
    public final int stealth;
    public final int vitality;

    public CreatureQuality(int strength, int speed, int endurance,
                           int intelligence, int stealth, int vitality) {
        this.strength     = clamp(strength);
        this.speed        = clamp(speed);
        this.endurance    = clamp(endurance);
        this.intelligence = clamp(intelligence);
        this.stealth      = clamp(stealth);
        this.vitality     = clamp(vitality);
    }

    /** Average of all six stats, rounded to the nearest integer. */
    public int overallRating() {
        return Math.round((strength + speed + endurance + intelligence + stealth + vitality) / 6f);
    }

    private static int clamp(int v) {
        return Math.max(1, Math.min(100, v));
    }

    @Override
    public String toString() {
        return String.format("STR:%d SPD:%d END:%d INT:%d STL:%d VIT:%d",
                strength, speed, endurance, intelligence, stealth, vitality);
    }
}

