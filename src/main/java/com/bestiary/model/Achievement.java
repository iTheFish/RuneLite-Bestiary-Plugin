package com.bestiary.model;

import java.awt.Color;

/**
 * Achievements that can be unlocked through bestiary activity.
 * countThreshold == 0 means the achievement is not unlock-by-count
 * and must be checked with custom logic in ProgressionService.
 */
public enum Achievement {

    FIRST_CATCH        ("First Blood",           "Capture your first creature",          1,    false, new Color(255, 200, 80)),
    TEN_CATCHES        ("Collector",             "Capture 10 creatures",                 10,   false, new Color(255, 200, 80)),
    FIFTY_CATCHES      ("Enthusiast",            "Capture 50 creatures",                 50,   false, new Color(255, 200, 80)),
    HUNDRED_CATCHES    ("Hoarder",               "Capture 100 creatures",                100,  false, new Color(255, 200, 80)),
    TWO_FIFTY_CATCHES  ("Curator",               "Capture 250 creatures",                250,  false, new Color(255, 200, 80)),
    FIVE_HUNDRED_CATCHES("Archivist",            "Capture 500 creatures",                500,  false, new Color(255, 200, 80)),
    THOUSAND_CATCHES   ("Grand Collector",       "Capture 1,000 creatures",              1000, false, new Color(255, 165, 0)),
    FIVE_SPECIES       ("Scout",                 "Capture 5 different species",          5,    true,  new Color(255, 215, 0)),
    TWENTY_SPECIES     ("Naturalist",            "Capture 20 different species",         20,   true,  new Color(255, 215, 0)),
    FIFTY_SPECIES      ("Zoologist",             "Capture 50 different species",         50,   true,  new Color(255, 215, 0)),
    UNCOMMON_CATCH     ("Getting Started",       "Capture an Uncommon or better",        0,    false, new Color(80,  200, 80)),
    RARE_CATCH         ("Lucky Strike",          "Capture a Rare or better",             0,    false, new Color(80,  140, 255)),
    EPIC_CATCH         ("Storm Chaser",          "Capture an Epic or better",            0,    false, new Color(100, 180, 255)),
    LEGENDARY_CATCH    ("Fortune's Favourite",   "Capture a Legendary",                  0,    false, new Color(255, 165, 0)),
    MYTHIC_CATCH       ("Beyond Myth",           "Capture a Mythic creature",            0,    false, new Color(255, 50,  50)),
    SHINY_CATCH        ("Shiny Hunter",          "Capture a shiny creature",             0,    false, new Color(255, 240, 150)),
    FIVE_HUNDRED_KILLS ("Veteran",               "Accumulate 500 kills",                 0,    false, new Color(180, 180, 180)),
    FIVE_K_KILLS       ("Slaughterer",           "Accumulate 5,000 kills",               0,    false, new Color(180, 180, 180)),
    LEVEL_5            ("Novice Hunter",         "Reach Capture Level 5",                0,    false, new Color(255, 200, 80)),
    LEVEL_10           ("Apprentice Hunter",     "Reach Capture Level 10",               0,    false, new Color(255, 200, 80)),
    LEVEL_25           ("Hunter in Training",    "Reach Capture Level 25",               0,    false, new Color(255, 200, 80)),
    LEVEL_30           ("Adept Hunter",          "Reach Capture Level 30",               0,    false, new Color(255, 200, 80)),
    LEVEL_40           ("Skilled Hunter",        "Reach Capture Level 40",               0,    false, new Color(255, 200, 80)),
    LEVEL_50           ("Seasoned Hunter",       "Reach Capture Level 50",               0,    false, new Color(255, 200, 80)),
    LEVEL_60           ("Veteran Hunter",        "Reach Capture Level 60",               0,    false, new Color(255, 200, 80)),
    LEVEL_70           ("Master Tracker",        "Reach Capture Level 70",               0,    false, new Color(255, 200, 80)),
    LEVEL_75           ("Expert Hunter",         "Reach Capture Level 75",               0,    false, new Color(255, 200, 80)),
    LEVEL_80           ("Elite Hunter",          "Reach Capture Level 80",               0,    false, new Color(255, 180, 80)),
    LEVEL_85           ("Grandmaster Hunter",    "Reach Capture Level 85",               0,    false, new Color(255, 180, 80)),
    LEVEL_90           ("Legendary Hunter",      "Reach Capture Level 90",               0,    false, new Color(255, 165, 0)),
    LEVEL_92           ("Halfway There",         "Reach Capture Level 92",               0,    false, new Color(255, 165, 0)),
    LEVEL_95           ("Almost There",          "Reach Capture Level 95",               0,    false, new Color(255, 165, 0)),
    LEVEL_99           ("Capture Master",        "Reach Capture Level 99",               0,    false, new Color(255, 120, 20)),

    // --- Credits earned (lifetime) ---
    EARN_1K            ("Coin Purse",             "Earn 1,000 credits",                   1000,   false, new Color(255, 215, 0)),
    EARN_5K            ("Money Bags",             "Earn 5,000 credits",                   5000,   false, new Color(255, 215, 0)),
    EARN_10K           ("Small Fortune",          "Earn 10,000 credits",                  10000,  false, new Color(255, 215, 0)),
    EARN_50K           ("Well Off",               "Earn 50,000 credits",                  50000,  false, new Color(255, 200, 80)),
    EARN_100K          ("Prospector",             "Earn 100,000 credits",                 100000, false, new Color(255, 165, 0)),

    // --- Credits spent (lifetime) ---
    SPEND_1K           ("Window Shopper",         "Spend 1,000 credits",                  1000,   false, new Color(255, 215, 0)),
    SPEND_5K           ("Regular",                "Spend 5,000 credits",                  5000,   false, new Color(255, 215, 0)),
    SPEND_10K          ("Patron",                 "Spend 10,000 credits",                 10000,  false, new Color(255, 200, 80)),
    SPEND_50K          ("High Roller",            "Spend 50,000 credits",                 50000,  false, new Color(255, 180, 80)),
    SPEND_100K         ("Big Spender",            "Spend 100,000 credits",                100000, false, new Color(255, 165, 0)),

    // --- Rerolls ---
    REROLL_FIRST       ("Second Chances",         "Reroll a card for the first time",     1,      false, new Color(80,  140, 255)),
    REROLL_5           ("Do-Over",                "Reroll 5 cards",                       5,      false, new Color(80,  140, 255)),
    REROLL_10          ("Tinkerer",               "Reroll 10 cards",                      10,     false, new Color(80,  140, 255)),
    REROLL_25          ("Perfectionist",          "Reroll 25 cards",                      25,     false, new Color(100, 180, 255)),
    REROLL_50          ("Reroll Addict",          "Reroll 50 cards",                      50,     false, new Color(100, 180, 255)),
    REROLL_RANK_UP     ("Against the Odds",       "Rank a card up a rarity by rerolling", 0,      false, new Color(170, 120, 235)),

    // --- Collection milestones ---
    POWERHOUSE         ("Powerhouse",             "Own a card with Power Level 150+",     0,      false, new Color(240, 150, 45)),
    FULL_HOUSE         ("Full House",             "Catch all six rarities of one monster",0,      false, new Color(255, 165, 0)),
    CHROMATIC          ("Chromatic",              "Capture 5 shiny creatures",            5,      false, new Color(255, 240, 150)),
    COMPLETIONIST      ("Completionist",          "Catch every monster in the album",     0,      false, new Color(255, 120, 20)),
    CURATED            ("Curated",                "Favourite a card",                     1,      false, new Color(255, 215, 0));

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
