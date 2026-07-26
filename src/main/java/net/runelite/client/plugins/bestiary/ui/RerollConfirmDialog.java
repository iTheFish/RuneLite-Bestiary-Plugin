package net.runelite.client.plugins.bestiary.ui;

import net.runelite.client.plugins.bestiary.model.CapturedCreature;
import net.runelite.client.plugins.bestiary.util.OddsCalculator;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * MODELESS reroll confirmation: shows the card's current stats + Power Level breakdown
 * (via {@link OddsBreakdownPanel}) so the player can judge whether a reroll is worth it,
 * then Reroll / Cancel. Non-blocking.
 */
public class RerollConfirmDialog extends JDialog {

    private static RerollConfirmDialog current;

    public static void open(Window owner, CapturedCreature card, long cost, Runnable onReroll) {
        if (current != null && current.isShowing()) current.dispose();
        current = new RerollConfirmDialog(owner, card, cost, onReroll);
        current.setVisible(true);
    }

    private RerollConfirmDialog(Window owner, CapturedCreature card, long cost, Runnable onReroll) {
        super(owner, "Reroll card", ModalityType.MODELESS);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(ColorScheme.DARK_GRAY_COLOR);
        root.setBorder(new EmptyBorder(12, 14, 12, 14));

        JLabel title = new JLabel((card.isShiny() ? "✦ " : "") + card.rarity.label + " " + card.npcName);
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(card.rarity.displayColor);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(title);
        JLabel sub = new JLabel("Current roll — reroll re-rolls stats, prayer & shiny at the same rarity.");
        sub.setFont(FontManager.getRunescapeSmallFont());
        sub.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(sub);

        root.add(Box.createVerticalStrut(8));
        JPanel breakdown = OddsBreakdownPanel.build(OddsCalculator.compute(card));
        breakdown.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(breakdown);

        root.add(Box.createVerticalStrut(12));
        JButton reroll = new JButton("Reroll — " + cost + " credits");
        reroll.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        reroll.setBackground(new Color(60, 120, 60));
        reroll.setForeground(Color.WHITE);
        reroll.setFocusPainted(false);
        reroll.addActionListener(e -> { dispose(); onReroll.run(); });
        JButton cancel = new JButton("Cancel");
        cancel.setFont(FontManager.getRunescapeSmallFont());
        cancel.setFocusPainted(false);
        cancel.addActionListener(e -> dispose());
        JPanel btns = new JPanel(new GridLayout(1, 2, 8, 0));
        btns.setOpaque(false);
        btns.setAlignmentX(Component.LEFT_ALIGNMENT);
        btns.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        btns.add(reroll);
        btns.add(cancel);
        root.add(btns);

        setContentPane(new JScrollPane(root,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER));
        pack();
        setLocationRelativeTo(owner);
    }
}
