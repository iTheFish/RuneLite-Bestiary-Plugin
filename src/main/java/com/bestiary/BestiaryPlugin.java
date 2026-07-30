package com.bestiary;

import com.bestiary.model.Achievement;
import com.bestiary.model.CapturedCreature;
import com.bestiary.model.ChatNotifyMode;
import com.bestiary.model.CreatureRarity;
import com.bestiary.service.BestiaryDataService;
import com.bestiary.service.CaptureService;
import com.bestiary.service.KillTracker;
import com.bestiary.service.ProgressionService;
import com.bestiary.ui.BestiaryOverlay;
import com.bestiary.ui.BestiaryPanel;
import com.bestiary.ui.CardExportDialog;
import com.bestiary.util.RegionNames;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.HitsplatApplied;
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

    /** Plugin version, shown in the panel footer. Keep in sync with build.gradle's {@code version}. */
    public static final String VERSION = "1.0";

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
    @Inject private com.bestiary.service.SessionTracker sessionTracker;

    @Inject private BestiaryPanel panel;
    @Inject private BestiaryOverlay overlay;

    private NavigationButton navButton;

    // BATCHED mode: 5-second accumulation per npcName+rarity key (executor thread only)
    private final Map<String, Integer>            batchCounts       = new HashMap<>();
    private final Map<String, CapturedCreature>   batchLastCreature = new HashMap<>();
    private final Map<String, List<Integer>>      batchQualities    = new HashMap<>();
    private final Map<String, Long>               batchCredits      = new HashMap<>();
    private final Map<String, ScheduledFuture<?>> batchFutures      = new HashMap<>();

    // --- Lifecycle ---

    @Override
    protected void startUp() {
        // No collection is loaded until a character logs in — see onGameStateChanged(LOGGED_IN).
        // The panel shows a "log in to load your collection" placeholder until then.

        // Achievements unlocked by non-capture actions (rerolls, favourites, purchases) are detected
        // on panel refresh; announce them in chat just like capture achievements.
        BestiaryPanel.setAchievementNotifier(list -> list.forEach(this::sendAchievementMessage));

        navButton = NavigationButton.builder()
                .tooltip("Bestiary")
                .icon(buildPanelIcon())
                .priority(6)
                .panel(panel)
                .build();
        clientToolbar.addNavigation(navButton);
        overlayManager.add(overlay);

        CardExportDialog.setOnCopy(msg ->
                sendChatMessage(msg, ChatColorType.NORMAL));

        com.bestiary.ui.CaptureRow.setOnFavouriteLimitReached(() -> {
            sendChatMessage("Favourites limit reached (20/20). Remove a star to add another.",
                    ChatColorType.NORMAL);
            SwingUtilities.invokeLater(this::showFavouriteLimitPopup);
        });

        SwingUtilities.invokeLater(panel::refresh);
        log.info("Bestiary plugin started");
    }

    @Override
    protected void shutDown() {
        dataService.shutdown();
        overlayManager.remove(overlay);
        clientToolbar.removeNavigation(navButton);
        navButton = null;

        executor.execute(() -> {
            batchFutures.values().forEach(f -> f.cancel(false));
            batchFutures.clear();
            batchCounts.clear();
            batchLastCreature.clear();
            batchQualities.clear();
            batchCredits.clear();
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
        kill.ifPresent(npc -> handleKill(npc, killTracker.getLastKillDamage()));
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
    public void onGameStateChanged(GameStateChanged event) {
        killTracker.onGameStateChanged(event);
        if (event.getGameState() == GameState.LOGGED_IN) {
            sessionTracker.clear();
        }
    }

    @Subscribe
    public void onGameTick(net.runelite.api.events.GameTick event) {
        // Load (or switch to) this account's own collection, keyed by the stable accountHash.
        // Done on a tick — not on LOGGED_IN — because the accountHash (and RSN) aren't reliably
        // populated the instant the LOGGED_IN state fires. switchAccount no-ops on the same
        // account, so this is cheap to run every tick; it only reloads when the account changes.
        if (client.getGameState() != GameState.LOGGED_IN) return;
        long accountHash = client.getAccountHash();
        if (accountHash == -1L) return;
        String name = client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : "";
        if (dataService.switchAccount(accountHash, name)) {
            SwingUtilities.invokeLater(panel::refresh);
        }
    }

    // --- Kill handling ---

    private void handleKill(NPC npc, int observedDamage) {
        WorldPoint location = client.getLocalPlayer() != null
                ? client.getLocalPlayer().getWorldLocation()
                : null;

        String npcName = npc.getName() != null ? npc.getName() : "Unknown";

        // Restrict to the catalogued roster: off-roster NPCs (e.g. Jail guard) are ignored
        // entirely so they can't pollute kill counts, the dex, XP or the collection.
        if (!com.bestiary.model.MonsterRoster.isKnown(npcName)) {
            log.debug("Ignoring off-roster kill: {}", npcName);
            return;
        }

        // Track the kill + check kill-count achievements
        dataService.incrementKillCount(npcName);
        sessionTracker.addKill();
        List<Achievement> killAchievements = progressionService.checkKillAchievements();
        for (Achievement a : killAchievements) {
            sendAchievementMessage(a);
        }

        // Snapshot the level before ANY XP (kill or capture) so we can announce the level-up
        // regardless of which source crossed the threshold — capture XP is often the larger
        // source, so many level-ups happen during the capture, not the kill.
        int levelBefore = progressionService.getLevel();
        com.bestiary.model.DifficultyTier killTier =
                com.bestiary.model.MonsterRoster.getDifficulty(npcName, npc.getCombatLevel());
        // Base kill XP + the Hunter's Focus flat shop boost.
        long killXp  = com.bestiary.service.ProgressionService.killXp(killTier)
                + dataService.killXpFlatBonus();
        sessionTracker.addXp(killXp);
        progressionService.awardXp(killXp);

        // Attempt capture
        int captureLevel = progressionService.getLevel();
        int killCount    = dataService.getCollection().getKillCount(npcName);
        String region    = resolveRegionName(location);

        String playerName = client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : "";
        Optional<CapturedCreature> result = captureService.attemptCapture(
                npc, location, captureLevel, killCount, region, playerName, observedDamage,
                dataService.bonusShinyChance());

        // Overlay / animation
        if (config.showCaptureAnimation()) {
            overlay.startCaptureSequence(result.orElse(null), config.animationShowMisses());
        } else if (result.isPresent() && config.showOverlay()) {
            overlay.showCapture(result.get());
        }

        result.ifPresent(creature -> {
            sessionTracker.add(creature);
            dataService.addCapture(creature);

            // Award Bestiary Credits (difficulty × rarity, shiny doubles; Hunter's Bounty adds a passive %)
            long baseCredits = com.bestiary.util.CreditCalculator.forCapture(
                    com.bestiary.model.MonsterRoster.getDifficulty(
                            creature.npcName, creature.npcCombatLevel),
                    creature.rarity, creature.isShiny());
            long awardedCredits = dataService.awardCaptureCredits(baseCredits);

            if (config.captureXpEnabled()) {
                // Base capture XP + the Scholar's Insight % shop boost.
                long capXp = Math.round(
                        ProgressionService.captureXp(creature.npcCombatLevel, creature.rarity)
                        * (1.0 + dataService.captureXpBonus()));
                sessionTracker.addXp(capXp);
                progressionService.awardXp(capXp);
            }
            // XP already awarded above with the shop boost; recordCapture just checks achievements.
            List<Achievement> newAchievements = progressionService.recordCapture(creature, false);

            // Chat notification
            if (config.notifyOnCapture()) {
                boolean shouldNotify = creature.isShiny()          // shinies always announce
                        || !config.notifyRareOnly()
                        || creature.rarity.ordinal() >= CreatureRarity.RARE.ordinal();
                if (shouldNotify) {
                    if (config.chatNotifyMode() == ChatNotifyMode.BATCHED && !creature.isShiny()) {
                        // Submit to executor so batch maps are only touched on one thread
                        executor.execute(() -> accumulateBatch(creature, awardedCredits));
                    } else {
                        // Verbose (and always for shinies): include quality so identical
                        // captures produce unique messages, and a shiny is never buried in a batch
                        notifyCapture(creature, awardedCredits);
                    }
                }
            }

            for (Achievement a : newAchievements) {
                sendAchievementMessage(a);
            }
        });

        // Announce a level-up from EITHER kill XP or capture XP (checked once, after both).
        int levelAfter = progressionService.getLevel();
        if (levelAfter > levelBefore) {
            if (config.notifyOnLevelUp()) {
                sendChatMessage("Capture Level up! You are now level " + levelAfter + ".",
                        ChatColorType.HIGHLIGHT);
            }
            if (config.showCaptureAnimation() || config.showOverlay()) {
                overlay.enqueueLevelUp(levelAfter);
            }
        }

        // Refresh the panel after every kill — not just on level-ups or captures — so kill XP,
        // kill counts and any newly unlocked achievements are always reflected immediately.
        SwingUtilities.invokeLater(panel::refresh);
    }

    /**
     * BATCHED mode: accumulates captures for 5 seconds of inactivity per NPC+rarity key,
     * then posts a single "Nx Rarity Name captured!" message.  Timer resets on each kill.
     * Called on executor thread.
     */
    private void accumulateBatch(CapturedCreature creature, long credits) {
        String key = creature.npcName + ":" + creature.rarity.label;
        batchCounts.merge(key, 1, Integer::sum);
        batchLastCreature.put(key, creature);
        batchQualities.computeIfAbsent(key, k -> new ArrayList<>()).add(creature.powerLevel());
        batchCredits.merge(key, credits, Long::sum);

        ScheduledFuture<?> existing = batchFutures.remove(key);
        if (existing != null) existing.cancel(false);

        batchFutures.put(key, executor.schedule(() -> flushBatch(key), 5, TimeUnit.SECONDS));
    }

    private void flushBatch(String key) {
        Integer count     = batchCounts.remove(key);
        CapturedCreature last = batchLastCreature.remove(key);
        List<Integer> qualities = batchQualities.remove(key);
        Long credits      = batchCredits.remove(key);
        batchFutures.remove(key);
        if (count == null || last == null) return;

        String qualStr = qualities == null || qualities.isEmpty() ? ""
                : "  PWR:" + qualities.stream()
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
        if (credits != null && credits > 0) {
            builder.append(CREDIT_CHAT_COLOR, "  +" + credits + " credits");
        }

        chatMessageManager.queue(QueuedMessage.builder()
                .type(ChatMessageType.GAMEMESSAGE)
                .runeLiteFormattedMessage(builder.build())
                .build());
    }

    /** Colour used for the SHINY marker in capture chat messages. */
    private static final java.awt.Color SHINY_CHAT_COLOR = new java.awt.Color(255, 235, 120);
    /** Colour used for the "+N credits" suffix in capture / achievement chat messages. */
    private static final java.awt.Color CREDIT_CHAT_COLOR = new java.awt.Color(120, 190, 255);

    private void notifyCapture(CapturedCreature creature, long credits) {
        int quality = creature.powerLevel();
        int killNum = creature.killsBeforeCapture; // already includes current kill
        // kill# and quality together ensure no two consecutive messages are identical
        // (RuneLite silently drops duplicate chat messages)
        ChatMessageBuilder builder = new ChatMessageBuilder();
        if (creature.isShiny()) {
            builder.append(SHINY_CHAT_COLOR, "✦ SHINY ✦ ");
        }
        builder.append(creature.rarity.displayColor, creature.rarity.label)
                .append(ChatColorType.HIGHLIGHT)
                .append(" " + creature.npcName + " captured!")
                .append(ChatColorType.NORMAL)
                .append("  Kill #" + killNum + "  PWR:" + quality);
        if (credits > 0) {
            builder.append(CREDIT_CHAT_COLOR, "  +" + credits + " credits");
        }
        String message = builder.build();
        chatMessageManager.queue(QueuedMessage.builder()
                .type(ChatMessageType.GAMEMESSAGE)
                .runeLiteFormattedMessage(message)
                .build());
    }

    private void sendAchievementMessage(Achievement a) {
        ChatMessageBuilder mb = new ChatMessageBuilder()
                .append(ChatColorType.NORMAL)
                .append("Achievement unlocked: ")
                .append(a.chatColor, a.title)
                .append(ChatColorType.NORMAL)
                .append(" - " + a.description);
        if (a.creditReward > 0) {
            mb.append(CREDIT_CHAT_COLOR, "  +" + a.creditReward + " credits");
        }
        String formatted = mb.build();
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

    private void showFavouriteLimitPopup() {
        JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(panel),
                "Favourites Full", java.awt.Dialog.ModalityType.MODELESS);
        dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dlg.setResizable(false);
        dlg.setAlwaysOnTop(true);

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBorder(BorderFactory.createEmptyBorder(16, 20, 12, 20));
        content.setBackground(net.runelite.client.ui.ColorScheme.DARK_GRAY_COLOR);

        JLabel msg = new JLabel(
                "<html>Favourites limit reached (20/20).<br>Remove a star to add another.</html>");
        msg.setForeground(Color.WHITE);
        msg.setFont(net.runelite.client.ui.FontManager.getRunescapeBoldFont());

        JButton ok = new JButton("OK");
        ok.addActionListener(e -> dlg.dispose());
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        btnRow.setOpaque(false);
        btnRow.add(ok);

        content.add(msg,    BorderLayout.CENTER);
        content.add(btnRow, BorderLayout.SOUTH);

        dlg.setContentPane(content);
        dlg.pack();
        java.awt.Point mouse  = java.awt.MouseInfo.getPointerInfo().getLocation();
        java.awt.Dimension sz = dlg.getSize();
        java.awt.Window ancestor = SwingUtilities.getWindowAncestor(panel);
        if (ancestor != null) {
            java.awt.Rectangle win = ancestor.getBounds();
            int x = Math.min(mouse.x, win.x + win.width  - sz.width);
            int y = Math.min(mouse.y, win.y + win.height - sz.height);
            dlg.setLocation(Math.max(x, win.x), Math.max(y, win.y));
        } else {
            dlg.setLocation(mouse.x, mouse.y);
        }
        dlg.setVisible(true);
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
