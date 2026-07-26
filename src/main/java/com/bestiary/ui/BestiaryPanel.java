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

    private final JLabel statsLabel;
    private CollectionTab collectionTab;
    private ProgressTab progressTab;
    private InfoTab infoTab;
    private ShopTab shopTab;

    @Inject
    public BestiaryPanel(BestiaryDataService dataService, ProgressionService progressionService,
                         WikiImageService imageService, BestiaryConfig config,
                         SessionTracker sessionTracker,
                         net.runelite.client.game.SkillIconManager skillIconManager,
                         @javax.inject.Named("developerMode") boolean developerMode) {
        super(false); // false = don't auto-wrap in scroll pane
        this.dataService        = dataService;
        this.progressionService = progressionService;
        this.sessionTracker     = sessionTracker;
        this.developerMode      = developerMode;
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
            RerollConfirmDialog.open(win, cap, cost, progressionService.getLevel(), () -> {
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
        progressTab   = new ProgressTab(progressionService, sessionTracker);
        shopTab       = new ShopTab(dataService, progressionService);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(ColorScheme.DARK_GRAY_COLOR);
        tabs.setForeground(Color.WHITE);
        tabs.setFont(FontManager.getRunescapeSmallFont());

        infoTab = new InfoTab(dataService, progressionService,
                () -> collectionTab.openAlbum(SwingUtilities.getWindowAncestor(this)),
                () -> { tabs.setSelectedIndex(1); collectionTab.showFavourites(); },
                () -> SessionRecapDialog.open(SwingUtilities.getWindowAncestor(this), sessionTracker),
                () -> CaptureRateDialog.open(SwingUtilities.getWindowAncestor(this), progressionService),
                view -> DashboardDialog.open(SwingUtilities.getWindowAncestor(this), dataService, progressionService, view),
                view -> DashboardDialog.copyViewToClipboard(dataService, progressionService, view));

        tabs.addTab("Info",     infoTab);
        tabs.addTab("Cards",    collectionTab);
        tabs.addTab("Shop",     shopTab);
        tabs.addTab("Progress", progressTab);

        add(header,          BorderLayout.NORTH);
        add(tabs,            BorderLayout.CENTER);
        add(buildSouthPanel(), BorderLayout.SOUTH);
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
        }

        panel.add(buildWipeBtn());
        return panel;
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
        refresh();
    }

    /**
     * Refreshes all visible data.  Must be called from the EDT
     * (use {@code SwingUtilities.invokeLater(panel::refresh)} from game thread).
     */
    public void refresh() {
        int species  = (int) dataService.getCollection().uniqueSpeciesCount();
        int captures = dataService.getCollection().totalCaptures();
        statsLabel.setText(species + " species  |  " + captures + " captures");

        collectionTab.refresh();
        shopTab.refresh();
        progressTab.refresh();
        infoTab.refresh();
    }
}

