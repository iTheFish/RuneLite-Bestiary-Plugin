package net.runelite.client.plugins.bestiary.model;

/**
 * Achievements that can be unlocked through bestiary activity.
 * countThreshold == 0 means the achievement is not unlock-by-count
 * and must be checked with custom logic in ProgressionService.
 */
public enum Achievement {

    FIRST_CATCH        ("First Blood",           "Capture your first creature",          1,   false),
    TEN_CATCHES        ("Collector",            "Capture 10 creatures",                 10,  false),
    FIFTY_CATCHES      ("Enthusiast",           "Capture 50 creatures",                 50,  false),
    HUNDRED_CATCHES    ("Hoarder",              "Capture 100 creatures",                100, false),
    FIVE_SPECIES       ("Scout",               "Capture 5 different species",           5,   true),
    TWENTY_SPECIES     ("Naturalist",          "Capture 20 different species",          20,  true),
    FIFTY_SPECIES      ("Zoologist",           "Capture 50 different species",          50,  true),
    UNCOMMON_CATCH     ("Getting Started",     "Capture an Uncommon or better",         0,   false),
    RARE_CATCH         ("Lucky Strike",        "Capture a Rare or better",              0,   false),
    EPIC_CATCH         ("Storm Chaser",        "Capture an Epic or better",             0,   false),
    LEGENDARY_CATCH    ("Fortune's Favourite", "Capture a Legendary",                   0,   false),
    MYTHIC_CATCH       ("Beyond Myth",         "Capture a Mythic creature",             0,   false),
    FIVE_HUNDRED_KILLS ("Veteran",             "Accumulate 500 kills",                  0,   false),
    FIVE_K_KILLS       ("Slaughterer",         "Accumulate 5,000 kills",                0,   false),
    LEVEL_10           ("Apprentice Hunter",   "Reach Capture Level 10",                0,   false),
    LEVEL_25           ("Hunter in Training",  "Reach Capture Level 25",                0,   false),
    LEVEL_50           ("Seasoned Hunter",     "Reach Capture Level 50",                0,   false),
    LEVEL_75           ("Expert Hunter",       "Reach Capture Level 75",                0,   false),
    LEVEL_100          ("Master Hunter",       "Reach Capture Level 100",               0,   false);

    public final String title;
    public final String description;

    /**
     * If > 0: unlock when total captures (or species count if isSpeciesBased) reaches this value.
     * If 0: checked via custom logic.
     */
    public final int countThreshold;

    /** When true, countThreshold applies to unique species rather than total captures. */
    public final boolean isSpeciesBased;

    Achievement(String title, String description, int countThreshold, boolean isSpeciesBased) {
        this.title         = title;
        this.description   = description;
        this.countThreshold = countThreshold;
        this.isSpeciesBased = isSpeciesBased;
    }
}

