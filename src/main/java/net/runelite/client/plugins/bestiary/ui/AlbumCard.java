package net.runelite.client.plugins.bestiary.ui;

import net.runelite.client.plugins.bestiary.BestiaryConfig;
import net.runelite.client.plugins.bestiary.model.BestiaryCollection;
import net.runelite.client.plugins.bestiary.model.CapturedCreature;
import net.runelite.client.plugins.bestiary.model.CreatureRarity;
import net.runelite.client.plugins.bestiary.model.CreatureSpecies;
import net.runelite.client.plugins.bestiary.model.DifficultyTier;
import net.runelite.client.plugins.bestiary.model.MonsterRoster;
import net.runelite.client.plugins.bestiary.model.CombatClass;
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

    private static BestiaryConfig sharedConfig;
    public static void setConfig(BestiaryConfig cfg) { sharedConfig = cfg; }

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
    private final CombatClass combatClass;
    private final CreatureSpecies species;
    @Nullable private final WikiImageService imageService;
    private boolean hovered = false;
    private float shimmerPhase = 0f;
    private javax.swing.Timer shimmerTimer;
    private final boolean locked;

    private static final java.util.List<AlbumCard> SHIMMER_CARDS = new java.util.ArrayList<>();
    private static javax.swing.Timer GLOBAL_SHIMMER_TIMER;

    // Captured-only
    @Nullable private final List<CapturedCreature> captures;
    @Nullable private final BestiaryCollection collection;
    @Nullable private final CreatureRarity rarest;
    @Nullable private final List<CreatureRarity> rarityDots;
    private final int combatLevel;
    private final int[] avgStats;

    // Locked-only
    private final int killCount;
    private final boolean hasShiny;

    // Optional override: if set, clicking opens detail dialog with these captures instead of `captures`
    @Nullable private List<CapturedCreature> detailCaptures;

    public void setDetailCaptures(List<CapturedCreature> all) {
        this.detailCaptures = all;
    }

    // If set, left-click fires this instead of the default detail-dialog logic
    @Nullable private Runnable clickOverride;
    public void setClickOverride(Runnable override) { this.clickOverride = override; }

    // If set, right-click menu shows "Remove Favourite" which fires this
    @Nullable private Runnable unfavouriteCallback;
    public void setUnfavouriteCallback(Runnable r) { this.unfavouriteCallback = r; }

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

        // Best capture = highest rarity, then highest overall quality as tiebreaker
        CapturedCreature best = captures.stream()
                .max(Comparator.comparingInt((CapturedCreature c) -> c.rarity.ordinal())
                        .thenComparingInt(c -> c.quality.overallRating()))
                .orElse(captures.get(0));
        this.combatLevel = best.npcCombatLevel;

        this.rarest = best.rarity;

        // Rarity dots show every rarity the player has unlocked across all captures
        CreatureRarity[] order = {
            CreatureRarity.MYTHIC, CreatureRarity.LEGENDARY, CreatureRarity.EPIC,
            CreatureRarity.RARE,   CreatureRarity.UNCOMMON,  CreatureRarity.COMMON
        };
        List<CreatureRarity> dots = new ArrayList<>();
        for (CreatureRarity r : order) {
            if (captures.stream().anyMatch(c -> c.rarity == r)) { dots.add(r); if (dots.size() == 3) break; }
        }
        this.rarityDots = dots;

        // Stats displayed are the best capture's individual stats, not an average
        this.avgStats = new int[]{
            best.quality.strength,
            best.quality.speed,
            best.quality.endurance,
            best.quality.intelligence,
            best.quality.stealth,
            best.quality.vitality,
        };

        this.difficulty = MonsterRoster.getDifficulty(npcName, combatLevel);
        this.combatClass = MonsterRoster.getCombatClass(npcName, combatLevel);
        this.species    = MonsterRoster.getSpecies(npcName, combatLevel);
        this.hasShiny   = captures.stream().anyMatch(CapturedCreature::isShiny);
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
        this.combatClass = MonsterRoster.getCombatClass(npcName, 0);
        this.species    = MonsterRoster.getSpecies(npcName, 0);
        this.hasShiny   = false;
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

        if (!locked && rarest != null && rarest.ordinal() >= CreatureRarity.EPIC.ordinal()) {
            shimmerTimer = new javax.swing.Timer(30, e -> {
                shimmerPhase += 0.022f;
                if (shimmerPhase > 1.05f) { shimmerTimer.stop(); shimmerPhase = 0f; }
                repaint();
            });
            shimmerTimer.setRepeats(true);
            SHIMMER_CARDS.add(this);
            if (GLOBAL_SHIMMER_TIMER == null) {
                GLOBAL_SHIMMER_TIMER = new javax.swing.Timer(10_000, e -> {
                    if (sharedConfig != null && !sharedConfig.autoShimmer()) return;
                    for (AlbumCard c : SHIMMER_CARDS) { c.shimmerPhase = 0f; c.shimmerTimer.restart(); }
                });
                GLOBAL_SHIMMER_TIMER.setRepeats(true);
                GLOBAL_SHIMMER_TIMER.start();
            }
        }

        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                hovered = true;
                if (shimmerTimer != null) { shimmerPhase = 0f; shimmerTimer.restart(); }
                repaint();
            }
            @Override public void mouseExited(MouseEvent e) {
                hovered = false;
                if (shimmerTimer != null) shimmerTimer.stop();
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger() && !locked && captures != null) {
                    showExportMenu(e);
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                if (clickOverride != null) {
                    clickOverride.run();
                    return;
                }
                if (!locked && captures != null && collection != null) {
                    List<CapturedCreature> forDialog = (detailCaptures != null && !detailCaptures.isEmpty())
                            ? detailCaptures : captures;
                    new CreatureDetailDialog(
                            SwingUtilities.getWindowAncestor(AlbumCard.this), forDialog, collection, "By Rarity", rarest);
                }
            }

            private void showExportMenu(MouseEvent e) {
                JPopupMenu menu = new JPopupMenu();
                if (unfavouriteCallback != null) {
                    JMenuItem unfavItem = new JMenuItem("✩ Remove Favourite");
                    unfavItem.addActionListener(ev -> unfavouriteCallback.run());
                    menu.add(unfavItem);
                    menu.addSeparator();
                }
                List<CapturedCreature> sorted = new ArrayList<>(captures);
                sorted.sort(java.util.Comparator.<CapturedCreature>
                        comparingInt(c -> c.rarity.ordinal())
                        .reversed()
                        .thenComparingInt(c -> -c.quality.overallRating()));

                if (sorted.size() == 1) {
                    JMenuItem item = new JMenuItem("Export Card");
                    CapturedCreature only = sorted.get(0);
                    item.addActionListener(ev ->
                            CardExportDialog.open(SwingUtilities.getWindowAncestor(AlbumCard.this), only));
                    menu.add(item);
                } else {
                    JMenu sub = new JMenu("Export Card");
                    int shown = Math.min(sorted.size(), 8);
                    for (int i = 0; i < shown; i++) {
                        CapturedCreature c = sorted.get(i);
                        String label = (c.nickname != null && !c.nickname.isEmpty())
                                ? "\"" + c.nickname + "\"  Q:" + c.quality.overallRating()
                                : c.rarity.label + "  Q:" + c.quality.overallRating();
                        JMenuItem item = new JMenuItem(label);
                        item.setForeground(c.rarity.displayColor);
                        item.addActionListener(ev ->
                                CardExportDialog.open(SwingUtilities.getWindowAncestor(AlbumCard.this), c));
                        sub.add(item);
                    }
                    menu.add(sub);
                }
                menu.show(AlbumCard.this, e.getX(), e.getY());
            }
        });
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        if (shimmerTimer != null) shimmerTimer.stop();
        SHIMMER_CARDS.remove(this);
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

        // --- Header: left side (nickname or rarity dots) + right side (dex number) ---
        FontMetrics dfm = sfm;
        String dexStr = String.format("no. %03d", dexNumber);
        g2.setFont(smallFont);
        int dexX = w - PAD - dfm.stringWidth(dexStr);
        int dexY = HEADER_Y + (HEADER_H + dfm.getAscent() - dfm.getDescent()) / 2;
        g2.setColor(locked ? new Color(75, 75, 75) : ColorScheme.LIGHT_GRAY_COLOR);
        g2.drawString(dexStr, dexX, dexY);
        if (hasShiny) {
            String star = "✦";
            g2.setColor(new Color(255, 215, 0));
            g2.drawString(star, dexX - dfm.stringWidth(star) - 3, dexY);
        }

        // Left of header: nickname (single-capture card only) or rarity dots
        String headerNickname = (!locked && captures != null && captures.size() == 1
                && captures.get(0).nickname != null && !captures.get(0).nickname.isEmpty())
                ? captures.get(0).nickname : null;
        if (headerNickname != null) {
            Font nickFont = smallFont.deriveFont(Font.BOLD | Font.ITALIC, 11f);
            g2.setFont(nickFont);
            FontMetrics nfm = g2.getFontMetrics();
            int maxNickW = dexX - PAD - 6;
            String nick = truncate(headerNickname, nfm, maxNickW);
            g2.setColor(new Color(200, 155, 50));
            g2.drawString(nick, PAD + 2, dexY);
            g2.setFont(smallFont);
        } else if (!locked && rarityDots != null) {
            int dotSize = 7, dotY = HEADER_Y + (HEADER_H - 7) / 2, dotX = PAD + 2;
            for (CreatureRarity r : rarityDots) {
                g2.setColor(r.displayColor);
                g2.fillOval(dotX, dotY, dotSize, dotSize);
                dotX += dotSize + 4;
            }
        }

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

        int pillH = 12, pillPad = 4;
        int pillY = COMBAT_Y + (COMBAT_H - pillH) / 2;
        int pillBase = pillY + (pillH + sfm.getAscent() - sfm.getDescent()) / 2;

        // Species badge — right-aligned, always shown
        String speciesLabel = species.label;
        int spW = sfm.stringWidth(speciesLabel) + pillPad * 2;
        int spX = w - PAD - spW;
        if (!locked) {
            g2.setColor(new Color(species.displayColor.getRed(), species.displayColor.getGreen(),
                    species.displayColor.getBlue(), 160));
        } else {
            g2.setColor(new Color(50, 50, 50));
        }
        g2.fillRoundRect(spX, pillY, spW, pillH, 4, 4);
        g2.setColor(locked ? new Color(75, 75, 75) : Color.WHITE);
        g2.drawString(speciesLabel, spX + pillPad, pillBase);

        if (!locked) {
            // Level pill — dark/black background, white text
            String lvlLabel = combatLevel > 0 ? "Lvl " + combatLevel : "Non-combat";
            int lvlW = sfm.stringWidth(lvlLabel) + pillPad * 2;
            int lvlX = imgX + 2;
            g2.setColor(new Color(12, 12, 12));
            g2.fillRoundRect(lvlX, pillY, lvlW, pillH, 4, 4);
            g2.setColor(Color.WHITE);
            g2.drawString(lvlLabel, lvlX + pillPad, pillBase);

            // Class pill — amber, same style as species
            String clsLabel = combatClass.label;
            int clsW = sfm.stringWidth(clsLabel) + pillPad * 2;
            int clsX = lvlX + lvlW + 4;
            g2.setColor(new Color(160, 110, 30, 180));
            g2.fillRoundRect(clsX, pillY, clsW, pillH, 4, 4);
            g2.setColor(Color.WHITE);
            g2.drawString(clsLabel, clsX + pillPad, pillBase);
        } else {
            // Locked: plain kill count text
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
            boolean primary = !locked && combatClass.isPrimary(i);
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

        // Shimmer overlay for Epic+. Strip = 2×CARD_W so both transparent ends are always
        // off-screen — the peak is fully bright when it crosses the card's right edge.
        if (shimmerTimer != null && shimmerTimer.isRunning()) {
            float cx   = shimmerPhase * (3 * CARD_W) - CARD_W;
            Color mid  = rarest == CreatureRarity.MYTHIC
                    ? new Color(255, 120, 120, 110)
                    : rarest == CreatureRarity.LEGENDARY
                    ? new Color(255, 210, 80,  110)
                    : new Color(210, 120, 240, 100);
            Color none = new Color(mid.getRed(), mid.getGreen(), mid.getBlue(), 0);
            g2.setPaint(new java.awt.LinearGradientPaint(
                    cx - CARD_W, 0, cx + CARD_W, 0,
                    new float[]{0f, 0.5f, 1f},
                    new Color[]{none, mid, none}));
            g2.fillRoundRect(0, 0, CARD_W, CARD_H, 8, 8);
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
