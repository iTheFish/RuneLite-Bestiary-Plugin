package com.bestiary.model;

/**
 * Minimum rarity a capture must be to trigger a chat notification. {@link #ALL} notifies for every
 * capture; the others notify only for that rarity and above. (Shinies always notify regardless.)
 */
public enum CaptureNotifyFilter {

    ALL      (CreatureRarity.COMMON,    "All"),
    UNCOMMON (CreatureRarity.UNCOMMON,  "Uncommon+"),
    RARE     (CreatureRarity.RARE,      "Rare+"),
    EPIC     (CreatureRarity.EPIC,      "Epic+"),
    LEGENDARY(CreatureRarity.LEGENDARY, "Legendary+"),
    MYTHIC   (CreatureRarity.MYTHIC,    "Mythic only");

    private final CreatureRarity min;
    private final String label;

    CaptureNotifyFilter(CreatureRarity min, String label) {
        this.min = min;
        this.label = label;
    }

    /** True if a capture of {@code rarity} clears this filter's threshold. */
    public boolean accepts(CreatureRarity rarity) {
        return rarity.ordinal() >= min.ordinal();
    }

    @Override
    public String toString() {
        return label;
    }
}
