package net.runelite.client.plugins.bestiary.service;

import net.runelite.client.plugins.bestiary.BestiaryConfig;
import net.runelite.client.plugins.bestiary.model.CapturedCreature;
import net.runelite.client.plugins.bestiary.model.CreatureQuality;
import net.runelite.client.plugins.bestiary.model.CreatureRarity;
import net.runelite.client.plugins.bestiary.model.DevCaptureMode;
import net.runelite.client.plugins.bestiary.model.DevRarityOverride;
import net.runelite.client.plugins.bestiary.util.RarityRoller;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.Optional;
import java.util.Random;

/**
 * Pure capture logic \u00e2\u20ac" no RuneLite event handling, no UI, no I/O.
 * Depends only on the config and a Random instance, so it is easy to unit test.
 */
@Slf4j
@Singleton
public class CaptureService {

    private final BestiaryConfig config;
    private final Random rng;

    @Inject
    public CaptureService(BestiaryConfig config) {
        this.config = config;
        this.rng    = new Random();
    }

    /** Constructor for tests where a seeded RNG is required. */
    CaptureService(BestiaryConfig config, Random rng) {
        this.config = config;
        this.rng    = rng;
    }

    /**
     * Attempt to capture an NPC after a confirmed kill.
     *
     * @param npc           the NPC that was killed
     * @param location      where the kill happened
     * @param captureLevel  the player's current Capture Level
     * @param killCount     how many times this species has been killed before (used as metadata)
     * @param regionName    human-readable area name
     * @return a populated {@link CapturedCreature} on success, or empty on failure
     */
    public Optional<CapturedCreature> attemptCapture(NPC npc, WorldPoint location,
                                                     int captureLevel, int killCount,
                                                     String regionName) {
        if (!config.captureEnabled()) {
            return Optional.empty();
        }

        double catchRate = config.devCaptureMode() == DevCaptureMode.FORCE_0   ? 0.0
                        : config.devCaptureMode() == DevCaptureMode.FORCE_100 ? 1.0
                        : calculateCatchRate(captureLevel);
        double roll      = rng.nextDouble();

        log.debug("Capture roll for {}: roll={} catchRate={}", npc.getName(),
                String.format("%.3f", roll), String.format("%.3f", catchRate));

        if (roll >= catchRate) {
            return Optional.empty();
        }

        DevRarityOverride forceRarity = config.devForceRarity();
        CreatureRarity rarity = (forceRarity != null && forceRarity != DevRarityOverride.NONE)
                ? CreatureRarity.fromLabel(forceRarity.name())
                : RarityRoller.roll(rng);
        CreatureQuality quality = RarityRoller.generateQuality(rarity, rng);

        CapturedCreature creature = CapturedCreature.builder()
                .npcId(npc.getId())
                .npcName(npc.getName() != null ? npc.getName() : "Unknown")
                .npcCombatLevel(npc.getCombatLevel())
                .rarity(rarity)
                .quality(quality)
                .captureTime(Instant.now())
                .regionName(regionName)
                .captureLevel(captureLevel)
                .killsBeforeCapture(killCount)
                .build();

        log.info("Captured {} [{}]", creature.npcName, creature.rarity.label);
        return Optional.of(creature);
    }

    /**
     * Catch rate formula:
     *   rate = baseCatchRate% + (captureLevel \u00e2\u02c6\u2019 1) \u00c3\u2014 0.5%
     *   rate = min(rate, maxCatchRate%)
     */
    double calculateCatchRate(int captureLevel) {
        double base     = config.baseCaptureRate() / 100.0;
        double levelAdd = (captureLevel - 1) * 0.005;
        double max      = config.maxCaptureRate() / 100.0;
        return Math.min(base + levelAdd, max);
    }
}

