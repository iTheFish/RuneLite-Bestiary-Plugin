package net.runelite.client.plugins.bestiary.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Container for the player's entire bestiary — the in-memory model.
 * Persistence is handled by BestiaryDatabase (SQLite); this object is loaded
 * from / synced to the DB by BestiaryDataService.
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

