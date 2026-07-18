package net.runelite.client.plugins.bestiary;

import net.runelite.client.plugins.bestiary.model.Achievement;
import net.runelite.client.plugins.bestiary.model.CapturedCreature;
import net.runelite.client.plugins.bestiary.model.CreatureRarity;
import net.runelite.client.plugins.bestiary.service.BestiaryDataService;
import net.runelite.client.plugins.bestiary.service.CaptureService;
import net.runelite.client.plugins.bestiary.service.KillTracker;
import net.runelite.client.plugins.bestiary.service.ProgressionService;
import net.runelite.client.plugins.bestiary.ui.BestiaryOverlay;
import net.runelite.client.plugins.bestiary.ui.BestiaryPanel;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;

@Slf4j
@PluginDescriptor(
        name = "Bestiary",
        description = "Catch and collect creatures as you slay them",
        tags = {"collection", "npc", "combat", "creatures", "bestiary"}
)
public class BestiaryPlugin extends Plugin {

    @Inject private Client client;
    @Inject private BestiaryConfig config;
    @Inject private ClientToolbar clientToolbar;
    @Inject private ChatMessageManager chatMessageManager;
    @Inject private OverlayManager overlayManager;

    @Inject private KillTracker killTracker;
    @Inject private CaptureService captureService;
    @Inject private ProgressionService progressionService;
    @Inject private BestiaryDataService dataService;

    @Inject private BestiaryPanel panel;
    @Inject private BestiaryOverlay overlay;

    private NavigationButton navButton;

    // --- Lifecycle ---

    @Override
    protected void startUp() {
        dataService.load();

        navButton = NavigationButton.builder()
                .tooltip("Bestiary")
                .icon(buildPanelIcon())
                .priority(6)
                .panel(panel)
                .build();
        clientToolbar.addNavigation(navButton);
        overlayManager.add(overlay);

        SwingUtilities.invokeLater(panel::refresh);
        log.info("Bestiary plugin started");
    }

    @Override
    protected void shutDown() {
        dataService.saveNow();
        overlayManager.remove(overlay);
        clientToolbar.removeNavigation(navButton);
        navButton = null;

        log.info("Bestiary plugin stopped");
    }

    // --- Event handlers ---

    @Subscribe
    public void onActorDeath(ActorDeath event) {
        Optional<NPC> kill = killTracker.onActorDeath(event);
        kill.ifPresent(this::handleKill);
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event) {
        killTracker.onHitsplatApplied(event);
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event) {
        killTracker.onNpcDespawned(event);
    }

    @Subscribe
    public void onInteractingChanged(InteractingChanged event) {
        killTracker.onInteractingChanged(event);
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        killTracker.onGameStateChanged(event);
    }

    // --- Kill handling ---

    private void handleKill(NPC npc) {
        WorldPoint location = client.getLocalPlayer() != null
                ? client.getLocalPlayer().getWorldLocation()
                : null;

        // Track the kill + check kill-count achievements
        dataService.incrementKillCount(npc.getId());
        List<Achievement> killAchievements = progressionService.checkKillAchievements();
        for (Achievement a : killAchievements) {
            sendChatMessage("Achievement unlocked: " + a.title + " - " + a.description,
                    ChatColorType.HIGHLIGHT);
        }

        int newLevel = progressionService.recordKill(npc);
        if (newLevel > 0) {
            if (config.notifyOnLevelUp()) {
                sendChatMessage("Capture Level up! You are now level " + newLevel + ".",
                        ChatColorType.HIGHLIGHT);
            }
            SwingUtilities.invokeLater(panel::refresh);
        }

        // Attempt capture
        int captureLevel = progressionService.getLevel();
        int killCount    = dataService.getCollection().getKillCount(npc.getId());
        String region    = resolveRegionName(location);

        Optional<CapturedCreature> result = captureService.attemptCapture(
                npc, location, captureLevel, killCount, region);

        // Overlay / animation
        if (config.showCaptureAnimation()) {
            overlay.startCaptureSequence(result.orElse(null), config.animationShowMisses());
        } else if (result.isPresent() && config.showOverlay()) {
            overlay.showCapture(result.get());
        }

        result.ifPresent(creature -> {
            dataService.addCapture(creature);

            List<Achievement> newAchievements = progressionService.recordCapture(creature, config.captureXpEnabled());

            // Notifications
            if (config.notifyOnCapture()) {
                boolean shouldNotify = !config.notifyRareOnly()
                        || creature.rarity.ordinal() >= CreatureRarity.RARE.ordinal();
                if (shouldNotify) {
                    notifyCapture(creature);
                }
            }
            for (Achievement a : newAchievements) {
                sendChatMessage("Achievement unlocked: " + a.title + " - " + a.description,
                        ChatColorType.HIGHLIGHT);
            }

            SwingUtilities.invokeLater(panel::refresh);
        });
    }

    private void notifyCapture(CapturedCreature creature) {
        String message = new ChatMessageBuilder()
                .append(ChatColorType.HIGHLIGHT)
                .append("You captured a ")
                .append(creature.rarity.displayColor, creature.rarity.label + " ")
                .append(ChatColorType.HIGHLIGHT)
                .append(creature.npcName)
                .append(ChatColorType.NORMAL)
                .append("!")
                .build();
        chatMessageManager.queue(QueuedMessage.builder()
                .type(ChatMessageType.GAMEMESSAGE)
                .runeLiteFormattedMessage(message)
                .build());
    }

    private void sendChatMessage(String message, ChatColorType type) {
        String formatted = new ChatMessageBuilder()
                .append(type)
                .append(message)
                .build();
        chatMessageManager.queue(QueuedMessage.builder()
                .type(ChatMessageType.GAMEMESSAGE)
                .runeLiteFormattedMessage(formatted)
                .build());
    }

    /**
     * Resolves a human-readable area name from a WorldPoint.
     * Phase 1: uses the region ID as a placeholder.
     * Phase 4: replace with a full region name lookup table.
     */
    private String resolveRegionName(WorldPoint location) {
        if (location == null) return "Unknown";
        int regionId = location.getRegionID();
        return "Region " + regionId;
    }

    /** 16\u00c3\u201416 orange square placeholder icon for the nav button. */
    @Provides
    BestiaryConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(BestiaryConfig.class);
    }

    private static BufferedImage buildPanelIcon() {
        BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = icon.createGraphics();
        g.setColor(new Color(255, 165, 0));
        g.fillOval(2, 2, 12, 12);
        g.setColor(new Color(200, 120, 0));
        g.drawOval(2, 2, 12, 12);
        g.dispose();
        return icon;
    }
}

