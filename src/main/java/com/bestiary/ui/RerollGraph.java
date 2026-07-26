package com.bestiary.ui;

import com.bestiary.model.CapturedCreature;
import com.bestiary.model.CreatureQuality;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * A customizable line chart of a card's stat values across its reroll timeline
 * (Original → Roll 1 → … → Current). Each stat is a toggleable series; the Y axis auto-scales
 * to the visible series so Power Level (which can exceed 99) fits alongside the 1–99 stats.
 */
public class RerollGraph extends JPanel {

    private static final String[] NAMES  = {"ATK", "STR", "DEF", "MAG", "RNG", "AGI", "PRAY", "PWR"};
    private static final Color[]  COLORS = {
            new Color(210, 90, 90),  new Color(230, 150, 60), new Color(90, 140, 255),
            new Color(175, 105, 225), new Color(90, 200, 90),  new Color(80, 205, 205),
            new Color(235, 205, 95),  new Color(235, 235, 235)
    };

    private final String[] labels;      // point labels
    private final int[][] values;       // [series 0..7][point]; -1 = missing
    private final boolean[] available;  // whether a series has data at every point
    private final boolean[] visible;

    public RerollGraph(CapturedCreature c) {
        setLayout(new BorderLayout(0, 4));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(new EmptyBorder(10, 12, 10, 12));

        int n = c.rerollCount() + 1;               // history entries + current
        labels = new String[n];
        values = new int[8][n];
        available = new boolean[8];
        visible = new boolean[8];

        List<CapturedCreature.RerollState> h = c.rerollHistory;
        for (int i = 0; i < n; i++) {
            boolean isCurrent = i == h.size();
            CreatureQuality q = isCurrent ? c.quality : h.get(i).quality;
            int prayer = isCurrent ? c.prayer : h.get(i).prayer;
            int power  = isCurrent ? c.powerLevel() : h.get(i).powerLevel;
            labels[i] = isCurrent ? "Now" : (i == 0 ? "Orig" : "R" + i);
            int[] combat = q != null
                    ? new int[]{q.attack, q.strength, q.defence, q.magic, q.ranged, q.agility}
                    : new int[]{-1, -1, -1, -1, -1, -1};
            for (int s = 0; s < 6; s++) values[s][i] = combat[s];
            values[6][i] = prayer;
            values[7][i] = power;
        }
        for (int s = 0; s < 8; s++) {
            boolean ok = true;
            for (int i = 0; i < n; i++) if (values[s][i] < 0) ok = false;
            available[s] = ok;
            visible[s]   = ok && s != 7;   // default: all stats + prayer on, Power off
        }

        Chart chart = new Chart();
        chart.setPreferredSize(new Dimension(360, 190));
        add(chart, BorderLayout.CENTER);
        add(buildToggles(chart), BorderLayout.SOUTH);
    }

    private JPanel buildToggles(Chart chart) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        row.setOpaque(false);
        for (int s = 0; s < 8; s++) {
            final int idx = s;
            JToggleButton b = new JToggleButton(NAMES[s], visible[s]);
            b.setFont(FontManager.getRunescapeSmallFont());
            b.setForeground(COLORS[s]);
            b.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            b.setOpaque(true);
            b.setFocusPainted(false);
            b.setBorderPainted(false);
            b.setMargin(new Insets(1, 5, 1, 5));
            b.setEnabled(available[s]);
            if (!available[s]) b.setToolTipText("No data for this stat on older rerolls");
            b.addActionListener(e -> { visible[idx] = b.isSelected(); chart.repaint(); });
            row.add(b);
        }
        return row;
    }

    private final class Chart extends JPanel {
        Chart() { setBackground(ColorScheme.DARKER_GRAY_COLOR); }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();
            int padL = 36, padR = 10, padT = 10, padB = 20;
            int plotW = w - padL - padR, plotH = h - padT - padB;
            int n = labels.length;
            g.setFont(FontManager.getRunescapeSmallFont());

            if (n < 2) {
                message(g, w, h, "Reroll this card to chart how its stats change.");
                g.dispose();
                return;
            }

            // Y range = min/max of the VISIBLE series (padded), so a narrow band (or a PWR-only
            // view) fills the plot instead of being flattened against a 0-based axis.
            int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
            boolean any = false;
            for (int s = 0; s < 8; s++) if (visible[s] && available[s]) {
                any = true;
                for (int v : values[s]) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
            }
            if (!any) {
                message(g, w, h, "Toggle a stat below to plot it.");
                g.dispose();
                return;
            }
            if (hi == lo) { lo -= 2; hi += 2; }
            int pad = Math.max(1, (int) Math.ceil((hi - lo) * 0.15));
            int yLo = Math.max(0, lo - pad), yHi = hi + pad;
            double span = Math.max(1, yHi - yLo);

            // Gridlines + Y labels
            for (int gl = 0; gl <= 4; gl++) {
                int y = padT + plotH - (plotH * gl / 4);
                int val = (int) Math.round(yLo + span * gl / 4);
                g.setColor(new Color(58, 58, 58));
                g.drawLine(padL, y, padL + plotW, y);
                g.setColor(new Color(120, 120, 120));
                g.drawString(String.valueOf(val), 4, y + 4);
            }
            // X labels
            g.setColor(new Color(150, 150, 150));
            for (int i = 0; i < n; i++) {
                int x = padL + (n == 1 ? plotW / 2 : plotW * i / (n - 1));
                String lab = labels[i];
                g.drawString(lab, x - g.getFontMetrics().stringWidth(lab) / 2, h - 6);
            }

            // Series polylines
            for (int s = 0; s < 8; s++) {
                if (!visible[s] || !available[s]) continue;
                g.setColor(COLORS[s]);
                g.setStroke(new BasicStroke(2f));
                int prevX = 0, prevY = 0;
                for (int i = 0; i < n; i++) {
                    int x = padL + (n == 1 ? plotW / 2 : plotW * i / (n - 1));
                    int y = padT + plotH - (int) ((values[s][i] - yLo) / span * plotH);
                    if (i > 0) g.drawLine(prevX, prevY, x, y);
                    g.fillOval(x - 3, y - 3, 6, 6);
                    prevX = x; prevY = y;
                }
            }
            g.dispose();
        }

        private void message(Graphics2D g, int w, int h, String msg) {
            g.setColor(new Color(150, 150, 150));
            g.drawString(msg, (w - g.getFontMetrics().stringWidth(msg)) / 2, h / 2);
        }
    }
}
