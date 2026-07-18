package net.runelite.client.plugins.bestiary.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Container for the player's entire bestiary.
 * This is the root object serialised to collection.json.
 */
public class BestiaryCollection {

    /** All captures in chronological order. */
    public List<CapturedCreature> creatures = new ArrayList<>();

    /** npcName -> total kills (keyed by name so variants like "Goblin" all count together). */
    public Map<String, Integer> killCounts = new HashMap<>();

    /** npcName -> total successful captures. */
    public Map<String, Integer> captureCountByNpc = new HashMap<>();

    // --- mutators called by BestiaryDataService ---

    public void addCapture(CapturedCreature c) {
        creatures.add(c);
        captureCountByNpc.merge(c.npcName, 1, Integer::sum);
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
}

