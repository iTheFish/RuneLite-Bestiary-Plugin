package net.runelite.client.plugins.bestiary.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import net.runelite.client.plugins.bestiary.model.CapturedCreature;
import net.runelite.client.plugins.bestiary.model.CreatureQuality;
import net.runelite.client.plugins.bestiary.model.CreatureRarity;
import org.junit.Test;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Guards the JSON persistence: a capture (with Instant, enums, shiny and reroll history)
 * must round-trip losslessly through the same Gson shape the store uses.
 */
public class StoreSerializationTest {

    /** Mirrors BestiaryStore's Instant<->epoch-second adapter. */
    private static final class InstantAdapter
            implements JsonSerializer<Instant>, JsonDeserializer<Instant> {
        public JsonElement serialize(Instant s, Type t, JsonSerializationContext c) {
            return new JsonPrimitive(s.getEpochSecond());
        }
        public Instant deserialize(JsonElement j, Type t, JsonDeserializationContext c) {
            return Instant.ofEpochSecond(j.getAsLong());
        }
    }

    private Gson gson() {
        return new GsonBuilder()
                .registerTypeAdapter(Instant.class, new InstantAdapter())
                .create();
    }

    @Test
    public void capturedCreatureRoundTrips() {
        CapturedCreature c = CapturedCreature.builder()
                .npcId(5).npcName("Goblin").npcCombatLevel(2)
                .rarity(CreatureRarity.MYTHIC)
                .quality(new CreatureQuality(90, 80, 70, 60, 50, 40))
                .captureTime(Instant.ofEpochSecond(1_700_000_000L))
                .regionName("Lumbridge").captureLevel(50).killsBeforeCapture(7)
                .playerName("Zezima").shiny(true).prayer(33).observedHp(120)
                .rerolledBy("Zezima")
                .rerollHistory(Collections.singletonList(
                        new CapturedCreature.RerollState(CreatureRarity.EPIC, 42, false, 10, "Zezima", 1_699_000_000L)))
                .build();

        Gson g = gson();
        CapturedCreature back = g.fromJson(g.toJson(c), CapturedCreature.class);

        assertEquals(c.id, back.id);
        assertEquals(c.npcName, back.npcName);
        assertEquals(c.rarity, back.rarity);
        assertEquals(c.captureTime, back.captureTime);
        assertEquals(c.quality.attack, back.quality.attack);
        assertEquals(c.quality.agility, back.quality.agility);
        assertTrue(back.isShiny());
        assertEquals(c.prayer, back.prayer);
        assertEquals(c.observedHp, back.observedHp);
        assertEquals(1, back.rerollCount());
        assertEquals("Zezima", back.rerollHistory.get(0).rerolledBy);
        assertEquals(CreatureRarity.EPIC, back.rerollHistory.get(0).rarity);
        assertEquals(c.powerLevel(), back.powerLevel());
    }

    @Test
    public void storeDataRoundTrips() {
        BestiaryStore.StoreData d = new BestiaryStore.StoreData();
        d.credits = 12_345L;
        d.totalXp = 99_999L;
        d.killCounts.put("Goblin", 42);
        d.achievements.add("SHINY_CATCH");

        Gson g = gson();
        BestiaryStore.StoreData back = g.fromJson(g.toJson(d), BestiaryStore.StoreData.class);

        assertEquals(1, back.version);
        assertEquals(12_345L, back.credits);
        assertEquals(99_999L, back.totalXp);
        assertEquals(Integer.valueOf(42), back.killCounts.get("Goblin"));
        assertEquals("SHINY_CATCH", back.achievements.get(0));
    }
}
