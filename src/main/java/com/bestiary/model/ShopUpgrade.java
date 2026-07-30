package com.bestiary.model;

/**
 * Passive shop unlocks (issue #39). Each upgrade has up to {@link #maxTier} tiers; buying a tier
 * adds {@link #perTierEffect} to a passive bonus that is applied automatically forever after.
 *
 * <p>Costs either escalate geometrically ({@code baseCost * 2^ownedTiers}) or follow an explicit
 * per-tier curve ({@link #tierCosts}, used by the cheap credit boosts). Effects are stored as
 * fractional amounts (0.001 = 0.1%, 0.02 = 2%) additive to the base rates they modify.
 */
public enum ShopUpgrade {

    /** Flat +credits added to every capture reward (cheap, early-game). effect = flat credits/tier. */
    CREDIT_CAPTURE(
            "Hunter's Bounty",
            "Adds flat bonus credits to every capture (helps low-tier catches).",
            ShopCategory.PROGRESSION, 5, new long[]{10, 20, 50, 100, 250}, 2.0),

    /** +credits from discarding cards (cheap, early-game). */
    CREDIT_DISCARD(
            "Salvager's Eye",
            "Raises the credits you earn from discarding cards.",
            ShopCategory.PROGRESSION, 5, new long[]{10, 20, 50, 100, 250}, 0.02),

    /** Flat +XP added to every non-capture (kill) XP award. effect = flat XP/tier (+5..+30). */
    KILL_XP(
            "Hunter's Focus",
            "Adds flat bonus XP to every kill, on top of the base kill XP.",
            ShopCategory.PROGRESSION, 5, new long[]{500, 1000, 2000, 4000, 8000}, 5.0),

    /** +% to the XP earned on every capture (+5%..+25%). */
    CAPTURE_XP(
            "Scholar's Insight",
            "Increases the XP earned from every successful capture.",
            ShopCategory.PROGRESSION, 5, new long[]{500, 1000, 2500, 5000, 10000}, 0.05),

    /** Adds to the passive shiny chance on every capture. */
    SHINY_CHANCE(
            "Shiny Charm",
            "Raises your passive shiny chance on every capture.",
            ShopCategory.PROGRESSION, 5, 1500, 0.001),

    /** Adds to the shiny chance rolled when a card is rerolled. */
    REROLL_SHINY(
            "Reroll Shine",
            "Raises the shiny chance when you reroll a card.",
            ShopCategory.REROLLS, 5, 1500, 0.001),

    /** Adds to the chance a reroll bumps a non-Mythic card up one rarity. */
    REROLL_RARITY(
            "Reroll Fortune",
            "Raises the chance a reroll bumps a card up one rarity.",
            ShopCategory.REROLLS, 5, 1500, 0.01);

    public final String       title;
    public final String       description;
    public final ShopCategory category;
    public final int          maxTier;
    public final long         baseCost;
    /** Explicit per-tier cost curve; null = use the geometric {@link #baseCost} curve. */
    public final long[]       tierCosts;
    public final double       perTierEffect;

    /** Geometric-cost upgrade. */
    ShopUpgrade(String title, String description, ShopCategory category,
                int maxTier, long baseCost, double perTierEffect) {
        this(title, description, category, maxTier, baseCost, null, perTierEffect);
    }

    /** Explicit per-tier-cost upgrade. */
    ShopUpgrade(String title, String description, ShopCategory category,
                int maxTier, long[] tierCosts, double perTierEffect) {
        this(title, description, category, maxTier, tierCosts[0], tierCosts, perTierEffect);
    }

    ShopUpgrade(String title, String description, ShopCategory category,
                int maxTier, long baseCost, long[] tierCosts, double perTierEffect) {
        this.title         = title;
        this.description   = description;
        this.category      = category;
        this.maxTier       = maxTier;
        this.baseCost      = baseCost;
        this.tierCosts     = tierCosts;
        this.perTierEffect = perTierEffect;
    }

    /** Cost to buy the tier after {@code ownedTiers}. */
    public long costForNextTier(int ownedTiers) {
        int owned = Math.max(0, ownedTiers);
        if (tierCosts != null) {
            return tierCosts[Math.min(owned, tierCosts.length - 1)];
        }
        return baseCost * (1L << owned);
    }

    /** Total passive bonus granted by owning {@code ownedTiers} tiers. */
    public double effectFor(int ownedTiers) {
        return perTierEffect * Math.max(0, Math.min(maxTier, ownedTiers));
    }

    /** True if {@link #effectFor} is a flat credit amount (vs. a fractional percentage). */
    public boolean isFlatCredits() {
        return this == CREDIT_CAPTURE;
    }

    /** True if {@link #effectFor} is a flat XP amount (vs. a fractional percentage). */
    public boolean isFlatXp() {
        return this == KILL_XP;
    }
}
