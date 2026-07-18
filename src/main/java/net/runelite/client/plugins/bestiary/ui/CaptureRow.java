package net.runelite.client.plugins.bestiary.ui;

import net.runelite.client.plugins.bestiary.model.BestiaryCollection;
import net.runelite.client.plugins.bestiary.model.CapturedCreature;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.Color;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Compact two-row row representing a single capture in Individual view mode.
 * Top: NPC name (left) | rarity (right)
 * Bot: combat level (left) | Q + region + date (right)
 */
public class CaptureRow extends JPanel {

    private static final int   ROW_HEIGHT = 50;
    private static final Color ROW_BG     = ColorScheme.DARKER_GRAY_COLOR;
    private static final Color ROW_HOVER  = new Color(55, 55, 55);
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM HH:mm").withZone(ZoneId.systemDefault());

    public CaptureRow(CapturedCreature capture, BestiaryCollection collection) {
        setLayout(new GridLayout(2, 1, 0, 3));
        setBackground(ROW_BG);
        setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 4, 0, 0, capture.rarity.displayColor),
                new EmptyBorder(5, 8, 5, 8)));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, ROW_HEIGHT));
        setPreferredSize(new Dimension(200, ROW_HEIGHT));

        // Top row: NPC name left | rarity right
        JPanel topRow = new JPanel(new BorderLayout(4, 0));
        topRow.setOpaque(false);

        JLabel nameLabel = new JLabel(capture.npcName);
        nameLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        nameLabel.setForeground(Color.WHITE);

        JLabel rarityLabel = new JLabel("● " + capture.rarity.label);
        rarityLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        rarityLabel.setForeground(capture.rarity.displayColor);

        topRow.add(nameLabel,   BorderLayout.CENTER);
        topRow.add(rarityLabel, BorderLayout.EAST);

        // Bottom row: combat level left | Q + region + date right
        JPanel botRow = new JPanel(new BorderLayout(4, 0));
        botRow.setOpaque(false);

        String levelText = capture.npcCombatLevel > 0 ? "Lvl " + capture.npcCombatLevel : "Non-cb";
        JLabel levelLabel = new JLabel(levelText);
        levelLabel.setFont(FontManager.getRunescapeSmallFont());
        levelLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        int q      = capture.quality.overallRating();
        String reg = shorten(capture.regionName, 12);
        String dt  = DATE_FMT.format(capture.captureTime);

        // Quality in gold, location + date in same muted tone as level label
        JLabel qualLabel = new JLabel("Q:" + q);
        qualLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        qualLabel.setForeground(capture.rarity.displayColor);

        JLabel locDateLabel = new JLabel("  " + reg + "  " + dt);
        locDateLabel.setFont(FontManager.getRunescapeSmallFont());
        locDateLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        JPanel statsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        statsPanel.setOpaque(false);
        statsPanel.add(qualLabel);
        statsPanel.add(locDateLabel);

        botRow.add(levelLabel,  BorderLayout.WEST);
        botRow.add(statsPanel,  BorderLayout.EAST);

        add(topRow);
        add(botRow);

        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                List<CapturedCreature> group = collection.creatures.stream()
                        .filter(c -> c.npcName.equals(capture.npcName) && c.rarity == capture.rarity)
                        .collect(Collectors.toList());
                new CreatureDetailDialog(
                        SwingUtilities.getWindowAncestor(CaptureRow.this),
                        group, collection).setVisible(true);
            }

            @Override public void mouseEntered(java.awt.event.MouseEvent e) { setBackground(ROW_HOVER); repaint(); }
            @Override public void mouseExited(java.awt.event.MouseEvent e)  { setBackground(ROW_BG);    repaint(); }
        });
    }

    private static String shorten(String s, int max) {
        if (s == null || s.isEmpty()) return "?";
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }
}
