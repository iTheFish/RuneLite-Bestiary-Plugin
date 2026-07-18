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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MonsterSummaryCard extends JPanel {

    private static final Color HOVER_BG  = new Color(50, 50, 50);
    private static final Color NORMAL_BG = ColorScheme.DARKER_GRAY_COLOR;

    private static final String[] RARITY_ABBREV = { "Cmn", "Unc", "Rare", "Epic", "Lgd", "Mth" };

    public MonsterSummaryCard(String npcName, List<CapturedCreature> captures,
                               BestiaryCollection collection) {
        // Left border = rarest rarity this NPC has been caught at
        CreatureRarity rarest = captures.stream()
                .map(c -> c.rarity)
                .max(Comparator.comparingInt(Enum::ordinal))
                .orElse(CreatureRarity.COMMON);

        // Count per rarity, rarest first, skipping absent rarities
        Map<CreatureRarity, Integer> countByRarity = new LinkedHashMap<>();
        CreatureRarity[] rarityOrder = {
            CreatureRarity.MYTHIC, CreatureRarity.LEGENDARY, CreatureRarity.EPIC,
            CreatureRarity.RARE,   CreatureRarity.UNCOMMON,  CreatureRarity.COMMON
        };
        for (CreatureRarity r : rarityOrder) {
            int cnt = (int) captures.stream().filter(c -> c.rarity == r).count();
            if (cnt > 0) countByRarity.put(r, cnt);
        }

        setBackground(NORMAL_BG);
        setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 3, 0, 0, rarest.displayColor),
                new EmptyBorder(5, 8, 5, 8)));
        setLayout(new GridLayout(2, 1, 0, 3));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Top row: NPC name (left) + total caught (right)
        JPanel topRow = new JPanel(new BorderLayout(4, 0));
        topRow.setOpaque(false);

        JLabel nameLabel = new JLabel(npcName);
        nameLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        nameLabel.setForeground(Color.WHITE);

        JLabel totalLabel = new JLabel(captures.size() + " caught");
        totalLabel.setFont(FontManager.getRunescapeSmallFont());
        totalLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        topRow.add(nameLabel,  BorderLayout.WEST);
        topRow.add(totalLabel, BorderLayout.EAST);

        // Bottom row: colored rarity breakdown (● N Abbrev per rarity present)
        JPanel rarityRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        rarityRow.setOpaque(false);

        for (Map.Entry<CreatureRarity, Integer> entry : countByRarity.entrySet()) {
            CreatureRarity r = entry.getKey();
            JLabel tag = new JLabel("● " + entry.getValue()
                    + " " + RARITY_ABBREV[r.ordinal()]);
            tag.setFont(FontManager.getRunescapeSmallFont());
            tag.setForeground(r.displayColor);
            rarityRow.add(tag);
        }

        add(topRow);
        add(rarityRow);

        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { setBackground(HOVER_BG); }
            @Override public void mouseExited(MouseEvent e)  { setBackground(NORMAL_BG); }
            @Override public void mouseClicked(MouseEvent e) {
                new CreatureDetailDialog(
                        SwingUtilities.getWindowAncestor(MonsterSummaryCard.this),
                        captures, collection);
            }
        });
    }
}
