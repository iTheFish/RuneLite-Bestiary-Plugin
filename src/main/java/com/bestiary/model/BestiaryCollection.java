package com.bestiary.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Container for the player's entire bestiary — the in-memory model.
 * Persistence is handled by BestiaryStore (JSON); this object is loaded
 * from / snapshotted to disk by BestiaryDataService.
 */
public class BestiaryCollection {

    /** All captures in chronological order. */
    public List<CapturedCreature> creatures = new ArrayList<>();

    /** npcName -> total kills (keyed by name so variants like "Goblin" all count together). */
    public Map<String, Integer> killCounts = new HashMap<>();

    /** npcName -> total successful captures. */
    public Map<String, Integer> captureCountByNpc = new HashMap<>();

    /** Bestiary Credits — earned on capture, spent in the Shop. */
    public long credits = 0;

    /** Lifetime credits ever earned (captures + discards + bonuses). Never decreases. */
    public long lifetimeCreditsEarned = 0;

    /** Lifetime credits ever spent (rerolls + shop upgrades). Never decreases. */
    public long lifetimeCreditsSpent = 0;

    /** Owned shop-upgrade tiers, keyed by {@link ShopUpgrade#name()}. Absent = 0 tiers. */
    public Map<String, Integer> shopUpgrades = new HashMap<>();

    /** Tiers owned of a passive shop upgrade (0 if never bought). */
    public int getUpgradeTier(ShopUpgrade u) {
        return shopUpgrades.getOrDefault(u.name(), 0);
    }

    // --- Aggregate helpers (used by achievements + the economy dashboard) ---

    /** Total reroll operations performed across every owned card. */
    public int totalRerolls() {
        int sum = 0;
        for (CapturedCreature c : creatures) sum += c.rerollCount();
        return sum;
    }

    /** True if any card was ever bumped up a rarity by a reroll (its history holds a lower rarity). */
    public boolean hasRerollRankUp() {
        for (CapturedCreature c : creatures) {
            for (CapturedCreature.RerollState s : c.rerollHistory) {
                if (s.rarity != null && s.rarity.ordinal() < c.rarity.ordinal()) return true;
            }
        }
        return false;
    }

    /** Number of shiny cards owned. */
    public long shinyCount() {
        return creatures.stream().filter(CapturedCreature::isShiny).count();
    }

    /** Highest Power Level of any owned card (0 if empty). */
    public int maxPowerLevel() {
        int max = 0;
        for (CapturedCreature c : creatures) max = Math.max(max, c.powerLevel());
        return max;
    }

    /** True if any single monster has been caught in all six rarities. */
    public boolean hasFullRaritySet() {
        Map<String, java.util.EnumSet<CreatureRarity>> byName = new HashMap<>();
        for (CapturedCreature c : creatures) {
            byName.computeIfAbsent(c.npcName, k -> java.util.EnumSet.noneOf(CreatureRarity.class)).add(c.rarity);
        }
        for (java.util.EnumSet<CreatureRarity> set : byName.values()) {
            if (set.size() >= CreatureRarity.values().length) return true;
        }
        return false;
    }

    /** True if at least one card is favourited. */
    public boolean hasFavourite() {
        return creatures.stream().anyMatch(c -> c.favourite);
    }

    /** True once every monster on the roster has at least one capture (album complete). */
    public boolean isAlbumComplete() {
        java.util.Set<String> caught = new java.util.HashSet<>();
        for (CapturedCreature c : creatures) caught.add(c.npcName);
        for (String name : MonsterRoster.ROSTER) {
            if (!caught.contains(name)) return false;
        }
        return true;
    }

    // --- mutators called by BestiaryDataService ---

    public void addCapture(CapturedCreature c) {
        creatures.add(c);
        captureCountByNpc.merge(c.npcName, 1, Integer::sum);
    }

    /** Replaces a capture (matched by id) in place. Returns true if one was found. */
    public boolean replaceCapture(CapturedCreature nc) {
        for (int i = 0; i < creatures.size(); i++) {
            if (creatures.get(i).id.equals(nc.id)) {
                creatures.set(i, nc);
                return true;
            }
        }
        return false;
    }

    /** Removes a capture from the collection. Returns true only if it was present. */
    public boolean removeCapture(CapturedCreature c) {
        if (creatures.remove(c)) {
            captureCountByNpc.computeIfPresent(c.npcName, (k, v) -> v > 1 ? v - 1 : null);
            return true;
        }
        return false;
    }

    public void incrementKillCount(String npcName) {
        killCounts.merge(npcName, 1, Integer::sum);
    }

    public int getKillCount(String npcName) {
        return killCounts.getOrDefault(npcName, 0);
    }

    public int getCaptureCount(String npcName) {
        return captureCountByNpc.getOrDefault(npcName, 0);
    }

    public int totalCaptures() {
        return creatures.size();
    }

    public long uniqueSpeciesCount() {
        return creatures.stream().map(c -> c.npcName).distinct().count();
    }

    public int totalKills() {
        return killCounts.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int countFavourites() {
        return (int) creatures.stream().filter(c -> c.favourite).count();
    }

    /**
     * Marks {@code target} as the album cover for its monster, clearing any previous
     * cover on the same npcName (one cover per monster). Returns the affected captures
     * so callers can persist just those if desired.
     */
    public void setAlbumCover(CapturedCreature target) {
        for (CapturedCreature c : creatures) {
            if (c.npcName.equals(target.npcName)) {
                c.albumCover = (c == target);
            }
        }
    }
}

