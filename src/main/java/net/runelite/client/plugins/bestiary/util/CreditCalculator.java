package net.runelite.client.plugins.bestiary.util;

import net.runelite.client.plugins.bestiary.model.CreatureRarity;
import net.runelite.client.plugins.bestiary.model.DifficultyTier;

/**
 * Computes Bestiary Credits awarded for a capture.
 *
 * Credits = difficultyBase × rarityMultiplier, doubled for a shiny.
 * The two curves are intentionally separate from the XP curve so the economy
 * can be retuned independently as the Shop (#39) is designed.
 *
 * Sample awards:
 *   Beginner Common          = 2
 *   Medium   Rare            = 20
 *   Boss     Legendary       = 280
 *   Boss     Mythic          = 480   (shiny: 960)
 */
public final class CreditCalculator {

    private CreditCalculator() {}

    private static int difficultyBase(DifficultyTier tier) {
        switch (tier) {
            case BEGINNER: return 2;
            case EASY:     return 4;
            case MEDIUM:   return 8;
            case HARD:     return 15;
            case ELITE:    return 25;
            case BOSS:     return 40;
            default:       return 8;
        }
    }

    private static double rarityMultiplier(CreatureRarity rarity) {
        switch (rarity) {
            case COMMON:    return 1.0;
            case UNCOMMON:  return 1.6;
            case RARE:      return 2.5;
            case EPIC:      return 4.0;
            case LEGENDARY: return 7.0;
            case MYTHIC:    return 12.0;
            default:        return 1.0;
        }
    }

    /** Credits awarded for a single capture. Shiny doubles the reward. Minimum 1. */
    public static long forCapture(DifficultyTier tier, CreatureRarity rarity, boolean shiny) {
        double credits = difficultyBase(tier) * rarityMultiplier(rarity);
        if (shiny) credits *= 2.0;
        return Math.max(1L, Math.round(credits));
    }

    /** Flat credit bonus a shiny adds when discarded. */
    public static final long SHINY_DISCARD_BONUS = 1000L;

    /**
     * Credits refunded for discarding a card = its base (non-shiny) capture value,
     * plus a flat {@link #SHINY_DISCARD_BONUS} for shinies. Minimum 1.
     */
    public static long forDiscard(DifficultyTier tier, CreatureRarity rarity, boolean shiny) {
        return forCapture(tier, rarity, false) + (shiny ? SHINY_DISCARD_BONUS : 0L);
    }
}
