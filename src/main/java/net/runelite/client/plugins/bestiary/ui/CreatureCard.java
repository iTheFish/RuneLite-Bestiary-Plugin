package net.runelite.client.plugins.bestiary.ui;

import net.runelite.client.plugins.bestiary.model.BestiaryCollection;
import net.runelite.client.plugins.bestiary.model.CapturedCreature;
import net.runelite.client.plugins.bestiary.model.CreatureRarity;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.util.List;

/**
 * A compact card representing one NPC+rarity combination in the collection.
 * All captures passed in share the same npcId and rarity.
 * Clicking opens {@link CreatureDetailDialog} with the capture history.
 */
public class CreatureCard extends JPanel {

    private static final int   CARD_HEIGHT = 58;
    private static final Color CARD_BG     = ColorScheme.DARKER_GRAY_COLOR;
    private static final Color CARD_HOVER  = new Color(55, 55, 55);

    /**
     * @param captures All captures of this NPC with this specific rarity (homogeneous list).
     * @param collection Full collection for kill-count lookup.
     */
    public CreatureCard(List<CapturedCreature> captures, BestiaryCollection collection) {
        CapturedCreature sample = captures.get(0);
        CreatureRarity rarity   = sample.rarity;
        String npcName          = sample.npcName;
        int npcId               = sample.npcId;
        int combatLevel         = sample.npcCombatLevel;

        setLayout(new BorderLayout(8, 0));
        setBackground(CARD_BG);
        int t = borderThick(rarity);
        setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(t, 4, t, t, rarity.displayColor),
                new EmptyBorder(6 - t, 8, 6 - t, 8 - t)));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, CARD_HEIGHT));
        setPreferredSize(new Dimension(200, CARD_HEIGHT));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Left column: name + combat level
        JPanel leftCol = new JPanel(new GridLayout(2, 1, 0, 2));
        leftCol.setOpaque(false);

        JLabel nameLabel = new JLabel(npcName);
        nameLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        nameLabel.setForeground(Color.WHITE);

        String levelText = combatLevel > 0 ? "Combat lvl " + combatLevel : "Non-combat";
        JLabel levelLabel = new JLabel(levelText);
        levelLabel.setFont(FontManager.getRunescapeSmallFont());
        levelLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        leftCol.add(nameLabel);
        leftCol.add(levelLabel);

        // Right column: rarity badge + count/quality
        JPanel rightCol = new JPanel(new GridLayout(2, 1, 0, 2));
        rightCol.setOpaque(false);

        JLabel rarityLabel = new JLabel("\u25cf " + rarity.label, SwingConstants.RIGHT);
        rarityLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        rarityLabel.setForeground(rarity.displayColor);

        int avgQuality = (int) captures.stream()
                .mapToInt(c -> c.quality.overallRating())
                .average()
                .orElse(0);
        int kills = collection.getKillCount(npcName);
        JLabel statsLabel = new JLabel(captures.size() + " caught  \u00b7  avg " + avgQuality, SwingConstants.RIGHT);
        statsLabel.setFont(FontManager.getRunescapeSmallFont());
        statsLabel.setForeground(rarity.displayColor);

        int aStr = (int) captures.stream().mapToInt(c -> c.quality.strength).average().orElse(0);
        int aSpd = (int) captures.stream().mapToInt(c -> c.quality.speed).average().orElse(0);
        int aEnd = (int) captures.stream().mapToInt(c -> c.quality.endurance).average().orElse(0);
        int aInt = (int) captures.stream().mapToInt(c -> c.quality.intelligence).average().orElse(0);
        int aStl = (int) captures.stream().mapToInt(c -> c.quality.stealth).average().orElse(0);
        int aVit = (int) captures.stream().mapToInt(c -> c.quality.vitality).average().orElse(0);
        statsLabel.setToolTipText(String.format(
                "<html>%d captures of this rarity  |  %d total kills<br>" +
                "Avg stats:  STR:%d  SPD:%d  END:%d  INT:%d  STL:%d  VIT:%d</html>",
                captures.size(), kills, aStr, aSpd, aEnd, aInt, aStl, aVit));

        rightCol.add(rarityLabel);
        rightCol.add(statsLabel);

        add(leftCol,  BorderLayout.CENTER);
        add(rightCol, BorderLayout.EAST);


        // Click -> detail dialog; hover highlight
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                new CreatureDetailDialog(
                        SwingUtilities.getWindowAncestor(CreatureCard.this),
                        captures, collection);
            }

            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                setBackground(CARD_HOVER);
                repaint();
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                setBackground(CARD_BG);
                repaint();
            }
        });
    }

    /** EPIC and above get a 2px frame; Common–Rare get 1px. */
    private static int borderThick(CreatureRarity r) {
        return r.ordinal() >= CreatureRarity.EPIC.ordinal() ? 2 : 1;
    }
}
