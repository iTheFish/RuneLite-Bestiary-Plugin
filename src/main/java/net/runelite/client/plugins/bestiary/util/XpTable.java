package net.runelite.client.plugins.bestiary.util;

/**
 * Maps between Capture Level (1-100) and cumulative XP.
 *
 * Uses the official OSRS XP formula: Points(L) = floor((L + 300 * 2^(L/7)) / 4).
 * XP required for level L = sum of Points(1) to Points(L-1).
 *
 * Generates values identical to the OSRS in-game skill table. Spot-check:
 *   Level  2 =         83 XP
 *   Level 10 =      1,154 XP
 *   Level 50 =    101,333 XP
 *   Level 75 =  1,210,421 XP
 *   Level 99 = 13,034,431 XP
 * Level 100 cap continues the curve beyond the standard OSRS table.
 */
public final class XpTable {

    /** XP_FOR_LEVEL[L] = XP needed to reach level L (index 0 and 1 are both 0). */
    private static final long[] XP_FOR_LEVEL = new long[101];

    static {
        double accumulator = 0;
        for (int level = 1; level <= 99; level++) {
            double points = Math.floor((level + 300.0 * Math.pow(2.0, level / 7.0)) / 4.0);
            XP_FOR_LEVEL[level] = (long) accumulator;
            accumulator += points;
        }
        XP_FOR_LEVEL[100] = (long) accumulator;
    }

    private XpTable() {}

    /** Returns the level (1\u00e2\u20ac"100) for a given total XP amount. */
    public static int levelForXp(long xp) {
        for (int level = 100; level >= 1; level--) {
            if (xp >= XP_FOR_LEVEL[level]) {
                return level;
            }
        }
        return 1;
    }

    /** Returns the total XP required to reach the given level. */
    public static long xpForLevel(int level) {
        if (level < 1)   return 0;
        if (level > 100) return XP_FOR_LEVEL[100];
        return XP_FOR_LEVEL[level];
    }

    /** Returns the XP still needed to advance from the current total to the next level. */
    public static long xpToNextLevel(long currentXp) {
        int currentLevel = levelForXp(currentXp);
        if (currentLevel >= 100) return 0;
        return XP_FOR_LEVEL[currentLevel + 1] - currentXp;
    }

    /** XP required for level 100 (the cap). */
    public static long maxXp() {
        return XP_FOR_LEVEL[100];
    }
}

