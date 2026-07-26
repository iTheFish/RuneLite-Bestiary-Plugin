package com.bestiary.ui;

import com.bestiary.util.OddsCalculator;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Reusable stats table + Power Level breakdown (with average-roll comparison) for a
 * capture, without the roll-chain odds or explainer text. Used by the reroll-confirm
 * screen to give context before spending credits.
 */
final class OddsBreakdownPanel {

    private OddsBreakdownPanel() {}

    static JPanel build(OddsCalculator.Result r) {
        float base = FontManager.getRunescapeSmallFont().getSize2D();
        Font body     = FontManager.getRunescapeSmallFont().deriveFont(base + 1f);
        Font bodyBold = body.deriveFont(Font.BOLD);

        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setOpaque(false);

        // Stats
        root.add(header(r.shiny
                ? "Stats — shiny roll bands (above " + r.rarity.label + ")"
                : "Stats — " + r.rarity.label + " roll bands", bodyBold));
        root.add(statsTable(r, body, bodyBold));

        root.add(Box.createVerticalStrut(10));

        // Power Level: 7-stat average + HP at 1/6 weight (Prayer is the 7th stat)
        int sevenStats = r.statSum + r.prayer;
        int statAvg = Math.round(sevenStats / 7f);
        Color nearWhite = new Color(235, 235, 235);
        root.add(header("Power Level", bodyBold));
        root.add(value(kvRow("Stat average  (" + sevenStats + " ÷ 7)", body, ColorScheme.LIGHT_GRAY_COLOR, String.valueOf(statAvg)), nearWhite));
        root.add(value(kvRow("Hitpoints (factual)  ÷ 6", body, new Color(120, 200, 120), "+" + Math.round(r.hp / 6f)), nearWhite));
        root.add(value(kvRow("= Power Level  (" + sevenStats + " ÷ 7) + (" + r.hp + " ÷ 6)", body, Color.WHITE,
                String.valueOf(r.powerLevel)), new Color(120, 200, 120)));

        root.add(Box.createVerticalStrut(4));
        root.add(separator());
        root.add(Box.createVerticalStrut(4));

        root.add(value(kvRow("Average " + r.rarity.label + " roll", body, ColorScheme.LIGHT_GRAY_COLOR,
                String.valueOf(r.avgPowerLevel)), nearWhite));
        int delta = r.powerLevel - r.avgPowerLevel;
        String deltaStr = delta > 0 ? "+" + delta + " above avg" : delta < 0 ? delta + " below avg" : "bang on avg";
        Color deltaCol = delta > 0 ? new Color(120, 200, 120) : delta < 0 ? new Color(224, 112, 112) : new Color(176, 176, 176);
        root.add(value(kvRow("This card vs average", body, Color.WHITE, deltaStr), deltaCol));

        return root;
    }

    private static JLabel header(String text, Font bold) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setFont(bold);
        l.setForeground(new Color(255, 152, 31));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(new EmptyBorder(0, 0, 3, 0));
        return l;
    }

    private static JPanel statsTable(OddsCalculator.Result r, Font body, Font bodyBold) {
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
            t.add(rollTagCell(s.value, s.lo, s.hi, body), g);
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

    private static JLabel rollTagCell(int value, int lo, int hi, Font body) {
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

    private static JPanel separator() {
        JPanel s = new JPanel();
        s.setBackground(new Color(70, 70, 70));
        s.setAlignmentX(Component.LEFT_ALIGNMENT);
        s.setPreferredSize(new Dimension(10, 1));
        s.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        return s;
    }

    private static Box kvRow(String left, Font leftFont, Color leftColor, String right) {
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

    private static Box value(Box row, Color colour) {
        ((JLabel) row.getComponent(row.getComponentCount() - 1)).setForeground(colour);
        return row;
    }
}
