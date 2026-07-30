package com.bestiary.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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

    /** RuneScape username of the player who captured this creature (the "Captured by" label on cards). */
    public String playerName;

    /** Optional user-assigned nickname for this individual capture. Null = not set. */
    public String nickname;

    /** Player has starred this capture as a favourite. Persisted to disk. */
    public boolean favourite;

    /** True when this capture won the independent shiny roll at capture time. Persisted. */
    public final boolean shiny;

    /** Rolled Prayer level for this capture (rarity-banded, half stat scale). Persisted. */
    public final int prayer;

    /** Damage the player dealt to this kill ("observed HP"). 0 = unknown → use placeholder. Persisted. */
    public final int observedHp;

    /**
     * The passive shiny-chance bonus (from the Shiny Charm shop upgrade) that was in effect when
     * this card's shiny was rolled — at capture, or at the most recent reroll. 0 for legacy/seed
     * cards and captures made with no charm. Persisted so the Odds view can show the shiny odds
     * that actually applied at the time, not the current bonus.
     */
    public final double shinyBonus;

    /** Player who last rerolled this card (empty = never rerolled — a raw pull). Persisted. */
    public final String rerolledBy;

    /**
     * Chronological log of this card's prior states — one entry per reroll, each capturing what
     * the card looked like BEFORE that reroll and who performed it. Empty = a raw pull. Persisted.
     * Drives "Rerolled N times", the unique-reroller count, and (future #77) a per-card Data panel.
     */
    public final List<RerollState> rerollHistory;

    /** An immutable snapshot of the card's state just before one reroll replaced it. */
    public static final class RerollState {
        public final CreatureRarity rarity;
        /** The 6 combat stats at this point. May be null for reroll entries stored before this was tracked. */
        public final CreatureQuality quality;
        public final int powerLevel;
        public final boolean shiny;
        public final int prayer;
        /** Player who performed the reroll that superseded this state. */
        public final String rerolledBy;
        /** UTC epoch second the reroll happened. */
        public final long epoch;

        public RerollState(CreatureRarity rarity, CreatureQuality quality, int powerLevel, boolean shiny,
                           int prayer, String rerolledBy, long epoch) {
            this.rarity     = rarity;
            this.quality    = quality;
            this.powerLevel = powerLevel;
            this.shiny      = shiny;
            this.prayer     = prayer;
            this.rerolledBy = rerolledBy != null ? rerolledBy : "";
            this.epoch      = epoch;
        }
    }

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
        this.prayer            = b.prayer >= 0 ? b.prayer : MonsterRoster.getPrayer(b.npcName);
        this.observedHp        = b.observedHp;
        this.shinyBonus        = b.shinyBonus;
        this.rerolledBy        = b.rerolledBy != null ? b.rerolledBy : "";
        this.rerollHistory     = b.rerollHistory != null
                ? Collections.unmodifiableList(new ArrayList<>(b.rerollHistory))
                : Collections.emptyList();
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
        private int prayer = -1;   // -1 = unset → defaults to the monster's base prayer
        private int observedHp = 0;
        private double shinyBonus = 0.0;
        private String rerolledBy = "";
        private List<RerollState> rerollHistory = null;

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
        public Builder prayer(int v)              { this.prayer = v; return this; }
        public Builder observedHp(int v)          { this.observedHp = v; return this; }
        public Builder shinyBonus(double v)       { this.shinyBonus = v; return this; }
        public Builder rerolledBy(String v)       { this.rerolledBy = v; return this; }
        public Builder rerollHistory(List<RerollState> v) { this.rerollHistory = v; return this; }

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

    /** How many times this card has been rerolled (0 = a raw pull). */
    public int rerollCount() {
        return rerollHistory.size();
    }

    /** The distinct set of players who have ever rerolled this card, in first-seen order. */
    public Set<String> uniqueRerollers() {
        Set<String> names = new LinkedHashSet<>();
        for (RerollState s : rerollHistory) {
            if (s.rerolledBy != null && !s.rerolledBy.isEmpty()) names.add(s.rerolledBy);
        }
        return names;
    }

    /**
     * The HP used for this card: the damage the player actually dealt to the kill
     * ("observed HP") when known, else the monster's placeholder wiki HP. For solo
     * kills of normal monsters these are ~equal; for scaled/group content observed
     * reflects the player's own contribution.
     */
    public int hitpoints() {
        return observedHp > 0 ? observedHp : MonsterRoster.getHitpoints(npcName);
    }

    /**
     * "Power Level" — the headline card metric. The average of the 7 rolled stats (6 combat +
     * Prayer, all on the 1-99 scale) PLUS two factual difficulty terms, each at 1/6 weight: the
     * monster's HP and its combat level. Splitting the terms keeps the stat average at face value
     * (~its rolled quality) while the two objective attributes separate the tiers: HP/6 adds ~13
     * at 80 HP, ~40 at 250 HP, ~165 at 1000 HP; combat level/6 adds a comparable lift (~21 at cmb
     * 124, ~233 at cmb 1400). Combat level is the killed NPC's own level (stored per capture), so
     * same-name variants keep their individual value. Power Level can exceed 99 for bosses;
     * stats/prayer are flavour.
     */
    public int powerLevel() {
        return Math.round((quality.statSum() + prayer) / 7f
                + hitpoints() / 6f
                + Math.max(0, npcCombatLevel) / 6f);
    }

    @Override
    public String toString() {
        return String.format("[%s] %s (ID %d) - %s @ %s",
                rarity.label, npcName, npcId, captureTime, regionName);
    }
}

