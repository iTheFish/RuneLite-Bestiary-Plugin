package com.bestiary.ui;

import com.bestiary.model.CreatureRarity;
import com.bestiary.model.DifficultyTier;
import com.bestiary.service.ProgressionService;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;

/**
 * MODELESS info dialog showing catch-chance per difficulty and rarity odds
 * at the player's current Bestiary level. Recreated on each open so the
 * level displayed is always current.
 */
public class CaptureRateDialog extends JDialog {

    private static final Color ORANGE = new Color(255, 165, 0);
    private static final Color MUTED  = new Color(115, 115, 115);
    private static final Color BG     = new Color(28,  28,  28);
    private static final Color TEXT   = new Color(220, 220, 220);

    private static CaptureRateDialog current;

    public static void open(Window owner, ProgressionService ps) {
        if (current != null) current.dispose();
        current = new CaptureRateDialog(owner, ps.getLevel());
    }

    private CaptureRateDialog(Window owner, int level) {
        super(owner, "Capture Rates", ModalityType.MODELESS);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(BG);
        root.setBorder(new EmptyBorder(12, 14, 14, 14));

        JLabel title = new JLabel("CAPTURE RATES  —  LEVEL " + level);
        title.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        title.setForeground(ORANGE);
        title.setAlignmentX(LEFT_ALIGNMENT);
        root.add(title);
        root.add(Box.createVerticalStrut(12));

        root.add(sectionHeader("CATCH CHANCE BY DIFFICULTY"));
        root.add(Box.createVerticalStrut(4));
        root.add(buildCatchTable(level));
        root.add(Box.createVerticalStrut(14));

        root.add(sectionHeader("RARITY ODDS AT YOUR LEVEL"));
        root.add(Box.createVerticalStrut(4));
        root.add(buildRarityTable(level));
        root.add(Box.createVerticalStrut(12));

        root.add(noteRow("These are separate rolls: first the catch lands (or doesn't),"));
        root.add(noteRow("then rarity is decided. Both improve as your level rises."));
        root.add(Box.createVerticalStrut(8));

        double shinyPct = (0.002 + Math.max(0, Math.min(98, level - 1)) / 98.0 * (0.02 - 0.002)) * 100.0;
        JLabel shinyTitle = new JLabel(String.format("SHINY CHANCE:  %.2f%%", shinyPct));
        shinyTitle.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        shinyTitle.setForeground(new Color(255, 235, 120));
        shinyTitle.setAlignmentX(LEFT_ALIGNMENT);
        root.add(shinyTitle);
        root.add(noteRow("A third independent roll — any rarity can be shiny (0.2% at"));
        root.add(noteRow("Lv 1, up to 2% at Lv 99). A shiny always rolls near-max stats."));

        setContentPane(root);
        pack();
        setMinimumSize(new Dimension(380, getHeight()));
        setLocationRelativeTo(owner);
        setVisible(true);
        toFront();
    }

    // -------------------------------------------------------------------------
    // Catch chance table
    // -------------------------------------------------------------------------

    // Formula mirrors CaptureService.calculateCatchRate exactly.
    // Tiers ordered BEGINNER→BOSS, matching DifficultyTier.values().
    private static final double[] BASE     = {0.20,  0.15,  0.10,  0.06,  0.03,  0.015};
    private static final double[] PER_LVL  = {0.0050,0.0040,0.0030,0.0022,0.0013,0.0007};
    private static final double[] CAP      = {0.60,  0.50,  0.40,  0.28,  0.15,  0.08 };

    private static JPanel buildCatchTable(int level) {
        JPanel panel = col();
        panel.add(tableRow("Difficulty", "Your level", "Max (cap)", new Color(180, 180, 180), true));
        panel.add(Box.createVerticalStrut(2));

        DifficultyTier[] tiers = DifficultyTier.values();
        for (int i = 0; i < tiers.length; i++) {
            double rate = Math.min(BASE[i] + (level - 1) * PER_LVL[i], CAP[i]);
            String cur  = String.format("%.1f%%", rate * 100);
            String cap  = String.format("%.0f%%", CAP[i] * 100);
            panel.add(tableRow("● " + tiers[i].label, cur, cap, tiers[i].displayColor, false));
            panel.add(Box.createVerticalStrut(1));
        }
        return panel;
    }

    // -------------------------------------------------------------------------
    // Rarity odds table
    // -------------------------------------------------------------------------

    // Multipliers mirror RarityRoller exactly (index = COMMON…MYTHIC ordinal).
    private static final double[] MAX_MULT = {0.50, 1.30, 2.00, 4.00, 8.00, 12.0};

    private static JPanel buildRarityTable(int level) {
        JPanel panel = col();
        panel.add(tableRow("Rarity", "Your level", "At Lv 99", new Color(180, 180, 180), true));
        panel.add(Box.createVerticalStrut(2));

        CreatureRarity[] rarities = CreatureRarity.values();
        double tNow = Math.max(0, Math.min(98, level - 1)) / 98.0;

        double totalNow = 0, total99 = 0;
        double[] wNow = new double[rarities.length];
        double[] w99  = new double[rarities.length];
        for (int i = 0; i < rarities.length; i++) {
            wNow[i] = rarities[i].probability * (1.0 + tNow * (MAX_MULT[i] - 1.0));
            w99[i]  = rarities[i].probability * MAX_MULT[i];
            totalNow += wNow[i];
            total99  += w99[i];
        }

        for (int i = 0; i < rarities.length; i++) {
            double pNow = wNow[i] / totalNow * 100;
            double p99  = w99[i]  / total99  * 100;
            String sNow = formatPct(pNow);
            String s99  = formatPct(p99);
            panel.add(tableRow("● " + rarities[i].label, sNow, s99, rarities[i].displayColor, false));
            panel.add(Box.createVerticalStrut(1));
        }
        return panel;
    }

    private static String formatPct(double pct) {
        if (pct >= 10.0) return String.format("%.1f%%", pct);
        if (pct >= 1.0)  return String.format("%.2f%%", pct);
        return String.format("%.3f%%", pct);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static JPanel col() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        p.setAlignmentX(LEFT_ALIGNMENT);
        return p;
    }

    private static JPanel sectionHeader(String text) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setAlignmentX(LEFT_ALIGNMENT);
        p.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 3, 0, 0, ORANGE),
                new EmptyBorder(3, 8, 3, 8)));
        JLabel l = new JLabel(text);
        l.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        l.setForeground(ORANGE);
        p.add(l, BorderLayout.WEST);
        return p;
    }

    private static JPanel tableRow(String col1, String col2, String col3,
                                   Color color, boolean header) {
        JPanel row = new JPanel(new GridLayout(1, 3, 0, 0));
        row.setBackground(header ? new Color(22, 22, 22) : new Color(33, 33, 33));
        row.setOpaque(true);
        row.setBorder(new EmptyBorder(3, 6, 3, 6));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        row.setAlignmentX(LEFT_ALIGNMENT);

        Font font = header
                ? FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD)
                : FontManager.getRunescapeSmallFont();
        JLabel l1 = new JLabel(col1);
        JLabel l2 = new JLabel(col2, SwingConstants.CENTER);
        JLabel l3 = new JLabel(col3, SwingConstants.RIGHT);
        for (JLabel l : new JLabel[]{l1, l2, l3}) {
            l.setFont(font);
            l.setForeground(color);
        }
        row.add(l1); row.add(l2); row.add(l3);
        return row;
    }

    private static JPanel noteRow(String text) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setAlignmentX(LEFT_ALIGNMENT);
        JLabel l = new JLabel(text);
        l.setFont(FontManager.getRunescapeSmallFont());
        l.setForeground(MUTED);
        p.add(l);
        return p;
    }
}
