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
 * count > 1), lists each group with an "Investigate" link (filters the album behind
 * it), and converts the extras to credits — keeping the best of each.
 */
public class DiscardDialog extends JDialog {

    private static DiscardDialog current;

    private final BestiaryDataService dataService;
    private final Runnable onDone;
    private final Map<CreatureRarity, JCheckBox> rarityBoxes = new LinkedHashMap<>();
    private JCheckBox protectBox;
    private JPanel groupsPanel;
    private JLabel totalLabel;
    private JButton discardBtn;

    public static void open(Window owner, BestiaryDataService dataService, Runnable onDone) {
        if (current != null && current.isShowing()) current.dispose();
        current = new DiscardDialog(owner, dataService, onDone);
        current.setVisible(true);
    }

    /** Re-scan for duplicates (e.g. after a card was discarded elsewhere). */
    public static void refreshOpen() {
        if (current != null && current.isShowing()) current.recompute();
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
        JLabel help = new JLabel("<html><div style='width:320px'>Keeps the best (highest Power Level) "
                + "of each creature + rarity and discards the rest. Use <i>Investigate</i> to review a "
                + "group in the album first.</div></html>");
        help.setFont(FontManager.getRunescapeSmallFont());
        help.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        help.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(help);

        root.add(Box.createVerticalStrut(8));
        JLabel rHdr = header("RARITIES TO INCLUDE");
        root.add(rHdr);
        JPanel rarRow = new JPanel(new GridLayout(0, 3, 4, 0));
        rarRow.setOpaque(false);
        rarRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        rarRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
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
        root.add(header("DUPLICATE GROUPS FOUND"));
        groupsPanel = new JPanel();
        groupsPanel.setLayout(new BoxLayout(groupsPanel, BoxLayout.Y_AXIS));
        groupsPanel.setOpaque(false);
        JScrollPane groupScroll = new JScrollPane(groupsPanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        groupScroll.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 60)));
        groupScroll.getVerticalScrollBar().setUnitIncrement(16);
        groupScroll.setPreferredSize(new Dimension(360, 200));
        groupScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        groupScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        root.add(groupScroll);

        root.add(Box.createVerticalStrut(8));
        totalLabel = new JLabel();
        totalLabel.setFont(FontManager.getRunescapeSmallFont());
        totalLabel.setForeground(Color.WHITE);
        totalLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(totalLabel);

        root.add(Box.createVerticalStrut(6));
        discardBtn = new JButton("Discard");
        discardBtn.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        discardBtn.setBackground(new Color(150, 60, 60));
        discardBtn.setForeground(Color.WHITE);
        discardBtn.setFocusPainted(false);
        discardBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        discardBtn.addActionListener(e -> doDiscard());
        root.add(discardBtn);

        setContentPane(root);
        recompute();
        pack();
        setLocationRelativeTo(owner);
    }

    private JLabel header(String text) {
        JLabel l = new JLabel(text);
        l.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        l.setForeground(new Color(255, 152, 31));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(new EmptyBorder(0, 0, 3, 0));
        return l;
    }

    /** One duplicate group: same creature + rarity, keep the best, discard the rest. */
    private static final class Group {
        final String npc;
        final CreatureRarity rarity;
        final List<CapturedCreature> discard = new ArrayList<>();
        Group(String npc, CreatureRarity rarity) { this.npc = npc; this.rarity = rarity; }
    }

    private List<Group> groups() {
        Map<String, List<CapturedCreature>> byKey = new LinkedHashMap<>();
        for (CapturedCreature c : dataService.getCollection().creatures) {
            byKey.computeIfAbsent(c.npcName + "|" + c.rarity.name(), k -> new ArrayList<>()).add(c);
        }
        boolean protect = protectBox.isSelected();
        List<Group> out = new ArrayList<>();
        for (List<CapturedCreature> list : byKey.values()) {
            if (list.size() <= 1) continue;
            CreatureRarity rar = list.get(0).rarity;
            if (!rarityBoxes.get(rar).isSelected()) continue;
            list.sort((a, b) -> Integer.compare(b.powerLevel(), a.powerLevel())); // best first
            Group g = new Group(list.get(0).npcName, rar);
            for (int i = 1; i < list.size(); i++) {
                CapturedCreature c = list.get(i);
                if (protect && (c.favourite || c.albumCover)) continue;
                g.discard.add(c);
            }
            if (!g.discard.isEmpty()) out.add(g);
        }
        return out;
    }

    private void recompute() {
        List<Group> groups = groups();
        groupsPanel.removeAll();
        long total = 0;
        int cards = 0;
        for (Group g : groups) {
            long credits = g.discard.stream().mapToLong(dataService::discardValue).sum();
            total += credits;
            cards += g.discard.size();

            JPanel rowP = new JPanel(new BorderLayout(6, 0));
            rowP.setOpaque(false);
            rowP.setAlignmentX(Component.LEFT_ALIGNMENT);
            rowP.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
            rowP.setBorder(new EmptyBorder(1, 2, 1, 2));
            JLabel l = new JLabel(g.npc + "  ·  " + g.rarity.label + "  — discard " + g.discard.size()
                    + "  (+" + credits + "cr)");
            l.setFont(FontManager.getRunescapeSmallFont());
            l.setForeground(g.rarity.displayColor);
            JButton inv = new JButton("Investigate");
            inv.setFont(FontManager.getRunescapeSmallFont());
            inv.setMargin(new Insets(0, 5, 0, 5));
            inv.setFocusPainted(false);
            inv.addActionListener(e -> AlbumDialog.requestOpenDetail(g.npc, g.rarity));
            rowP.add(l, BorderLayout.CENTER);
            rowP.add(inv, BorderLayout.EAST);
            groupsPanel.add(rowP);
        }
        if (groups.isEmpty()) {
            JLabel none = new JLabel("No duplicates match the current filters.");
            none.setFont(FontManager.getRunescapeSmallFont());
            none.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            none.setBorder(new EmptyBorder(6, 4, 0, 0));
            groupsPanel.add(none);
        }
        groupsPanel.revalidate();
        groupsPanel.repaint();

        totalLabel.setText(cards + " duplicate card" + (cards == 1 ? "" : "s")
                + " across " + groups.size() + " group" + (groups.size() == 1 ? "" : "s")
                + "  →  " + total + " credits");
        discardBtn.setEnabled(cards > 0);
        discardBtn.setText(cards == 0 ? "Nothing to discard" : "Discard " + cards + " for " + total + " credits");
    }

    private void doDiscard() {
        List<CapturedCreature> sel = new ArrayList<>();
        for (Group g : groups()) sel.addAll(g.discard);
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
