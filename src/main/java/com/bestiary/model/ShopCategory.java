package com.bestiary.model;

/**
 * Grouping for {@link ShopUpgrade}s in the Shop tab. Display order follows the
 * enum order here, and within a category by {@link ShopUpgrade} order.
 */
public enum ShopCategory {
    PROGRESSION("Progression"),
    REROLLS("Rerolls");

    public final String label;

    ShopCategory(String label) {
        this.label = label;
    }
}
