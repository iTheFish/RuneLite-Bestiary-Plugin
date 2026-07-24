package net.runelite.client.plugins.bestiary.util;

/**
 * Maps between Capture Level and cumulative XP.
 *
 * Uses the official OSRS XP formula: Points(L) = floor((L + 300 * 2^(L/7)) / 4).
 * XP required for level L = sum of Points(1) to Points(L-1).
 *
 * Level 99 (13,034,431 XP) is the "true" skill cap used for milestones such as
 * the Capture Master achievement. Past 99 the curve continues into RuneScape-style
 * virtual levels up to level 126, which is reached at the 200M XP cap. XP keeps
 * counting (and the level keeps advancing) until 200M.
 *
 * Spot-check:
 *   Level  10 =        1,154 XP
 *   Level  50 =      101,333 XP
 *   Level  99 =   13,034,431 XP
 *   Level 126 =  188,884,740 XP
 */
public final class XpTable {

    /** The "true" skill cap (used for the Capture Master milestone). */
    public static final int MAX_LEVEL = 99;

    /** Highest virtual level, reached at the 200M XP cap. */
    public static final int MAX_VIRTUAL_LEVEL = 126;

    /** OSRS-style experience cap. */
    public static final long MAX_XP = 200_000_000L;

    /** XP_FOR_LEVEL[L] = XP needed to reach level L (index 0 and 1 are both 0). */
    private static final long[] XP_FOR_LEVEL = new long[MAX_VIRTUAL_LEVEL + 1];

    static {
        double accumulator = 0;
        for (int level = 1; level <= MAX_VIRTUAL_LEVEL; level++) {
            XP_FOR_LEVEL[level] = (long) accumulator;
            double points = Math.floor((level + 300.0 * Math.pow(2.0, level / 7.0)) / 4.0);
            accumulator += points;
        }
    }

    private XpTable() {}

    /** Returns the level (1-126) for a given total XP amount. */
    public static int levelForXp(long xp) {
        for (int level = MAX_VIRTUAL_LEVEL; level >= 1; level--) {
            if (xp >= XP_FOR_LEVEL[level]) {
                return level;
            }
        }
        return 1;
    }

    /** Returns the total XP required to reach the given level. */
    public static long xpForLevel(int level) {
        if (level < 1)                  return 0;
        if (level > MAX_VIRTUAL_LEVEL)  return XP_FOR_LEVEL[MAX_VIRTUAL_LEVEL];
        return XP_FOR_LEVEL[level];
    }

    /** Returns the XP still needed to advance from the current total to the next level (0 at level 126). */
    public static long xpToNextLevel(long currentXp) {
        int currentLevel = levelForXp(currentXp);
        if (currentLevel >= MAX_VIRTUAL_LEVEL) return 0;
        return XP_FOR_LEVEL[currentLevel + 1] - currentXp;
    }

    /** The 200M XP cap. */
    public static long maxXp() {
        return MAX_XP;
    }
}
