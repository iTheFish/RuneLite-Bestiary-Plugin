package net.runelite.client.plugins.bestiary.ui;

import net.runelite.client.plugins.bestiary.model.BestiaryCollection;
import net.runelite.client.plugins.bestiary.model.CapturedCreature;
import net.runelite.client.plugins.bestiary.model.CreatureRarity;
import net.runelite.client.plugins.bestiary.model.DifficultyTier;
import net.runelite.client.plugins.bestiary.model.MonsterRoster;
import net.runelite.client.plugins.bestiary.model.StatArchetype;
import net.runelite.client.plugins.bestiary.service.WikiImageService;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/**
 * Fixed-size album card. Two modes:
 *   Captured — full colour, NPC image, rarity dots, difficulty badge, stat bars.
 *   Locked   — dark/grey, wiki image with dark overlay, difficulty badge, kill count.
 */
public class AlbumCard extends JPanel {

    public static final int CARD_W = 165;
    public static final int CARD_H = 300;

    private static final int PAD      = 8;
    private static final int LABEL_W  = 26;
    private static final int VAL_W    = 20;
    private static final int BAR_H    = 6;
    private static final int STAT_ROW = 16;

    private static final int HEADER_Y = 6;
    private static final int HEADER_H = 14;
    private static final int IMAGE_Y  = HEADER_Y + HEADER_H + 4;
    private static final int IMAGE_H  = 130;
    private static final int NAME_Y   = IMAGE_Y + IMAGE_H + 4;
    private static final int NAME_H   = 20;
    private static final int COMBAT_Y = NAME_Y + NAME_H;
    private static final int COMBAT_H = 14;
    private static final int STATS_Y  = COMBAT_Y + COMBAT_H + 3;

    private static final Color NORMAL_BG      = new Color(38, 38, 38);
    private static final Color HOVER_BG       = new Color(52, 52, 52);
    private static final Color LOCKED_BG      = new Color(22, 22, 22);
    private static final Color LOCKED_HOVER   = new Color(30, 30, 30);
    private static final Color LOCKED_ACCENT  = new Color(48, 48, 48);
    private static final Color IMAGE_BG       = new Color(22, 22, 22);

    private static final String[] STAT_LABELS = {"STR", "SPD", "END", "INT", "STL", "VIT"};

    private final String npcName;
    private final int dexNumber;
    private final DifficultyTier difficulty;
    private final StatArchetype archetype;
    @Nullable private final WikiImageService imageService;
    private boolean hovered = false;
    private final boolean locked;

    // Captured-only
    @Nullable private final List<CapturedCreature> captures;
    @Nullable private final BestiaryCollection collection;
    @Nullable private final CreatureRarity rarest;
    @Nullable private final List<CreatureRarity> rarityDots;
    private final int combatLevel;
    private final int[] avgStats;

    // Locked-only
    private final int killCount;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** Captured (unlocked) card. */
    public AlbumCard(int dexNumber, String npcName, List<CapturedCreature> captures,
                     BestiaryCollection collection, @Nullable WikiImageService imageService) {
        this.dexNumber    = dexNumber;
        this.npcName      = npcName;
        this.captures     = captures;
        this.collection   = collection;
        this.imageService = imageService;
        this.locked       = false;
        this.killCount    = 0;

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
            if (captures.stream().anyMatch(c -> c.rarity == r)) { dots.add(r); if (dots.size() == 3) break; }
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

        this.difficulty = MonsterRoster.getDifficulty(npcName, combatLevel);
        this.archetype  = MonsterRoster.getArchetype(npcName, combatLevel);
        init(imageService, true);
    }

    /** Locked (uncaptured) card. */
    public AlbumCard(int dexNumber, String npcName, int killCount,
                     @Nullable WikiImageService imageService) {
        this.dexNumber    = dexNumber;
        this.npcName      = npcName;
        this.captures     = null;
        this.collection   = null;
        this.imageService = imageService;
        this.locked       = true;
        this.killCount    = killCount;
        this.combatLevel  = 0;
        this.rarest       = null;
        this.rarityDots   = null;
        this.avgStats     = new int[6];

        this.difficulty = MonsterRoster.getDifficulty(npcName, 0);
        this.archetype  = MonsterRoster.getArchetype(npcName, 0);
        init(imageService, imageService != null);
    }

    private void init(@Nullable WikiImageService imgService, boolean fetchImage) {
        setOpaque(false);
        setPreferredSize(new Dimension(CARD_W, CARD_H));
        setMinimumSize(new Dimension(CARD_W, CARD_H));
        setMaximumSize(new Dimension(CARD_W, CARD_H));
        if (!locked) setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        if (fetchImage && imgService != null) {
            imgService.requestImage(npcName, this::repaint);
        }

        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hovered = true;  repaint(); }
            @Override public void mouseExited(MouseEvent e)  { hovered = false; repaint(); }
            @Override public void mouseClicked(MouseEvent e) {
                if (!locked && captures != null && collection != null) {
                    new CreatureDetailDialog(
                            SwingUtilities.getWindowAncestor(AlbumCard.this), captures, collection, "By Rarity", null);
                }
            }
        });
    }

    // -------------------------------------------------------------------------
    // Paint
    // -------------------------------------------------------------------------

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = makeG2(g);
        int w    = getWidth();
        int imgX = PAD;
        int imgW = w - PAD * 2;

        // Background + accent bar
        if (locked) {
            g2.setColor(hovered ? LOCKED_HOVER : LOCKED_BG);
            g2.fillRoundRect(0, 0, w, CARD_H, 8, 8);
            g2.setColor(LOCKED_ACCENT);
        } else {
            g2.setColor(hovered ? HOVER_BG : NORMAL_BG);
            g2.fillRoundRect(0, 0, w, CARD_H, 8, 8);
            g2.setColor(rarest.displayColor);
        }
        g2.fillRoundRect(0, 0, 4, CARD_H, 4, 4);

        Font smallFont = FontManager.getRunescapeSmallFont();
        Font boldFont  = FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD);
        Font smallBold = FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD, 14f);

        g2.setFont(smallFont);
        FontMetrics sfm = g2.getFontMetrics();

        // --- Header: rarity dots (captured only) + dex number ---
        if (!locked && rarityDots != null) {
            int dotSize = 7, dotY = HEADER_Y + (HEADER_H - 7) / 2, dotX = PAD + 2;
            for (CreatureRarity r : rarityDots) {
                g2.setColor(r.displayColor);
                g2.fillOval(dotX, dotY, dotSize, dotSize);
                dotX += dotSize + 4;
            }
        }
        FontMetrics dfm = sfm;
        String dexStr = String.format("no. %03d", dexNumber);
        g2.setFont(smallFont);
        g2.setColor(locked ? new Color(75, 75, 75) : ColorScheme.LIGHT_GRAY_COLOR);
        g2.drawString(dexStr, w - PAD - dfm.stringWidth(dexStr),
                HEADER_Y + (HEADER_H + dfm.getAscent() - dfm.getDescent()) / 2);

        // --- Image area ---
        g2.setColor(IMAGE_BG);
        g2.fillRoundRect(imgX, IMAGE_Y, imgW, IMAGE_H, 6, 6);

        BufferedImage npcImage = imageService != null ? imageService.getImage(npcName) : null;
        if (npcImage != null) {
            // Scale-to-fit, centred
            double scale = Math.min((double) imgW / npcImage.getWidth(),
                                    (double) IMAGE_H / npcImage.getHeight());
            int dw = (int) (npcImage.getWidth()  * scale);
            int dh = (int) (npcImage.getHeight() * scale);
            int dx = imgX + (imgW - dw) / 2;
            int dy = IMAGE_Y + (IMAGE_H - dh) / 2;
            Shape clip = g2.getClip();
            g2.setClip(imgX, IMAGE_Y, imgW, IMAGE_H);
            g2.drawImage(npcImage, dx, dy, dw, dh, null);
            if (locked) {
                // ~65% black overlay for the "greyed out" look
                g2.setColor(new Color(0, 0, 0, 165));
                g2.fillRect(imgX, IMAGE_Y, imgW, IMAGE_H);
            }
            g2.setClip(clip);
        } else if (locked) {
            // "?" placeholder for locked with no image yet
            g2.setColor(new Color(38, 38, 38));
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(imgX, IMAGE_Y, imgW, IMAGE_H, 6, 6);
            g2.setFont(boldFont.deriveFont(Font.BOLD, 28f));
            FontMetrics qfm = g2.getFontMetrics();
            g2.setColor(new Color(55, 55, 55));
            String q = "?";
            g2.drawString(q, imgX + (imgW - qfm.stringWidth(q)) / 2,
                    IMAGE_Y + (IMAGE_H + qfm.getAscent() - qfm.getDescent()) / 2);
        } else {
            // Captured card placeholder while loading
            g2.setColor(new Color(45, 45, 45));
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(imgX, IMAGE_Y, imgW, IMAGE_H, 6, 6);
            int cx = imgX + imgW / 2, cy = IMAGE_Y + IMAGE_H / 2 + 6;
            g2.setColor(new Color(50, 50, 50));
            g2.fillPolygon(new int[]{cx-24, cx-6,  cx-40}, new int[]{cy-10, cy+6, cy+6}, 3);
            g2.fillPolygon(new int[]{cx+10, cx+30, cx-10}, new int[]{cy-18, cy+6, cy+6}, 3);
            g2.setColor(new Color(60, 60, 60));
            g2.fillOval(cx - 34, IMAGE_Y + 10, 9, 9);
        }

        // --- Name row + difficulty badge ---
        g2.setFont(smallFont);
        sfm = g2.getFontMetrics();
        int nameBaseline = NAME_Y + (NAME_H + sfm.getAscent() - sfm.getDescent()) / 2;

        // Difficulty badge (right-aligned)
        g2.setFont(smallBold);
        FontMetrics sbfm = g2.getFontMetrics();
        String badgeText = difficulty.label;
        int badgePad = 5;
        int badgeW   = sbfm.stringWidth(badgeText) + badgePad * 2;
        int badgeH   = 16;
        int badgeX   = w - PAD - badgeW;
        int badgeY   = NAME_Y + (NAME_H - badgeH) / 2;

        Color badgeBg = locked
                ? new Color(difficulty.displayColor.getRed(), difficulty.displayColor.getGreen(),
                            difficulty.displayColor.getBlue(), 100)
                : new Color(difficulty.displayColor.getRed(), difficulty.displayColor.getGreen(),
                            difficulty.displayColor.getBlue(), 200);
        g2.setColor(badgeBg);
        g2.fillRoundRect(badgeX, badgeY, badgeW, badgeH, 4, 4);
        g2.setColor(locked ? new Color(120, 120, 120) : Color.WHITE);
        g2.drawString(badgeText, badgeX + badgePad,
                badgeY + (badgeH + sbfm.getAscent() - sbfm.getDescent()) / 2);

        // NPC name (truncated)
        g2.setFont(boldFont);
        FontMetrics bfm = g2.getFontMetrics();
        int maxNameW = badgeX - PAD - 4;
        g2.setColor(locked ? new Color(90, 90, 90) : Color.WHITE);
        g2.drawString(truncate(npcName, bfm, maxNameW), imgX + 2, nameBaseline);

        // --- Sub-name row: combat level (captured) or kill count (locked) ---
        g2.setFont(smallFont);
        g2.setFont(smallFont);
        sfm = g2.getFontMetrics();
        int subBaseline = COMBAT_Y + (COMBAT_H + sfm.getAscent() - sfm.getDescent()) / 2;

        if (!locked) {
            String combatStr = combatLevel > 0 ? "Lvl " + combatLevel : "Non-combat";
            g2.setColor(ColorScheme.LIGHT_GRAY_COLOR);
            g2.drawString(combatStr, imgX + 2, subBaseline);
            // Archetype label right-aligned
            g2.setColor(new Color(180, 140, 60));
            String archStr = "· " + archetype.label;
            g2.drawString(archStr, w - PAD - sfm.stringWidth(archStr), subBaseline);
        } else {
            g2.setColor(new Color(65, 65, 65));
            String killStr = killCount > 0 ? killCount + " kills" : "Not encountered";
            g2.drawString(killStr, imgX + 2, subBaseline);
        }

        // --- Stat bars (captured) / empty outlines (locked) ---
        g2.setFont(smallFont);
        sfm = g2.getFontMetrics();
        for (int i = 0; i < STAT_LABELS.length; i++) {
            int rowY     = STATS_Y + i * STAT_ROW;
            int baseline = rowY + (STAT_ROW + sfm.getAscent() - sfm.getDescent()) / 2;

            g2.setFont(smallFont);
            boolean primary = !locked && archetype.isPrimary(i);
            g2.setColor(primary ? new Color(200, 160, 60) : locked ? new Color(48, 48, 48) : new Color(160, 160, 160));
            g2.drawString(STAT_LABELS[i], imgX, baseline);

            int barX = imgX + LABEL_W + 3;
            int barW = imgW - LABEL_W - VAL_W - 6;
            int barY = rowY + (STAT_ROW - BAR_H) / 2;
            g2.setColor(new Color(28, 28, 28));
            g2.fillRoundRect(barX, barY, barW, BAR_H, 3, 3);

            if (!locked) {
                int fill = Math.round(barW * avgStats[i] / 100f);
                if (fill > 0) {
                    g2.setColor(new Color(rarest.displayColor.getRed(),
                            rarest.displayColor.getGreen(), rarest.displayColor.getBlue(), 210));
                    g2.fillRoundRect(barX, barY, fill, BAR_H, 3, 3);
                }
                g2.setFont(boldFont);
                FontMetrics vfm = g2.getFontMetrics();
                String valStr = String.valueOf(avgStats[i]);
                g2.setColor(Color.WHITE);
                g2.drawString(valStr, imgX + imgW - vfm.stringWidth(valStr), baseline);
            }
        }

        g2.dispose();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Graphics2D makeG2(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,     RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);
        return g2;
    }

    private static String truncate(String text, FontMetrics fm, int maxW) {
        if (fm.stringWidth(text) <= maxW) return text;
        while (text.length() > 0 && fm.stringWidth(text + "…") > maxW) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "…";
    }
}
