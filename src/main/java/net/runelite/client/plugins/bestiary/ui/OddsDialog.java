package net.runelite.client.plugins.bestiary.ui;

import net.runelite.client.plugins.bestiary.model.CapturedCreature;
import net.runelite.client.plugins.bestiary.util.OddsCalculator;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;

/**
 * MODELESS "What were the odds?" breakdown for a single capture — catch chance,
 * rarity, shiny, and each stat's individual wiggle odds, plus a combined figure.
 */
public class OddsDialog extends JDialog {

    private static OddsDialog current;

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

        JPanel root = new JPanel();
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
        sub.setFont(FontManager.getRunescapeSmallFont());
        sub.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(sub);

        root.add(Box.createVerticalStrut(8));

        // Roll-chain section
        root.add(sectionHeader("The roll chain"));
        root.add(oddsRow("Catch (gate)", r.catchChance, Color.WHITE));
        root.add(oddsRow("This rarity", r.rarityChance, r.rarity.displayColor));
        if (r.shiny) {
            root.add(oddsRow("Shiny", r.shinyChance, new Color(255, 215, 0)));
        }

        root.add(Box.createVerticalStrut(8));

        // Per-stat section
        root.add(sectionHeader(r.shiny ? "Stats (shiny = best roll, fixed)" : "Stat wiggle (±" + wiggleTag() + ")"));
        for (OddsCalculator.StatOdds s : r.stats) {
            root.add(statRow(s, r.shiny));
        }

        root.add(Box.createVerticalStrut(8));

        // Combined
        JPanel combined = new JPanel(new BorderLayout());
        combined.setOpaque(false);
        combined.setBorder(new MatteBorder(1, 0, 0, 0, new Color(70, 70, 70)));
        combined.setAlignmentX(Component.LEFT_ALIGNMENT);
        combined.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        JLabel cl = new JLabel("<html>This exact card<br>"
                + "<font color='#c8c8c8'>rarity × " + (r.shiny ? "shiny × " : "") + "all stats</font></html>");
        cl.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        cl.setForeground(Color.WHITE);
        cl.setBorder(new EmptyBorder(6, 0, 0, 0));
        JLabel cr = new JLabel(OddsCalculator.oneIn(r.overall));
        cr.setFont(FontManager.getRunescapeBoldFont());
        cr.setForeground(new Color(120, 200, 120));
        cr.setBorder(new EmptyBorder(6, 0, 0, 0));
        combined.add(cl, BorderLayout.WEST);
        combined.add(cr, BorderLayout.EAST);
        root.add(combined);

        JLabel note = new JLabel("<html><i>Excludes the catch gate above; that's the odds the "
                + "capture happened at all.</i></html>");
        note.setFont(FontManager.getRunescapeSmallFont());
        note.setForeground(new Color(130, 130, 130));
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(note);

        JScrollPane scroll = new JScrollPane(root,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        // Size to the content's natural width (+ padding for the scrollbar) so nothing clips.
        Dimension pref = root.getPreferredSize();
        int w = Math.min(560, pref.width + 28);
        int h = Math.min(640, pref.height + 8);
        scroll.setPreferredSize(new Dimension(w, h));

        setContentPane(scroll);
        pack();
        setMinimumSize(new Dimension(300, 220));
        setLocationRelativeTo(owner);
    }

    private static String wiggleTag() {
        return String.valueOf(net.runelite.client.plugins.bestiary.util.RarityRoller.WIGGLE);
    }

    private static JLabel sectionHeader(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        l.setForeground(new Color(255, 152, 31));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(new EmptyBorder(0, 0, 3, 0));
        return l;
    }

    private static JPanel oddsRow(String label, double p, Color accent) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        JLabel l = new JLabel(label);
        l.setFont(FontManager.getRunescapeSmallFont());
        l.setForeground(accent);
        JLabel v = new JLabel(OddsCalculator.pct(p) + "   (" + OddsCalculator.oneIn(p) + ")");
        v.setFont(FontManager.getRunescapeSmallFont());
        v.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        row.add(l, BorderLayout.WEST);
        row.add(v, BorderLayout.EAST);
        return row;
    }

    private static JPanel statRow(OddsCalculator.StatOdds s, boolean shiny) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        int off = s.value - s.centre;
        String offStr = shiny ? "" : "  (" + (off >= 0 ? "+" + off : String.valueOf(off)) + ")";
        JLabel l = new JLabel(s.name + ":  " + s.value + "   " + "expected " + s.centre + offStr);
        l.setFont(FontManager.getRunescapeSmallFont());
        l.setForeground(Color.WHITE);
        JLabel v = new JLabel(shiny ? "fixed" : OddsCalculator.pct(s.prob));
        v.setFont(FontManager.getRunescapeSmallFont());
        v.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        row.add(l, BorderLayout.WEST);
        row.add(v, BorderLayout.EAST);
        return row;
    }
}
