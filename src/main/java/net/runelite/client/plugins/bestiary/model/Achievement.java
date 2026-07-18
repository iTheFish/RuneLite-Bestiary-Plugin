package net.runelite.client.plugins.bestiary.model;

import java.awt.Color;

/**
 * Achievements that can be unlocked through bestiary activity.
 * countThreshold == 0 means the achievement is not unlock-by-count
 * and must be checked with custom logic in ProgressionService.
 */
public enum Achievement {

    FIRST_CATCH        ("First Blood",           "Capture your first creature",          1,   false, new Color(255, 200, 80)),
    TEN_CATCHES        ("Collector",             "Capture 10 creatures",                 10,  false, new Color(255, 200, 80)),
    FIFTY_CATCHES      ("Enthusiast",            "Capture 50 creatures",                 50,  false, new Color(255, 200, 80)),
    HUNDRED_CATCHES    ("Hoarder",               "Capture 100 creatures",                100, false, new Color(255, 200, 80)),
    FIVE_SPECIES       ("Scout",                 "Capture 5 different species",          5,   true,  new Color(255, 215, 0)),
    TWENTY_SPECIES     ("Naturalist",            "Capture 20 different species",         20,  true,  new Color(255, 215, 0)),
    FIFTY_SPECIES      ("Zoologist",             "Capture 50 different species",         50,  true,  new Color(255, 215, 0)),
    UNCOMMON_CATCH     ("Getting Started",       "Capture an Uncommon or better",        0,   false, new Color(80,  200, 80)),
    RARE_CATCH         ("Lucky Strike",          "Capture a Rare or better",             0,   false, new Color(80,  140, 255)),
    EPIC_CATCH         ("Storm Chaser",          "Capture an Epic or better",            0,   false, new Color(100, 180, 255)),
    LEGENDARY_CATCH    ("Fortune's Favourite",   "Capture a Legendary",                  0,   false, new Color(255, 165, 0)),
    MYTHIC_CATCH       ("Beyond Myth",           "Capture a Mythic creature",            0,   false, new Color(255, 50,  50)),
    FIVE_HUNDRED_KILLS ("Veteran",               "Accumulate 500 kills",                 0,   false, new Color(180, 180, 180)),
    FIVE_K_KILLS       ("Slaughterer",           "Accumulate 5,000 kills",               0,   false, new Color(180, 180, 180)),
    LEVEL_10           ("Apprentice Hunter",     "Reach Capture Level 10",               0,   false, new Color(255, 200, 80)),
    LEVEL_25           ("Hunter in Training",    "Reach Capture Level 25",               0,   false, new Color(255, 200, 80)),
    LEVEL_50           ("Seasoned Hunter",       "Reach Capture Level 50",               0,   false, new Color(255, 200, 80)),
    LEVEL_75           ("Expert Hunter",         "Reach Capture Level 75",               0,   false, new Color(255, 200, 80)),
    LEVEL_100          ("Master Hunter",         "Reach Capture Level 100",              0,   false, new Color(255, 165, 0));

    public final String title;
    public final String description;
    public final int countThreshold;
    public final boolean isSpeciesBased;
    /** Color used for the achievement title in chat notifications. */
    public final Color chatColor;

    Achievement(String title, String description, int countThreshold, boolean isSpeciesBased, Color chatColor) {
        this.title          = title;
        this.description    = description;
        this.countThreshold = countThreshold;
        this.isSpeciesBased = isSpeciesBased;
        this.chatColor      = chatColor;
    }
}
