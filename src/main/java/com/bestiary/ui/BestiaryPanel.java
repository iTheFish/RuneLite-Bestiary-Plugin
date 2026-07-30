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

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Root PluginPanel.  Contains a stats header and a tabbed pane with the
 * Collection and Progress tabs.
 */
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
    /** Bottom button strip (Reset + dev tools) — disabled while logged out. */
    private JPanel southPanel;

    @Inject
    public BestiaryPanel(BestiaryDataService dataService, ProgressionService progressionService,
                         WikiImageService imageService, BestiaryConfig config,
                         SessionTracker sessionTracker,
                         net.runelite.client.game.SkillIconManager skillIconManager,
                         @javax.inject.Named("developerMode") boolean developerMode,
                         com.bestiary.service.DevOptions devOptions) {
        super(false); // false = don't auto-wrap in scroll pane
        instance = this;
        this.dataService        = dataService;
        this.progressionService = progressionService;
        this.sessionTracker     = sessionTracker;
        this.developerMode      = developerMode;
        this.devOptions         = devOptions;
        CreatureDetailDialog.setConfig(config);
        CreatureDetailDialog.setSaveCallback(dataService::saveNow);
        CardExportDialog.setShared(imageService, dataService::getCollection);
        CardExportDialog.setOnMutate(() -> { dataService.saveNow(); refresh(); });
        AlbumCard.setConfig(config);
        AlbumCard.setSkillIconManager(skillIconManager);
        AlbumCard.setDiscardHandler((owner, cap) -> {
            long value = dataService.discardValue(cap);
            String label = (cap.isShiny() ? "✦ " : "") + cap.rarity.label + " " + cap.npcName;
            int choice = JOptionPane.showConfirmDialog(owner,
                    "Discard " + label + " for " + value + " credits?",
                    "Discard card", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) {
                dataService.discardCapture(cap);
                refresh();
                AlbumDialog.refreshOpenAlbum();
                DiscardDialog.refreshOpen();
            }
        });
        AlbumDialog.setDiscardOpener(win -> DiscardDialog.open(win, dataService, () -> {
            refresh();
            AlbumDialog.refreshOpenAlbum();
        }));
        AlbumCard.setRerollHandler((owner, cap) -> {
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

        header.add(titleRow, BorderLayout.NORTH);
        header.add(sep,      BorderLayout.CENTER);

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
                () -> CaptureRateDialog.open(SwingUtilities.getWindowAncestor(this), progressionService,
                        dataService.bonusShinyChance()),
                view -> DashboardDialog.open(SwingUtilities.getWindowAncestor(this), dataService, progressionService, view),
                view -> DashboardDialog.copyViewToClipboard(dataService, progressionService, view));

        tabs.addTab("Info",     infoTab);
        tabs.addTab("Cards",    collectionTab);
        tabs.addTab("Shop",     shopTab);
        tabs.addTab("Progress", progressTab);

        welcomeBanner = buildWelcomeBanner();

        JPanel centerWrap = new JPanel(new BorderLayout(0, 6));
        centerWrap.setOpaque(false);
        centerWrap.add(welcomeBanner, BorderLayout.NORTH);
        centerWrap.add(tabs,          BorderLayout.CENTER);

        southPanel = buildSouthPanel();

        add(header,      BorderLayout.NORTH);
        add(centerWrap,  BorderLayout.CENTER);
        add(southPanel,  BorderLayout.SOUTH);

        // Start locked — the Info/Guide tab stays browsable; a character login adds the rest.
        applyLockedState(true);
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

    /**
     * Locked (logged-out) = welcome banner + only the Info/Guide tab (which needs no account data).
     * Unlocked = banner hidden, all tabs present. Toggling tabs is cheap and only runs on login/logout.
     */
    private void applyLockedState(boolean locked) {
        welcomeBanner.setVisible(locked);
        if (locked) {
            while (tabs.getTabCount() > 1) tabs.remove(1);
            if (tabs.getTabCount() > 0) tabs.setSelectedIndex(0);
        } else if (tabs.getTabCount() == 1) {
            tabs.addTab("Cards",    collectionTab);
            tabs.addTab("Shop",     shopTab);
            tabs.addTab("Progress", progressTab);
        }
        // Everything that acts on a collection is inert while logged out — only the Info/Guide
        // sub-tabs stay usable. (The Info tab keeps its own sub-tabs live.)
        infoTab.setInteractiveEnabled(!locked);
        if (southPanel != null) setControlsEnabled(southPanel, !locked);
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

        // Version footer
        JLabel version = new JLabel("Bestiary v" + com.bestiary.BestiaryPlugin.VERSION);
        version.setFont(FontManager.getRunescapeSmallFont());
        version.setForeground(new Color(120, 120, 120));
        version.setAlignmentX(CENTER_ALIGNMENT);
        version.setHorizontalAlignment(SwingConstants.CENTER);
        version.setBorder(new EmptyBorder(6, 0, 0, 0));
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
    public void refresh() {
        // Before login: welcome banner + Info tab only. The collection is empty while logged out,
        // so the Info tab reads zeroes rather than the previous account's data.
        if (!dataService.hasActiveAccount()) {
            applyLockedState(true);
            statsLabel.setText("Not logged in");
            infoTab.refresh();
            return;
        }
        applyLockedState(false);

        int species  = (int) dataService.getCollection().uniqueSpeciesCount();
        int captures = dataService.getCollection().totalCaptures();
        statsLabel.setText(species + " species  |  " + captures + " captures");

        checkAndNotifyAchievements();

        collectionTab.refresh();
        shopTab.refresh();
        progressTab.refresh();
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

