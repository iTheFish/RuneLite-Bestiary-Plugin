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

    /** npcId \u00e2\u2020\u2019 total kills (including unsuccessful capture attempts). */
    public Map<Integer, Integer> killCounts = new HashMap<>();

    /** npcId \u00e2\u2020\u2019 total successful captures. */
    public Map<Integer, Integer> captureCountByNpc = new HashMap<>();

    // --- mutators called by BestiaryDataService ---

    public void addCapture(CapturedCreature c) {
        creatures.add(c);
        captureCountByNpc.merge(c.npcId, 1, Integer::sum);
    }

    public void incrementKillCount(int npcId) {
        killCounts.merge(npcId, 1, Integer::sum);
    }

    public int getKillCount(int npcId) {
        return killCounts.getOrDefault(npcId, 0);
    }

    public int getCaptureCount(int npcId) {
        return captureCountByNpc.getOrDefault(npcId, 0);
    }

    public int totalCaptures() {
        return creatures.size();
    }

    public long uniqueSpeciesCount() {
        return creatures.stream().mapToInt(c -> c.npcId).distinct().count();
    }

    public int totalKills() {
        return killCounts.values().stream().mapToInt(Integer::intValue).sum();
    }
}

