package net.runelite.client.plugins.bestiary.ui;

import net.runelite.client.plugins.bestiary.model.BestiaryCollection;
import net.runelite.client.plugins.bestiary.model.CapturedCreature;
import net.runelite.client.plugins.bestiary.model.CreatureRarity;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

/**
 * Fixed-size album card: image placeholder, name + rarity badge, combat level,
 * and 6 avg-stat bars (label | bar | value).
 * Clicking opens CreatureDetailDialog for all captures of this NPC.
 */
public class AlbumCard extends JPanel {

    public static final int CARD_W = 150;
    public static final int CARD_H = 250;

    private static final int PAD      = 8;
    private static final int LABEL_W  = 26;
    private static final int VAL_W    = 20;
    private static final int BAR_H    = 6;
    private static final int STAT_ROW = 16;

    // Layout Y positions
    private static final int HEADER_Y = 6;
    private static final int HEADER_H = 14;
    private static final int IMAGE_Y  = HEADER_Y + HEADER_H + 4;  // 24
    private static final int IMAGE_H  = 90;
    private static final int NAME_Y   = IMAGE_Y + IMAGE_H + 4;    // 118
    private static final int NAME_H   = 16;
    private static final int COMBAT_Y = NAME_Y + NAME_H;          // 134
    private static final int COMBAT_H = 14;
    private static final int STATS_Y  = COMBAT_Y + COMBAT_H + 3;  // 151

    private static final Color IMAGE_BG  = new Color(22, 22, 22);
    private static final Color NORMAL_BG = new Color(38, 38, 38);
    private static final Color HOVER_BG  = new Color(52, 52, 52);

    private static final String[] STAT_LABELS = {"STR", "SPD", "END", "INT", "STL", "VIT"};

    private final String npcName;
    private final int combatLevel;
    private final CreatureRarity rarest;
    private final List<CreatureRarity> rarityDots;
    private final int dexNumber;
    private final int[] avgStats;
    private final List<CapturedCreature> captures;
    private final BestiaryCollection collection;
    private boolean hovered = false;

    public AlbumCard(int dexNumber, String npcName, List<CapturedCreature> captures,
                     BestiaryCollection collection) {
        this.dexNumber  = dexNumber;
        this.npcName    = npcName;
        this.captures   = captures;
        this.collection = collection;

        CapturedCreature sample = captures.get(0);
        this.combatLevel = sample.npcCombatLevel;

        this.rarest = captures.stream()
                .map(c -> c.rarity)
                .max(Comparator.comparingInt(Enum::ordinal))
                .orElse(CreatureRarity.COMMON);

        CreatureRarity[] order = {
            CreatureRarity.MYTHIC, CreatureRarity.LEGENDARY, CreatureRarity.EPIC,
            CreatureRarity.RARE,   CreatureRarity.UNCOMMON,  CreatureRarity.COMMON
        };
        List<CreatureRarity> dots = new ArrayList<>();
        for (CreatureRarity r : order) {
            if (captures.stream().anyMatch(c -> c.rarity == r)) {
                dots.add(r);
                if (dots.size() == 3) break;
            }
        }
        this.rarityDots = dots;

        this.avgStats = new int[]{
            (int) captures.stream().mapToInt(c -> c.quality.strength).average().orElse(0),
            (int) captures.stream().mapToInt(c -> c.quality.speed).average().orElse(0),
            (int) captures.stream().mapToInt(c -> c.quality.endurance).average().orElse(0),
            (int) captures.stream().mapToInt(c -> c.quality.intelligence).average().orElse(0),
            (int) captures.stream().mapToInt(c -> c.quality.stealth).average().orElse(0),
            (int) captures.stream().mapToInt(c -> c.quality.vitality).average().orElse(0),
        };

        setOpaque(false);
        setPreferredSize(new Dimension(CARD_W, CARD_H));
        setMinimumSize(new Dimension(CARD_W, CARD_H));
        setMaximumSize(new Dimension(CARD_W, CARD_H));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hovered = true;  repaint(); }
            @Override public void mouseExited(MouseEvent e)  { hovered = false; repaint(); }
            @Override public void mouseClicked(MouseEvent e) {
                new CreatureDetailDialog(
                        SwingUtilities.getWindowAncestor(AlbumCard.this),
                        captures, collection);
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w    = getWidth();
        int imgX = PAD;
        int imgW = w - PAD * 2;

        // Card background
        g2.setColor(hovered ? HOVER_BG : NORMAL_BG);
        g2.fillRoundRect(0, 0, w, CARD_H, 8, 8);

        // Left accent bar (rarest rarity colour)
        g2.setColor(rarest.displayColor);
        g2.fillRoundRect(0, 0, 4, CARD_H, 4, 4);

        Font smallFont = FontManager.getRunescapeSmallFont();
        Font boldFont  = FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD);
        Font smallBold = FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD, 10f);

        g2.setFont(smallFont);
        FontMetrics sfm = g2.getFontMetrics();

        // --- Header: rarity dots (left) + dex number (right) ---
        int dotSize = 7;
        int dotY    = HEADER_Y + (HEADER_H - dotSize) / 2;
        int dotX    = PAD + 2;
        for (CreatureRarity r : rarityDots) {
            g2.setColor(r.displayColor);
            g2.fillOval(dotX, dotY, dotSize, dotSize);
            dotX += dotSize + 4;
        }

        String dexStr = String.format("no. %03d", dexNumber);
        g2.setFont(smallFont);
        g2.setColor(ColorScheme.MEDIUM_GRAY_COLOR);
        FontMetrics dfm = g2.getFontMetrics();
        g2.drawString(dexStr, w - PAD - dfm.stringWidth(dexStr),
                HEADER_Y + (HEADER_H + dfm.getAscent() - dfm.getDescent()) / 2);

        // --- Image placeholder ---
        g2.setColor(IMAGE_BG);
        g2.fillRoundRect(imgX, IMAGE_Y, imgW, IMAGE_H, 6, 6);
        g2.setColor(new Color(45, 45, 45));
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(imgX, IMAGE_Y, imgW, IMAGE_H, 6, 6);

        // Subtle landscape placeholder icon
        int cx = imgX + imgW / 2;
        int cy = IMAGE_Y + IMAGE_H / 2 + 6;
        g2.setColor(new Color(50, 50, 50));
        // left mountain
        g2.fillPolygon(new int[]{cx - 24, cx - 6,  cx - 40}, new int[]{cy - 10, cy + 6, cy + 6}, 3);
        // right larger mountain
        g2.fillPolygon(new int[]{cx + 10, cx + 30, cx - 10}, new int[]{cy - 18, cy + 6, cy + 6}, 3);
        // sun circle
        g2.setColor(new Color(60, 60, 60));
        g2.fillOval(cx - 34, IMAGE_Y + 10, 9, 9);

        // --- Name row + rarity badge ---
        int nameBaseline = NAME_Y + (NAME_H + sfm.getAscent() - sfm.getDescent()) / 2;

        // Rarity badge (right-aligned, draw first to know its width)
        g2.setFont(smallBold);
        FontMetrics sbfm = g2.getFontMetrics();
        String badgeText = rarest.label;
        int badgePad = 4;
        int badgeW   = sbfm.stringWidth(badgeText) + badgePad * 2;
        int badgeH   = 13;
        int badgeX   = w - PAD - badgeW;
        int badgeY   = NAME_Y + (NAME_H - badgeH) / 2;
        g2.setColor(new Color(rarest.displayColor.getRed(), rarest.displayColor.getGreen(),
                rarest.displayColor.getBlue(), 190));
        g2.fillRoundRect(badgeX, badgeY, badgeW, badgeH, 4, 4);
        g2.setColor(Color.WHITE);
        int badgeBaseline = badgeY + (badgeH + sbfm.getAscent() - sbfm.getDescent()) / 2;
        g2.drawString(badgeText, badgeX + badgePad, badgeBaseline);

        // NPC name (truncated to leave room for badge)
        g2.setFont(boldFont);
        FontMetrics bfm = g2.getFontMetrics();
        int maxNameW = badgeX - PAD - 4;
        String displayName = npcName;
        if (bfm.stringWidth(displayName) > maxNameW) {
            while (displayName.length() > 0 && bfm.stringWidth(displayName + "…") > maxNameW) {
                displayName = displayName.substring(0, displayName.length() - 1);
            }
            displayName += "…";
        }
        g2.setColor(Color.WHITE);
        g2.drawString(displayName, imgX + 2, nameBaseline);

        // --- Combat level ---
        g2.setFont(smallFont);
        g2.setColor(ColorScheme.LIGHT_GRAY_COLOR);
        String combatStr = combatLevel > 0 ? "Combat lvl " + combatLevel : "Non-combat";
        g2.drawString(combatStr, imgX + 2, COMBAT_Y + (COMBAT_H + sfm.getAscent() - sfm.getDescent()) / 2);

        // --- Stat bars ---
        Font valFont = boldFont;
        for (int i = 0; i < STAT_LABELS.length; i++) {
            int rowY     = STATS_Y + i * STAT_ROW;
            int baseline = rowY + (STAT_ROW + sfm.getAscent() - sfm.getDescent()) / 2;

            // Label
            g2.setFont(smallFont);
            g2.setColor(new Color(160, 160, 160));
            g2.drawString(STAT_LABELS[i], imgX, baseline);

            // Bar background + fill
            int barX = imgX + LABEL_W + 3;
            int barW = imgW - LABEL_W - VAL_W - 6;
            int barY = rowY + (STAT_ROW - BAR_H) / 2;
            g2.setColor(new Color(28, 28, 28));
            g2.fillRoundRect(barX, barY, barW, BAR_H, 3, 3);
            int fill = Math.round(barW * avgStats[i] / 100f);
            if (fill > 0) {
                g2.setColor(new Color(rarest.displayColor.getRed(), rarest.displayColor.getGreen(),
                        rarest.displayColor.getBlue(), 210));
                g2.fillRoundRect(barX, barY, fill, BAR_H, 3, 3);
            }

            // Value (right-aligned, bold)
            g2.setFont(valFont);
            FontMetrics vfm = g2.getFontMetrics();
            String valStr = String.valueOf(avgStats[i]);
            g2.setColor(Color.WHITE);
            g2.drawString(valStr, imgX + imgW - vfm.stringWidth(valStr), baseline);
        }

        g2.dispose();
    }
}
