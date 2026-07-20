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
import net.runelite.client.plugins.bestiary.ui.CardExportDialog;
import net.runelite.client.plugins.bestiary.util.RegionNames;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.GameState;
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
import net.runelite.client.events.ConfigChanged;
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
import java.util.ArrayList;
import java.util.EnumMap;
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

    // Session capture tracking (reset on login, flushed on logout)
    private final List<CapturedCreature> sessionCaptures = new ArrayList<>();

    // BATCHED mode: 5-second accumulation per npcName+rarity key (executor thread only)
    private final Map<String, Integer>            batchCounts       = new HashMap<>();
    private final Map<String, CapturedCreature>   batchLastCreature = new HashMap<>();
    private final Map<String, List<Integer>>      batchQualities    = new HashMap<>();
    private final Map<String, ScheduledFuture<?>> batchFutures      = new HashMap<>();

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

        executor.execute(() -> {
            batchFutures.values().forEach(f -> f.cancel(false));
            batchFutures.clear();
            batchCounts.clear();
            batchLastCreature.clear();
            batchQualities.clear();
        });

        log.info("Bestiary plugin stopped");
    }

    // --- Event handlers ---

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!"bestiary".equals(event.getGroup())) return;
        overlay.applyConfig(config);
    }

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
        if (event.getGameState() == GameState.LOGGED_IN && client.getLocalPlayer() != null) {
            String name = client.getLocalPlayer().getName();
            if (name != null && !name.isEmpty()) {
                for (net.runelite.client.plugins.bestiary.model.CapturedCreature c
                        : dataService.getCollection().creatures) {
                    if (c.playerName == null || c.playerName.isEmpty()) {
                        c.playerName = name;
                    }
                }
            }
            sessionCaptures.clear();
        }
        if (event.getGameState() == GameState.LOGIN_SCREEN && !sessionCaptures.isEmpty()) {
            sendSessionRecap();
        }
    }

    // --- Kill handling ---

    private void handleKill(NPC npc) {
        WorldPoint location = client.getLocalPlayer() != null
                ? client.getLocalPlayer().getWorldLocation()
                : null;

        String npcName = npc.getName() != null ? npc.getName() : "Unknown";

        // Track the kill + check kill-count achievements
        dataService.incrementKillCount(npcName);
        List<Achievement> killAchievements = progressionService.checkKillAchievements();
        for (Achievement a : killAchievements) {
            sendAchievementMessage(a);
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
        int killCount    = dataService.getCollection().getKillCount(npcName);
        String region    = resolveRegionName(location);

        String playerName = client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : "";
        Optional<CapturedCreature> result = captureService.attemptCapture(
                npc, location, captureLevel, killCount, region, playerName);

        // Overlay / animation
        if (config.showCaptureAnimation()) {
            overlay.startCaptureSequence(result.orElse(null), config.animationShowMisses());
        } else if (result.isPresent() && config.showOverlay()) {
            overlay.showCapture(result.get());
        }

        result.ifPresent(creature -> {
            sessionCaptures.add(creature);
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
                sendAchievementMessage(a);
            }

            SwingUtilities.invokeLater(panel::refresh);
        });
    }

    /**
     * BATCHED mode: accumulates captures for 5 seconds of inactivity per NPC+rarity key,
     * then posts a single "Nx Rarity Name captured!" message.  Timer resets on each kill.
     * Called on executor thread.
     */
    private void accumulateBatch(CapturedCreature creature) {
        String key = creature.npcName + ":" + creature.rarity.label;
        batchCounts.merge(key, 1, Integer::sum);
        batchLastCreature.put(key, creature);
        batchQualities.computeIfAbsent(key, k -> new ArrayList<>()).add(creature.quality.overallRating());

        ScheduledFuture<?> existing = batchFutures.remove(key);
        if (existing != null) existing.cancel(false);

        batchFutures.put(key, executor.schedule(() -> flushBatch(key), 5, TimeUnit.SECONDS));
    }

    private void flushBatch(String key) {
        Integer count     = batchCounts.remove(key);
        CapturedCreature last = batchLastCreature.remove(key);
        List<Integer> qualities = batchQualities.remove(key);
        batchFutures.remove(key);
        if (count == null || last == null) return;

        String qualStr = qualities == null || qualities.isEmpty() ? ""
                : "  Q:" + qualities.stream()
                        .map(String::valueOf)
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("");

        ChatMessageBuilder builder = new ChatMessageBuilder()
                .append(ChatColorType.NORMAL);
        if (count > 1) builder.append(count + "x ");
        builder.append(last.rarity.displayColor, last.rarity.label)
               .append(ChatColorType.HIGHLIGHT)
               .append(" " + last.npcName + " captured!")
               .append(ChatColorType.NORMAL)
               .append("  Kill #" + last.killsBeforeCapture + qualStr);

        chatMessageManager.queue(QueuedMessage.builder()
                .type(ChatMessageType.GAMEMESSAGE)
                .runeLiteFormattedMessage(builder.build())
                .build());
    }

    private void notifyCapture(CapturedCreature creature) {
        int quality = creature.quality.overallRating();
        int killNum = creature.killsBeforeCapture; // already includes current kill
        // kill# and quality together ensure no two consecutive messages are identical
        // (RuneLite silently drops duplicate chat messages)
        String message = new ChatMessageBuilder()
                .append(creature.rarity.displayColor, creature.rarity.label)
                .append(ChatColorType.HIGHLIGHT)
                .append(" " + creature.npcName + " captured!")
                .append(ChatColorType.NORMAL)
                .append("  Kill #" + killNum + "  Q:" + quality)
                .build();
        chatMessageManager.queue(QueuedMessage.builder()
                .type(ChatMessageType.GAMEMESSAGE)
                .runeLiteFormattedMessage(message)
                .build());
    }

    private void sendSessionRecap() {
        if (!config.sessionRecap() || sessionCaptures.isEmpty()) return;

        Map<CreatureRarity, Integer> counts = new EnumMap<>(CreatureRarity.class);
        CapturedCreature best = null;
        for (CapturedCreature c : sessionCaptures) {
            counts.merge(c.rarity, 1, Integer::sum);
            if (best == null
                    || c.rarity.ordinal() > best.rarity.ordinal()
                    || (c.rarity == best.rarity && c.quality.overallRating() > best.quality.overallRating())) {
                best = c;
            }
        }

        // Line 1: total + rarity breakdown (rarest first, skip zero counts)
        CreatureRarity[] order = {
            CreatureRarity.MYTHIC, CreatureRarity.LEGENDARY, CreatureRarity.EPIC,
            CreatureRarity.RARE,   CreatureRarity.UNCOMMON,  CreatureRarity.COMMON
        };
        ChatMessageBuilder line1 = new ChatMessageBuilder()
                .append(ChatColorType.HIGHLIGHT)
                .append("Bestiary session: " + sessionCaptures.size() + " capture"
                        + (sessionCaptures.size() == 1 ? "" : "s") + " — ");
        boolean first = true;
        for (CreatureRarity r : order) {
            int n = counts.getOrDefault(r, 0);
            if (n == 0) continue;
            if (!first) line1.append(ChatColorType.NORMAL).append(", ");
            line1.append(r.displayColor, r.label + " ×" + n);
            first = false;
        }
        chatMessageManager.queue(QueuedMessage.builder()
                .type(ChatMessageType.GAMEMESSAGE)
                .runeLiteFormattedMessage(line1.build())
                .build());

        // Line 2: best capture
        if (best != null) {
            ChatMessageBuilder line2 = new ChatMessageBuilder()
                    .append(ChatColorType.NORMAL).append("Best catch: ")
                    .append(best.rarity.displayColor, best.npcName + " " + best.rarity.label)
                    .append(ChatColorType.NORMAL).append("  Q:" + best.quality.overallRating());
            chatMessageManager.queue(QueuedMessage.builder()
                    .type(ChatMessageType.GAMEMESSAGE)
                    .runeLiteFormattedMessage(line2.build())
                    .build());
        }
    }

    private void sendAchievementMessage(Achievement a) {
        String formatted = new ChatMessageBuilder()
                .append(ChatColorType.NORMAL)
                .append("Achievement unlocked: ")
                .append(a.chatColor, a.title)
                .append(ChatColorType.NORMAL)
                .append(" - " + a.description)
                .build();
        chatMessageManager.queue(QueuedMessage.builder()
                .type(ChatMessageType.GAMEMESSAGE)
                .runeLiteFormattedMessage(formatted)
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
