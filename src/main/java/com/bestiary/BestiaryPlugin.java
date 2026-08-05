package com.bestiary;

import com.bestiary.model.Achievement;
import com.bestiary.model.CapturedCreature;
import com.bestiary.model.ChatNotifyMode;
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
    @Inject private com.bestiary.service.DiscordWebhookService discordWebhook;

    @Inject private BestiaryPanel panel;
    @Inject private BestiaryOverlay overlay;
    @Inject @javax.inject.Named("developerMode") private boolean developerMode;

    private NavigationButton navButton;

    /** Dev-only EDT-hang detector (#48 debugging): dumps the AWT stack to the log if the UI freezes. */
    private ScheduledFuture<?> edtWatchdog;

    // BATCHED mode: 9-second accumulation per npcName+rarity key (executor thread only)
    private final Map<String, Integer>            batchCounts       = new HashMap<>();
    private final Map<String, CapturedCreature>   batchLastCreature = new HashMap<>();
    private final Map<String, List<Integer>>      batchQualities    = new HashMap<>();
    private final Map<String, Long>               batchCredits      = new HashMap<>();
    private final Map<String, Long>               batchCreditsBonus = new HashMap<>();
    private final Map<String, ScheduledFuture<?>> batchFutures      = new HashMap<>();

    // --- Lifecycle ---

    @Override
    protected void startUp() {
        // No collection is loaded until a character logs in — see onGameStateChanged(LOGGED_IN).
        // The panel shows a "log in to load your collection" placeholder until then.

        // Achievements unlocked by non-capture actions (rerolls, favourites, purchases) are detected
        // on panel refresh; announce them in chat just like capture achievements.
        BestiaryPanel.setAchievementNotifier(list -> {
            if (config.notifyOnAchievement()) list.forEach(this::sendAchievementMessage);
        });

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
        if (developerMode) startEdtWatchdog();
        log.info("Bestiary plugin started");
    }

    /**
     * Dev-only: pings the EDT every 2s; if a ping goes unanswered for &gt;5s the UI is frozen
     * (e.g. an infinite layout/paint loop, which never throws so nothing hits the log). Dumps the
     * AWT-EventQueue stack to the log ONCE per freeze so the exact stuck line can be identified.
     * Never runs for live users (developer mode only).
     */
    private void startEdtWatchdog() {
        final java.util.concurrent.atomic.AtomicLong pingSent = new java.util.concurrent.atomic.AtomicLong(0);
        final java.util.concurrent.atomic.AtomicLong pongSeen = new java.util.concurrent.atomic.AtomicLong(0);
        final java.util.concurrent.atomic.AtomicBoolean reported = new java.util.concurrent.atomic.AtomicBoolean(false);
        edtWatchdog = executor.scheduleAtFixedRate(() -> {
            if (pingSent.get() != pongSeen.get()) {
                long waited = System.currentTimeMillis() - pingSent.get();
                if (waited > 5000 && reported.compareAndSet(false, true)) {
                    log.error("Bestiary EDT watchdog: UI unresponsive for ~{}ms — dumping AWT stack", waited);
                    for (Map.Entry<Thread, StackTraceElement[]> e : Thread.getAllStackTraces().entrySet()) {
                        if (!e.getKey().getName().startsWith("AWT-EventQueue")) continue;
                        StringBuilder sb = new StringBuilder("FROZEN ").append(e.getKey().getName()).append(":\n");
                        for (StackTraceElement f : e.getValue()) sb.append("\tat ").append(f).append('\n');
                        log.error(sb.toString());
                    }
                }
                return;   // don't queue another ping while one is outstanding
            }
            reported.set(false);
            long now = System.currentTimeMillis();
            pingSent.set(now);
            SwingUtilities.invokeLater(() -> pongSeen.set(now));
        }, 2, 2, TimeUnit.SECONDS);
    }

    @Override
    protected void shutDown() {
        if (edtWatchdog != null) { edtWatchdog.cancel(false); edtWatchdog = null; }
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
            batchCreditsBonus.clear();
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
        } else if (event.getGameState() == GameState.LOGIN_SCREEN) {
            // Logged out — flush + lock down: close any open Bestiary windows, clear the data, and
            // return the panel to the "log in" view. No data is shown or writable until next login.
            dataService.handleLogout();
            SwingUtilities.invokeLater(panel::onLoggedOut);
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
            // A real account change (incl. a Jagex-launcher hop with no LOGIN_SCREEN in between) must
            // dispose stale browsing windows bound to the previous account, not just refresh (#131).
            SwingUtilities.invokeLater(panel::onAccountChanged);
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
        if (config.notifyOnAchievement()) {
            for (Achievement a : killAchievements) {
                sendAchievementMessage(a);
            }
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
        // Use the PLAYED collection's kill count for the label — the on-screen view may be another
        // account (#48), but this kill belongs to the logged-in character.
        int killCount    = dataService.getPlayedCollection().getKillCount(npcName);
        String region    = resolveRegionName(location);

        String playerName = client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : "";
        Optional<CapturedCreature> result = captureService.attemptCapture(
                npc, location, captureLevel, killCount, region, playerName, observedDamage,
                dataService.bonusShinyChance(), dataService.bonusCaptureRarityChance());

        // Overlay / animation
        if (config.showCaptureAnimation()) {
            overlay.startCaptureSequence(result.orElse(null), config.animationShowMisses());
        } else if (result.isPresent() && config.showOverlay()) {
            overlay.showCapture(result.get());
        }

        result.ifPresent(creature -> {
            sessionTracker.add(creature);

            // Award Bestiary Credits (difficulty × rarity, shiny doubles; Hunter's Bounty adds a passive %)
            long baseCredits = com.bestiary.util.CreditCalculator.forCapture(
                    com.bestiary.model.MonsterRoster.getDifficulty(
                            creature.npcName, creature.npcCombatLevel),
                    creature.rarity, creature.isShiny());
            long awardedCredits = dataService.awardCaptureCredits(baseCredits);
            long bonusCredits   = awardedCredits - baseCredits;   // Hunter's Bounty flat boost
            // Record the true award now so Card Info stays accurate as-at-capture (set before the
            // capture is persisted via addCapture, so the value is written to disk with the card).
            creature.creditsEarned = awardedCredits;
            sessionTracker.addCredits(awardedCredits);

            // Base capture XP + the Scholar's Insight % shop boost. Computed before addCapture so the
            // awarded value is persisted with the card for accurate as-at-capture Card Info.
            long capXp = Math.round(
                    ProgressionService.captureXp(creature.npcCombatLevel, creature.rarity)
                    * (1.0 + dataService.captureXpBonus()));
            creature.xpEarned = capXp;
            dataService.addCapture(creature);

            if (capXp > 0) {
                sessionTracker.addXp(capXp);
                progressionService.awardXp(capXp);
            }
            // XP already awarded above with the shop boost; recordCapture just checks achievements.
            List<Achievement> newAchievements = progressionService.recordCapture(creature, false);
            // Achievement credit rewards (granted inside recordCapture) also count as session income.
            long achievementCredits = newAchievements.stream().mapToLong(a -> a.creditReward).sum();
            if (achievementCredits > 0) sessionTracker.addCredits(achievementCredits);

            // Chat notification
            if (config.notifyOnCapture()) {
                boolean shouldNotify = creature.isShiny()          // shinies always announce
                        || config.notifyRarityFilter().accepts(creature.rarity);
                if (shouldNotify) {
                    if (config.chatNotifyMode() == ChatNotifyMode.BATCHED && !creature.isShiny()) {
                        // Submit to executor so batch maps are only touched on one thread
                        executor.execute(() -> accumulateBatch(creature, baseCredits, bonusCredits));
                    } else {
                        // Verbose (and always for shinies): include quality so identical
                        // captures produce unique messages, and a shiny is never buried in a batch
                        notifyCapture(creature, baseCredits, bonusCredits);
                    }
                }
            }

            // Fortune's Favour proc (shop upgrade): a rare, exciting rarity climb — always worth a
            // shout, ring-of-wealth style, whenever capture notifications are on.
            if (creature.fortuneBumped && config.notifyOnCapture()) {
                sendFortuneMessage(creature);
            }

            // Discord webhook alert for high-rarity captures (opt-in via a pasted webhook URL).
            maybeSendDiscordAlert(creature);

            if (config.notifyOnAchievement()) {
                for (Achievement a : newAchievements) {
                    sendAchievementMessage(a);
                }
            }
        });

        // Announce a level-up from EITHER kill XP or capture XP (checked once, after both).
        int levelAfter = progressionService.getLevel();
        if (levelAfter > levelBefore) {
            if (config.notifyOnLevelUp()) {
                sendChatMessage("Capture Level up! You are now level " + levelAfter + ".",
                        ChatColorType.HIGHLIGHT);
            }
            if (config.showLevelUpOverlay()) {
                overlay.enqueueLevelUp(levelAfter);
            }
        }

        // Refresh the panel after every kill — not just on level-ups or captures — so kill XP,
        // kill counts and any newly unlocked achievements are always reflected immediately.
        SwingUtilities.invokeLater(panel::refresh);
    }

    /**
     * BATCHED mode: accumulates captures for 9 seconds of inactivity per NPC+rarity key,
     * then posts a single "Nx Rarity Name captured!" message.  Timer resets on each kill.
     * Called on executor thread.
     */
    private void accumulateBatch(CapturedCreature creature, long baseCredits, long bonusCredits) {
        String key = creature.npcName + ":" + creature.rarity.label;
        batchCounts.merge(key, 1, Integer::sum);
        batchLastCreature.put(key, creature);
        batchQualities.computeIfAbsent(key, k -> new ArrayList<>()).add(creature.powerLevel());
        batchCredits.merge(key, baseCredits, Long::sum);
        batchCreditsBonus.merge(key, bonusCredits, Long::sum);

        ScheduledFuture<?> existing = batchFutures.remove(key);
        if (existing != null) existing.cancel(false);

        batchFutures.put(key, executor.schedule(() -> flushBatch(key), 9, TimeUnit.SECONDS));
    }

    private void flushBatch(String key) {
        Integer count     = batchCounts.remove(key);
        CapturedCreature last = batchLastCreature.remove(key);
        List<Integer> qualities = batchQualities.remove(key);
        Long credits      = batchCredits.remove(key);
        Long bonus        = batchCreditsBonus.remove(key);
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
            if (bonus != null && bonus > 0) {
                builder.append(BOUNTY_CHAT_COLOR, " (+" + bonus + ")");
            }
        }

        chatMessageManager.queue(QueuedMessage.builder()
                .type(ChatMessageType.GAMEMESSAGE)
                .runeLiteFormattedMessage(builder.build())
                .build());
    }

    /** Colour used for the SHINY marker in capture chat messages. */
    private static final java.awt.Color SHINY_CHAT_COLOR = new java.awt.Color(255, 235, 120);
    /** Colour used for the base "+N credits" in capture / achievement chat messages (dark blue). */
    private static final java.awt.Color CREDIT_CHAT_COLOR = new java.awt.Color(51, 102, 204);
    /** Colour for the "(+N)" Hunter's Bounty shop bonus shown after the base credits (dark green). */
    private static final java.awt.Color BOUNTY_CHAT_COLOR = new java.awt.Color(34, 139, 34);
    /** Colour for "brightly" in the Fortune's Favour proc line (warm gold glow). */
    private static final java.awt.Color FORTUNE_CHAT_COLOR = new java.awt.Color(255, 200, 40);

    private void notifyCapture(CapturedCreature creature, long credits, long bonus) {
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
            if (bonus > 0) {
                builder.append(BOUNTY_CHAT_COLOR, " (+" + bonus + ")");
            }
        }
        String message = builder.build();
        chatMessageManager.queue(QueuedMessage.builder()
                .type(ChatMessageType.GAMEMESSAGE)
                .runeLiteFormattedMessage(message)
                .build());
    }

    /** Minimum rarity that triggers a Discord webhook alert (Legendary and above). */
    private static final com.bestiary.model.CreatureRarity DISCORD_MIN_RARITY =
            com.bestiary.model.CreatureRarity.LEGENDARY;

    /**
     * Posts a Legendary+ capture to the user's Discord webhook, if one is set. The card is rendered
     * on the EDT (Swing) and the HTTP POST is dispatched async by the webhook service, so neither
     * blocks the game thread.
     */
    private void maybeSendDiscordAlert(CapturedCreature creature) {
        String url = config.discordWebhookUrl();
        if (url == null || url.trim().isEmpty()) return;                          // disabled — no URL
        if (creature.rarity.ordinal() < DISCORD_MIN_RARITY.ordinal()) return;     // below threshold
        if (!com.bestiary.service.DiscordWebhookService.looksLikeWebhook(url)) return;
        SwingUtilities.invokeLater(() -> {
            java.awt.image.BufferedImage card = CardExportDialog.renderCardImage(creature);
            if (card != null) discordWebhook.sendCaptureAlert(url, creature, card);
        });
    }

    /**
     * The Fortune's Favour proc line, ring-of-wealth style: "shines brightly" glows gold and the
     * new rarity is drawn in its own rarity colour.
     */
    private void sendFortuneMessage(CapturedCreature creature) {
        String formatted = new ChatMessageBuilder()
                .append(ChatColorType.HIGHLIGHT)
                .append("Fortune's Favour shines ")
                .append(FORTUNE_CHAT_COLOR, "brightly")
                .append(ChatColorType.HIGHLIGHT)
                .append("! This capture climbed to ")
                .append(creature.rarity.displayColor, creature.rarity.label)
                .append(ChatColorType.HIGHLIGHT)
                .append(".")
                .build();
        chatMessageManager.queue(QueuedMessage.builder()
                .type(ChatMessageType.GAMEMESSAGE)
                .runeLiteFormattedMessage(formatted)
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

    /**
     * Sidebar icon: the Mythic-shiny collection jar drawn by {@link com.bestiary.util.PanelIcon}
     * (shared with the repo-root {@code icon.png} the Plugin Hub lists, so the two never diverge).
     * Rendered high-res so RuneLite scales it down to the toolbar button size crisply.
     */
    private static BufferedImage buildPanelIcon() {
        return com.bestiary.util.PanelIcon.render(128);
    }
}
