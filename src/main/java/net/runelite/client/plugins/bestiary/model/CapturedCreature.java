package net.runelite.client.plugins.bestiary.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable record of a single successful creature capture.
 * Stored permanently in the player's bestiary collection.
 */
public class CapturedCreature {

    /** Stable unique identifier for this capture record. */
    public final String id;

    /** RuneLite NPC definition ID. */
    public final int npcId;

    /** NPC display name at the time of capture. */
    public final String npcName;

    /** NPC combat level at capture (\u00e2\u02c6\u20191 for non-combat NPCs). */
    public final int npcCombatLevel;

    public final CreatureRarity rarity;
    public final CreatureQuality quality;

    /** UTC epoch second when the capture occurred. */
    public final Instant captureTime;

    /** Human-readable area name derived from the player's WorldPoint. Mutable so migrations can fix old "Region N" values. */
    public String regionName;

    /** The player's Capture Level at the moment this creature was caught. */
    public final int captureLevel;

    /** RuneScape username of the player who captured this creature. Mutable so LOGGED_IN can backfill pre-fix saves. */
    public String playerName;

    /** Optional user-assigned nickname for this individual capture. Null = not set. */
    public String nickname;

    /** Player has starred this capture as a favourite. Persisted to disk. */
    public boolean favourite;

    /** True when this capture won the independent shiny roll at capture time. Persisted. */
    public final boolean shiny;

    /** Player has chosen this capture as the album catalog cover for its monster. Persisted; one per npcName. */
    public boolean albumCover;

    /**
     * How many kills of this species the player had accumulated before this
     * capture succeeded (useful for showing "lucky" catches).
     */
    public final int killsBeforeCapture;

    private CapturedCreature(Builder b) {
        this.id                = b.id;
        this.npcId             = b.npcId;
        this.npcName           = b.npcName;
        this.npcCombatLevel    = b.npcCombatLevel;
        this.rarity            = b.rarity;
        this.quality           = b.quality;
        this.captureTime       = b.captureTime;
        this.regionName        = b.regionName;
        this.captureLevel      = b.captureLevel;
        this.killsBeforeCapture = b.killsBeforeCapture;
        this.playerName        = b.playerName != null ? b.playerName : "";
        this.shiny             = b.shiny;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id = UUID.randomUUID().toString();
        private int npcId;
        private String npcName = "Unknown";
        private int npcCombatLevel = -1;
        private CreatureRarity rarity = CreatureRarity.COMMON;
        private CreatureQuality quality;
        private Instant captureTime = Instant.now();
        private String regionName = "Unknown";
        private int captureLevel = 1;
        private int killsBeforeCapture = 0;
        private String playerName = "";
        private boolean shiny = false;

        public Builder id(String v)              { this.id = v; return this; }
        public Builder npcId(int v)             { this.npcId = v; return this; }
        public Builder npcName(String v)         { this.npcName = v; return this; }
        public Builder npcCombatLevel(int v)     { this.npcCombatLevel = v; return this; }
        public Builder rarity(CreatureRarity v)  { this.rarity = v; return this; }
        public Builder quality(CreatureQuality v){ this.quality = v; return this; }
        public Builder captureTime(Instant v)    { this.captureTime = v; return this; }
        public Builder regionName(String v)      { this.regionName = v; return this; }
        public Builder captureLevel(int v)       { this.captureLevel = v; return this; }
        public Builder killsBeforeCapture(int v) { this.killsBeforeCapture = v; return this; }
        public Builder playerName(String v)       { this.playerName = v; return this; }
        public Builder shiny(boolean v)           { this.shiny = v; return this; }

        public CapturedCreature build() {
            if (quality == null) {
                quality = new CreatureQuality(50, 50, 50, 50, 50, 50);
            }
            return new CapturedCreature(this);
        }
    }

    /** True when this capture won the independent shiny roll at capture time. */
    public boolean isShiny() {
        return shiny;
    }

    /** The monster's factual Hitpoints (wiki-sourced), shown as card info. */
    public int hitpoints() {
        return MonsterRoster.getHitpoints(npcName);
    }

    /**
     * "Power Level" — the headline card metric (replaces the old quality average).
     * Equals round((sum of the 6 rolled stats + monster HP) / 7). Because HP is a raw
     * number that dwarfs the 1-99 stats for tanky monsters, Power Level can exceed 99
     * for bosses. Rolled stats are pure flavour; HP is the real value driver.
     */
    public int powerLevel() {
        return Math.round((quality.statSum() + hitpoints()) / 7f);
    }

    @Override
    public String toString() {
        return String.format("[%s] %s (ID %d) - %s @ %s",
                rarity.label, npcName, npcId, captureTime, regionName);
    }
}

