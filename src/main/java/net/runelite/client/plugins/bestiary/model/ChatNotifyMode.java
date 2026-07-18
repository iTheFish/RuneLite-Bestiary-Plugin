package net.runelite.client.plugins.bestiary.model;

public enum ChatNotifyMode {
    /** One message per capture; quality score appended to prevent RuneLite deduplication. */
    VERBOSE,
    /** Accumulates captures over 30 s and posts a single count message per NPC+rarity. */
    BATCHED
}
