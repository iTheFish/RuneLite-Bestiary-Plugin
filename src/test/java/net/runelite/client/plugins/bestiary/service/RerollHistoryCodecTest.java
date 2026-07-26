package net.runelite.client.plugins.bestiary.service;

import net.runelite.client.plugins.bestiary.model.CapturedCreature;
import net.runelite.client.plugins.bestiary.model.CreatureRarity;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Round-trip coverage for the reroll-history string codec (see BestiaryDatabase).
 * This is persistence-critical: a bad encode/decode silently loses a card's history.
 */
public class RerollHistoryCodecTest {

    @Test
    public void emptyHistoryEncodesToEmptyString() {
        assertEquals("", BestiaryDatabase.encodeRerollHistory(null));
        assertEquals("", BestiaryDatabase.encodeRerollHistory(new ArrayList<>()));
        assertTrue(BestiaryDatabase.decodeRerollHistory(null).isEmpty());
        assertTrue(BestiaryDatabase.decodeRerollHistory("").isEmpty());
    }

    @Test
    public void roundTripsMultipleRecordsInOrder() {
        List<CapturedCreature.RerollState> history = Arrays.asList(
                new CapturedCreature.RerollState(CreatureRarity.COMMON, 42, false, 7, "Zezima", 1000L),
                new CapturedCreature.RerollState(CreatureRarity.EPIC, 88, true, 55, "Some Name", 2000L));

        String encoded = BestiaryDatabase.encodeRerollHistory(history);
        List<CapturedCreature.RerollState> decoded = BestiaryDatabase.decodeRerollHistory(encoded);

        assertEquals(2, decoded.size());
        CapturedCreature.RerollState a = decoded.get(0);
        assertEquals(CreatureRarity.COMMON, a.rarity);
        assertEquals(42, a.powerLevel);
        assertFalse(a.shiny);
        assertEquals(7, a.prayer);
        assertEquals("Zezima", a.rerolledBy);
        assertEquals(1000L, a.epoch);

        CapturedCreature.RerollState b = decoded.get(1);
        assertEquals(CreatureRarity.EPIC, b.rarity);
        assertEquals(88, b.powerLevel);
        assertTrue(b.shiny);
        assertEquals(55, b.prayer);
        assertEquals("Some Name", b.rerolledBy);
        assertEquals(2000L, b.epoch);
    }

    @Test
    public void malformedRecordsAreSkipped() {
        // valid ; then garbage ; then a record missing fields
        String raw = "COMMON|10|0|5|100|Bob;not-a-record;EPIC|20|1|9";
        List<CapturedCreature.RerollState> decoded = BestiaryDatabase.decodeRerollHistory(raw);
        assertEquals(1, decoded.size());
        assertEquals("Bob", decoded.get(0).rerolledBy);
    }
}
