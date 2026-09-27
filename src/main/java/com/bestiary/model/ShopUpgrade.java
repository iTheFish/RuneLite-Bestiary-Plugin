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

    /**
     * Chance that a capture rolls ONE rarity higher than it landed (Mythic can't climb further).
     * Owning any tier is what enables this extra roll — with zero tiers it never happens, which is
     * why the first tier is the priciest. effect = added rarity-up chance/tier.
     */
    CAPTURE_RARITY(
            "Fortune's Favour",
            "Gives every capture a chance to roll one rarity higher than it landed (Mythic can't "
                    + "climb further). Buying the first tier unlocks the roll; further tiers raise "
                    + "the chance. A proc is announced in chat.",
            ShopCategory.MECHANICS, 5, new long[]{7500, 1500, 3000, 4500, 6000}, 0.01),

    /**
     * Chance that a capture rolls its rarity TWICE and keeps the better result. Owning any tier is
     * what enables the second roll — with zero tiers it never happens, which is why the first tier is
     * the priciest. effect = added double-roll chance/tier (2% → 10%).
     */
    CAPTURE_DOUBLE_ROLL(
            "Keen Instinct",
            "Gives every kill a chance to attempt the capture twice and keep the better outcome, even "
                    + "turning a miss into a catch. Buying the first tier unlocks it; further tiers "
                    + "raise the chance.",
            ShopCategory.MECHANICS, 5, new long[]{25000, 10000, 15000, 20000, 25000}, 0.02),

    /** Adds to the passive shiny chance on every capture. */
    SHINY_CHANCE(
            "Shiny Charm",
            "Raises your passive shiny chance on every capture.",
            ShopCategory.MECHANICS, 5, 1500, 0.001),

    /** Adds to the shiny chance rolled when a card is rerolled. */
    REROLL_SHINY(
            "Reroll Shine",
            "Raises the shiny chance when you reroll a card.",
            ShopCategory.REROLLS, 5, 1500, 0.001),

    /** Adds to the chance a reroll bumps a non-Mythic card up one rarity. */
    REROLL_RARITY(
            "Reroll Fortune",
            "Raises the chance a reroll bumps a card up one rarity.",
            ShopCategory.REROLLS, 5, 1500, 0.01),

    /** Reduces the credit cost of every Card Reroller use (fractional discount/tier). */
    REROLL_COST(
            "Haggler",
            "Haggles down the credit cost of every Card Reroller use, stacking to a 20% discount "
                    + "at max tier.",
            ShopCategory.REROLLS, 5, new long[]{1500, 3000, 4500, 6000, 7500}, 0.04),

    // --- Level 99 endgame shop (all gated behind Capture Level 99; see purchaseUpgrade) ---

    /** Scholar's Insight II — extends the capture-XP % boost past the base upgrade (stacks additively). */
    CAPTURE_XP_II(
            "Scholar's Insight II",
            "Endgame extension of Scholar's Insight: further increases the XP earned from every "
                    + "successful capture, stacking on top of the base upgrade.",
            ShopCategory.LEVEL_99, 5, new long[]{5000, 10000, 20000, 40000, 80000}, 0.05),

    /** Hunter's Focus II — extends the flat kill-XP bonus (stacks additively). */
    KILL_XP_II(
            "Hunter's Focus II",
            "Endgame extension of Hunter's Focus: adds further flat bonus XP to every kill, stacking "
                    + "on top of the base upgrade.",
            ShopCategory.LEVEL_99, 5, new long[]{5000, 10000, 20000, 40000, 80000}, 5.0),

    /** Hunter's Bounty II — extends the flat capture-credit bonus (stacks additively). */
    CREDIT_CAPTURE_II(
            "Hunter's Bounty II",
            "Endgame extension of Hunter's Bounty: adds further flat bonus credits to every capture, "
                    + "stacking on top of the base upgrade.",
            ShopCategory.LEVEL_99, 5, new long[]{2500, 5000, 10000, 20000, 40000}, 10.0),

    /** Raises catch rate on ELITE-tier monsters (+2%/tier → +10% at max). */
    CATCH_RATE_ELITE(
            "Elite Tracker",
            "Raises your catch rate against Elite-tier monsters. Elite tops out at 35% at Capture "
                    + "Level 99 — this adds up to a further +10% on top.",
            ShopCategory.LEVEL_99, 5, new long[]{20000, 40000, 60000, 80000, 100000}, 0.02),

    /** Raises catch rate on BOSS-tier monsters (+2%/tier → +10% at max). */
    CATCH_RATE_BOSS(
            "Boss Tracker",
            "Raises your catch rate against Boss-tier monsters. Boss tops out at 25% at Capture "
                    + "Level 99 — this adds up to a further +10% on top.",
            ShopCategory.LEVEL_99, 5, new long[]{30000, 60000, 90000, 120000, 150000}, 0.02);

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
        return this == CREDIT_CAPTURE || this == CREDIT_CAPTURE_II;
    }

    /** True if {@link #effectFor} is a flat XP amount (vs. a fractional percentage). */
    public boolean isFlatXp() {
        return this == KILL_XP || this == KILL_XP_II;
    }
}
