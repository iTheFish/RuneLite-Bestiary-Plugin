package com.bestiary.ui;

import com.bestiary.model.ShopCategory;
import com.bestiary.model.ShopUpgrade;
import com.bestiary.service.BestiaryDataService;
import com.bestiary.service.ProgressionService;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
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

    private static final Color GOLD  = new Color(220, 190, 80);
    private static final Color DIM   = new Color(150, 150, 150);
    private static final Color PIP_ON  = new Color(120, 200, 120);
    private static final Color PIP_OFF = new Color(70, 70, 70);

    private JLabel creditsLabel;
    private JPanel upgradesPanel;

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

    private JScrollPane buildBody() {
        // Track the scroll viewport width so the cards (and their wrapping descriptions) fill it
        // instead of the panel sizing to a child's preferred width and clipping.
        upgradesPanel = new JPanel() {
            @Override public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                if (getParent() != null) d.width = getParent().getWidth();
                return d;
            }
        };
        upgradesPanel.setOpaque(false);
        upgradesPanel.setLayout(new BoxLayout(upgradesPanel, BoxLayout.Y_AXIS));
        upgradesPanel.setBorder(new EmptyBorder(10, 4, 8, 4));

        rebuildUpgrades();

        JScrollPane sp = new JScrollPane(upgradesPanel);
        sp.setBorder(null);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        // Never scroll horizontally — the panel width is fixed; content must wrap to it.
        sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        return sp;
    }

    /** Rebuilds the upgrade cards from current state, grouped under category headings. */
    private void rebuildUpgrades() {
        upgradesPanel.removeAll();
        boolean firstCategory = true;
        for (ShopCategory cat : ShopCategory.values()) {
            boolean any = false;
            for (ShopUpgrade u : ShopUpgrade.values()) {
                if (u.category != cat) continue;
                if (!any) {
                    if (!firstCategory) upgradesPanel.add(Box.createVerticalStrut(12));
                    upgradesPanel.add(categoryHeading(cat.label.toUpperCase()));
                    upgradesPanel.add(Box.createVerticalStrut(6));
                    any = true;
                    firstCategory = false;
                }
                upgradesPanel.add(upgradeCard(u));
                upgradesPanel.add(Box.createVerticalStrut(8));
            }
        }
        upgradesPanel.revalidate();
        upgradesPanel.repaint();
    }

    private JLabel categoryHeading(String text) {
        JLabel heading = new JLabel(text);
        heading.setFont(FontManager.getRunescapeSmallFont());
        heading.setForeground(DIM);
        heading.setAlignmentX(LEFT_ALIGNMENT);
        return heading;
    }

    private JPanel upgradeCard(ShopUpgrade u) {
        int owned  = dataService.getUpgradeTier(u);
        boolean maxed = owned >= u.maxTier;
        long cost  = dataService.upgradeCost(u);

        // Cap height to the card's *current* preferred height (recomputed live) so the parent
        // BoxLayout can't stretch the card, while the wrapping description can still grow it.
        JPanel card = new JPanel() {
            @Override public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(255, 165, 0, 70), 1, true),
                new EmptyBorder(8, 8, 8, 8)));
        card.setAlignmentX(LEFT_ALIGNMENT);

        JLabel title = new JLabel(u.title);
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(ORANGE);
        title.setAlignmentX(LEFT_ALIGNMENT);
        card.add(title);

        // A wrapping JTextArea in a BorderLayout wraps to the card's real width and reports a
        // correct height (a fixed-width HTML label clips when the panel is narrower than assumed).
        JTextArea desc = new JTextArea(u.description);
        desc.setEditable(false);
        desc.setFocusable(false);
        desc.setLineWrap(true);
        desc.setWrapStyleWord(true);
        desc.setOpaque(false);
        desc.setFont(FontManager.getRunescapeSmallFont());
        desc.setForeground(DIM);
        JPanel descWrap = new JPanel(new BorderLayout());
        descWrap.setOpaque(false);
        descWrap.setAlignmentX(LEFT_ALIGNMENT);
        descWrap.setBorder(new EmptyBorder(2, 0, 4, 0));
        descWrap.add(desc, BorderLayout.CENTER);
        card.add(descWrap);

        // Current effect: "+0.3% shiny chance"
        JLabel effect = new JLabel("Current bonus: +" + formatPct(u.effectFor(owned)));
        effect.setFont(FontManager.getRunescapeSmallFont());
        effect.setForeground(GOLD);
        effect.setAlignmentX(LEFT_ALIGNMENT);
        card.add(effect);

        // Tier pips + "N/5"
        JPanel pips = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        pips.setOpaque(false);
        pips.setAlignmentX(LEFT_ALIGNMENT);
        pips.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        for (int i = 0; i < u.maxTier; i++) {
            pips.add(new Pip(i < owned));
        }
        JLabel tierLbl = new JLabel(owned + "/" + u.maxTier);
        tierLbl.setFont(FontManager.getRunescapeSmallFont());
        tierLbl.setForeground(DIM);
        pips.add(tierLbl);
        card.add(pips);

        // Buy button
        JButton buy = new JButton();
        buy.setFont(FontManager.getRunescapeSmallFont());
        buy.setFocusPainted(false);
        buy.setAlignmentX(LEFT_ALIGNMENT);
        if (maxed) {
            buy.setText("MAXED");
            buy.setEnabled(false);
            buy.setForeground(PIP_ON);
        } else {
            boolean afford = dataService.getCredits() >= cost;
            buy.setText("Buy tier " + (owned + 1) + "  —  " + cost + " cr");
            buy.setEnabled(afford);
            buy.setForeground(afford ? GOLD : DIM);
            buy.addActionListener(e -> {
                if (dataService.purchaseUpgrade(u)) {
                    refresh();
                }
            });
        }
        card.add(Box.createVerticalStrut(4));
        card.add(buy);
        return card;
    }

    /** Formats a fractional probability as a percentage, e.g. 0.003 -> "0.3%". */
    private static String formatPct(double frac) {
        return String.format("%.1f%%", frac * 100.0);
    }

    /** A small round tier indicator. */
    private static final class Pip extends JComponent {
        private final boolean on;
        Pip(boolean on) {
            this.on = on;
            setPreferredSize(new Dimension(10, 10));
        }
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(on ? PIP_ON : PIP_OFF);
            g2.fillOval(0, 0, 9, 9);
            g2.dispose();
        }
    }

    // -------------------------------------------------------------------------
    // Refresh
    // -------------------------------------------------------------------------

    public void refresh() {
        creditsLabel.setText(dataService.getCredits() + " credits");
        if (upgradesPanel != null) {
            rebuildUpgrades();
        }
    }
}
