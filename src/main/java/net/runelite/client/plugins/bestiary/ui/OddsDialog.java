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
 * rarity, shiny (the odds), the stat/prayer roll bands, and the Power Level maths.
 * Zoomable (A− / A+ buttons or Ctrl + mouse wheel).
 */
public class OddsDialog extends JDialog {

    private static OddsDialog current;
    private static final int CONTENT_W = 300;

    private final OddsCalculator.Result r;
    private final CapturedCreature capture;
    private final JScrollPane scroll = new JScrollPane();
    private JLabel zoomLabel;
    private double zoom = 1.0;

    private Font fSmall, fSmallBold, fBold;

    public static void open(Window owner, CapturedCreature capture) {
        if (current != null && current.isShowing()) current.dispose();
        current = new OddsDialog(owner, capture);
        current.setVisible(true);
    }

    private OddsDialog(Window owner, CapturedCreature capture) {
        super(owner, "What were the odds?", ModalityType.MODELESS);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(true);
        this.capture = capture;
        this.r = OddsCalculator.compute(capture);

        // Zoom toolbar
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        bar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        zoomLabel = new JLabel("100%");
        zoomLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        zoomLabel.setFont(FontManager.getRunescapeSmallFont());
        bar.add(zoomButton("A−", -0.1));
        bar.add(zoomLabel);
        bar.add(zoomButton("A+", +0.1));

        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.addMouseWheelListener(e -> {
            if (e.isControlDown()) {
                setZoom(zoom + (e.getWheelRotation() < 0 ? 0.1 : -0.1));
                e.consume();
            } else if (scroll.getVerticalScrollBar().isVisible()) {
                JScrollBar sb = scroll.getVerticalScrollBar();
                sb.setValue(sb.getValue() + e.getUnitsToScroll() * sb.getUnitIncrement());
            }
        });

        JPanel outer = new JPanel(new BorderLayout());
        outer.add(bar, BorderLayout.NORTH);
        outer.add(scroll, BorderLayout.CENTER);
        setContentPane(outer);

        rebuild(true);
        setMinimumSize(new Dimension(320, 260));
        setLocationRelativeTo(owner);
    }

    private JButton zoomButton(String text, double delta) {
        JButton b = new JButton(text);
        b.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        b.setMargin(new Insets(0, 6, 0, 6));
        b.setFocusPainted(false);
        b.setBackground(ColorScheme.DARK_GRAY_COLOR);
        b.setForeground(Color.WHITE);
        b.setToolTipText("Zoom (or Ctrl + mouse wheel)");
        b.addActionListener(e -> setZoom(zoom + delta));
        return b;
    }

    private void setZoom(double z) {
        z = Math.max(0.8, Math.min(2.4, Math.round(z * 10) / 10.0));
        if (Math.abs(z - zoom) < 0.001) return;
        zoom = z;
        rebuild(false);
    }

    private void rebuild(boolean firstBuild) {
        float bs = FontManager.getRunescapeSmallFont().getSize2D();
        float bb = FontManager.getRunescapeBoldFont().getSize2D();
        fSmall     = FontManager.getRunescapeSmallFont().deriveFont((float) (bs * zoom));
        fSmallBold = fSmall.deriveFont(Font.BOLD);
        fBold      = FontManager.getRunescapeBoldFont().deriveFont((float) (bb * zoom));
        zoomLabel.setText(Math.round(zoom * 100) + "%");

        int vpw = scroll.getViewport().getWidth();
        int htmlW = vpw > 60 ? vpw - 34 : (int) (CONTENT_W * zoom);

        JPanel root = buildBody(htmlW);
        scroll.setViewportView(root);

        if (firstBuild) {
            Dimension pref = root.getPreferredSize();
            int w = Math.max(340, Math.min(600, pref.width + 30));
            int h = Math.min(680, pref.height + 44);
            scroll.setPreferredSize(new Dimension(w, h));
            pack();
        } else {
            root.revalidate();
            scroll.revalidate();
            scroll.repaint();
        }
    }

    private JPanel buildBody(int htmlW) {
        ScrollablePanel root = new ScrollablePanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(ColorScheme.DARK_GRAY_COLOR);
        root.setBorder(new EmptyBorder(12, 14, 12, 14));

        String shinyTag = r.shiny ? "  ✦ SHINY" : "";
        JLabel title = new JLabel(capture.npcName + "  —  " + r.rarity.label + shinyTag);
        title.setFont(fBold);
        title.setForeground(r.rarity.displayColor);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(title);

        JLabel sub = new JLabel("Captured at Bestiary level " + r.level + "  ·  " + r.difficulty.label + " tier");
        sub.setFont(fSmall);
        sub.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(sub);

        root.add(Box.createVerticalStrut(8));

        // Roll chain (the odds)
        root.add(sectionHeader("The roll chain"));
        root.add(kvRow("Catch (gate)", fSmall, Color.WHITE,
                OddsCalculator.pct(r.catchChance) + "  (" + OddsCalculator.oneIn(r.catchChance) + ")"));
        root.add(kvRow("This rarity", fSmall, r.rarity.displayColor,
                OddsCalculator.pct(r.rarityChance) + "  (" + OddsCalculator.oneIn(r.rarityChance) + ")"));
        if (r.shiny) {
            root.add(kvRow("Shiny", fSmall, new Color(255, 215, 0),
                    OddsCalculator.pct(r.shinyChance) + "  (" + OddsCalculator.oneIn(r.shinyChance) + ")"));
        }

        root.add(Box.createVerticalStrut(8));

        // Stats (info only)
        String statHdr = r.shiny
                ? "Stats — shiny (rolls above the " + r.rarity.label + " band)"
                : "Stats — " + r.rarity.label + " roll bands";
        root.add(sectionHeader(statHdr));
        root.add(statsTable());

        JLabel explain = new JLabel("<html><div style='width:" + htmlW + "px'>"
                + "Higher rarities lift the roll toward 99 (bigger lift for low stats); lower rarities "
                + "can dip below the base — never under 1. Bands overlap, so a lucky Rare can beat an "
                + "unlucky Epic. Prayer rolls the same way at half scale. "
                + "<font color='#a0a0a0'>Example, base&nbsp;50: Common&nbsp;37–50, Uncommon&nbsp;41–52, "
                + "Rare&nbsp;45–53, Epic&nbsp;50–61, Legendary&nbsp;56–74, Mythic&nbsp;70–90.</font></div></html>");
        explain.setFont(fSmall);
        explain.setForeground(new Color(140, 140, 140));
        explain.setAlignmentX(Component.LEFT_ALIGNMENT);
        explain.setBorder(new EmptyBorder(6, 0, 0, 0));
        root.add(explain);

        root.add(Box.createVerticalStrut(10));

        // Power Level — Prayer is the 7th stat
        int sevenStats = r.statSum + r.prayer;
        Color nearWhite = new Color(235, 235, 235);
        root.add(sectionHeader("Power Level"));
        Box sevenRow = kvRow("7 stats total", fSmall, ColorScheme.LIGHT_GRAY_COLOR, String.valueOf(sevenStats));
        styleValue(sevenRow, nearWhite, false);
        root.add(sevenRow);
        Box hpRow = kvRow("Hitpoints (factual)", fSmall, new Color(120, 200, 120), String.valueOf(r.hp));
        styleValue(hpRow, nearWhite, false);
        root.add(hpRow);
        Box plRow = kvRow("= Power Level  (" + sevenStats + " + " + r.hp + ") ÷ 8",
                fSmallBold, Color.WHITE, String.valueOf(r.powerLevel));
        styleValue(plRow, new Color(120, 200, 120), true);
        root.add(plRow);
        JLabel plNote = new JLabel("<html><div style='width:" + htmlW + "px'>"
                + "Power Level averages the 7 stats (Prayer counts as the 7th) with the monster's HP, ÷8. "
                + "HP isn't on the 1–99 scale, so it counts at 1/8 weight — <font color='#a0a0a0'>"
                + "negligible for a low-HP creature (stats decide), but dominant for a boss "
                + "(1200&nbsp;HP adds ~150). HP takes over above ~450&nbsp;HP.</font></div></html>");
        plNote.setFont(fSmall);
        plNote.setForeground(new Color(140, 140, 140));
        plNote.setAlignmentX(Component.LEFT_ALIGNMENT);
        plNote.setBorder(new EmptyBorder(4, 0, 0, 0));
        root.add(plNote);

        root.add(Box.createVerticalStrut(10));

        // Combined odds
        Box perCap = kvRow("Per capture (" + (r.shiny ? "Rarity × Shiny" : "Rarity chance") + ")",
                fSmallBold, Color.WHITE, OddsCalculator.oneIn(r.perCapture));
        perCap.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(1, 0, 0, 0, new Color(70, 70, 70)), new EmptyBorder(7, 0, 2, 0)));
        styleValue(perCap, new Color(120, 200, 120), true);
        root.add(perCap);

        Box perKill = kvRow("Per kill (Catch chance × Rarity chance" + (r.shiny ? " × Shiny" : "") + ")",
                fSmallBold, Color.WHITE, OddsCalculator.oneIn(r.perKill));
        styleValue(perKill, Color.WHITE, true);
        root.add(perKill);

        JLabel note = new JLabel("<html><div style='width:" + htmlW + "px'><i>\"Of your captures\" is how "
                + "often a capture is this rarity at level " + r.level + " — high levels make rarities much "
                + "more common. \"Per kill\" folds in the catch chance. Stat/prayer rolls are flavour and "
                + "don't affect either.</i></div></html>");
        note.setFont(fSmall);
        note.setForeground(new Color(130, 130, 130));
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        note.setBorder(new EmptyBorder(6, 0, 0, 0));
        root.add(note);

        return root;
    }

    /** Aligned Stat / Base / Roll / Band / Quality table (6 combat stats + Prayer). */
    private JPanel statsTable() {
        JPanel t = new JPanel(new GridBagLayout());
        t.setOpaque(false);
        t.setAlignmentX(Component.LEFT_ALIGNMENT);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(1, 0, 1, (int) (14 * zoom));

        String[] heads = {"Stat", "Base", "Roll", "Band", "Quality"};
        for (int c = 0; c < heads.length; c++) {
            g.gridx = c; g.gridy = 0;
            g.anchor = (c == 1 || c == 2) ? GridBagConstraints.EAST : GridBagConstraints.WEST;
            t.add(cell(heads[c], fSmallBold, new Color(140, 140, 140)), g);
        }

        int row = 1;
        for (OddsCalculator.StatOdds s : r.stats) {
            g.gridy = row++;
            g.gridx = 0; g.anchor = GridBagConstraints.WEST;
            t.add(cell(s.name, fSmall, new Color(210, 210, 210)), g);
            g.gridx = 1; g.anchor = GridBagConstraints.EAST;
            t.add(cell(String.valueOf(s.base), fSmall, new Color(150, 150, 150)), g);
            g.gridx = 2; g.anchor = GridBagConstraints.EAST;
            t.add(cell(String.valueOf(s.value), fSmallBold, Color.WHITE), g);
            g.gridx = 3; g.anchor = GridBagConstraints.WEST;
            t.add(cell(s.lo + "–" + s.hi, fSmall, ColorScheme.LIGHT_GRAY_COLOR), g);
            g.gridx = 4; g.anchor = GridBagConstraints.WEST;
            if (r.shiny) t.add(cell("✦ shiny", fSmall, new Color(255, 215, 0)), g);
            else         t.add(rollTagCell(s.value, s.lo, s.hi), g);
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
        if (hi <= lo)           { label = "fixed";    colour = new Color(144, 144, 144); }
        else {
            double f = (value - lo) / (double) (hi - lo);
            if (value >= hi)    { label = "MAX roll";  colour = new Color(122, 214, 122); }
            else if (f >= 0.67) { label = "high roll"; colour = new Color(143, 208, 143); }
            else if (f <= 0.33) { label = value <= lo ? "MIN roll" : "low roll"; colour = new Color(224, 112, 112); }
            else                { label = "mid roll";  colour = new Color(176, 176, 176); }
        }
        return cell(label, fSmall, colour);
    }

    /** Recolour a kvRow's value label (last component); optionally bump it to the bold font. */
    private void styleValue(Box row, Color colour, boolean bold) {
        JLabel v = (JLabel) row.getComponent(row.getComponentCount() - 1);
        if (bold) v.setFont(fBold);
        v.setForeground(colour);
    }

    private JLabel sectionHeader(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setFont(fSmallBold);
        l.setForeground(new Color(255, 152, 31));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(new EmptyBorder(0, 0, 3, 0));
        return l;
    }

    /** A left-label + right-value row; value snaps to the right and never overlaps the left. */
    private Box kvRow(String left, Font leftFont, Color leftColor, String right) {
        Box row = Box.createHorizontalBox();
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, (int) (18 * zoom) + 4));
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

    /** Content panel that tracks the scroll viewport's width so rows reflow instead of clipping. */
    private static final class ScrollablePanel extends JPanel implements Scrollable {
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 48; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
