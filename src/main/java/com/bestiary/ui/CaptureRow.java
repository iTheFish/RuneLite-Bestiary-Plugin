package com.bestiary.ui;

import com.bestiary.model.BestiaryCollection;
import com.bestiary.model.CapturedCreature;
import com.bestiary.model.CreatureRarity;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.event.MouseAdapter;
import java.awt.*;
import java.awt.Color;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Compact two-row row representing a single capture in Individual view mode.
 * Top: NPC name (left) | rarity (right)
 * Bot: Power (left) | region + date (right). Combat level is in the tooltip.
 */
public class CaptureRow extends JPanel {

    private static final int   ROW_HEIGHT = 50;
    private static final Color ROW_BG     = ColorScheme.DARKER_GRAY_COLOR;
    private static final Color ROW_HOVER  = new Color(55, 55, 55);

    private static Runnable onFavouriteLimitReached;
    public static void setOnFavouriteLimitReached(Runnable r) { onFavouriteLimitReached = r; }
    public static Runnable getOnFavouriteLimitReached() { return onFavouriteLimitReached; }
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM HH:mm").withZone(ZoneId.systemDefault());

    public CaptureRow(CapturedCreature capture, BestiaryCollection collection,
                      Runnable onFavouriteChanged) {
        setLayout(new GridLayout(2, 1, 0, 3));
        setBackground(ROW_BG);
        int t = borderThick(capture.rarity);
        setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(t, 4, t, t, capture.rarity.displayColor),
                new EmptyBorder(5 - t, 8, 5 - t, 8 - t)));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, ROW_HEIGHT));
        setPreferredSize(new Dimension(200, ROW_HEIGHT));

        // Top row: NPC name left | rarity right
        JPanel topRow = new JPanel(new BorderLayout(4, 0));
        topRow.setOpaque(false);

        String nameText = capture.favourite ? "★ " + capture.npcName : capture.npcName;
        JLabel nameLabel = new JLabel(nameText);
        nameLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        nameLabel.setForeground(capture.favourite ? new Color(255, 195, 40) : Color.WHITE);

        JLabel rarityLabel = new JLabel("● " + capture.rarity.label);
        rarityLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        rarityLabel.setForeground(capture.rarity.displayColor);

        topRow.add(nameLabel,   BorderLayout.CENTER);
        topRow.add(rarityLabel, BorderLayout.EAST);

        // Bottom row: Power (left, roomy) | region + date right. Combat level moves to the tooltip.
        JPanel botRow = new JPanel(new BorderLayout(4, 0));
        botRow.setOpaque(false);

        int q      = capture.powerLevel();
        String reg = shorten(capture.regionName, 16);
        String dt  = DATE_FMT.format(capture.captureTime);

        // Power in rarity colour, location + date in a muted tone
        JLabel powerLabel = new JLabel("P:" + q);
        powerLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        powerLabel.setForeground(capture.rarity.displayColor);

        JLabel locDateLabel = new JLabel(reg + "  " + dt);
        locDateLabel.setFont(FontManager.getRunescapeSmallFont());
        locDateLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        botRow.add(powerLabel,   BorderLayout.WEST);
        botRow.add(locDateLabel, BorderLayout.EAST);

        add(topRow);
        add(botRow);

        String lvlText = capture.npcCombatLevel > 0 ? "Combat level " + capture.npcCombatLevel : "Non-combat";
        setToolTipText(String.format(
                "<html>%s<br>ATK:%d&nbsp;&nbsp;STR:%d&nbsp;&nbsp;DEF:%d<br>MAG:%d&nbsp;&nbsp;RNG:%d&nbsp;&nbsp;AGI:%d</html>",
                lvlText,
                capture.quality.attack, capture.quality.strength, capture.quality.defence,
                capture.quality.magic, capture.quality.ranged, capture.quality.agility));

        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger()) {
                    JPopupMenu menu = new JPopupMenu();

                    JMenuItem favItem = new JMenuItem(capture.favourite ? "✩ Remove Favourite" : "★ Favourite");
                    favItem.addActionListener(ev -> {
                        if (!capture.favourite && collection.countFavourites() >= 20) {
                            if (onFavouriteLimitReached != null) onFavouriteLimitReached.run();
                            return;
                        }
                        capture.favourite = !capture.favourite;
                        if (onFavouriteChanged != null) onFavouriteChanged.run();
                    });
                    menu.add(favItem);

                    JMenuItem coverItem = new JMenuItem(
                            capture.albumCover ? "Remove album cover" : "Set as album cover");
                    coverItem.addActionListener(ev -> {
                        if (capture.albumCover) {
                            capture.albumCover = false;
                        } else {
                            collection.setAlbumCover(capture); // clears any other cover for this monster
                        }
                        if (onFavouriteChanged != null) onFavouriteChanged.run();
                    });
                    menu.add(coverItem);

                    JMenuItem exportItem = new JMenuItem("Card info + export");
                    exportItem.addActionListener(ev ->
                            CardExportDialog.open(SwingUtilities.getWindowAncestor(CaptureRow.this), capture));
                    menu.add(exportItem);
                    menu.show(CaptureRow.this, e.getX(), e.getY());
                }
            }

            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    AlbumDialog.requestOpenDetail(capture.npcName, capture.captureTime);
                }
            }

            @Override public void mouseEntered(java.awt.event.MouseEvent e) { setBackground(ROW_HOVER); repaint(); }
            @Override public void mouseExited(java.awt.event.MouseEvent e)  { setBackground(ROW_BG);    repaint(); }
        });
    }

    private static String shorten(String s, int max) {
        if (s == null || s.isEmpty()) return "?";
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }

    private static int borderThick(CreatureRarity r) {
        return r.ordinal() >= CreatureRarity.EPIC.ordinal() ? 2 : 1;
    }
}
