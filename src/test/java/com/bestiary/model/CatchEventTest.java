package com.bestiary.model;

import org.junit.Test;
import java.time.Instant;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CatchEventTest {

    private static final Instant DURING   = Instant.parse("2026-11-30T23:59:59Z");
    private static final Instant AFTER    = Instant.parse("2026-12-01T00:00:00Z");

    @Test
    public void raidBossesGuaranteedDuringEvent() {
        assertTrue(CatchEvent.guaranteed("Ocellus", DURING));
        assertTrue(CatchEvent.guaranteed("Dagon'hai Monk", DURING));
        assertTrue(CatchEvent.guaranteed("the smorks", DURING)); // case-insensitive like the roster
    }

    @Test
    public void endsAtMidnightUtc() {
        assertFalse(CatchEvent.guaranteed("Ocellus", AFTER));
        assertFalse(CatchEvent.isActive(AFTER));
    }

    @Test
    public void otherMonstersUnaffected() {
        assertFalse(CatchEvent.guaranteed("Great Olm", DURING));
        assertFalse(CatchEvent.guaranteed(null, DURING));
        assertFalse(CatchEvent.guaranteed("Ocellus", null));
    }

    @Test
    public void everyEventBossIsOnTheRoster() {
        for (String n : new String[]{"Archive Guardian", "Ezekiel", "Bork", "The Smorks", "Moia",
                "Dreadnoth the Jellied", "Dagon'hai Elder", "Dagon'hai Monk", "Xarth", "Ocellus"}) {
            assertTrue(n, MonsterRoster.isKnown(n));
            assertTrue(n, CatchEvent.guaranteed(n, DURING));
        }
    }

    @Test
    public void lastDayLabel() {
        assertEquals("30 Nov", CatchEvent.lastDayLabel());
    }
}
