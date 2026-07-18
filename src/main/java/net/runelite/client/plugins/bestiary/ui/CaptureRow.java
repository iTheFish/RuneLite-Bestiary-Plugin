package net.runelite.client.plugins.bestiary.ui;

import net.runelite.client.plugins.bestiary.model.BestiaryCollection;
import net.runelite.client.plugins.bestiary.model.CapturedCreature;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Compact row representing a single capture in Individual view mode.
 */
public class CaptureRow extends JPanel {

    private static final int   ROW_HEIGHT = 46;
    private static final Color ROW_BG     = ColorScheme.DARKER_GRAY_COLOR;
    private static final Color ROW_HOVER  = new Color(55, 55, 55);
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM HH:mm").withZone(ZoneId.systemDefault());

    public CaptureRow(CapturedCreature capture, BestiaryCollection collection) {
        setLayout(new BorderLayout(8, 0));
        setBackground(ROW_BG);
        setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 4, 0, 0, capture.rarity.displayColor),
                new EmptyBorder(6, 8, 6, 8)));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, ROW_HEIGHT));
        setPreferredSize(new Dimension(200, ROW_HEIGHT));

        // Left: NPC name (bold, clipped) + combat level
        JPanel left = new JPanel(new GridLayout(2, 1, 0, 2));
        left.setOpaque(false);

        String displayName = capture.npcName.length() > 18
                ? capture.npcName.substring(0, 17) + "…"
                : capture.npcName;
        JLabel nameLabel = new JLabel(displayName);
        nameLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        nameLabel.setForeground(Color.WHITE);

        String levelText = capture.npcCombatLevel > 0 ? "Lvl " + capture.npcCombatLevel : "Non-cb";
        JLabel levelLabel = new JLabel(levelText);
        levelLabel.setFont(FontManager.getRunescapeSmallFont());
        levelLabel.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);

        left.add(nameLabel);
        left.add(levelLabel);

        // Right: rarity label + compact stats line
        JPanel right = new JPanel(new GridLayout(2, 1, 0, 2));
        right.setOpaque(false);

        JLabel rarityLabel = new JLabel("● " + capture.rarity.label, SwingConstants.RIGHT);
        rarityLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        rarityLabel.setForeground(capture.rarity.displayColor);

        int q = capture.quality.overallRating();
        String region = shorten(capture.regionName, 10);
        String date   = DATE_FMT.format(capture.captureTime);
        JLabel infoLabel = new JLabel("Q:" + q + " · " + region + " · " + date, SwingConstants.RIGHT);
        infoLabel.setFont(FontManager.getRunescapeSmallFont());
        infoLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        right.add(rarityLabel);
        right.add(infoLabel);

        add(left,  BorderLayout.CENTER);
        add(right, BorderLayout.EAST);

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

            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                setBackground(ROW_HOVER);
                repaint();
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                setBackground(ROW_BG);
                repaint();
            }
        });
    }

    private static String shorten(String name, int max) {
        if (name == null || name.isEmpty()) return "?";
        return name.length() > max ? name.substring(0, max - 1) + "…" : name;
    }
}
