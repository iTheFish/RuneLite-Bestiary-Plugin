package net.runelite.client.plugins.bestiary;

import net.runelite.client.plugins.bestiary.model.Achievement;
import net.runelite.client.plugins.bestiary.model.CapturedCreature;
import net.runelite.client.plugins.bestiary.model.ChatNotifyMode;
import net.runelite.client.plugins.bestiary.model.CreatureRarity;
import net.runelite.client.plugins.bestiary.service.BestiaryDataService;
import net.runelite.client.plugins.bestiary.service.CaptureService;
import net.runelite.client.plugins.bestiary.service.KillTracker;
import net.runelite.client.plugins.bestiary.service.ProgressionService;
import net.runelite.client.plugins.bestiary.ui.BestiaryOverlay;
import net.runelite.client.plugins.bestiary.ui.BestiaryPanel;
import net.runelite.client.plugins.bestiary.util.RegionNames;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

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
    @Inject private ScheduledExecutorService executor;

    @Inject private KillTracker killTracker;
    @Inject private CaptureService captureService;
    @Inject private ProgressionService progressionService;
    @Inject private BestiaryDataService dataService;

    @Inject private BestiaryPanel panel;
    @Inject private BestiaryOverlay overlay;

    private NavigationButton navButton;

    // Batched notification state (accessed only on executor thread)
    private final Map<String, Integer> batchCounts  = new HashMap<>();
    private final Map<String, ScheduledFuture<?>> batchFutures = new HashMap<>();

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

        // Cancel any pending batched notifications
        executor.execute(() -> {
            batchFutures.values().forEach(f -> f.cancel(false));
            batchFutures.clear();
            batchCounts.clear();
        });

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

            // Chat notification
            if (config.notifyOnCapture()) {
                boolean shouldNotify = !config.notifyRareOnly()
                        || creature.rarity.ordinal() >= CreatureRarity.RARE.ordinal();
                if (shouldNotify) {
                    if (config.chatNotifyMode() == ChatNotifyMode.BATCHED) {
                        // Submit to executor so batch maps are only touched on one thread
                        executor.execute(() -> accumulateBatch(creature));
                    } else {
                        // Verbose: include quality so identical captures produce unique messages
                        notifyCapture(creature);
                    }
                }
            }

            for (Achievement a : newAchievements) {
                sendChatMessage("Achievement unlocked: " + a.title + " - " + a.description,
                        ChatColorType.HIGHLIGHT);
            }

            SwingUtilities.invokeLater(panel::refresh);
        });
    }

    /** Accumulates a capture for batched chat notification. Called on executor thread. */
    private void accumulateBatch(CapturedCreature creature) {
        String key = creature.rarity.label + " " + creature.npcName;
        batchCounts.merge(key, 1, Integer::sum);

        // Cancel any existing scheduled flush for this key
        ScheduledFuture<?> existing = batchFutures.remove(key);
        if (existing != null) existing.cancel(false);

        // Schedule a new flush 30s from now
        final String rarity = creature.rarity.label;
        final String name   = creature.npcName;
        ScheduledFuture<?> future = executor.schedule(() -> {
            Integer count = batchCounts.remove(key);
            batchFutures.remove(key);
            if (count != null && count > 0) {
                String msg = count > 1
                        ? count + "x " + rarity + " " + name + " captured!"
                        : rarity + " " + name + " captured!";
                sendChatMessage(msg, ChatColorType.HIGHLIGHT);
            }
        }, 30, TimeUnit.SECONDS);

        batchFutures.put(key, future);
    }

    private void notifyCapture(CapturedCreature creature) {
        int quality  = creature.quality.overallRating();
        int killNum  = creature.killsBeforeCapture + 1;
        // Include kill# so identical consecutive captures produce distinct messages
        // (RuneLite silently drops duplicate chat messages)
        String message = new ChatMessageBuilder()
                .append(ChatColorType.HIGHLIGHT)
                .append(creature.rarity.label + " " + creature.npcName + " captured!")
                .append(ChatColorType.NORMAL)
                .append("  Kill #" + killNum + "  Q:" + quality)
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

    private String resolveRegionName(WorldPoint location) {
        if (location == null) return "Unknown";
        return RegionNames.get(location.getRegionID());
    }

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
