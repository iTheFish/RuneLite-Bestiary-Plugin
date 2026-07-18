package net.runelite.client.plugins.bestiary.ui;

import net.runelite.client.plugins.bestiary.model.CreatureRarity;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;

public class InfoTab extends JPanel {

    private static final Color ORANGE = new Color(255, 165, 0);
    private static final Color GREEN  = new Color(80, 200, 80);

    public InfoTab() {
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ColorScheme.DARK_GRAY_COLOR);
        content.setBorder(new EmptyBorder(6, 6, 6, 6));

        content.add(section("Overview",
                "Bestiary tracks every creature you kill and gives you a chance "
              + "to capture them. Captured creatures are logged in your Collection "
              + "with quality scores, locations, and rarity tiers."));

        content.add(Box.createVerticalStrut(8));

        content.add(section("Capture System",
                "On every kill a random roll is made against your capture rate. "
              + "Base rate is set in the plugin config (default 10%, up to 100%). "
              + "Each capture gets a quality score (0-100) based on the creature's "
              + "combat stats. Higher quality = rarer roll."));

        content.add(Box.createVerticalStrut(8));

        content.add(raritySection());

        content.add(Box.createVerticalStrut(8));

        content.add(section("XP & Levels",
                "You earn XP on every kill and bonus XP on successful captures. "
              + "Capture Level ranges from 1-100 and is shown in the Progress tab. "
              + "Rarer captures award more XP via a rarity multiplier. "
              + "Capture XP bonus can be toggled off in config."));

        content.add(Box.createVerticalStrut(8));

        content.add(section("Collection Tab",
                "Cards are grouped by creature AND rarity - Goblin Uncommon and "
              + "Goblin Rare are separate entries. Click any card to open a full "
              + "capture history: quality score, location, date, and which kill "
              + "triggered the capture."));

        content.add(Box.createVerticalStrut(8));

        content.add(section("Progress Tab",
                "Shows your Capture Level, XP progress bar, and all achievements. "
              + "Achievements unlock automatically when you meet their criteria "
              + "(species count, kill count, level milestones, rarity captures)."));

        content.add(Box.createVerticalStrut(8));

        content.add(section("Overlay & Animation",
                "A capture notification appears at the top-centre of the game "
              + "window on each catch. Enable 'Show Capture Animation' in config "
              + "for a pokeball-style shake animation on every kill attempt. "
              + "'Animate Misses' shows the animation even on failed captures."));

        content.add(Box.createVerticalStrut(8));

        content.add(tipRow("Tip: Set Base Capture Rate to 100% in config to test the plugin quickly."));

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        add(scroll, BorderLayout.CENTER);
    }

    private JPanel section(String title, String body) {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setOpaque(false);
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        panel.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 3, 0, 0, ORANGE),
                new EmptyBorder(3, 8, 3, 0)));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        titleLabel.setForeground(ORANGE);

        JTextArea bodyText = new JTextArea(body);
        bodyText.setFont(FontManager.getRunescapeSmallFont());
        bodyText.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        bodyText.setBackground(ColorScheme.DARK_GRAY_COLOR);
        bodyText.setEditable(false);
        bodyText.setLineWrap(true);
        bodyText.setWrapStyleWord(true);
        bodyText.setBorder(null);
        bodyText.setFocusable(false);

        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(bodyText,   BorderLayout.CENTER);
        return panel;
    }

    private JPanel raritySection() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setOpaque(false);
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        panel.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 3, 0, 0, ORANGE),
                new EmptyBorder(3, 8, 3, 0)));

        JLabel title = new JLabel("Rarity Tiers (lowest to highest)");
        title.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        title.setForeground(ORANGE);

        JPanel rows = new JPanel();
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.setOpaque(false);

        for (CreatureRarity r : CreatureRarity.values()) {
            int pct = (int) Math.round(r.probability * 100);
            String pctStr = pct > 0 ? pct + "%" : "<1%";
            JLabel row = new JLabel(
                    "● " + r.label + "  —  ~" + pctStr + " of captures  ("
                    + (int) r.xpMultiplier + "x XP)");
            row.setFont(FontManager.getRunescapeSmallFont());
            row.setForeground(r.displayColor);
            rows.add(row);
        }

        panel.add(title, BorderLayout.NORTH);
        panel.add(rows,  BorderLayout.CENTER);
        return panel;
    }

    private JPanel tipRow(String text) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        panel.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 3, 0, 0, GREEN),
                new EmptyBorder(3, 8, 3, 0)));

        JLabel label = new JLabel("<html>" + text + "</html>");
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setForeground(GREEN);
        panel.add(label, BorderLayout.CENTER);
        return panel;
    }
}
