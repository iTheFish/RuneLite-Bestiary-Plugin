package net.runelite.client.plugins.bestiary.util;

/**
 * Maps between Capture Level (1-99) and cumulative XP.
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
 * Level 99 is the max level; XP continues to accrue past it up to the 200M
 * cap (as in OSRS) without advancing the level.
 */
public final class XpTable {

    /** Maximum Capture Level. */
    public static final int MAX_LEVEL = 99;

    /** OSRS-style experience cap. Level stays 99 but XP keeps counting to here. */
    public static final long MAX_XP = 200_000_000L;

    /** XP_FOR_LEVEL[L] = XP needed to reach level L (index 0 and 1 are both 0). */
    private static final long[] XP_FOR_LEVEL = new long[MAX_LEVEL + 1];

    static {
        double accumulator = 0;
        for (int level = 1; level <= MAX_LEVEL; level++) {
            XP_FOR_LEVEL[level] = (long) accumulator;
            double points = Math.floor((level + 300.0 * Math.pow(2.0, level / 7.0)) / 4.0);
            accumulator += points;
        }
    }

    private XpTable() {}

    /** Returns the level (1-99) for a given total XP amount. */
    public static int levelForXp(long xp) {
        for (int level = MAX_LEVEL; level >= 1; level--) {
            if (xp >= XP_FOR_LEVEL[level]) {
                return level;
            }
        }
        return 1;
    }

    /** Returns the total XP required to reach the given level. */
    public static long xpForLevel(int level) {
        if (level < 1)          return 0;
        if (level > MAX_LEVEL)  return XP_FOR_LEVEL[MAX_LEVEL];
        return XP_FOR_LEVEL[level];
    }

    /** Returns the XP still needed to advance from the current total to the next level (0 at max). */
    public static long xpToNextLevel(long currentXp) {
        int currentLevel = levelForXp(currentXp);
        if (currentLevel >= MAX_LEVEL) return 0;
        return XP_FOR_LEVEL[currentLevel + 1] - currentXp;
    }

    /** The 200M XP cap. */
    public static long maxXp() {
        return MAX_XP;
    }
}
