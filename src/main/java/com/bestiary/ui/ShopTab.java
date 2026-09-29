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
        // Two columns so the tabs wrap into a tidy grid (e.g. 4 categories → 2×2) rather than
        // cramming every tab into one row in the narrow side panel.
        JPanel bar = new JPanel(new GridLayout(0, 2, 4, 4));
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
            if (cat == ShopCategory.LEVEL_99) {
                buildLevel99Into(inner);   // locked teaser, or Passive/Consumable sub-tabs
                inner.revalidate();
                inner.repaint();
                continue;
            }
            boolean any = false;
            for (ShopUpgrade u : ShopUpgrade.values()) {
                if (u.category != cat) continue;
                inner.add(upgradeCard(u));
                inner.add(Box.createVerticalStrut(8));
                any = true;
            }
            if (!any) {
                inner.add(emptyLabel("Nothing here yet."));
            }
            inner.revalidate();
            inner.repaint();
        }
    }

    /** Which Level 99 sub-tab is showing: 0 = Passive, 1 = Consumable. Remembered across rebuilds. */
    private int level99Sub = 0;

    /**
     * Level 99 endgame shop: locked until Capture Level 99, then two sub-tabs — Passive (the endgame
     * upgrades) and Consumable (coming soon).
     */
    private void buildLevel99Into(JPanel inner) {
        boolean unlocked = dataService.isLevel99Unlocked();

        JPanel subBar = new JPanel(new GridLayout(1, 2, 4, 4));
        subBar.setOpaque(false);
        subBar.setAlignmentX(LEFT_ALIGNMENT);
        subBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        JToggleButton passiveBtn    = subTabButton("Passive");
        JToggleButton consumableBtn = subTabButton("Consumable");

        CardLayout sub = new CardLayout();
        JPanel subContent = new JPanel(sub);
        subContent.setOpaque(false);
        subContent.setAlignmentX(LEFT_ALIGNMENT);

        JPanel passivePanel = vbox();
        if (!unlocked) {
            passivePanel.add(emptyLabel("Reach Capture Level 99 to reveal these upgrades."));
            passivePanel.add(Box.createVerticalStrut(8));
        }
        for (ShopUpgrade u : ShopUpgrade.values()) {
            if (u.category != ShopCategory.LEVEL_99) continue;
            // Locked: same dark padlock look as an uncaught album card, contents hidden.
            passivePanel.add(unlocked ? upgradeCard(u) : lockedUpgradeCard());
            passivePanel.add(Box.createVerticalStrut(8));
        }
        JPanel consumablePanel = vbox();
        consumablePanel.add(consumableTeaser());

        subContent.add(passivePanel,    "P");
        subContent.add(consumablePanel, "C");

        passiveBtn.addActionListener(e -> {
            level99Sub = 0; sub.show(subContent, "P"); styleSub(passiveBtn, consumableBtn);
        });
        consumableBtn.addActionListener(e -> {
            level99Sub = 1; sub.show(subContent, "C"); styleSub(passiveBtn, consumableBtn);
        });

        subBar.add(passiveBtn);
        subBar.add(consumableBtn);
        inner.add(subBar);
        inner.add(Box.createVerticalStrut(8));
        inner.add(subContent);

        styleSub(passiveBtn, consumableBtn);
        sub.show(subContent, level99Sub == 0 ? "P" : "C");
    }

    private JToggleButton subTabButton(String text) {
        JToggleButton b = new JToggleButton(text);
        b.setFont(FontManager.getRunescapeSmallFont());
        b.setFocusPainted(false);
        b.setContentAreaFilled(false);
        b.setOpaque(true);
        b.setMargin(new Insets(2, 2, 2, 2));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private void styleSub(JToggleButton passive, JToggleButton consumable) {
        styleSubTab(passive,    level99Sub == 0); passive.setSelected(level99Sub == 0);
        styleSubTab(consumable, level99Sub == 1); consumable.setSelected(level99Sub == 1);
    }

    /** Level 99 sub-tabs: selected = gold text on a warm dark fill with a gold underline. */
    private static void styleSubTab(JToggleButton b, boolean active) {
        b.setBackground(active ? SUB_ACTIVE_BG : ColorScheme.DARKER_GRAY_COLOR);
        b.setForeground(active ? SUB_ACTIVE_FG : ColorScheme.LIGHT_GRAY_COLOR);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 2, 0, active ? SUB_ACTIVE_FG : ColorScheme.DARKER_GRAY_COLOR),
                new EmptyBorder(3, 4, 1, 4)));
    }

    private static final Color SUB_ACTIVE_BG = new Color(62, 50, 24);
    private static final Color SUB_ACTIVE_FG = new Color(255, 210, 100);

    /** A padlocked Level 99 upgrade slot, styled like an uncaught album card (details hidden). */
    private JPanel lockedUpgradeCard() {
        JPanel card = new JPanel(new BorderLayout(10, 0)) {
            @Override public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(LOCKED_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(LOCKED_ACCENT);
                g2.fillRoundRect(0, 0, 4, getHeight(), 4, 4);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(8, 12, 8, 8));
        card.setAlignmentX(LEFT_ALIGNMENT);

        JLabel lock = new JLabel(new PadlockIcon());
        card.add(lock, BorderLayout.WEST);

        JPanel text = new JPanel(new GridLayout(2, 1, 0, 2));
        text.setOpaque(false);
        JLabel title = new JLabel("???");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(new Color(110, 110, 110));
        JLabel sub = new JLabel("Unlocks at Capture Level 99");
        sub.setFont(FontManager.getRunescapeSmallFont());
        sub.setForeground(new Color(95, 95, 95));
        text.add(title);
        text.add(sub);
        card.add(text, BorderLayout.CENTER);
        return card;
    }

    private static final Color LOCKED_BG     = new Color(22, 22, 22);
    private static final Color LOCKED_ACCENT = new Color(48, 48, 48);

    /** Small grey padlock matching the album's locked-card padlock. */
    private static final class PadlockIcon implements Icon {
        @Override public int getIconWidth()  { return 22; }
        @Override public int getIconHeight() { return 26; }
        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int bodyW = 20, bodyH = 14, bodyX = x + 1, bodyY = y + 11;
            int cx = bodyX + bodyW / 2;
            g2.setColor(new Color(70, 70, 70));
            g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawArc(cx - 6, y + 2, 12, 16, 0, 180);
            g2.setColor(new Color(60, 60, 60));
            g2.fillRoundRect(bodyX, bodyY, bodyW, bodyH, 4, 4);
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(new Color(90, 90, 90));
            g2.drawRoundRect(bodyX, bodyY, bodyW, bodyH, 4, 4);
            g2.setColor(new Color(30, 30, 30));
            g2.fillOval(cx - 2, bodyY + 4, 4, 4);
            g2.fillRect(cx - 1, bodyY + 7, 2, 4);
            g2.dispose();
        }
    }

    private static JPanel vbox() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setAlignmentX(LEFT_ALIGNMENT);
        return p;
    }

    private JLabel emptyLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(FontManager.getRunescapeSmallFont());
        l.setForeground(DIM);
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    /** "Coming soon" card for the Level 99 Consumable sub-tab (the mechanic is still being designed). */
    private JPanel consumableTeaser() {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(255, 165, 0, 70), 1, true),
                new EmptyBorder(10, 10, 10, 10)));
        card.setAlignmentX(LEFT_ALIGNMENT);

        JLabel title = new JLabel("🧪 Consumables");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(GOLD);
        title.setAlignmentX(LEFT_ALIGNMENT);
        card.add(title);

        JTextArea body = new JTextArea(
                "Coming soon. One-time boosts you buy and activate for a temporary edge, being "
                        + "designed now.\n\nGot ideas for what they should do? Share them in the "
                        + "suggestions channel on our Discord.");
        body.setEditable(false);
        body.setFocusable(false);
        body.setLineWrap(true);
        body.setWrapStyleWord(true);
        body.setOpaque(false);
        body.setFont(FontManager.getRunescapeSmallFont());
        body.setForeground(DIM);
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.setAlignmentX(LEFT_ALIGNMENT);
        wrap.setBorder(new EmptyBorder(4, 0, 0, 0));
        wrap.add(body, BorderLayout.CENTER);
        card.add(wrap);

        JButton discord = new JButton("Discord");
        discord.setFont(FontManager.getRunescapeSmallFont());
        discord.setFocusPainted(false);
        discord.setToolTipText(AboutDialog.DISCORD_URL);
        discord.setAlignmentX(LEFT_ALIGNMENT);
        discord.addActionListener(e -> net.runelite.client.util.LinkBrowser.browse(AboutDialog.DISCORD_URL));
        card.add(Box.createVerticalStrut(8));
        card.add(discord);
        return card;
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
            buy.setText("Buy tier " + (owned + 1) + "  ·  " + cost + " credits");
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
        // Fold in the Level 99 unlock so reaching level 99 rebuilds the shop (revealing the tab).
        sig = sig * 31 + (dataService.isLevel99Unlocked() ? 1 : 0);
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
