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
        for (OddsCalculator.StatOdds s : r.stats) {
            String right = r.shiny
                    ? "expected " + s.centre
                    : "rolled in " + s.lo + "–" + s.hi;
            root.add(kvRow(s.name + ":  " + s.value, smallBold, Color.WHITE, right));
        }

        root.add(Box.createVerticalStrut(10));

        // Combined — rarity (× shiny). Stat wiggle is not a factor.
        Box combined = kvRow("This exact card  (rarity" + (r.shiny ? " × shiny" : "") + ")",
                smallBold, Color.WHITE, OddsCalculator.oneIn(r.overall));
        combined.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(1, 0, 0, 0, new Color(70, 70, 70)), new EmptyBorder(7, 0, 0, 0)));
        combined.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        // Recolour the value label (last component) green + bold
        JLabel val = (JLabel) combined.getComponent(combined.getComponentCount() - 1);
        val.setFont(FontManager.getRunescapeBoldFont());
        val.setForeground(new Color(120, 200, 120));
        root.add(combined);

        JLabel note = new JLabel("<html><div style='width:" + CONTENT_W + "px'><i>Stat rolls are flavour "
                + "and don't affect these odds. The catch gate above is the separate chance the capture "
                + "happened at all.</i></div></html>");
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
