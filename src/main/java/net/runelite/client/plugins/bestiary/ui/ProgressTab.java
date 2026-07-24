package net.runelite.client.plugins.bestiary.ui;

import net.runelite.client.plugins.bestiary.model.Achievement;
import net.runelite.client.plugins.bestiary.service.ProgressionService;
import net.runelite.client.plugins.bestiary.service.SessionTracker;
import net.runelite.client.plugins.bestiary.util.XpTable;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Progress tab: Capture Level bar, XP totals, achievement list, and session recap button.
 */
public class ProgressTab extends JPanel {

    private final ProgressionService progressionService;
    private final SessionTracker sessionTracker;

    private final JLabel levelLabel;
    private final JProgressBar xpBar;
    private final JLabel xpLabel;
    private final JPanel achievementPanel;

    public ProgressTab(ProgressionService progressionService, SessionTracker sessionTracker) {
        this.progressionService = progressionService;
        this.sessionTracker     = sessionTracker;
        setLayout(new BorderLayout(0, 8));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(new EmptyBorder(8, 8, 8, 8));

        // Level header
        JPanel levelPanel = new JPanel(new BorderLayout(0, 4));
        levelPanel.setOpaque(false);

        levelLabel = new JLabel("Capture Level 1", SwingConstants.CENTER);
        levelLabel.setFont(FontManager.getRunescapeBoldFont().deriveFont(18f));
        levelLabel.setForeground(Color.WHITE);

        xpBar = new JProgressBar(0, 100);
        xpBar.setStringPainted(false);
        xpBar.setForeground(new Color(255, 165, 0)); // orange theme
        xpBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        xpBar.setBorderPainted(false);
        xpBar.setPreferredSize(new Dimension(0, 8));

        xpLabel = new JLabel("0 XP  |  Next level: calculating...", SwingConstants.CENTER);
        xpLabel.setFont(FontManager.getRunescapeSmallFont());
        xpLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        levelPanel.add(levelLabel, BorderLayout.NORTH);
        levelPanel.add(xpBar,     BorderLayout.CENTER);
        levelPanel.add(xpLabel,   BorderLayout.SOUTH);

        // Session Recap button
        JButton recapBtn = new JButton("Session Recap");
        recapBtn.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        recapBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        recapBtn.setForeground(new Color(255, 165, 0));
        recapBtn.setFocusPainted(false);
        recapBtn.setBorderPainted(true);
        recapBtn.setToolTipText("View captures from this session");
        recapBtn.addActionListener(e ->
            SessionRecapDialog.open(SwingUtilities.getWindowAncestor(this), sessionTracker));

        JPanel recapRow = new JPanel(new BorderLayout());
        recapRow.setOpaque(false);
        recapRow.setBorder(new EmptyBorder(4, 0, 4, 0));
        recapRow.add(recapBtn, BorderLayout.CENTER);

        // Achievements
        achievementPanel = new JPanel();
        achievementPanel.setLayout(new BoxLayout(achievementPanel, BoxLayout.Y_AXIS));
        achievementPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JScrollPane scroll = new JScrollPane(achievementPanel);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        // Invisible vertical scrollbar (no width taken) — mouse wheel still scrolls
        scroll.getVerticalScrollBar().setPreferredSize(new java.awt.Dimension(0, 0));
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        JLabel achievementHeader = new JLabel("Achievements");
        achievementHeader.setFont(FontManager.getRunescapeBoldFont());
        achievementHeader.setForeground(Color.WHITE);
        achievementHeader.setBorder(new EmptyBorder(4, 0, 4, 0));

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setOpaque(false);
        centerPanel.add(achievementHeader, BorderLayout.NORTH);
        centerPanel.add(scroll,            BorderLayout.CENTER);

        JPanel northStack = new JPanel(new BorderLayout(0, 0));
        northStack.setOpaque(false);
        northStack.add(levelPanel, BorderLayout.NORTH);
        northStack.add(recapRow,   BorderLayout.CENTER);

        add(northStack,  BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);

        refresh();
    }

    /** Rebuild UI to reflect current progression state. Call on EDT. */
    public void refresh() {
        int level      = progressionService.getLevel();
        long totalXp   = progressionService.getTotalXp();
        long toNext    = progressionService.getXpToNextLevel();

        levelLabel.setText("Capture Level " + level);

        if (level < XpTable.MAX_VIRTUAL_LEVEL) {
            // Normal per-level progress, including virtual levels 100-126 past the skill cap.
            long xpThisLevel  = totalXp - XpTable.xpForLevel(level);
            long xpLevelRange = XpTable.xpForLevel(level + 1) - XpTable.xpForLevel(level);
            int pct = (int) (100L * xpThisLevel / Math.max(1, xpLevelRange));
            xpBar.setValue(pct);
            xpLabel.setText(String.format("%,d XP  |  Next level in %,d XP", totalXp, toNext));
        } else {
            // Level 126 / 200M XP: fully maxed.
            xpBar.setValue(100);
            xpLabel.setText(String.format("%,d XP  |  200M XP reached!", totalXp));
        }

        // Rebuild achievement rows
        achievementPanel.removeAll();
        ProgressionService.ProgressionState state = progressionService.getState();

        for (Achievement a : Achievement.values()) {
            boolean unlocked = state.unlockedAchievements.contains(a);
            achievementPanel.add(buildAchievementRow(a, unlocked));
            achievementPanel.add(Box.createVerticalStrut(2));
        }

        achievementPanel.revalidate();
        achievementPanel.repaint();
        revalidate();
        repaint();
    }

    private JPanel buildAchievementRow(Achievement a, boolean unlocked) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setBackground(unlocked ? ColorScheme.DARKER_GRAY_COLOR : new Color(30, 30, 30));
        Color accent = unlocked ? new Color(80, 200, 80) : new Color(55, 55, 55);
        row.setBorder(BorderFactory.createCompoundBorder(
                new javax.swing.border.MatteBorder(0, 3, 0, 0, accent),
                new EmptyBorder(5, 8, 5, 8)));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));

        JLabel icon = new JLabel(unlocked ? "✔" : "○");
        icon.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        icon.setForeground(unlocked ? new Color(80, 200, 80) : ColorScheme.MEDIUM_GRAY_COLOR);

        JPanel text = new JPanel(new GridLayout(2, 1, 0, 1));
        text.setOpaque(false);

        JLabel title = new JLabel(a.title);
        title.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        title.setForeground(unlocked ? Color.WHITE : ColorScheme.MEDIUM_GRAY_COLOR);

        JLabel desc = new JLabel(a.description);
        desc.setFont(FontManager.getRunescapeSmallFont());
        desc.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        text.add(title);
        text.add(desc);

        row.add(icon, BorderLayout.WEST);
        row.add(text, BorderLayout.CENTER);
        return row;
    }
}

