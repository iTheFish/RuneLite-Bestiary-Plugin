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
 * A compact 38px row representing a single capture, used in Individual view mode.
 * Clicking opens the full capture history dialog for that NPC+rarity.
 */
public class CaptureRow extends JPanel {

    private static final int    ROW_HEIGHT = 38;
    private static final Color  ROW_BG     = ColorScheme.DARKER_GRAY_COLOR;
    private static final Color  ROW_HOVER  = new Color(55, 55, 55);
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM HH:mm").withZone(ZoneId.systemDefault());

    public CaptureRow(CapturedCreature capture, BestiaryCollection collection) {
        setLayout(new BorderLayout(6, 0));
        setBackground(ROW_BG);
        setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 4, 0, 0, capture.rarity.displayColor),
                new EmptyBorder(4, 8, 4, 8)));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, ROW_HEIGHT));
        setPreferredSize(new Dimension(200, ROW_HEIGHT));

        // Left: NPC name (bold) + combat level
        JPanel left = new JPanel(new GridLayout(2, 1, 0, 1));
        left.setOpaque(false);

        JLabel nameLabel = new JLabel(capture.npcName);
        nameLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        nameLabel.setForeground(Color.WHITE);

        String levelText = capture.npcCombatLevel > 0 ? "Lvl " + capture.npcCombatLevel : "Non-combat";
        JLabel levelLabel = new JLabel(levelText);
        levelLabel.setFont(FontManager.getRunescapeSmallFont());
        levelLabel.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);

        left.add(nameLabel);
        left.add(levelLabel);

        // Right: rarity dot + quality | region | date
        JPanel right = new JPanel(new GridLayout(2, 1, 0, 1));
        right.setOpaque(false);

        JLabel rarityLabel = new JLabel("● " + capture.rarity.label, SwingConstants.RIGHT);
        rarityLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        rarityLabel.setForeground(capture.rarity.displayColor);

        int q = capture.quality.overallRating();
        String info = "Q:" + q + "  " + shorten(capture.regionName) + "  " + DATE_FMT.format(capture.captureTime);
        JLabel infoLabel = new JLabel(info, SwingConstants.RIGHT);
        infoLabel.setFont(FontManager.getRunescapeSmallFont());
        infoLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        right.add(rarityLabel);
        right.add(infoLabel);

        add(left,  BorderLayout.CENTER);
        add(right, BorderLayout.EAST);

        // Click: open detail dialog for the full NPC+rarity group
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                List<CapturedCreature> group = collection.creatures.stream()
                        .filter(c -> c.npcId == capture.npcId && c.rarity == capture.rarity)
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

    /** Truncates a region name to fit the compact row. */
    private static String shorten(String name) {
        if (name == null || name.isEmpty()) return "Unknown";
        return name.length() > 14 ? name.substring(0, 13) + "…" : name;
    }
}
