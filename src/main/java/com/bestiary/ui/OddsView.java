package com.bestiary.ui;

import com.bestiary.model.CapturedCreature;
import com.bestiary.util.OddsCalculator;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;

/**
 * The "What were the odds?" breakdown for a single capture, as a reusable, width-tracking
 * content panel: the roll chain (catch/rarity/shiny → per-capture/per-kill), the stat roll
 * bands, and the Power Level maths with an average-roll comparison. Embedded both by the
 * standalone {@link OddsDialog} and the {@link CardDataDialog} "Odds" tab.
 */
public class OddsView extends JPanel implements Scrollable {

    private final OddsCalculator.Result r;
    private final CapturedCreature capture;
    private final Font body;
    private final Font bodyBold;

    public OddsView(CapturedCreature capture) {
        this.capture = capture;
        this.r = OddsCalculator.compute(capture);
        float base = FontManager.getRunescapeSmallFont().getSize2D();
        this.body     = FontManager.getRunescapeSmallFont().deriveFont(base + 1f);
        this.bodyBold = body.deriveFont(Font.BOLD);

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(new EmptyBorder(12, 14, 12, 14));
        build();
    }

    private void build() {
        // Title row — name + rarity on the left, REROLLED flag pinned top-right (if applicable)
        boolean rerolled = capture.rerolledBy != null && !capture.rerolledBy.isEmpty();
        Box titleRow = Box.createHorizontalBox();
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel title = new JLabel(capture.npcName + "  —  " + r.rarity.label + (r.shiny ? "  ✦ SHINY" : ""));
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(r.rarity.displayColor);
        titleRow.add(title);
        titleRow.add(Box.createHorizontalGlue());
        if (rerolled) {
            titleRow.add(rerolledBadge());
        }
        add(titleRow);
        JLabel sub = new JLabel("Captured at Bestiary level " + r.level + "  ·  " + r.difficulty.label + " tier");
        sub.setFont(body);
        sub.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(sub);

        if (rerolled) {
            add(paragraph("This card was rerolled — the odds below describe a raw pull at this rarity, "
                    + "not how this particular card was produced.", new Color(150, 120, 200)));
        }

        add(Box.createVerticalStrut(8));

        // Roll chain
        add(sectionHeader("The roll chain"));
        add(kvRow("Catch (gate)", body, Color.WHITE,
                OddsCalculator.pct(r.catchChance) + "  (" + OddsCalculator.oneIn(r.catchChance) + ")"));
        add(kvRow("This rarity", body, r.rarity.displayColor,
                OddsCalculator.pct(r.rarityChance) + "  (" + OddsCalculator.oneIn(r.rarityChance) + ")"));
        if (r.shiny) {
            add(kvRow("Shiny", body, new Color(255, 215, 0),
                    OddsCalculator.pct(r.shinyChance) + "  (" + OddsCalculator.oneIn(r.shinyChance) + ")"));
        }
        add(Box.createVerticalStrut(4));
        add(separator());
        add(Box.createVerticalStrut(4));
        Box perCap = kvRow("Per capture (" + (r.shiny ? "Rarity × Shiny" : "Rarity chance") + ")",
                bodyBold, Color.WHITE, OddsCalculator.oneIn(r.perCapture));
        styleValue(perCap, new Color(120, 200, 120));
        add(perCap);
        Box perKill = kvRow("Per kill (Catch chance × Rarity chance" + (r.shiny ? " × Shiny" : "") + ")",
                bodyBold, Color.WHITE, OddsCalculator.oneIn(r.perKill));
        styleValue(perKill, Color.WHITE);
        add(perKill);
        add(paragraph("<i>Per capture = how often a capture is this rarity at level " + r.level
                + " (high levels make rarities much more common). Per kill folds in the catch chance. "
                + "Stat rolls are flavour — they don't affect these odds.</i>"));

        add(Box.createVerticalStrut(10));

        // Stats
        add(sectionHeader(r.shiny
                ? "Stats — shiny roll bands (above " + r.rarity.label + ")"
                : "Stats — " + r.rarity.label + " roll bands"));
        add(statsTable());
        add(paragraph("Higher rarities lift the roll toward 99 (bigger lift for low stats); "
                + "lower rarities can dip below the base — never under 1. Bands overlap, so a lucky Rare "
                + "can beat an unlucky Epic. Prayer and Agility roll the same way at half scale. "
                + "<font color='#a0a0a0'>Example, base&nbsp;50: Common&nbsp;37–50, Uncommon&nbsp;41–52, "
                + "Rare&nbsp;45–53, Epic&nbsp;50–61, Legendary&nbsp;56–74, Mythic&nbsp;70–90.</font>"));

        add(Box.createVerticalStrut(10));

        // Power Level
        int sevenStats = r.statSum + r.prayer;
        int statAvg = Math.round(sevenStats / 7f);
        Color nearWhite = new Color(235, 235, 235);
        add(sectionHeader("Power Level"));
        add(styleValue(kvRow("7 stats total", body, ColorScheme.LIGHT_GRAY_COLOR,
                String.valueOf(sevenStats)), nearWhite));
        add(styleValue(kvRow("Stat average  (" + sevenStats + " ÷ 7)", body, ColorScheme.LIGHT_GRAY_COLOR,
                String.valueOf(statAvg)), nearWhite));
        add(styleValue(kvRow("Hitpoints (factual)  ÷ 6", body, new Color(120, 200, 120),
                "+" + Math.round(r.hp / 6f)), nearWhite));
        add(styleValue(kvRow("= Power Level  (" + sevenStats + " ÷ 7) + (" + r.hp + " ÷ 6)",
                body, Color.WHITE, String.valueOf(r.powerLevel)), new Color(120, 200, 120)));

        add(Box.createVerticalStrut(4));
        add(separator());
        add(Box.createVerticalStrut(4));

        add(styleValue(kvRow("Average " + r.rarity.label + " roll", body, ColorScheme.LIGHT_GRAY_COLOR,
                String.valueOf(r.avgPowerLevel)), nearWhite));
        int delta = r.powerLevel - r.avgPowerLevel;
        String deltaStr = delta > 0 ? "+" + delta + " above avg" : delta < 0 ? delta + " below avg" : "bang on avg";
        Color deltaCol = delta > 0 ? new Color(120, 200, 120) : delta < 0 ? new Color(224, 112, 112) : new Color(176, 176, 176);
        add(styleValue(kvRow("This card vs average", body, Color.WHITE, deltaStr), deltaCol));

        add(styleValue(kvRow("Average " + r.rarity.label + " shiny roll", body, ColorScheme.LIGHT_GRAY_COLOR,
                String.valueOf(r.avgShinyPowerLevel)), nearWhite));
        int dShiny = r.powerLevel - r.avgShinyPowerLevel;
        String dShinyStr = dShiny > 0 ? "+" + dShiny + " above avg shiny" : dShiny < 0 ? dShiny + " below avg shiny" : "bang on avg shiny";
        Color dShinyCol = dShiny > 0 ? new Color(120, 200, 120) : dShiny < 0 ? new Color(224, 112, 112) : new Color(176, 176, 176);
        add(styleValue(kvRow("This card vs average shiny", body, Color.WHITE, dShinyStr), dShinyCol));

        add(paragraph("Power Level = the 7-stat average + the monster's HP at 1/6 weight. "
                + "HP isn't on the 1–99 scale, so it's added separately — <font color='#a0a0a0'>"
                + "negligible for a low-HP creature (stats decide: +13 at 80&nbsp;HP), but dominant for a "
                + "boss (1200&nbsp;HP adds ~200). HP takes over above ~420&nbsp;HP.</font>"));
    }

    // ---- helpers ----

    private JLabel rerolledBadge() {
        JLabel badge = new JLabel("REROLLED");
        badge.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        badge.setForeground(new Color(180, 150, 230));
        badge.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(1, 1, 1, 1, new Color(120, 90, 170)),
                new EmptyBorder(1, 5, 1, 5)));
        return badge;
    }

    private JComponent paragraph(String html) {
        return paragraph(html, new Color(140, 140, 140));
    }

    /** A wrapping paragraph. JTextArea wraps to the panel's actual width — reliable at any size. */
    private JComponent paragraph(String html, Color colour) {
        JTextArea a = new JTextArea(stripHtml(html));
        a.setFont(body);
        a.setForeground(colour);
        a.setBackground(ColorScheme.DARK_GRAY_COLOR);
        a.setOpaque(false);
        a.setEditable(false);
        a.setFocusable(false);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrap.setBorder(new EmptyBorder(5, 0, 0, 0));
        wrap.add(a, BorderLayout.CENTER);
        return wrap;
    }

    private static String stripHtml(String html) {
        return html.replaceAll("<[^>]+>", "").replace("&nbsp;", " ").trim();
    }

    private JPanel statsTable() {
        JPanel t = new JPanel(new GridBagLayout());
        t.setOpaque(false);
        t.setAlignmentX(Component.LEFT_ALIGNMENT);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(1, 0, 1, 14);

        String[] heads = {"Stat", "Base", "Roll", "Band", "Quality"};
        for (int c = 0; c < heads.length; c++) {
            g.gridx = c; g.gridy = 0;
            g.anchor = (c == 1 || c == 2) ? GridBagConstraints.EAST : GridBagConstraints.WEST;
            t.add(cell(heads[c], bodyBold, new Color(140, 140, 140)), g);
        }
        int row = 1;
        for (OddsCalculator.StatOdds s : r.stats) {
            g.gridy = row++;
            g.gridx = 0; g.anchor = GridBagConstraints.WEST;
            t.add(cell(s.name, body, new Color(210, 210, 210)), g);
            g.gridx = 1; g.anchor = GridBagConstraints.EAST;
            t.add(cell(String.valueOf(s.base), body, new Color(150, 150, 150)), g);
            g.gridx = 2; g.anchor = GridBagConstraints.EAST;
            t.add(cell(String.valueOf(s.value), bodyBold, Color.WHITE), g);
            g.gridx = 3; g.anchor = GridBagConstraints.WEST;
            t.add(cell(s.lo + "–" + s.hi, body, ColorScheme.LIGHT_GRAY_COLOR), g);
            g.gridx = 4; g.anchor = GridBagConstraints.WEST;
            t.add(rollTagCell(s.value, s.lo, s.hi), g);
        }
        t.setMaximumSize(new Dimension(Integer.MAX_VALUE, t.getPreferredSize().height));
        return t;
    }

    private JLabel cell(String text, Font font, Color colour) {
        JLabel l = new JLabel(text);
        l.setFont(font);
        l.setForeground(colour);
        return l;
    }

    private JLabel rollTagCell(int value, int lo, int hi) {
        String label; Color colour;
        if (hi <= lo) {
            label = value >= 99 ? "MAX roll" : "fixed";
            colour = value >= 99 ? new Color(122, 214, 122) : new Color(144, 144, 144);
        } else {
            double f = (value - lo) / (double) (hi - lo);
            if (value >= hi)    { label = "MAX roll";  colour = new Color(122, 214, 122); }
            else if (f >= 0.67) { label = "high roll"; colour = new Color(143, 208, 143); }
            else if (f <= 0.33) { label = value <= lo ? "MIN roll" : "low roll"; colour = new Color(224, 112, 112); }
            else                { label = "mid roll";  colour = new Color(176, 176, 176); }
        }
        return cell(label, body, colour);
    }

    private JLabel sectionHeader(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setFont(bodyBold);
        l.setForeground(new Color(255, 152, 31));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(new EmptyBorder(0, 0, 3, 0));
        return l;
    }

    private JPanel separator() {
        JPanel s = new JPanel();
        s.setBackground(new Color(70, 70, 70));
        s.setAlignmentX(Component.LEFT_ALIGNMENT);
        s.setPreferredSize(new Dimension(10, 1));
        s.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        return s;
    }

    private Box kvRow(String left, Font leftFont, Color leftColor, String right) {
        Box row = Box.createHorizontalBox();
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, leftFont.getSize() + 8));
        JLabel l = new JLabel(left);
        l.setFont(leftFont);
        l.setForeground(leftColor);
        JLabel rr = new JLabel(right);
        rr.setFont(leftFont.deriveFont(Font.PLAIN));
        rr.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        row.add(l);
        row.add(Box.createHorizontalStrut(14));
        row.add(Box.createHorizontalGlue());
        row.add(rr);
        return row;
    }

    private Box styleValue(Box row, Color colour) {
        ((JLabel) row.getComponent(row.getComponentCount() - 1)).setForeground(colour);
        return row;
    }

    // Track the viewport width so rows reflow instead of clipping.
    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 16; }
    @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 48; }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }
}
