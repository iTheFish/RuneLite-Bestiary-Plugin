package net.runelite.client.plugins.bestiary.ui;

import net.runelite.client.plugins.bestiary.model.CapturedCreature;
import net.runelite.client.plugins.bestiary.util.OddsCalculator;
import net.runelite.client.plugins.bestiary.util.RarityRoller;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;

/**
 * MODELESS "What were the odds?" breakdown for a single capture — catch chance,
 * rarity, shiny (the odds), plus each stat's roll-from-expected (info only).
 */
public class OddsDialog extends JDialog {

    private static OddsDialog current;
    private static final int CONTENT_W = 300;

    public static void open(Window owner, CapturedCreature capture) {
        if (current != null && current.isShowing()) current.dispose();
        current = new OddsDialog(owner, capture);
        current.setVisible(true);
    }

    private OddsDialog(Window owner, CapturedCreature capture) {
        super(owner, "What were the odds?", ModalityType.MODELESS);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(true);

        OddsCalculator.Result r = OddsCalculator.compute(capture);
        Font small = FontManager.getRunescapeSmallFont();
        Font smallBold = small.deriveFont(Font.BOLD);

        ScrollablePanel root = new ScrollablePanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(ColorScheme.DARK_GRAY_COLOR);
        root.setBorder(new EmptyBorder(12, 14, 12, 14));

        // Title
        String shinyTag = r.shiny ? "  ✦ SHINY" : "";
        JLabel title = new JLabel(capture.npcName + "  —  " + r.rarity.label + shinyTag);
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(r.rarity.displayColor);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(title);

        JLabel sub = new JLabel("Captured at Bestiary level " + r.level + "  ·  " + r.difficulty.label + " tier");
        sub.setFont(small);
        sub.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(sub);

        root.add(Box.createVerticalStrut(8));

        // Roll-chain section (these ARE the odds)
        root.add(sectionHeader("The roll chain"));
        root.add(kvRow("Catch (gate)", small, Color.WHITE,
                OddsCalculator.pct(r.catchChance) + "  (" + OddsCalculator.oneIn(r.catchChance) + ")"));
        root.add(kvRow("This rarity", small, r.rarity.displayColor,
                OddsCalculator.pct(r.rarityChance) + "  (" + OddsCalculator.oneIn(r.rarityChance) + ")"));
        if (r.shiny) {
            root.add(kvRow("Shiny", small, new Color(255, 215, 0),
                    OddsCalculator.pct(r.shinyChance) + "  (" + OddsCalculator.oneIn(r.shinyChance) + ")"));
        }

        root.add(Box.createVerticalStrut(8));

        // Per-stat section — info only (the roll does NOT affect the odds)
        String statHdr = r.shiny
                ? "Stats — shiny bonus +" + RarityRoller.SHINY_MIN_BONUS + ".." + RarityRoller.SHINY_MAX_BONUS
                : "Stats — " + r.rarity.label + " roll band (overlaps neighbours)";
        root.add(sectionHeader(statHdr));
        root.add(statsTable(r, small, smallBold));

        JLabel explain = new JLabel("<html><div style='width:" + CONTENT_W + "px'>"
                + "Higher rarities lift the roll toward 99 (bigger lift for low stats); lower rarities "
                + "can dip below the base — never under 1. Bands overlap, so a lucky Rare can beat an "
                + "unlucky Epic. <font color='#a0a0a0'>Example, base&nbsp;50: Common&nbsp;37–50, "
                + "Uncommon&nbsp;41–52, Rare&nbsp;45–53, Epic&nbsp;50–61, Legendary&nbsp;56–74, "
                + "Mythic&nbsp;70–90.</font></div></html>");
        explain.setFont(small);
        explain.setForeground(new Color(140, 140, 140));
        explain.setAlignmentX(Component.LEFT_ALIGNMENT);
        explain.setBorder(new EmptyBorder(6, 0, 0, 0));
        root.add(explain);

        root.add(Box.createVerticalStrut(10));

        // Power Level derivation
        root.add(sectionHeader("Power Level"));
        root.add(kvRow("6 stats total", small, ColorScheme.LIGHT_GRAY_COLOR, String.valueOf(r.statSum)));
        root.add(kvRow("Hitpoints (factual)", small, new Color(120, 200, 120), String.valueOf(r.hp)));
        Box plRow = kvRow("= Power Level  (" + r.statSum + " + " + r.hp + ") ÷ 7", smallBold, Color.WHITE,
                String.valueOf(r.powerLevel));
        plColour(plRow);
        root.add(plRow);
        root.add(kvRow("Prayer (card info)", small, new Color(90, 190, 235), String.valueOf(r.prayer)));

        root.add(Box.createVerticalStrut(10));

        // Combined odds — two perspectives. Stat wiggle is not a factor.
        Box perCap = kvRow("Of your captures" + (r.shiny ? " (rarity × shiny)" : " (this rarity)"),
                smallBold, Color.WHITE, OddsCalculator.oneIn(r.perCapture));
        perCap.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(1, 0, 0, 0, new Color(70, 70, 70)), new EmptyBorder(7, 0, 2, 0)));
        perCap.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        plColour(perCap);
        root.add(perCap);

        Box perKill = kvRow("Per kill (× catch — the whole event)", small, ColorScheme.LIGHT_GRAY_COLOR,
                OddsCalculator.oneIn(r.perKill));
        perKill.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        root.add(perKill);

        JLabel note = new JLabel("<html><div style='width:" + CONTENT_W + "px'><i>\"Of your captures\" is "
                + "how often a capture is this rarity at level " + r.level + " — high levels make rarities "
                + "much more common. \"Per kill\" folds in the catch chance for the true odds of the whole "
                + "event. Stat rolls are flavour and don't affect either.</i></div></html>");
        note.setFont(small);
        note.setForeground(new Color(130, 130, 130));
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        note.setBorder(new EmptyBorder(6, 0, 0, 0));
        root.add(note);

        JScrollPane scroll = new JScrollPane(root,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        Dimension pref = root.getPreferredSize();
        int w = Math.max(330, Math.min(560, pref.width + 28));
        int h = Math.min(640, pref.height + 8);
        scroll.setPreferredSize(new Dimension(w, h));

        setContentPane(scroll);
        pack();
        setMinimumSize(new Dimension(320, 240));
        setLocationRelativeTo(owner);
    }

    /** Content panel that tracks the scroll viewport's width, so rows reflow (and the
     *  right-hand values move left) when the window is narrowed instead of being clipped. */
    private static final class ScrollablePanel extends JPanel implements Scrollable {
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 48; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    /** Aligned STAT / ROLL / BAND / QUALITY table for the six stats. */
    private static JPanel statsTable(OddsCalculator.Result r, Font small, Font smallBold) {
        JPanel t = new JPanel(new GridBagLayout());
        t.setOpaque(false);
        t.setAlignmentX(Component.LEFT_ALIGNMENT);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(1, 0, 1, 14);

        String[] heads = {"Stat", "Base", "Roll", r.shiny ? "Expected" : "Band", r.shiny ? "" : "Quality"};
        for (int c = 0; c < heads.length; c++) {
            g.gridx = c; g.gridy = 0;
            g.anchor = (c == 1 || c == 2) ? GridBagConstraints.EAST : GridBagConstraints.WEST;
            t.add(cell(heads[c], small.deriveFont(Font.BOLD), new Color(140, 140, 140)), g);
        }

        int row = 1;
        for (OddsCalculator.StatOdds s : r.stats) {
            g.gridy = row++;
            g.gridx = 0; g.anchor = GridBagConstraints.WEST;
            t.add(cell(s.name, small, new Color(210, 210, 210)), g);
            g.gridx = 1; g.anchor = GridBagConstraints.EAST;
            t.add(cell(String.valueOf(s.base), small, new Color(150, 150, 150)), g);
            g.gridx = 2; g.anchor = GridBagConstraints.EAST;
            t.add(cell(String.valueOf(s.value), smallBold, Color.WHITE), g);
            g.gridx = 3; g.anchor = GridBagConstraints.WEST;
            t.add(cell(r.shiny ? String.valueOf(s.centre) : s.lo + "–" + s.hi, small,
                    ColorScheme.LIGHT_GRAY_COLOR), g);
            g.gridx = 4; g.anchor = GridBagConstraints.WEST;
            if (r.shiny) {
                t.add(cell("shiny", small, new Color(255, 215, 0)), g);
            } else {
                t.add(rollTagCell(s.value, s.lo, s.hi, small), g);
            }
        }

        t.setMaximumSize(new Dimension(Integer.MAX_VALUE, t.getPreferredSize().height));
        return t;
    }

    private static JLabel cell(String text, Font font, Color colour) {
        JLabel l = new JLabel(text);
        l.setFont(font);
        l.setForeground(colour);
        return l;
    }

    /** A coloured "high/mid/low" label for where a value landed within its [lo, hi] band. */
    private static JLabel rollTagCell(int value, int lo, int hi, Font font) {
        String label; Color colour;
        if (hi <= lo)           { label = "fixed";    colour = new Color(144, 144, 144); }
        else {
            double f = (value - lo) / (double) (hi - lo);
            if (value >= hi)    { label = "MAX roll";  colour = new Color(122, 214, 122); }
            else if (f >= 0.67) { label = "high roll"; colour = new Color(143, 208, 143); }
            else if (f <= 0.33) { label = value <= lo ? "MIN roll" : "low roll"; colour = new Color(224, 112, 112); }
            else                { label = "mid roll";  colour = new Color(176, 176, 176); }
        }
        return cell(label, font, colour);
    }

    /** Recolour a kvRow's value label (last component) bold green. */
    private static void plColour(Box row) {
        JLabel v = (JLabel) row.getComponent(row.getComponentCount() - 1);
        v.setFont(FontManager.getRunescapeBoldFont());
        v.setForeground(new Color(120, 200, 120));
    }

    private static JLabel sectionHeader(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        l.setForeground(new Color(255, 152, 31));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(new EmptyBorder(0, 0, 3, 0));
        return l;
    }

    /**
     * A left-label + right-value row. Uses a horizontal box (left, min gap, glue, right)
     * so the value snaps to the right edge and can never overlap the left text.
     */
    private static Box kvRow(String left, Font leftFont, Color leftColor, String right) {
        Box row = Box.createHorizontalBox();
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        JLabel l = new JLabel(left);
        l.setFont(leftFont);
        l.setForeground(leftColor);
        JLabel rr = new JLabel(right);
        rr.setFont(FontManager.getRunescapeSmallFont());
        rr.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        row.add(l);
        row.add(Box.createHorizontalStrut(14));   // guaranteed gap so left/right never touch
        row.add(Box.createHorizontalGlue());       // pushes the value to the right edge
        row.add(rr);
        return row;
    }
}
