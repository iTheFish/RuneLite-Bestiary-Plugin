package com.bestiary.util;

import com.bestiary.model.CapturedCreature;
import com.bestiary.model.CreatureRarity;
import com.bestiary.model.CreatureQuality;
import com.bestiary.model.MonsterRoster;

/**
 * Encodes a capture into a fixed-width 36-character card ID:
 *
 *   [dex 3][ATK 2][STR 2][DEF 2][MAG 2][RNG 2][AGI 2][rarity 1][shiny 1][HP 5][prayer 2][player 12]
 *
 * Stats 1–99 are zero-padded; 100 encodes as "00". HP is zero-padded to 5 digits,
 * Prayer to 2. Shiny is 1/0. Player name is left-padded with '0' to exactly 12 chars.
 * Rarity: 1=Common … 6=Mythic.
 *
 * HP, Prayer and Agility are all included so the ID self-describes everything that
 * feeds a card's Power Level. Two captures that differ only by owner share every
 * character except the trailing 12 — enabling future trading provenance tracking.
 */
public final class CardId {

    public static final int LENGTH = 36;
    private static final int PLAYER_LEN = 12;

    private CardId() {}

    public static String encode(int dexNumber, CapturedCreature capture) {
        CreatureQuality q = capture.quality;
        int hp     = Math.min(99999, MonsterRoster.getHitpoints(capture.npcName));
        int prayer = Math.min(99, capture.quality.prayer);
        return String.format("%03d%s%s%s%s%s%s%d%d%05d%02d%s",
                dexNumber,
                encodeStat(q.attack),
                encodeStat(q.strength),
                encodeStat(q.defence),
                encodeStat(q.magic),
                encodeStat(q.ranged),
                encodeStat(q.agility),
                rarityDigit(capture.rarity),
                capture.isShiny() ? 1 : 0,
                hp,
                prayer,
                encodePlayer(capture.playerName));
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
