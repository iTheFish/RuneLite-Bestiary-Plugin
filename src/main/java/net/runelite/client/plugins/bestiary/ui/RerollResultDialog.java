package net.runelite.client.plugins.bestiary.ui;

import net.runelite.client.plugins.bestiary.model.CapturedCreature;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * MODELESS reroll outcome screen: shows the card's stats before vs after a reroll,
 * with per-stat deltas, plus any rarity/shiny change. Modeless so it never blocks
 * the game or other panels.
 */
public class RerollResultDialog extends JDialog {

    private static RerollResultDialog current;

    public static void open(Window owner, CapturedCreature before, CapturedCreature after) {
        if (current != null && current.isShowing()) current.dispose();
        current = new RerollResultDialog(owner, before, after);
        current.setVisible(true);
    }

    /** A small MODELESS info popup (non-blocking) — e.g. "not enough credits". */
    public static void info(Window owner, String title, String message) {
        JDialog d = new JDialog(owner, title, ModalityType.MODELESS);
        d.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBackground(ColorScheme.DARK_GRAY_COLOR);
        p.setBorder(new EmptyBorder(14, 16, 12, 16));
        JLabel l = new JLabel("<html><div style='width:240px'>" + message + "</div></html>");
        l.setFont(FontManager.getRunescapeSmallFont());
        l.setForeground(Color.WHITE);
        JButton ok = new JButton("OK");
        ok.setFocusPainted(false);
        ok.addActionListener(e -> d.dispose());
        JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        row.setOpaque(false);
        row.add(ok);
        p.add(l, BorderLayout.CENTER);
        p.add(row, BorderLayout.SOUTH);
        d.setContentPane(p);
        d.pack();
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
    }

    private RerollResultDialog(Window owner, CapturedCreature b, CapturedCreature a) {
        super(owner, "Reroll result", ModalityType.MODELESS);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(ColorScheme.DARK_GRAY_COLOR);
        root.setBorder(new EmptyBorder(12, 14, 12, 14));

        JLabel title = new JLabel(a.npcName);
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(a.rarity.displayColor);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(title);

        if (a.rarity != b.rarity) {
            JLabel up = new JLabel("Rarity: " + b.rarity.label + "  →  " + a.rarity.label + "  ⬆");
            up.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
            up.setForeground(new Color(255, 215, 0));
            up.setAlignmentX(Component.LEFT_ALIGNMENT);
            root.add(up);
        }
        if (a.isShiny() && !b.isShiny()) {
            JLabel sh = new JLabel("✦ Now SHINY!");
            sh.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
            sh.setForeground(new Color(255, 215, 0));
            sh.setAlignmentX(Component.LEFT_ALIGNMENT);
            root.add(sh);
        }

        root.add(Box.createVerticalStrut(8));

        // Stats table: Stat | Before | After | Δ
        JPanel t = new JPanel(new GridBagLayout());
        t.setOpaque(false);
        t.setAlignmentX(Component.LEFT_ALIGNMENT);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(1, 0, 1, 14);
        String[] heads = {"Stat", "Before", "After", "Δ"};
        for (int c = 0; c < heads.length; c++) {
            g.gridx = c; g.gridy = 0;
            g.anchor = c == 0 ? GridBagConstraints.WEST : GridBagConstraints.EAST;
            t.add(cell(heads[c], true, new Color(140, 140, 140)), g);
        }
        String[] names = {"Attack", "Strength", "Defence", "Magic", "Ranged", "Agility", "Prayer", "Power Level"};
        int[] bv = {b.quality.attack, b.quality.strength, b.quality.defence, b.quality.magic,
                    b.quality.ranged, b.quality.agility, b.prayer, b.powerLevel()};
        int[] av = {a.quality.attack, a.quality.strength, a.quality.defence, a.quality.magic,
                    a.quality.ranged, a.quality.agility, a.prayer, a.powerLevel()};
        for (int i = 0; i < names.length; i++) {
            int d = av[i] - bv[i];
            g.gridy = i + 1;
            g.gridx = 0; g.anchor = GridBagConstraints.WEST;
            boolean pl = i == names.length - 1;
            t.add(cell(names[i], pl, new Color(210, 210, 210)), g);
            g.gridx = 1; g.anchor = GridBagConstraints.EAST;
            t.add(cell(String.valueOf(bv[i]), false, new Color(150, 150, 150)), g);
            g.gridx = 2; g.anchor = GridBagConstraints.EAST;
            t.add(cell(String.valueOf(av[i]), pl, Color.WHITE), g);
            g.gridx = 3; g.anchor = GridBagConstraints.EAST;
            String ds = d > 0 ? "+" + d : String.valueOf(d);
            Color dc = d > 0 ? new Color(120, 200, 120) : d < 0 ? new Color(224, 112, 112) : new Color(150, 150, 150);
            t.add(cell(ds, pl, dc), g);
        }
        root.add(t);

        root.add(Box.createVerticalStrut(10));
        JButton odds = new JButton("What were the odds?");
        odds.setFont(FontManager.getRunescapeSmallFont());
        odds.setBackground(new Color(60, 90, 150));
        odds.setForeground(Color.WHITE);
        odds.setFocusPainted(false);
        odds.addActionListener(e -> OddsDialog.open(owner, a));
        JButton close = new JButton("Close");
        close.setFont(FontManager.getRunescapeSmallFont());
        close.setFocusPainted(false);
        close.addActionListener(e -> dispose());
        JPanel btns = new JPanel(new GridLayout(1, 2, 8, 0));
        btns.setOpaque(false);
        btns.setAlignmentX(Component.LEFT_ALIGNMENT);
        btns.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        btns.add(odds);
        btns.add(close);
        root.add(btns);

        setContentPane(root);
        pack();
        setLocationRelativeTo(owner);
    }

    private JLabel cell(String text, boolean bold, Color colour) {
        JLabel l = new JLabel(text);
        l.setFont(bold ? FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD)
                       : FontManager.getRunescapeSmallFont());
        l.setForeground(colour);
        return l;
    }
}
