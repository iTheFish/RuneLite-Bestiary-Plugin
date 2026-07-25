package net.runelite.client.plugins.bestiary.ui;

import net.runelite.client.plugins.bestiary.model.CapturedCreature;
import net.runelite.client.plugins.bestiary.model.CreatureRarity;
import net.runelite.client.plugins.bestiary.service.BestiaryDataService;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MODELESS bulk-discard screen. Finds duplicate captures (same creature + rarity,
 * count > 1) and lets the player convert the extras to credits, keeping the best of
 * each. Rarity filters + a favourites/album-cover guard, then a final confirm.
 */
public class DiscardDialog extends JDialog {

    private static DiscardDialog current;

    private final BestiaryDataService dataService;
    private final Runnable onDone;
    private final Map<CreatureRarity, JCheckBox> rarityBoxes = new LinkedHashMap<>();
    private JCheckBox protectBox;
    private JLabel previewLabel;
    private JButton discardBtn;

    public static void open(Window owner, BestiaryDataService dataService, Runnable onDone) {
        if (current != null && current.isShowing()) current.dispose();
        current = new DiscardDialog(owner, dataService, onDone);
        current.setVisible(true);
    }

    private DiscardDialog(Window owner, BestiaryDataService dataService, Runnable onDone) {
        super(owner, "Discard duplicates", ModalityType.MODELESS);
        this.dataService = dataService;
        this.onDone = onDone;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(ColorScheme.DARK_GRAY_COLOR);
        root.setBorder(new EmptyBorder(12, 14, 12, 14));

        JLabel title = new JLabel("Discard duplicates for credits");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(Color.WHITE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(title);
        JLabel help = new JLabel("<html><div style='width:300px'>Keeps the best (highest Power Level) "
                + "of each creature + rarity and discards the rest.</div></html>");
        help.setFont(FontManager.getRunescapeSmallFont());
        help.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        help.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(help);

        root.add(Box.createVerticalStrut(8));
        JLabel rHdr = new JLabel("RARITIES TO INCLUDE");
        rHdr.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        rHdr.setForeground(new Color(255, 152, 31));
        rHdr.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(rHdr);
        JPanel rarRow = new JPanel(new GridLayout(0, 2, 4, 0));
        rarRow.setOpaque(false);
        rarRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (CreatureRarity rar : CreatureRarity.values()) {
            JCheckBox cb = new JCheckBox(rar.label, rar.ordinal() <= CreatureRarity.RARE.ordinal());
            cb.setOpaque(false);
            cb.setForeground(rar.displayColor);
            cb.setFont(FontManager.getRunescapeSmallFont());
            cb.addActionListener(e -> recompute());
            rarityBoxes.put(rar, cb);
            rarRow.add(cb);
        }
        root.add(rarRow);

        protectBox = new JCheckBox("Protect favourites & album covers", true);
        protectBox.setOpaque(false);
        protectBox.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        protectBox.setFont(FontManager.getRunescapeSmallFont());
        protectBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        protectBox.addActionListener(e -> recompute());
        root.add(Box.createVerticalStrut(4));
        root.add(protectBox);

        root.add(Box.createVerticalStrut(8));
        previewLabel = new JLabel();
        previewLabel.setFont(FontManager.getRunescapeSmallFont());
        previewLabel.setForeground(Color.WHITE);
        previewLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(previewLabel);

        root.add(Box.createVerticalStrut(8));
        discardBtn = new JButton("Discard");
        discardBtn.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        discardBtn.setBackground(new Color(150, 60, 60));
        discardBtn.setForeground(Color.WHITE);
        discardBtn.setFocusPainted(false);
        discardBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        discardBtn.addActionListener(e -> doDiscard());
        root.add(discardBtn);

        setContentPane(new JScrollPane(root,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER));
        recompute();
        pack();
        setLocationRelativeTo(owner);
    }

    /** Cards that would be discarded under the current filters. */
    private List<CapturedCreature> selection() {
        Map<String, List<CapturedCreature>> groups = new LinkedHashMap<>();
        for (CapturedCreature c : dataService.getCollection().creatures) {
            groups.computeIfAbsent(c.npcName + "|" + c.rarity.name(), k -> new ArrayList<>()).add(c);
        }
        List<CapturedCreature> out = new ArrayList<>();
        boolean protect = protectBox.isSelected();
        for (List<CapturedCreature> group : groups.values()) {
            if (group.size() <= 1) continue;
            group.sort((a, b) -> Integer.compare(b.powerLevel(), a.powerLevel())); // best first
            for (int i = 1; i < group.size(); i++) {                                // keep index 0
                CapturedCreature c = group.get(i);
                if (!rarityBoxes.get(c.rarity).isSelected()) continue;
                if (protect && (c.favourite || c.albumCover)) continue;
                out.add(c);
            }
        }
        return out;
    }

    private void recompute() {
        List<CapturedCreature> sel = selection();
        long credits = sel.stream().mapToLong(dataService::discardValue).sum();
        previewLabel.setText(sel.size() + " duplicate card" + (sel.size() == 1 ? "" : "s")
                + "  →  " + credits + " credits");
        discardBtn.setEnabled(!sel.isEmpty());
        discardBtn.setText(sel.isEmpty() ? "Nothing to discard" : "Discard " + sel.size() + " for " + credits + " credits");
    }

    private void doDiscard() {
        List<CapturedCreature> sel = selection();
        if (sel.isEmpty()) return;
        long credits = sel.stream().mapToLong(dataService::discardValue).sum();
        int choice = JOptionPane.showConfirmDialog(this,
                "Discard " + sel.size() + " duplicate cards for " + credits + " credits? This cannot be undone.",
                "Confirm discard", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;
        dataService.discardCaptures(sel);
        if (onDone != null) onDone.run();
        recompute();
    }
}
