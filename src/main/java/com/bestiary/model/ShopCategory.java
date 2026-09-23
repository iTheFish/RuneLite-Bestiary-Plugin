package com.bestiary.model;

/**
 * Grouping for {@link ShopUpgrade}s in the Shop tab. Display order follows the
 * enum order here, and within a category by {@link ShopUpgrade} order.
 */
public enum ShopCategory {
    PROGRESSION("Progression"),
    REROLLS("Rerolls"),
    /** Upgrades that alter the capture roll itself (rarity/shiny/double-roll). */
    MECHANICS("Mechanics"),
    /** Endgame shop — placeholder for now; consumables unlock at Capture Level 99. */
    LEVEL_99("Level 99");

    public final String label;

    ShopCategory(String label) {
        this.label = label;
    }
}
