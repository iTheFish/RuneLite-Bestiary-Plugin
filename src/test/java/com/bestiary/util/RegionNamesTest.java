package com.bestiary.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Guards the Misthalin / Morytania / Desert boundary in the {@code approximateZone}
 * fallback. Region id = (rx &lt;&lt; 8) | ry, where rx = worldX/64 and ry = worldY/64.
 * The ids chosen here are deliberately NOT in the explicit name map, so they exercise
 * the zone heuristic (a real "Brutus" kill in eastern Misthalin used to read Morytania).
 */
public class RegionNamesTest {

    private static int regionId(int rx, int ry) {
        return (rx << 8) | ry;
    }

    @Test
    public void easternMisthalinIsNotMorytania() {
        // rx == 53 is eastern Misthalin, not Morytania (which starts at rx >= 54).
        assertEquals("Misthalin", RegionNames.get(regionId(53, 49)));
        assertEquals("Misthalin", RegionNames.get(regionId(53, 50)));
    }

    @Test
    public void realMorytaniaStillResolves() {
        // rx >= 54 east of the Salve is genuine Morytania.
        assertEquals("Morytania", RegionNames.get(regionId(56, 52)));
    }

    @Test
    public void gatewayLatitudeIsNotDesert() {
        // ry 48 at this longitude is the Al Kharid / eastern-Misthalin gateway, not desert
        // (13360 is unnamed, so it exercises the heuristic; the old rule read "Desert").
        assertEquals("Misthalin", RegionNames.get(regionId(52, 48)));
    }

    @Test
    public void desertProperStillResolves() {
        // The desert proper sits at ry <= 47.
        assertEquals("Desert", RegionNames.get(regionId(51, 45)));
    }
}
