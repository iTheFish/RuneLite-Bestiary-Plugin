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
    private CardLayout contentLayout;
    private JPanel contentCards;
    private final java.util.List<JToggleButton> catButtons = new java.util.ArrayList<>();
    private final java.util.Map<ShopCategory, JPanel> categoryPanels =
            new java.util.EnumMap<>(ShopCategory.class);
    private final java.util.Map<ShopCategory, JScrollPane> categoryScrolls =
            new java.util.EnumMap<>(ShopCategory.class);
    /** Live buy-button per upgrade, so affordability can update in place without a card rebuild. */
    private final java.util.Map<ShopUpgrade, JButton> buyButtons =
            new java.util.EnumMap<>(ShopUpgrade.class);
    private int selectedCat = 0;

    private final Runnable showDashboard;

    public ShopTab(BestiaryDataService dataService, ProgressionService progressionService,
                   Runnable showDashboard) {
        this.dataService        = dataService;
        this.progressionService = progressionService;
        this.showDashboard      = showDashboard;

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

        // Show Dashboard button — opens the Economy dashboard (same flow as the Info tab stat boxes)
        JButton dashBtn = new JButton("Show Dashboard");
        dashBtn.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        dashBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        dashBtn.setForeground(ORANGE);
        dashBtn.setFocusPainted(false);
        dashBtn.setBorderPainted(true);
        dashBtn.setToolTipText("Open the Economy dashboard");
        dashBtn.addActionListener(e -> { if (showDashboard != null) showDashboard.run(); });

        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(255, 165, 0, 60));

        JPanel wrapper = new JPanel(new BorderLayout(0, 4));
        wrapper.setOpaque(false);
        wrapper.setBorder(new EmptyBorder(6, 0, 0, 0));
        wrapper.add(p,       BorderLayout.NORTH);
        wrapper.add(dashBtn, BorderLayout.CENTER);
        wrapper.add(sep,     BorderLayout.SOUTH);
        return wrapper;
    }

    /** Category sub-tabs (styled like the Info tab) over a scrollable card per category. */
    private JComponent buildBody() {
        contentLayout = new CardLayout();
        contentCards  = new JPanel(contentLayout);
        contentCards.setOpaque(false);

        for (ShopCategory cat : ShopCategory.values()) {
            // Track the viewport width so cards (and their wrapping descriptions) fill it, not clip.
            JPanel inner = new JPanel() {
                @Override public Dimension getPreferredSize() {
                    Dimension d = super.getPreferredSize();
                    if (getParent() != null) d.width = getParent().getWidth();
                    return d;
                }
            };
            inner.setOpaque(false);
            inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
            inner.setBorder(new EmptyBorder(10, 4, 8, 4));
            categoryPanels.put(cat, inner);

            JScrollPane sp = new JScrollPane(inner);
            sp.setBorder(null);
            sp.setOpaque(false);
            sp.getViewport().setOpaque(false);
            sp.getVerticalScrollBar().setUnitIncrement(16);
            // Hidden zero-width scrollbar (always-on so content width can't reflow); wheel still works.
            sp.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
            sp.getVerticalScrollBar().setPreferredSize(new Dimension(0, 0));
            sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            categoryScrolls.put(cat, sp);
            contentCards.add(sp, cat.name());
        }

        JPanel body = new JPanel(new BorderLayout(0, 6));
        body.setOpaque(false);
        body.add(buildTabBar(), BorderLayout.NORTH);
        body.add(contentCards, BorderLayout.CENTER);

        rebuildUpgrades();
        selectCategory(0);
        return body;
    }

    /** Info-tab-style toggle-button bar, one per shop category. */
    private JPanel buildTabBar() {
        ShopCategory[] cats = ShopCategory.values();
        JPanel bar = new JPanel(new GridLayout(1, cats.length, 4, 0));
        bar.setOpaque(false);
        bar.setBorder(new EmptyBorder(2, 4, 4, 4));
        for (int i = 0; i < cats.length; i++) {
            final int idx = i;
            JToggleButton b = new JToggleButton(cats[i].label);
            b.setFont(FontManager.getRunescapeSmallFont());
            b.setFocusPainted(false);
            b.setBorderPainted(false);
            b.setMargin(new Insets(2, 2, 2, 2));
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            b.addActionListener(e -> selectCategory(idx));
            styleTab(b, i == 0);
            catButtons.add(b);
            bar.add(b);
        }
        return bar;
    }

    private void selectCategory(int idx) {
        selectedCat = idx;
        contentLayout.show(contentCards, ShopCategory.values()[idx].name());
        for (int i = 0; i < catButtons.size(); i++) {
            styleTab(catButtons.get(i), i == idx);
            catButtons.get(i).setSelected(i == idx);
        }
    }

    private static void styleTab(JToggleButton b, boolean active) {
        b.setOpaque(true);
        b.setBackground(active ? ORANGE : ColorScheme.DARKER_GRAY_COLOR);
        b.setForeground(active ? new Color(30, 30, 30) : ColorScheme.LIGHT_GRAY_COLOR);
    }

    /** Rebuilds each category's upgrade cards from current state (the tab is the category label). */
    private void rebuildUpgrades() {
        buyButtons.clear();   // stale button refs — repopulated as cards are rebuilt below
        for (ShopCategory cat : ShopCategory.values()) {
            JPanel inner = categoryPanels.get(cat);
            inner.removeAll();
            boolean any = false;
            for (ShopUpgrade u : ShopUpgrade.values()) {
                if (u.category != cat) continue;
                inner.add(upgradeCard(u));
                inner.add(Box.createVerticalStrut(8));
                any = true;
            }
            if (!any) {
                JLabel none = new JLabel("Nothing here yet.");
                none.setFont(FontManager.getRunescapeSmallFont());
                none.setForeground(DIM);
                none.setAlignmentX(LEFT_ALIGNMENT);
                inner.add(none);
            }
            inner.revalidate();
            inner.repaint();
        }
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

        // Effect: current bonus, and (unless maxed) what the next tier upgrades it to.
        String effectText = maxed
                ? "Bonus: " + formatEffect(u, owned) + "  (max)"
                : "Bonus: " + formatEffect(u, owned) + "  →  " + formatEffect(u, owned + 1);
        JLabel effect = new JLabel(effectText);
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
            buy.setText("Buy tier " + (owned + 1) + "  —  " + cost + " credits");
            buy.setEnabled(afford);
            buy.setForeground(afford ? GOLD : DIM);
            buy.addActionListener(e -> {
                if (dataService.purchaseUpgrade(u)) {
                    refresh();
                    BestiaryPanel.recheckAchievements();
                }
            });
            // Registered so a credit-only change (e.g. a capture) can re-check affordability in place,
            // without rebuilding the card (the rebuild is what made the shop jiggle on capture).
            buyButtons.put(u, buy);
        }
        card.add(Box.createVerticalStrut(4));
        card.add(buy);
        return card;
    }

    /** Formats a fractional probability as a percentage, e.g. 0.003 -> "0.3%". */
    private static String formatPct(double frac) {
        return String.format("%.1f%%", frac * 100.0);
    }

    /** Formats an upgrade's total effect at {@code tiers} — flat credits, flat XP, or a percentage. */
    private static String formatEffect(ShopUpgrade u, int tiers) {
        if (u.isFlatCredits()) return "+" + (long) u.effectFor(tiers) + " credits";
        if (u.isFlatXp())      return "+" + (long) u.effectFor(tiers) + " XP";
        return "+" + formatPct(u.effectFor(tiers));
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
        // Rebuilding the upgrade cards (removeAll + re-add) is what makes the shop visibly "jiggle".
        // The panel refreshes on every kill, and a CAPTURE awards credits — so rebuilding whenever
        // credits move meant the shop jumped on every capture. Split the cases:
        //   • an owned TIER changed (a purchase) → full rebuild (rare, user-initiated, expected).
        //   • only CREDITS changed (a capture / discard) → re-check buy-button affordability in place,
        //     with NO teardown, so nothing shifts.
        //   • nothing changed → no-op.
        long credits  = dataService.getCredits();
        int  tierSig  = tierSignature();

        if (tierSig != lastTierSig) {
            lastTierSig = tierSig;
            lastCredits = credits;
            creditsLabel.setText(credits + " credits");
            if (contentCards != null) {
                // Preserve the active tab's scroll position so a rebuild doesn't jump the shop.
                final JScrollPane active = categoryScrolls.get(ShopCategory.values()[selectedCat]);
                final Point pos = active != null ? active.getViewport().getViewPosition() : null;
                rebuildUpgrades();
                if (active != null && pos != null) {
                    SwingUtilities.invokeLater(() -> active.getViewport().setViewPosition(pos));
                }
            }
            return;
        }

        if (credits != lastCredits) {
            lastCredits = credits;
            creditsLabel.setText(credits + " credits");
            updateAffordability(credits);
        }
    }

    /** Owned tiers when the cards were last built; a change means a purchase → full rebuild needed. */
    private int  lastTierSig = Integer.MIN_VALUE;
    /** Credit balance at the last refresh; a change alone only re-checks affordability (no rebuild). */
    private long lastCredits = Long.MIN_VALUE;

    /** A fingerprint of every upgrade's owned tier — changes only on a purchase. */
    private int tierSignature() {
        int sig = 1;
        for (ShopUpgrade u : ShopUpgrade.values()) {
            sig = sig * 31 + dataService.getUpgradeTier(u);
        }
        return sig;
    }

    /**
     * Re-checks each (non-maxed) buy button against the new credit balance, toggling only its
     * enabled state and colour. This touches no layout, so it can run on every capture without the
     * shop shifting — unlike a full {@link #rebuildUpgrades()}. Tier costs are unchanged here (only a
     * purchase changes a tier, and that path rebuilds), so the button text needs no update.
     */
    private void updateAffordability(long credits) {
        for (java.util.Map.Entry<ShopUpgrade, JButton> e : buyButtons.entrySet()) {
            long cost = dataService.upgradeCost(e.getKey());   // -1 if maxed (not in the map anyway)
            boolean afford = cost >= 0 && credits >= cost;
            JButton buy = e.getValue();
            buy.setEnabled(afford);
            buy.setForeground(afford ? GOLD : DIM);
        }
    }
}
