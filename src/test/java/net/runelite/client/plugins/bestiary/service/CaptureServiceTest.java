package net.runelite.client.plugins.bestiary.service;

import net.runelite.client.plugins.bestiary.BestiaryConfig;
import net.runelite.client.plugins.bestiary.model.CapturedCreature;
import net.runelite.client.plugins.bestiary.model.CreatureRarity;
import net.runelite.client.plugins.bestiary.model.DifficultyTier;
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
        when(npc.getId()).thenReturn(1);
        when(npc.getName()).thenReturn("Cow");   // BEGINNER tier
        when(npc.getCombatLevel()).thenReturn(2);
    }

    // --- Catch rate formula ---

    @Test
    public void beginnerBaseRateAtLevel1Is20Percent() {
        CaptureService service = new CaptureService(config, new Random(SEED));
        assertEquals(0.20, service.calculateCatchRate(1, DifficultyTier.BEGINNER), 0.0001);
    }

    @Test
    public void bossBaseRateAtLevel1Is1Point5Percent() {
        CaptureService service = new CaptureService(config, new Random(SEED));
        assertEquals(0.015, service.calculateCatchRate(1, DifficultyTier.BOSS), 0.0001);
    }

    @Test
    public void catchRateIncreasesWithLevel() {
        CaptureService service = new CaptureService(config, new Random(SEED));
        double level1  = service.calculateCatchRate(1, DifficultyTier.BEGINNER);
        double level50 = service.calculateCatchRate(50, DifficultyTier.BEGINNER);
        assertTrue("Rate at 50 should exceed rate at 1", level50 > level1);
    }

    @Test
    public void harderTiersHaveLowerRateAtSameLevel() {
        CaptureService service = new CaptureService(config, new Random(SEED));
        double beginner = service.calculateCatchRate(50, DifficultyTier.BEGINNER);
        double boss     = service.calculateCatchRate(50, DifficultyTier.BOSS);
        assertTrue("BEGINNER rate should exceed BOSS at same level", beginner > boss);
    }

    @Test
    public void beginnerCapsAt60PercentAtHighLevel() {
        CaptureService service = new CaptureService(config, new Random(SEED));
        assertEquals(0.60, service.calculateCatchRate(99, DifficultyTier.BEGINNER), 0.0001);
    }

    @Test
    public void bossCapsAt8PercentAtHighLevel() {
        CaptureService service = new CaptureService(config, new Random(SEED));
        assertEquals(0.08, service.calculateCatchRate(99, DifficultyTier.BOSS), 0.0001);
    }

    // --- Capture disabled ---

    @Test
    public void returnEmptyWhenCaptureDisabled() {
        when(config.captureEnabled()).thenReturn(false);
        CaptureService service = new CaptureService(config, new Random(SEED));
        Optional<CapturedCreature> result = service.attemptCapture(npc, null, 1, 0, "Test", "Player");
        assertFalse(result.isPresent());
    }

    // --- Forced success ---

    @Test
    public void captureSucceedsWithForce100DevMode() {
        when(config.devCaptureMode()).thenReturn(
                net.runelite.client.plugins.bestiary.model.DevCaptureMode.FORCE_100);
        when(config.devForceRarity()).thenReturn(
                net.runelite.client.plugins.bestiary.model.DevRarityOverride.NONE);
        CaptureService service = new CaptureService(config, new Random(SEED));

        Optional<CapturedCreature> result = service.attemptCapture(npc, null, 1, 5, "Lumbridge", "Player");
        assertTrue(result.isPresent());

        CapturedCreature c = result.get();
        assertEquals(1, c.npcId);
        assertEquals("Cow", c.npcName);
        assertEquals(5, c.killsBeforeCapture);
        assertEquals("Lumbridge", c.regionName);
        assertNotNull(c.captureTime);
        assertNotNull(c.quality);
    }

    // --- Rarity distribution (base, level 1) ---

    @Test
    public void rarityDistributionAtLevel1MatchesBaseWeights() {
        Random rng = new Random(12345L);
        Map<CreatureRarity, Integer> counts = new EnumMap<>(CreatureRarity.class);
        for (CreatureRarity r : CreatureRarity.values()) counts.put(r, 0);

        int trials = 100_000;
        for (int i = 0; i < trials; i++) {
            counts.merge(RarityRoller.roll(rng, 1), 1, Integer::sum);
        }

        double commonPct   = counts.get(CreatureRarity.COMMON)   / (double) trials;
        double uncommonPct = counts.get(CreatureRarity.UNCOMMON) / (double) trials;
        double rarePct     = counts.get(CreatureRarity.RARE)     / (double) trials;
        double mythicPct   = counts.get(CreatureRarity.MYTHIC)   / (double) trials;

        assertEquals(0.750, commonPct,   0.015);
        assertEquals(0.170, uncommonPct, 0.010);
        assertEquals(0.060, rarePct,     0.005);
        assertTrue("Mythic should be very rare at level 1", mythicPct < 0.005);
    }

    @Test
    public void rarityDistributionAtLevel99HasMoreHighRarities() {
        Random rng = new Random(99999L);
        Map<CreatureRarity, Integer> lvl1  = rollCounts(rng, 1,  100_000);
        rng = new Random(99999L);
        Map<CreatureRarity, Integer> lvl99 = rollCounts(rng, 99, 100_000);
        int trials = 100_000;

        double mythicLvl1  = lvl1 .get(CreatureRarity.MYTHIC)   / (double) trials;
        double mythicLvl99 = lvl99.get(CreatureRarity.MYTHIC)   / (double) trials;
        double commonLvl1  = lvl1 .get(CreatureRarity.COMMON)   / (double) trials;
        double commonLvl99 = lvl99.get(CreatureRarity.COMMON)   / (double) trials;

        assertTrue("MYTHIC should be more likely at level 99 vs level 1", mythicLvl99 > mythicLvl1);
        assertTrue("COMMON should be less likely at level 99 vs level 1", commonLvl99 < commonLvl1);
    }

    private static Map<CreatureRarity, Integer> rollCounts(Random rng, int level, int trials) {
        Map<CreatureRarity, Integer> counts = new EnumMap<>(CreatureRarity.class);
        for (CreatureRarity r : CreatureRarity.values()) counts.put(r, 0);
        for (int i = 0; i < trials; i++) counts.merge(RarityRoller.roll(rng, level), 1, Integer::sum);
        return counts;
    }
}
