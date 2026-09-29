package com.bestiary.ui;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.*;

/**
 * Shared "gold on warm dark" tab style (first used by the Level 99 shop sub-tabs): selected = gold
 * text on a dark gold fill with a gold underline; unselected = light grey on the panel's darker grey.
 */
final class TabStyle {

    static final Color ACTIVE_BG = new Color(62, 50, 24);
    static final Color ACTIVE_FG = new Color(255, 210, 100);
    static final Color IDLE_BG   = ColorScheme.DARKER_GRAY_COLOR;
    static final Color IDLE_FG   = ColorScheme.LIGHT_GRAY_COLOR;

    private TabStyle() {}

    /**
     * Prepares a toggle button for {@link #style}. contentAreaFilled(false) stops the look-and-feel
     * painting its own dark "selected" fill over ours (which made the selected text unreadable).
     */
    static void prepare(AbstractButton b) {
        b.setFont(FontManager.getRunescapeSmallFont());
        b.setFocusPainted(false);
        b.setContentAreaFilled(false);
        b.setOpaque(true);
        b.setMargin(new Insets(2, 2, 2, 2));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    static void style(AbstractButton b, boolean active) {
        b.setBackground(active ? ACTIVE_BG : IDLE_BG);
        b.setForeground(active ? ACTIVE_FG : IDLE_FG);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 2, 0, active ? ACTIVE_FG : IDLE_BG),
                new EmptyBorder(3, 4, 1, 4)));
    }

    /** Applies the style to a JTabbedPane: equal-width tabs across the full width, no LAF chrome. */
    static void apply(JTabbedPane tabs) {
        tabs.setUI(new GoldTabbedPaneUI());
        tabs.setFont(FontManager.getRunescapeSmallFont());
    }

    private static final class GoldTabbedPaneUI extends BasicTabbedPaneUI {
        private static final int GAP = 3;   // horizontal gap between neighbouring tabs

        @Override protected void installDefaults() {
            super.installDefaults();
            tabInsets             = new Insets(5, 2, 4, 2);
            selectedTabPadInsets  = new Insets(0, 0, 0, 0);
            tabAreaInsets         = new Insets(0, 0, 4, 0);
            contentBorderInsets   = new Insets(0, 0, 0, 0);
        }

        /** Split the full width evenly (last tab absorbs the rounding) so the bar looks like a grid. */
        @Override protected int calculateTabWidth(int placement, int tabIndex, FontMetrics metrics) {
            int n = tabPane.getTabCount();
            int avail = tabPane.getWidth() - tabAreaInsets.left - tabAreaInsets.right;
            if (n == 0 || avail <= 0) return super.calculateTabWidth(placement, tabIndex, metrics);
            int each = avail / n;
            return tabIndex == n - 1 ? avail - each * (n - 1) : each;
        }

        @Override protected void paintTabBackground(Graphics g, int placement, int tabIndex,
                                                    int x, int y, int w, int h, boolean isSelected) {
            int gap = tabIndex == tabPane.getTabCount() - 1 ? 0 : GAP;
            g.setColor(isSelected ? ACTIVE_BG : IDLE_BG);
            g.fillRect(x, y, w - gap, h);
        }

        @Override protected void paintTabBorder(Graphics g, int placement, int tabIndex,
                                                int x, int y, int w, int h, boolean isSelected) {
            if (!isSelected) return;
            int gap = tabIndex == tabPane.getTabCount() - 1 ? 0 : GAP;
            g.setColor(ACTIVE_FG);
            g.fillRect(x, y + h - 2, w - gap, 2);
        }

        @Override protected void paintText(Graphics g, int placement, Font font, FontMetrics metrics,
                                           int tabIndex, String title, Rectangle textRect, boolean isSelected) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(font);
            g2.setColor(!tabPane.isEnabledAt(tabIndex) ? ColorScheme.MEDIUM_GRAY_COLOR
                    : isSelected ? ACTIVE_FG : IDLE_FG);
            g2.drawString(title, textRect.x, textRect.y + metrics.getAscent());
        }

        @Override protected void paintFocusIndicator(Graphics g, int placement, Rectangle[] rects,
                                                     int tabIndex, Rectangle iconRect, Rectangle textRect,
                                                     boolean isSelected) { }

        @Override protected void paintContentBorder(Graphics g, int placement, int selectedIndex) { }
    }
}
