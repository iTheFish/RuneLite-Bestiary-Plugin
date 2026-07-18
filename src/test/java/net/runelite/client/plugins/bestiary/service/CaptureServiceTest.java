package net.runelite.client.plugins.bestiary.service;

import net.runelite.client.plugins.bestiary.BestiaryConfig;
import net.runelite.client.plugins.bestiary.model.CapturedCreature;
import net.runelite.client.plugins.bestiary.model.CreatureRarity;
import net.runelite.client.plugins.bestiary.util.RarityRoller;
import net.runelite.api.NPC;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CaptureServiceTest {

    @Mock private BestiaryConfig config;
    @Mock private NPC npc;

    private static final long SEED = 42L;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(config.captureEnabled()).thenReturn(true);
        when(config.baseCaptureRate()).thenReturn(10);
        when(config.maxCaptureRate()).thenReturn(60);
        when(npc.getId()).thenReturn(1);
        when(npc.getName()).thenReturn("Test NPC");
        when(npc.getCombatLevel()).thenReturn(50);
    }

    // --- Catch rate formula ---

    @Test
    public void catchRateAtLevel1EqualsBase() {
        CaptureService service = new CaptureService(config, new Random(SEED));
        assertEquals(0.10, service.calculateCatchRate(1), 0.0001);
    }

    @Test
    public void catchRateIncreasesWithLevel() {
        CaptureService service = new CaptureService(config, new Random(SEED));
        double level1  = service.calculateCatchRate(1);
        double level50 = service.calculateCatchRate(50);
        assertTrue("Rate at 50 should exceed rate at 1", level50 > level1);
    }

    @Test
    public void catchRateCappeByMaxConfig() {
        when(config.maxCaptureRate()).thenReturn(15);
        CaptureService service = new CaptureService(config, new Random(SEED));
        // At level 100 the uncapped rate would be 10 + 49*0.5 = 34.5% but max is 15%
        double rate = service.calculateCatchRate(100);
        assertEquals(0.15, rate, 0.0001);
    }

    @Test
    public void catchRateAtLevel100WithDefaultMax() {
        CaptureService service = new CaptureService(config, new Random(SEED));
        // 10% + 99 * 0.5% = 59.5%, below the 60% ceiling
        double rate = service.calculateCatchRate(100);
        assertEquals(0.595, rate, 0.0001);
    }

    // --- Capture disabled ---

    @Test
    public void returnEmptyWhenCaptureDisabled() {
        when(config.captureEnabled()).thenReturn(false);
        CaptureService service = new CaptureService(config, new Random(SEED));
        Optional<CapturedCreature> result = service.attemptCapture(npc, null, 1, 0, "Test");
        assertFalse(result.isPresent());
    }

    // --- Forced success with 100% rate ---

    @Test
    public void captureSucceedsAt100PercentRate() {
        when(config.baseCaptureRate()).thenReturn(100);
        when(config.maxCaptureRate()).thenReturn(100);
        CaptureService service = new CaptureService(config, new Random(SEED));

        Optional<CapturedCreature> result = service.attemptCapture(npc, null, 1, 5, "Lumbridge");
        assertTrue(result.isPresent());

        CapturedCreature c = result.get();
        assertEquals(1, c.npcId);
        assertEquals("Test NPC", c.npcName);
        assertEquals(50, c.npcCombatLevel);
        assertEquals(5, c.killsBeforeCapture);
        assertEquals("Lumbridge", c.regionName);
        assertNotNull(c.captureTime);
        assertNotNull(c.quality);
        assertNotNull(c.id);
    }

    // --- Rarity distribution ---

    @Test
    public void rarityDistributionIsWithinExpectedBands() {
        Random rng = new Random(12345L);
        Map<CreatureRarity, Integer> counts = new EnumMap<>(CreatureRarity.class);
        for (CreatureRarity r : CreatureRarity.values()) counts.put(r, 0);

        int trials = 100_000;
        for (int i = 0; i < trials; i++) {
            CreatureRarity r = RarityRoller.roll(rng);
            counts.merge(r, 1, Integer::sum);
        }

        double commonPct     = counts.get(CreatureRarity.COMMON)     / (double) trials;
        double uncommonPct   = counts.get(CreatureRarity.UNCOMMON)   / (double) trials;
        double rarePct       = counts.get(CreatureRarity.RARE)       / (double) trials;
        double mythicPct     = counts.get(CreatureRarity.MYTHIC)     / (double) trials;

        // Allow 10% relative tolerance
        assertEquals(0.750, commonPct,   0.015);
        assertEquals(0.170, uncommonPct, 0.010);
        assertEquals(0.060, rarePct,     0.005);
        assertTrue("Mythic should be very rare", mythicPct < 0.005);
    }
}

