package com.bestiary.model;

public enum ChatNotifyMode {
    /** One message per capture; quality score appended to prevent RuneLite deduplication. */
    VERBOSE,
    /** Accumulates captures over a 5 s lull and posts a single count message per NPC+rarity. */
    BATCHED
}
