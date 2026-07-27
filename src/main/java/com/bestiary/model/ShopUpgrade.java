package com.bestiary.model;

/**
 * Passive shop unlocks (issue #39). Each upgrade has up to {@link #maxTier} tiers; buying a tier
 * adds {@link #perTierEffect} to a passive bonus that is applied automatically forever after.
 *
 * <p>Costs escalate geometrically ({@code baseCost * 2^ownedTiers}) so later tiers are a real
 * credit sink. Effects are stored as fractional probabilities (0.001 = 0.1%) and are purely
 * additive to the base rates in {@code CaptureService}/{@code BestiaryDataService}.
 */
public enum ShopUpgrade {

    /** Adds to the passive shiny chance on every capture — and on every reroll. */
    SHINY_CHANCE(
            "Shiny Charm",
            "Raises your passive shiny chance. Applies to captures and rerolls.",
            5, 2000, 0.001),

    /** Adds to the chance a reroll bumps a non-Mythic card up one rarity. */
    REROLL_RARITY(
            "Reroll Fortune",
            "Raises the chance a reroll bumps a card up one rarity.",
            5, 3000, 0.01);

    public final String title;
    public final String description;
    public final int    maxTier;
    public final long   baseCost;
    public final double perTierEffect;

    ShopUpgrade(String title, String description, int maxTier, long baseCost, double perTierEffect) {
        this.title         = title;
        this.description   = description;
        this.maxTier       = maxTier;
        this.baseCost      = baseCost;
        this.perTierEffect = perTierEffect;
    }

    /** Cost to buy the tier after {@code ownedTiers} (geometric). */
    public long costForNextTier(int ownedTiers) {
        return baseCost * (1L << Math.max(0, ownedTiers));
    }

    /** Total passive bonus granted by owning {@code ownedTiers} tiers. */
    public double effectFor(int ownedTiers) {
        return perTierEffect * Math.max(0, Math.min(maxTier, ownedTiers));
    }
}
