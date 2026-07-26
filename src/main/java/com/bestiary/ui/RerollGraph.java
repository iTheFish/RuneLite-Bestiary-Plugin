package com.bestiary.ui;

import com.bestiary.model.CapturedCreature;
import com.bestiary.model.CreatureQuality;
import com.bestiary.util.OddsCalculator;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Per-card graphs, switchable via a dropdown:
 *  - <b>Reroll timeline</b> — a line chart of each stat across the reroll timeline (Original → Now);
 *    every stat is a toggleable series, the Y axis auto-zooms to the visible values, and hovering a
 *    point shows that roll's per-stat deltas.
 *  - <b>Percentile</b> — how this card's current stats rank within the possible roll band for its
 *    rarity (0% = the weakest possible roll, 100% = the best).
 */
public class RerollGraph extends JPanel {

    private enum Mode { TIMELINE, PERCENTILE }

    private static final String[] NAMES  = {"ATK", "STR", "DEF", "MAG", "RNG", "AGI", "PRAY", "PWR"};
    private static final Color[]  COLORS = {
            new Color(210, 90, 90),  new Color(230, 150, 60), new Color(90, 140, 255),
            new Color(175, 105, 225), new Color(90, 200, 90),  new Color(80, 205, 205),
            new Color(235, 205, 95),  new Color(235, 235, 235)
    };

    // Timeline data
    private final String[] labels;      // point labels
    private final int[][] values;       // [series 0..7][point]; -1 = missing
    private final boolean[] available;
    private final boolean[] visible;

    // Percentile data (current card vs its rarity's roll band)
    private final String[] pctNames;
    private final int[] pctValue, pctLo, pctHi;

    private Mode mode;
    private final Chart chart;
    private final JPanel toggles;

    public RerollGraph(CapturedCreature c) {
        setLayout(new BorderLayout(0, 4));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(new EmptyBorder(8, 12, 10, 12));

        // ---- timeline series ----
        int n = c.rerollCount() + 1;
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
            visible[s]   = ok && s != 7;   // default: stats + prayer on, Power off
        }

        // ---- percentile bands (current card vs rarity range) ----
        OddsCalculator.Result r = OddsCalculator.compute(c);
        int m = r.stats.size();
        pctNames = new String[m];
        pctValue = new int[m];
        pctLo = new int[m];
        pctHi = new int[m];
        for (int i = 0; i < m; i++) {
            OddsCalculator.StatOdds s = r.stats.get(i);
            pctNames[i] = s.name;
            pctValue[i] = s.value;
            pctLo[i] = s.lo;
            pctHi[i] = s.hi;
        }

        boolean rerolled = c.rerollCount() > 0;
        mode = Mode.PERCENTILE;   // percentile is the more broadly useful default

        chart = new Chart();
        toggles = buildToggles();
        add(buildSwitcher(rerolled), BorderLayout.NORTH);
        add(chart, BorderLayout.CENTER);
        add(toggles, BorderLayout.SOUTH);
        toggles.setVisible(false);
    }

    private JComponent buildSwitcher(boolean rerolled) {
        JComboBox<String> combo = new JComboBox<>(rerolled
                ? new String[]{"Percentile (this rarity)", "Reroll timeline"}
                : new String[]{"Percentile (this rarity)"});
        combo.setFont(FontManager.getRunescapeSmallFont());
        combo.addActionListener(e -> {
            mode = "Reroll timeline".equals(combo.getSelectedItem()) ? Mode.TIMELINE : Mode.PERCENTILE;
            toggles.setVisible(mode == Mode.TIMELINE);
            chart.repaint();
            revalidate();
        });
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        JLabel l = new JLabel("Graph:  ");
        l.setFont(FontManager.getRunescapeSmallFont());
        l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        row.add(l, BorderLayout.WEST);
        row.add(combo, BorderLayout.CENTER);
        return row;
    }

    private JPanel buildToggles() {
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
        Chart() {
            setBackground(ColorScheme.DARKER_GRAY_COLOR);
            addMouseMotionListener(new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) { setToolTipText(pointTip(e.getX())); }
            });
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setFont(FontManager.getRunescapeSmallFont());
            if (mode == Mode.PERCENTILE) drawPercentile(g);
            else drawTimeline(g);
            g.dispose();
        }

        private void drawTimeline(Graphics2D g) {
            int w = getWidth(), h = getHeight();
            int padL = 36, padR = 10, padT = 10, padB = 20;
            int plotW = w - padL - padR, plotH = h - padT - padB;
            int n = labels.length;

            if (n < 2) { message(g, w, h, "Reroll this card to chart how its stats change."); return; }

            int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
            boolean any = false;
            for (int s = 0; s < 8; s++) if (visible[s] && available[s]) {
                any = true;
                for (int v : values[s]) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
            }
            if (!any) { message(g, w, h, "Toggle a stat below to plot it."); return; }
            if (hi == lo) { lo -= 2; hi += 2; }
            int pad = Math.max(1, (int) Math.ceil((hi - lo) * 0.15));
            int yLo = Math.max(0, lo - pad), yHi = hi + pad;
            double span = Math.max(1, yHi - yLo);

            for (int gl = 0; gl <= 4; gl++) {
                int y = padT + plotH - (plotH * gl / 4);
                int val = (int) Math.round(yLo + span * gl / 4);
                g.setColor(new Color(58, 58, 58));
                g.drawLine(padL, y, padL + plotW, y);
                g.setColor(new Color(120, 120, 120));
                g.drawString(String.valueOf(val), 4, y + 4);
            }
            g.setColor(new Color(150, 150, 150));
            for (int i = 0; i < n; i++) {
                int x = padL + plotW * i / (n - 1);
                g.drawString(labels[i], x - g.getFontMetrics().stringWidth(labels[i]) / 2, h - 6);
            }
            for (int s = 0; s < 8; s++) {
                if (!visible[s] || !available[s]) continue;
                g.setColor(COLORS[s]);
                g.setStroke(new BasicStroke(2f));
                int prevX = 0, prevY = 0;
                for (int i = 0; i < n; i++) {
                    int x = padL + plotW * i / (n - 1);
                    int y = padT + plotH - (int) ((values[s][i] - yLo) / span * plotH);
                    if (i > 0) g.drawLine(prevX, prevY, x, y);
                    g.fillOval(x - 3, y - 3, 6, 6);
                    prevX = x; prevY = y;
                }
            }
        }

        private void drawPercentile(Graphics2D g) {
            int w = getWidth(), h = getHeight();
            int n = pctNames.length;
            int padT = 10, padB = 10, left = 64, right = 118;
            int barX = left, barW = Math.max(20, w - left - right);
            int rows = n + 1;   // Overall + per-stat
            int rowH = Math.max(16, (h - padT - padB) / rows);

            // Overall = average of the per-stat percentiles ("how maxed out" — 50% ≈ an average roll).
            double overall = 0;
            for (int i = 0; i < n; i++) overall += pOf(i);
            overall = n > 0 ? overall / n : 0;
            drawPctRow(g, padT + rowH / 2, "Overall", overall,
                    Math.round(overall * 100) + "%  " + tier(overall), barX, barW, true);
            g.setColor(new Color(72, 72, 72));
            g.drawLine(4, padT + rowH, w - 6, padT + rowH);

            for (int i = 0; i < n; i++) {
                int cy = padT + (i + 1) * rowH + rowH / 2;
                double p = pOf(i);
                drawPctRow(g, cy, pctNames[i], p,
                        pctValue[i] + "  (" + Math.round(p * 100) + "%)", barX, barW, false);
            }
        }

        private double pOf(int i) {
            double p = pctHi[i] > pctLo[i]
                    ? (pctValue[i] - pctLo[i]) / (double) (pctHi[i] - pctLo[i])
                    : (pctValue[i] >= pctHi[i] ? 1.0 : 0.5);
            return Math.max(0, Math.min(1, p));
        }

        private void drawPctRow(Graphics2D g, int cy, String label, double p, String rightText,
                                int barX, int barW, boolean bold) {
            g.setFont(bold ? FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD)
                           : FontManager.getRunescapeSmallFont());
            g.setColor(bold ? new Color(255, 200, 90) : new Color(200, 200, 200));
            g.drawString(label, 4, cy + 4);
            int by = cy - 5;
            g.setColor(new Color(45, 45, 45));
            g.fillRoundRect(barX, by, barW, 10, 4, 4);
            g.setColor(pctColor(p));
            g.fillRoundRect(barX, by, Math.max(2, (int) (barW * p)), 10, 4, 4);
            g.setColor(bold ? Color.WHITE : new Color(200, 200, 200));
            g.drawString(rightText, barX + barW + 6, cy + 4);
        }

        private String tier(double p) {
            if (p >= 0.85) return "near-max";
            if (p >= 0.65) return "strong";
            if (p >= 0.40) return "average";
            if (p >= 0.20) return "low";
            return "poor";
        }

        private void message(Graphics2D g, int w, int h, String msg) {
            g.setColor(new Color(150, 150, 150));
            g.drawString(msg, (w - g.getFontMetrics().stringWidth(msg)) / 2, h / 2);
        }
    }

    /** Timeline hover tooltip: the nearest roll's per-stat values + deltas from the previous roll. */
    private String pointTip(int mouseX) {
        if (mode != Mode.TIMELINE) return null;
        int n = labels.length;
        if (n < 2) return null;
        int padL = 36, padR = 10;
        int plotW = chart.getWidth() - padL - padR;
        if (plotW <= 0) return null;
        int best = -1;
        double bestDx = 14;
        for (int i = 0; i < n; i++) {
            int x = padL + plotW * i / (n - 1);
            if (Math.abs(mouseX - x) < bestDx) { best = i; bestDx = Math.abs(mouseX - x); }
        }
        if (best < 0) return null;

        StringBuilder sb = new StringBuilder("<html><b>").append(labelLong(best)).append("</b>");
        for (int s = 0; s < 8; s++) {
            if (!visible[s] || !available[s]) continue;
            String hex = String.format("#%06x", COLORS[s].getRGB() & 0xFFFFFF);
            sb.append("<br><font color='").append(hex).append("'>").append(NAMES[s]).append("</font> ")
              .append(values[s][best]);
            if (best > 0) {
                int d = values[s][best] - values[s][best - 1];
                String dcol = d > 0 ? "#78dc78" : d < 0 ? "#e07070" : "#909090";
                sb.append(" <font color='").append(dcol).append("'>")
                  .append(d > 0 ? "+" + d : String.valueOf(d)).append("</font>");
            }
        }
        return sb.append("</html>").toString();
    }

    private String labelLong(int i) {
        if (i == 0) return "Original roll";
        if (i == labels.length - 1) return "Current";
        return "Reroll " + i;
    }

    private static Color pctColor(double p) {
        // red (weak roll) → amber → green (top roll)
        int red   = (int) (235 * (1 - p) + 90 * p);
        int green = (int) (90 * (1 - p) + 210 * p);
        return new Color(Math.min(255, red), Math.min(255, green), 70);
    }
}
