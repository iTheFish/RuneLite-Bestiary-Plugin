package net.runelite.client.plugins.bestiary.ui;

import net.runelite.client.plugins.bestiary.BestiaryConfig;
import net.runelite.client.plugins.bestiary.service.BestiaryDataService;
import net.runelite.client.plugins.bestiary.service.ProgressionService;
import net.runelite.client.plugins.bestiary.service.SessionTracker;
import net.runelite.client.plugins.bestiary.service.WikiImageService;
import net.runelite.client.plugins.bestiary.ui.SessionRecapDialog;
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

    private final JLabel statsLabel;
    private CollectionTab collectionTab;
    private ProgressTab progressTab;
    private InfoTab infoTab;

    @Inject
    public BestiaryPanel(BestiaryDataService dataService, ProgressionService progressionService,
                         WikiImageService imageService, BestiaryConfig config,
                         SessionTracker sessionTracker) {
        super(false); // false = don't auto-wrap in scroll pane
        this.dataService        = dataService;
        this.progressionService = progressionService;
        this.sessionTracker     = sessionTracker;
        CreatureDetailDialog.setConfig(config);
        CreatureDetailDialog.setSaveCallback(dataService::saveNow);
        CardExportDialog.setShared(imageService, dataService::getCollection);

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

        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(ColorScheme.DARK_GRAY_COLOR);
        tabs.setForeground(Color.WHITE);

        infoTab = new InfoTab(dataService, progressionService,
                () -> collectionTab.openAlbum(SwingUtilities.getWindowAncestor(this)),
                () -> { tabs.setSelectedIndex(1); collectionTab.showFavourites(); },
                () -> SessionRecapDialog.open(SwingUtilities.getWindowAncestor(this), sessionTracker),
                () -> CaptureRateDialog.open(SwingUtilities.getWindowAncestor(this), progressionService),
                view -> DashboardDialog.open(SwingUtilities.getWindowAncestor(this), dataService, progressionService, view),
                view -> DashboardDialog.copyViewToClipboard(dataService, progressionService, view));

        tabs.addTab("Info",       infoTab);
        tabs.addTab("Collection", collectionTab);
        tabs.addTab("Progress",   progressTab);

        add(header,          BorderLayout.NORTH);
        add(tabs,            BorderLayout.CENTER);
        add(buildWipeBtn(),  BorderLayout.SOUTH);
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
        progressTab.refresh();
        infoTab.refresh();
    }
}

