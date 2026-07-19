package net.runelite.client.plugins.bestiary.util;

import net.runelite.client.plugins.bestiary.model.CapturedCreature;
import net.runelite.client.plugins.bestiary.model.CreatureRarity;
import net.runelite.client.plugins.bestiary.model.CreatureQuality;

/**
 * Encodes a capture into a fixed-width 28-character card ID:
 *
 *   [dex 3][str 2][spd 2][end 2][int 2][stl 2][vit 2][rarity 1][player 12]
 *
 * Stats 1–99 are zero-padded; 100 encodes as "00".
 * Player name is left-padded with '0' to exactly 12 characters.
 * Rarity: 1=Common … 6=Mythic.
 *
 * Two captures of the same monster/stats/rarity by different players are
 * identical except for the trailing 12 player characters — enabling future
 * trading provenance tracking.
 */
public final class CardId {

    public static final int LENGTH = 28;
    private static final int PLAYER_LEN = 12;

    private CardId() {}

    public static String encode(int dexNumber, CapturedCreature capture, String playerName) {
        CreatureQuality q = capture.quality;
        return String.format("%03d%s%s%s%s%s%s%d%s",
                dexNumber,
                encodeStat(q.strength),
                encodeStat(q.speed),
                encodeStat(q.endurance),
                encodeStat(q.intelligence),
                encodeStat(q.stealth),
                encodeStat(q.vitality),
                rarityDigit(capture.rarity),
                encodePlayer(playerName));
    }

    /** Returns the dex number encoded in the ID (chars 0–2), or -1 if malformed. */
    public static int decodeDex(String id) {
        if (id == null || id.length() != LENGTH) return -1;
        try { return Integer.parseInt(id.substring(0, 3)); } catch (NumberFormatException e) { return -1; }
    }

    /** Returns the player name decoded from the ID (trailing 12 chars, leading zeros stripped). */
    public static String decodePlayer(String id) {
        if (id == null || id.length() != LENGTH) return "";
        return id.substring(LENGTH - PLAYER_LEN).replaceFirst("^0+(?!$)", "");
    }

    // -------------------------------------------------------------------------

    private static String encodeStat(int stat) {
        return String.format("%02d", stat == 100 ? 0 : stat);
    }

    private static int rarityDigit(CreatureRarity rarity) {
        switch (rarity) {
            case UNCOMMON:  return 2;
            case RARE:      return 3;
            case EPIC:      return 4;
            case LEGENDARY: return 5;
            case MYTHIC:    return 6;
            default:        return 1; // COMMON
        }
    }

    private static String encodePlayer(String playerName) {
        if (playerName == null) playerName = "";
        // Truncate to 12 if too long (unlikely — RS max is 12)
        if (playerName.length() > PLAYER_LEN) playerName = playerName.substring(0, PLAYER_LEN);
        // Left-pad with '0'
        return String.format("%0" + PLAYER_LEN + "d", 0).substring(playerName.length()) + playerName;
    }
}
