package com.bestiary.ui;

import com.bestiary.service.BestiaryDataService;
import com.bestiary.service.ProgressionService;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Shop tab — spend Bestiary Credits on temporary boosts and cosmetics.
 * Credits are earned automatically on capture (difficulty × rarity weight).
 */
public class ShopTab extends JPanel {

    private static final Color ORANGE = new Color(255, 165, 0);
    private static final Color BG     = ColorScheme.DARK_GRAY_COLOR;

    private final BestiaryDataService dataService;
    private final ProgressionService  progressionService;

    private JLabel creditsLabel;

    public ShopTab(BestiaryDataService dataService, ProgressionService progressionService) {
        this.dataService        = dataService;
        this.progressionService = progressionService;

        setLayout(new BorderLayout(0, 0));
        setBackground(BG);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(),   BorderLayout.CENTER);
    }

    // -------------------------------------------------------------------------
    // Build
    // -------------------------------------------------------------------------

    private JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(8, 4, 8, 4));

        JLabel title = new JLabel("SHOP");
        title.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        title.setForeground(ORANGE);

        creditsLabel = new JLabel("0 credits");
        creditsLabel.setFont(FontManager.getRunescapeSmallFont());
        creditsLabel.setForeground(new Color(220, 190, 80));
        creditsLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        p.add(title,        BorderLayout.WEST);
        p.add(creditsLabel, BorderLayout.EAST);

        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(255, 165, 0, 60));

        JPanel wrapper = new JPanel(new BorderLayout(0, 4));
        wrapper.setOpaque(false);
        wrapper.setBorder(new EmptyBorder(6, 0, 0, 0));
        wrapper.add(p,   BorderLayout.NORTH);
        wrapper.add(sep, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel buildBody() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new EmptyBorder(12, 4, 8, 4));

        JLabel placeholder = new JLabel("Coming soon…");
        placeholder.setFont(FontManager.getRunescapeSmallFont());
        placeholder.setForeground(new Color(100, 100, 100));
        placeholder.setAlignmentX(LEFT_ALIGNMENT);
        p.add(placeholder);

        return p;
    }

    // -------------------------------------------------------------------------
    // Refresh
    // -------------------------------------------------------------------------

    public void refresh() {
        long credits = dataService.getCredits();
        creditsLabel.setText(credits + " credits");
    }
}
