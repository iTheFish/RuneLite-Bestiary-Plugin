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
import java.util.ArrayList;
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

        int rarityRows = Math.max(1, (int) Math.ceil(countByRarity.size() / 3.0));
        int cardHeight = 32 + rarityRows * 20;

        setBackground(NORMAL_BG);
        setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 3, 0, 0, rarest.displayColor),
                new EmptyBorder(5, 8, 5, 8)));
        setLayout(new GridLayout(1 + rarityRows, 1, 0, 2));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, cardHeight));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Top row: NPC name (centre, compresses) + total caught (right, fixed)
        JPanel topRow = new JPanel(new BorderLayout(4, 0));
        topRow.setOpaque(false);

        JLabel nameLabel = new JLabel(npcName);
        nameLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        nameLabel.setForeground(Color.WHITE);

        JLabel totalLabel = new JLabel(captures.size() + " caught");
        totalLabel.setFont(FontManager.getRunescapeSmallFont());
        totalLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        topRow.add(nameLabel,  BorderLayout.CENTER);
        topRow.add(totalLabel, BorderLayout.EAST);

        // One label per row of up to 3 rarities (rarest first)
        List<Map.Entry<CreatureRarity, Integer>> rarityEntries = new ArrayList<>(countByRarity.entrySet());

        add(topRow);

        for (int i = 0; i < rarityEntries.size(); i += 3) {
            StringBuilder sb = new StringBuilder("<html>");
            for (int j = i; j < Math.min(i + 3, rarityEntries.size()); j++) {
                CreatureRarity r = rarityEntries.get(j).getKey();
                if (j > i) sb.append("&nbsp;&nbsp;");
                sb.append(String.format("<font color='#%02x%02x%02x'>&#9679; %d %s</font>",
                        r.displayColor.getRed(), r.displayColor.getGreen(), r.displayColor.getBlue(),
                        rarityEntries.get(j).getValue(), RARITY_ABBREV[r.ordinal()]));
            }
            sb.append("</html>");
            JLabel rowLabel = new JLabel(sb.toString());
            rowLabel.setFont(FontManager.getRunescapeSmallFont());
            add(rowLabel);
        }

        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { setBackground(HOVER_BG); }
            @Override public void mouseExited(MouseEvent e)  { setBackground(NORMAL_BG); }
            @Override public void mouseClicked(MouseEvent e) {
                new CreatureDetailDialog(
                        SwingUtilities.getWindowAncestor(MonsterSummaryCard.this),
                        captures, collection, "By Rarity", null);
            }
        });
    }
}
