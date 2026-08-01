package com.bestiary.ui;

import com.bestiary.BestiaryConfig;
import com.bestiary.service.BestiaryDataService;
import com.bestiary.service.ProgressionService;
import com.bestiary.service.SessionTracker;
import com.bestiary.service.WikiImageService;
import com.bestiary.ui.SessionRecapDialog;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Root PluginPanel.  Contains a stats header and a tabbed pane with the
 * Collection and Progress tabs.
 */
@Slf4j
@Singleton
public class BestiaryPanel extends PluginPanel {

    private final BestiaryDataService dataService;
    private final ProgressionService progressionService;
    private final SessionTracker sessionTracker;
    private final boolean developerMode;
    private final com.bestiary.service.DevOptions devOptions;

    /** Set by the plugin — sends chat messages for achievements unlocked outside the capture flow. */
    private static java.util.function.Consumer<java.util.List<com.bestiary.model.Achievement>> achievementNotifier;
    public static void setAchievementNotifier(
            java.util.function.Consumer<java.util.List<com.bestiary.model.Achievement>> n) {
        achievementNotifier = n;
    }

    /**
     * Read-only gate for card right-click menus (#48). While viewing another account, cards are
     * look-only: no favourite/nickname/album-cover/discard/reroll/transfer/export-copy. Left-click
     * (open a card to view it) still works. Static so the various card/row classes can query it
     * without each holding a dataService reference.
     */
    private static java.util.function.BooleanSupplier readOnlyGate = () -> false;
    public static boolean isReadOnly() {
        return readOnlyGate != null && readOnlyGate.getAsBoolean();
    }

    /** The live singleton, so non-capture actions elsewhere can trigger an achievement re-check. */
    private static BestiaryPanel instance;
    /** Re-checks achievements after a non-capture action (favourite, purchase, etc.). EDT-only. */
    public static void recheckAchievements() {
        if (instance != null) instance.checkAndNotifyAchievements();
    }

    private final JLabel statsLabel;
    private CollectionTab collectionTab;
    private ProgressTab progressTab;
    private InfoTab infoTab;
    private ShopTab shopTab;

    /** The top-level tab pane. When logged out, only the Info tab is kept and a welcome banner shows. */
    private JTabbedPane tabs;
    /** "Log in to view your collection" banner shown above the tabs while no account is active. */
    private JPanel welcomeBanner;
    /** "Viewing AltRSN (read-only)" banner shown above the tabs while viewing another account (#48). */
    private JPanel viewingBanner;
    private JLabel viewingBannerLabel;
    /** Bottom button strip (Reset + dev tools) — disabled while logged out. */
    private JPanel southPanel;

    /** Account switcher (#48): pick the played account or view any known account read-only. */
    private JComboBox<AccountItem> accountSwitcher;
    private JPanel accountRow;
    /** Guards the switcher listener while its model is rebuilt programmatically in {@link #refresh}. */
    private boolean switcherUpdating;

    /** Panel display state — drives which tabs/banners show and what's interactive. */
    private enum PanelState { LOCKED, VIEWING, NORMAL }
    private PanelState panelState;

    @Inject
    public BestiaryPanel(BestiaryDataService dataService, ProgressionService progressionService,
                         WikiImageService imageService, BestiaryConfig config,
                         SessionTracker sessionTracker,
                         net.runelite.client.game.SkillIconManager skillIconManager,
                         @javax.inject.Named("developerMode") boolean developerMode,
                         com.bestiary.service.DevOptions devOptions) {
        super(false); // false = don't auto-wrap in scroll pane
        instance = this;
        readOnlyGate = dataService::isViewing;   // cards become look-only while viewing another account
        this.dataService        = dataService;
        this.progressionService = progressionService;
        this.sessionTracker     = sessionTracker;
        this.developerMode      = developerMode;
        this.devOptions         = devOptions;
        CreatureDetailDialog.setConfig(config);
        CreatureDetailDialog.setSaveCallback(dataService::saveNow);
        CardExportDialog.setShared(imageService, dataService::getCollection);
        CardDataDialog.setCaptureCreditBonus(dataService::captureCreditFlatBonus);
        CardDataDialog.setCaptureXpBonus(dataService::captureXpBonus);
        CardDataDialog.setKillXpFlatBonus(dataService::killXpFlatBonus);
        CardExportDialog.setOnMutate(() -> { dataService.saveNow(); refresh(); });
        AlbumCard.setConfig(config);
        AlbumCard.setSkillIconManager(skillIconManager);
        AlbumCard.setDiscardHandler((owner, cap) -> {
            if (dataService.isViewing()) return;   // read-only view of another account (#48)
            long base  = dataService.discardValueBase(cap);
            long bonus = dataService.discardValue(cap) - base;   // extra from Salvager's Eye
            String label = (cap.isShiny() ? "✦ " : "") + cap.rarity.label + " " + cap.npcName;
            String msg = "<html>Discard " + label + " for " + base + " credits"
                    + (bonus > 0 ? " <font color='#78d278'>(+" + bonus + ")</font>" : "") + "?</html>";
            int choice = JOptionPane.showConfirmDialog(owner, msg,
                    "Discard card", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) {
                dataService.discardCapture(cap);
                refresh();
                AlbumDialog.refreshOpenAlbum();
                DiscardDialog.refreshOpen();
            }
        });
        AlbumDialog.setDiscardOpener(win -> {
            if (dataService.isViewing()) return;   // read-only view of another account (#48)
            DiscardDialog.open(win, dataService, () -> {
                refresh();
                AlbumDialog.refreshOpenAlbum();
                TransferDialog.refreshOpen();
            });
        });
        AlbumDialog.setTransferOpener(win -> {
            if (dataService.isViewing()) return;   // read-only view of another account (#48)
            TransferDialog.open(win, dataService, () -> {
                refresh();
                AlbumDialog.refreshOpenAlbum();
                DiscardDialog.refreshOpen();
            });
        });
        AlbumCard.setRerollHandler((owner, cap) -> {
            if (dataService.isViewing()) return;   // read-only view of another account (#48)
            Window win = SwingUtilities.getWindowAncestor(owner);
            long cost = com.bestiary.service.BestiaryDataService.rerollCost(cap);
            if (dataService.getCredits() < cost) {
                RerollResultDialog.info(win, "Card Reroller",
                        "You need " + cost + " credits to reroll (you have " + dataService.getCredits() + ").");
                return;
            }
            RerollConfirmDialog.open(win, cap, cost, progressionService.getLevel(),
                    dataService.bonusRerollShinyChance(), dataService.bonusRerollRarityChance(), () -> {
                com.bestiary.model.CapturedCreature nc =
                        dataService.rerollCard(cap, progressionService.getLevel());
                refresh();
                AlbumDialog.refreshOpenAlbum();
                if (nc != null) {
                    RerollResultDialog.open(win, cap, nc);   // MODELESS before/after
                }
            });
        });

        setLayout(new BorderLayout(0, 6));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(new EmptyBorder(8, 8, 8, 8));

        // Header
        JPanel header = new JPanel(new BorderLayout(0, 3));
        header.setOpaque(false);

        JLabel title = new JLabel("Bestiary");
        title.setFont(FontManager.getRunescapeBoldFont().deriveFont(16f));
        title.setForeground(new Color(255, 165, 0));

        statsLabel = new JLabel("0 species  |  0 captures");
        statsLabel.setFont(FontManager.getRunescapeSmallFont());
        statsLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(255, 165, 0, 80));
        sep.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        titleRow.add(title,      BorderLayout.WEST);
        titleRow.add(statsLabel, BorderLayout.EAST);

        accountRow = buildAccountRow();

        JPanel headerTop = new JPanel();
        headerTop.setOpaque(false);
        headerTop.setLayout(new BoxLayout(headerTop, BoxLayout.Y_AXIS));
        titleRow.setAlignmentX(LEFT_ALIGNMENT);
        accountRow.setAlignmentX(LEFT_ALIGNMENT);
        headerTop.add(titleRow);
        headerTop.add(accountRow);

        header.add(headerTop, BorderLayout.NORTH);
        header.add(sep,       BorderLayout.CENTER);

        // Tabs
        collectionTab = new CollectionTab(dataService, imageService);
        progressTab   = new ProgressTab(progressionService, sessionTracker,
                () -> DashboardDialog.open(SwingUtilities.getWindowAncestor(this), dataService,
                        progressionService, DashboardDialog.DashView.PROGRESSION));
        shopTab       = new ShopTab(dataService, progressionService,
                () -> DashboardDialog.open(SwingUtilities.getWindowAncestor(this), dataService,
                        progressionService, DashboardDialog.DashView.ECONOMY));

        tabs = new JTabbedPane();
        tabs.setBackground(ColorScheme.DARK_GRAY_COLOR);
        tabs.setForeground(Color.WHITE);
        tabs.setFont(FontManager.getRunescapeSmallFont());

        infoTab = new InfoTab(dataService, progressionService,
                () -> collectionTab.openAlbum(SwingUtilities.getWindowAncestor(this)),
                () -> { if (tabs.getTabCount() > 1) { tabs.setSelectedIndex(1); collectionTab.showFavourites(); } },
                () -> SessionRecapDialog.open(SwingUtilities.getWindowAncestor(this), sessionTracker),
                () -> CaptureRateDialog.open(SwingUtilities.getWindowAncestor(this),
                        dataService.getDisplayLevel(), dataService.displayBonusShinyChance()),
                view -> DashboardDialog.open(SwingUtilities.getWindowAncestor(this), dataService, progressionService, view),
                view -> DashboardDialog.copyViewToClipboard(dataService, progressionService, view));

        tabs.addTab("Info",     infoTab);
        tabs.addTab("Cards",    collectionTab);
        tabs.addTab("Shop",     shopTab);
        tabs.addTab("Progress", progressTab);
        // Block account switching while the Cards tab is open: switching removes that tab, and tearing
        // down a large rendered collection (e.g. a dev-seeded account) while it's the on-screen tab
        // triggers AWT's slow shape-mixing → a long freeze. Leaving Cards first makes the switch fast.
        tabs.addChangeListener(e -> updateSwitcherEnabled());

        welcomeBanner = buildWelcomeBanner();
        viewingBanner = buildViewingBanner();

        JPanel bannerStack = new JPanel();
        bannerStack.setOpaque(false);
        bannerStack.setLayout(new BoxLayout(bannerStack, BoxLayout.Y_AXIS));
        welcomeBanner.setAlignmentX(LEFT_ALIGNMENT);
        viewingBanner.setAlignmentX(LEFT_ALIGNMENT);
        bannerStack.add(welcomeBanner);
        bannerStack.add(viewingBanner);

        JPanel centerWrap = new JPanel(new BorderLayout(0, 6));
        centerWrap.setOpaque(false);
        centerWrap.add(bannerStack, BorderLayout.NORTH);
        centerWrap.add(tabs,        BorderLayout.CENTER);

        southPanel = buildSouthPanel();

        add(header,      BorderLayout.NORTH);
        add(centerWrap,  BorderLayout.CENTER);
        add(southPanel,  BorderLayout.SOUTH);

        // Start locked — the Info/Guide tab stays browsable; a character login adds the rest.
        applyState(PanelState.LOCKED);
    }

    // -------------------------------------------------------------------------
    // Account switcher (#48)
    // -------------------------------------------------------------------------

    /** One entry in the account-switcher dropdown: a placeholder, the played account, or a viewable one. */
    private static final class AccountItem {
        final Long hash;        // null = the "not logged in" placeholder (selecting it does nothing)
        final String rsn;
        final boolean played;   // true = the logged-in character (selecting it clears any view)
        AccountItem(Long hash, String rsn, boolean played) {
            this.hash = hash; this.rsn = rsn; this.played = played;
        }
        @Override public String toString() {
            if (hash == null) return "— Not logged in —";
            String name = rsn != null && !rsn.isEmpty() ? rsn : "Unknown";
            return played ? "★ " + name + " (you)" : "👁 " + name;
        }
    }

    /** Builds the switcher row (hidden until 2+ accounts are known, or a view is active). */
    private JPanel buildAccountRow() {
        accountSwitcher = new JComboBox<>();
        accountSwitcher.setFont(FontManager.getRunescapeSmallFont());
        accountSwitcher.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        accountSwitcher.setForeground(Color.WHITE);
        accountSwitcher.setToolTipText("Switch which account's collection you're viewing (read-only for other accounts)");
        accountSwitcher.addActionListener(e -> {
            if (switcherUpdating) return;
            // A dropdown action must never wedge the client — guard the whole handler.
            try {
                AccountItem sel = (AccountItem) accountSwitcher.getSelectedItem();
                if (sel == null) return;
                if (sel.hash == null) {
                    // "— Not logged in —" placeholder: stop viewing if we were (matches the Return button).
                    if (dataService.isViewing()) {
                        dataService.clearView();
                        closeAllBestiaryWindows();
                        refresh();
                    }
                    return;
                }
                Long viewed = dataService.getViewedAccountHash();
                // Selecting the account already shown changes nothing — skip the rebuild + window churn.
                boolean already = sel.played
                        ? (!dataService.isViewing())
                        : (viewed != null && sel.hash.equals(viewed));
                if (already) return;
                if (sel.played) {
                    dataService.clearView();
                } else {
                    dataService.viewAccount(sel.hash, sel.rsn);
                }
                // Open albums/dashboards/card views hold the old collection — drop them, then rebuild.
                closeAllBestiaryWindows();
                refresh();
            } catch (Exception ex) {
                log.warn("Account switcher action failed", ex);
            }
        });

        JPanel row = new JPanel(new BorderLayout(0, 0));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(4, 0, 0, 0));
        row.add(accountSwitcher, BorderLayout.CENTER);
        return row;
    }

    /**
     * Rebuilds the switcher's model from the known-accounts registry and selects the current
     * (played or viewed) account. Hidden unless there's a real choice to make (2+ accounts) or a
     * view is currently active. Runs under {@link #switcherUpdating} so it never fires the listener.
     */
    private void refreshAccountSwitcher() {
        java.util.List<com.bestiary.service.BestiaryStore.AccountRef> accounts = dataService.listAllAccounts();
        Long activeHash = dataService.getActiveAccountHash();
        Long viewedHash = dataService.getViewedAccountHash();
        boolean viewing = dataService.isViewing();
        boolean loggedIn = activeHash != null;

        switcherUpdating = true;
        try {
            accountSwitcher.removeAllItems();
            AccountItem toSelect = null;

            // Logged-out default: a non-account placeholder, so nothing is "viewed" until picked.
            AccountItem placeholder = null;
            if (!loggedIn) {
                placeholder = new AccountItem(null, null, false);
                accountSwitcher.addItem(placeholder);
            }

            for (com.bestiary.service.BestiaryStore.AccountRef a : accounts) {
                boolean isPlayed = loggedIn && a.hash == activeHash;
                AccountItem item = new AccountItem(a.hash, a.rsn, isPlayed);
                accountSwitcher.addItem(item);
                if (viewing) {
                    if (viewedHash != null && a.hash == viewedHash) toSelect = item;
                } else if (isPlayed) {
                    toSelect = item;
                }
            }
            // Logged out and not viewing → show the placeholder ("— Not logged in —").
            if (toSelect == null && placeholder != null) toSelect = placeholder;
            if (toSelect != null) accountSwitcher.setSelectedItem(toSelect);
        } finally {
            switcherUpdating = false;
        }
        // Show when there's a real choice (2+ accounts), while viewing, or while logged out with any
        // known account to browse (so the "not logged in" default + browsable accounts are visible).
        accountRow.setVisible(accounts.size() >= 2 || viewing || (!loggedIn && !accounts.isEmpty()));
        updateSwitcherEnabled();
    }

    /**
     * Disables the account switcher while the Cards tab is the one on screen. Switching account removes
     * that tab, and tearing down a large rendered collection while it's showing is what triggers AWT's
     * O(n²) shape-mixing freeze (#48). Leaving Cards first (Info/Shop/Progress) makes the switch fast.
     */
    private void updateSwitcherEnabled() {
        if (accountSwitcher == null) return;
        boolean onCards = tabs.getSelectedComponent() == collectionTab;
        accountSwitcher.setEnabled(!onCards);
        accountSwitcher.setToolTipText(onCards
                ? "Leave the Cards tab to switch account"
                : "Switch which account's collection you're viewing (read-only for other accounts)");
    }

    /** Friendly banner shown above the tabs while logged out, inviting the player to log in. */
    private JPanel buildWelcomeBanner() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(new Color(45, 38, 20));
        p.setBorder(BorderFactory.createCompoundBorder(
                new javax.swing.border.LineBorder(new Color(255, 165, 0, 90), 1, true),
                new EmptyBorder(7, 10, 7, 10)));
        JLabel msg = new JLabel("<html><b style='color:#FFA500;'>Welcome to Bestiary</b><br>"
                + "<span style='color:#C8C8C8;'>Log in to view and grow your collection.</span></html>");
        msg.setFont(FontManager.getRunescapeSmallFont());
        p.add(msg, BorderLayout.CENTER);
        return p;
    }

    /** Blue "read-only view" banner shown while viewing another account (#48), with a Return button. */
    private JPanel buildViewingBanner() {
        JPanel p = new JPanel(new BorderLayout(6, 0));
        p.setBackground(new Color(20, 40, 55));
        p.setBorder(BorderFactory.createCompoundBorder(
                new javax.swing.border.LineBorder(new Color(90, 160, 220, 120), 1, true),
                new EmptyBorder(6, 10, 6, 8)));
        viewingBannerLabel = new JLabel();
        viewingBannerLabel.setFont(FontManager.getRunescapeSmallFont());
        JButton ret = new JButton("Return");
        ret.setFont(FontManager.getRunescapeSmallFont());
        ret.setBackground(new Color(35, 60, 80));
        ret.setForeground(new Color(150, 200, 240));
        ret.setFocusPainted(false);
        ret.setBorderPainted(false);
        ret.setToolTipText("Stop viewing and return to your own collection");
        ret.addActionListener(e -> {
            try {
                dataService.clearView();
                closeAllBestiaryWindows();
                refresh();
            } catch (Exception ex) {
                log.warn("Return-to-collection failed", ex);
            }
        });
        p.add(viewingBannerLabel, BorderLayout.CENTER);
        p.add(ret,                BorderLayout.EAST);
        return p;
    }

    /**
     * Applies a display state (#48):
     * <ul>
     *   <li>LOCKED (logged out, no view) — welcome banner + Info/Guide tab only; everything inert.</li>
     *   <li>VIEWING — read-only banner + Info + Cards for the viewed account; Shop/Progress hidden;
     *       reset/dev controls disabled (they act on the played account).</li>
     *   <li>NORMAL — no banners; all four tabs; everything interactive.</li>
     * </ul>
     * Tabs are only rebuilt on a state change (cheap; keeps the selected tab stable during refreshes).
     */
    private void applyState(PanelState state) {
        boolean changed = state != panelState;
        panelState = state;

        welcomeBanner.setVisible(state == PanelState.LOCKED);
        viewingBanner.setVisible(state == PanelState.VIEWING);

        if (changed) applyTabs(state);

        // Reset/dev controls act on the PLAYED account — only enable them when actually playing.
        if (southPanel != null) setControlsEnabled(southPanel, state == PanelState.NORMAL);
        // Info header shortcuts/stat boxes browse the current collection (played or viewed) read-only.
        infoTab.setInteractiveEnabled(state != PanelState.LOCKED);
        // While viewing another account, disable the "your play" shortcuts (Session Recap, Favourites).
        infoTab.setViewingAnotherAccount(state == PanelState.VIEWING);
    }

    /** Rebuilds the tab set to match {@code state}. Only called on transitions (see {@link #applyState}). */
    private void applyTabs(PanelState state) {
        // Keep Info (index 0); drop the rest. Hard cap the iterations so a pathological
        // tab-count state can never spin the EDT (defensive — normally 3 removals max).
        for (int guard = 0; tabs.getTabCount() > 1 && guard < 8; guard++) tabs.remove(1);
        // Only the played account gets the full tab set. VIEWING another account is Info-only: its
        // cards live in the Album and dashboards, so we never build/tear down the heavy Cards tab on a
        // profile switch (that teardown is what froze the client on large collections, #48).
        if (state == PanelState.NORMAL) {
            tabs.addTab("Cards",    collectionTab);
            tabs.addTab("Shop",     shopTab);
            tabs.addTab("Progress", progressTab);
        }
        tabs.setSelectedIndex(0);   // always land on Info after a state change
    }

    /** Recursively enables/disables buttons and combo boxes under {@code root}. */
    private static void setControlsEnabled(Container root, boolean enabled) {
        for (Component c : root.getComponents()) {
            if (c instanceof AbstractButton || c instanceof JComboBox) {
                c.setEnabled(enabled);
            }
            if (c instanceof Container) {
                setControlsEnabled((Container) c, enabled);
            }
        }
    }

    /**
     * Called on logout: dispose every open Bestiary dialog (album, dashboards, card views, etc.) so
     * none linger showing the now-cleared collection, then re-lock the panel.
     */
    public void onLoggedOut() {
        closeAllBestiaryWindows();
        refresh();
    }

    /**
     * Called when the played account changes (login or a direct account hop, #131): dispose any
     * browsing windows still bound to the previous account before they can be mutated against the
     * new one, then refresh. Mirrors the logout cleanup for the no-LOGIN_SCREEN hop case.
     */
    public void onAccountChanged() {
        closeAllBestiaryWindows();
        refresh();
    }

    private JPanel buildSouthPanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(4, 0, 0, 0));

        // Dev-only helpers — hidden for live users (only present when RuneLite runs in developer mode).
        if (developerMode) {
            JButton seedBtn = new JButton("[DEV] Seed Test Data");
            seedBtn.setFont(FontManager.getRunescapeSmallFont());
            seedBtn.setBackground(new Color(20, 50, 80));
            seedBtn.setForeground(new Color(100, 180, 255));
            seedBtn.setBorderPainted(false);
            seedBtn.setFocusPainted(false);
            seedBtn.setToolTipText("Wipe collection and insert 1 capture per rarity for every roster monster");
            seedBtn.addActionListener(e -> {
                dataService.seedTestCollection();
                refresh();
            });
            panel.add(fullWidth(seedBtn));
            panel.add(Box.createVerticalStrut(4));

            JButton creditBtn = new JButton("[DEV] +100k Credits");
            creditBtn.setFont(FontManager.getRunescapeSmallFont());
            creditBtn.setBackground(new Color(20, 60, 40));
            creditBtn.setForeground(new Color(120, 220, 150));
            creditBtn.setBorderPainted(false);
            creditBtn.setFocusPainted(false);
            creditBtn.addActionListener(e -> { dataService.awardCredits(100_000); refresh(); });
            panel.add(fullWidth(creditBtn));
            panel.add(Box.createVerticalStrut(4));

            // Dev capture overrides (never shown to live users; state lives in DevOptions).
            panel.add(devCombo("Catch",
                    com.bestiary.model.DevCaptureMode.values(), devOptions.captureMode,
                    v -> devOptions.captureMode = v));
            panel.add(Box.createVerticalStrut(3));
            panel.add(devCombo("Rarity",
                    com.bestiary.model.DevRarityOverride.values(), devOptions.forceRarity,
                    v -> devOptions.forceRarity = v));
            panel.add(Box.createVerticalStrut(3));

            // Toggle button (not a checkbox — the dark-theme checkbox tick is unreadable).
            JToggleButton shinyBtn = new JToggleButton();
            shinyBtn.setSelected(devOptions.forceShiny);
            shinyBtn.setFont(FontManager.getRunescapeSmallFont());
            shinyBtn.setFocusPainted(false);
            shinyBtn.setBorderPainted(false);
            shinyBtn.setOpaque(true);
            shinyBtn.setAlignmentX(CENTER_ALIGNMENT);
            shinyBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
            Runnable styleShiny = () -> {
                boolean on = shinyBtn.isSelected();
                shinyBtn.setText("Always Roll Shiny: " + (on ? "ON ✦" : "OFF"));
                shinyBtn.setBackground(on ? new Color(120, 90, 20) : ColorScheme.DARKER_GRAY_COLOR);
                shinyBtn.setForeground(on ? new Color(255, 215, 0) : new Color(150, 150, 150));
            };
            styleShiny.run();
            shinyBtn.addActionListener(e -> { devOptions.forceShiny = shinyBtn.isSelected(); styleShiny.run(); });
            panel.add(shinyBtn);
            panel.add(Box.createVerticalStrut(6));
        }

        panel.add(buildWipeBtn());

        // Version footer — click to open the About / version-log dialog.
        JLabel version = new JLabel("Bestiary v" + com.bestiary.BestiaryPlugin.VERSION);
        version.setFont(FontManager.getRunescapeSmallFont());
        version.setForeground(new Color(150, 150, 150));
        version.setAlignmentX(CENTER_ALIGNMENT);
        version.setHorizontalAlignment(SwingConstants.CENTER);
        version.setBorder(new EmptyBorder(6, 0, 0, 0));
        version.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        version.setToolTipText("What's new — version log");
        version.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                AboutDialog.open(SwingUtilities.getWindowAncestor(BestiaryPanel.this));
            }
            @Override public void mouseEntered(java.awt.event.MouseEvent e) {
                version.setForeground(new Color(255, 165, 0));
            }
            @Override public void mouseExited(java.awt.event.MouseEvent e) {
                version.setForeground(new Color(150, 150, 150));
            }
        });
        panel.add(version);
        return panel;
    }

    /** A compact "label + dropdown" row for a dev override; calls {@code onChange} on selection. */
    private static <T> JComponent devCombo(String label, T[] values, T current, java.util.function.Consumer<T> onChange) {
        JComboBox<T> combo = new JComboBox<>(values);
        combo.setSelectedItem(current);
        combo.setFont(FontManager.getRunescapeSmallFont());
        combo.addActionListener(e -> {
            @SuppressWarnings("unchecked") T v = (T) combo.getSelectedItem();
            if (v != null) onChange.accept(v);
        });

        JLabel l = new JLabel(label);
        l.setFont(FontManager.getRunescapeSmallFont());
        l.setForeground(new Color(100, 180, 255));
        l.setBorder(new EmptyBorder(0, 0, 0, 4));

        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setOpaque(false);
        row.setAlignmentX(CENTER_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        row.add(l,     BorderLayout.WEST);
        row.add(combo, BorderLayout.CENTER);
        return row;
    }

    private JButton buildWipeBtn() {
        JButton btn = new JButton("Reset Collection");
        btn.setFont(FontManager.getRunescapeSmallFont());
        btn.setBackground(new Color(80, 20, 20));
        btn.setForeground(new Color(220, 100, 100));
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setToolTipText("Permanently delete all captures and progression");
        btn.addActionListener(e -> confirmWipe());
        return fullWidth(btn);
    }

    /** Makes a button span the full panel width (consistent bottom-row buttons). */
    private static JButton fullWidth(JButton btn) {
        btn.setAlignmentX(CENTER_ALIGNMENT);
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        return btn;
    }

    private void confirmWipe() {
        int first = JOptionPane.showConfirmDialog(
                this,
                "This will permanently delete ALL capture history, kill counts,\n"
              + "XP, levels, and achievements.\n\nThis cannot be undone. Continue?",
                "Reset Collection",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (first != JOptionPane.YES_OPTION) return;

        int total = dataService.getCollection().totalCaptures();
        int second = JOptionPane.showConfirmDialog(
                this,
                "FINAL WARNING: " + total + " capture" + (total == 1 ? "" : "s")
              + " will be permanently erased.\n\nAre you absolutely sure?",
                "Confirm Reset",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (second != JOptionPane.YES_OPTION) return;

        dataService.wipeCollection();
        closeAllBestiaryWindows();   // open albums/dashboards/card views now show stale data
        refresh();
    }

    /**
     * Disposes every open Bestiary dialog (album, dashboards, card data/export, odds, etc.) after a
     * reset, since they'd otherwise keep showing the wiped collection. Matches by package so it also
     * covers any dialog added later without needing per-class hooks. The sidebar panel isn't a
     * Window, so it's untouched.
     */
    private static void closeAllBestiaryWindows() {
        for (Window w : Window.getWindows()) {
            if (w != null && w.isDisplayable()
                    && w.getClass().getName().startsWith("com.bestiary.")) {
                w.dispose();
            }
        }
    }

    /**
     * Refreshes all visible data.  Must be called from the EDT
     * (use {@code SwingUtilities.invokeLater(panel::refresh)} from game thread).
     */
    /** Guards against a re-entrant refresh (a refresh triggering another refresh) freezing the EDT. */
    private boolean refreshing;

    public void refresh() {
        // Circuit-breaker: if a refresh is already running on this stack (e.g. a Swing model change
        // during refresh re-fires a listener that calls refresh again), skip the nested call and log
        // WHERE it came from, rather than looping forever and locking the client (unclickable UI, #48).
        if (refreshing) {
            log.warn("Skipped re-entrant Bestiary panel refresh", new Throwable("re-entrant refresh call site"));
            return;
        }
        refreshing = true;
        // A panel refresh (game tick, login/logout, switcher click) must never throw to the EDT and
        // take the client down — log the stack and keep going.
        try {
            refreshInternal();
        } catch (Exception ex) {
            log.warn("Bestiary panel refresh failed", ex);
        } finally {
            refreshing = false;
        }
    }

    private void refreshInternal() {
        refreshAccountSwitcher();

        boolean viewing = dataService.isViewing();
        // Show a collection when logged in OR when browsing another account read-only (#48).
        if (!dataService.hasActiveAccount() && !viewing) {
            // Before login: welcome banner + Info tab only. The collection is empty while logged out,
            // so the Info tab reads zeroes rather than the previous account's data.
            applyState(PanelState.LOCKED);
            statsLabel.setText("Not logged in");
            infoTab.refresh();
            return;
        }
        applyState(viewing ? PanelState.VIEWING : PanelState.NORMAL);

        com.bestiary.model.BestiaryCollection col = dataService.getCollection();
        int species   = (int) col.uniqueSpeciesCount();
        int heldCards = col.totalCaptures();
        statsLabel.setText(species + " species  |  " + heldCards + " cards");

        if (viewing) {
            String who = dataService.getViewedAccountName();
            viewingBannerLabel.setText("<html><span style='color:#7FB8E6;'>👁 Viewing "
                    + "<b>" + (who == null || who.isEmpty() ? "another account" : who)
                    + "</b> — read-only</span></html>");
        } else {
            // Achievements only fire for the played account (never while browsing someone else's cards).
            checkAndNotifyAchievements();
        }

        // Cards/Shop/Progress only exist for the played account; skip them entirely while viewing.
        if (!viewing) {
            collectionTab.refresh();
            shopTab.refresh();
            progressTab.refresh();
        }
        infoTab.refresh();
    }

    /**
     * Catches achievements unlocked by non-capture actions (rerolls, favourites, purchases,
     * discards). checkNewAchievements only returns entries not already unlocked, so calling it
     * repeatedly is cheap and can't double-notify the capture path.
     */
    private void checkAndNotifyAchievements() {
        java.util.List<com.bestiary.model.Achievement> newly =
                progressionService.checkNewAchievements(null);
        if (!newly.isEmpty()) {
            dataService.saveProgressionState();
            if (achievementNotifier != null) achievementNotifier.accept(newly);
        }
    }
}

