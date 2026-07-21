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
import java.util.List;

/**
 * A compact card representing one NPC+rarity combination in the collection.
 * All captures passed in share the same npcId and rarity.
 * Clicking opens {@link CreatureDetailDialog} with the capture history.
 */
public class CreatureCard extends JPanel {

    private static final int   CARD_HEIGHT = 58;
    private static final Color CARD_BG     = ColorScheme.DARKER_GRAY_COLOR;
    private static final Color CARD_HOVER  = new Color(55, 55, 55);

    private final CreatureRarity rarity;
    private float shimmerPhase = 0f;
    private javax.swing.Timer shimmerTimer;
    private boolean hovered = false;

    private JLabel rarityLabelRef;
    private JLabel statsLabelRef;
    private String rateTooltip;
    private String statsTooltip;

    /**
     * @param captures All captures of this NPC with this specific rarity (homogeneous list).
     * @param collection Full collection for kill-count lookup.
     */
    public CreatureCard(List<CapturedCreature> captures, BestiaryCollection collection) {
        CapturedCreature sample = captures.get(0);
        rarity                  = sample.rarity;
        String npcName          = sample.npcName;
        int npcId               = sample.npcId;
        int combatLevel         = sample.npcCombatLevel;

        setLayout(new BorderLayout(8, 0));
        setBackground(CARD_BG);
        int t = borderThick(rarity);
        setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(t, 4, t, t, rarity.displayColor),
                new EmptyBorder(6 - t, 8, 6 - t, 8 - t)));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, CARD_HEIGHT));
        setPreferredSize(new Dimension(200, CARD_HEIGHT));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Left column: name + combat level
        JPanel leftCol = new JPanel(new GridLayout(2, 1, 0, 2));
        leftCol.setOpaque(false);

        JLabel nameLabel = new JLabel(npcName);
        nameLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        nameLabel.setForeground(Color.WHITE);

        String levelText = combatLevel > 0 ? "Combat lvl " + combatLevel : "Non-combat";
        JLabel levelLabel = new JLabel(levelText);
        levelLabel.setFont(FontManager.getRunescapeSmallFont());
        levelLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        leftCol.add(nameLabel);
        leftCol.add(levelLabel);

        // Right column: rarity badge + count/quality
        JPanel rightCol = new JPanel(new GridLayout(2, 1, 0, 2));
        rightCol.setOpaque(false);

        rarityLabelRef = new JLabel("\u25cf " + rarity.label, SwingConstants.RIGHT);
        rarityLabelRef.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        rarityLabelRef.setForeground(rarity.displayColor);

        int avgQuality = (int) captures.stream()
                .mapToInt(c -> c.quality.overallRating())
                .average()
                .orElse(0);
        int kills = collection.getKillCount(npcName);
        statsLabelRef = new JLabel(captures.size() + " caught  \u00b7  avg " + avgQuality, SwingConstants.RIGHT);
        statsLabelRef.setFont(FontManager.getRunescapeSmallFont());
        statsLabelRef.setForeground(rarity.displayColor);

        int aStr = (int) captures.stream().mapToInt(c -> c.quality.strength).average().orElse(0);
        int aSpd = (int) captures.stream().mapToInt(c -> c.quality.speed).average().orElse(0);
        int aEnd = (int) captures.stream().mapToInt(c -> c.quality.endurance).average().orElse(0);
        int aInt = (int) captures.stream().mapToInt(c -> c.quality.intelligence).average().orElse(0);
        int aStl = (int) captures.stream().mapToInt(c -> c.quality.stealth).average().orElse(0);
        int aVit = (int) captures.stream().mapToInt(c -> c.quality.vitality).average().orElse(0);
        String yourRate = kills > 0
                ? "Your rate: 1 in " + Math.round((double) kills / Math.max(1, captures.size())) + " kills"
                : "No kills recorded yet";
        rateTooltip = String.format(
                "<html><b>%s capture rate</b><br>Base chance: %.1f%% (~1 in %d kills)<br>%s</html>",
                rarity.label, rarity.probability * 100,
                Math.round(1.0 / rarity.probability), yourRate);
        statsTooltip = String.format(
                "<html>%d captures  |  %d total kills<br>Avg stats:  STR:%d  SPD:%d  END:%d  INT:%d  STL:%d  VIT:%d</html>",
                captures.size(), kills, aStr, aSpd, aEnd, aInt, aStl, aVit);
        ToolTipManager.sharedInstance().registerComponent(this);

        rightCol.add(rarityLabelRef);
        rightCol.add(statsLabelRef);

        add(leftCol,  BorderLayout.CENTER);
        add(rightCol, BorderLayout.EAST);


        // Foil shimmer: one sweep per hover for Epic and above
        if (rarity.ordinal() >= CreatureRarity.EPIC.ordinal()) {
            shimmerTimer = new javax.swing.Timer(30, e -> {
                shimmerPhase += 0.022f;
                if (shimmerPhase > 1.05f) {
                    shimmerTimer.stop(); shimmerPhase = 0f;
                    if (hovered) {
                        javax.swing.Timer repeat = new javax.swing.Timer(500, ev -> {
                            if (hovered) { shimmerPhase = 0f; shimmerTimer.restart(); }
                        });
                        repeat.setRepeats(false); repeat.start();
                    }
                }
                repaint();
            });
            shimmerTimer.setRepeats(true);
        }

        // Click -> detail dialog; hover highlight + shimmer
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                AlbumDialog.requestOpenDetail(captures.get(0).npcName);
            }

            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                hovered = true;
                setBackground(CARD_HOVER);
                if (shimmerTimer != null) { shimmerPhase = 0f; shimmerTimer.restart(); }
                repaint();
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                hovered = false;
                setBackground(CARD_BG);
                if (shimmerTimer != null) shimmerTimer.stop();
                repaint();
            }
        });
    }

    @Override
    public String getToolTipText(java.awt.event.MouseEvent event) {
        Component c = SwingUtilities.getDeepestComponentAt(this, event.getX(), event.getY());
        if (c == rarityLabelRef) return rateTooltip;
        if (c == statsLabelRef)  return statsTooltip;
        return null;
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g); // background + children + border first
        if (shimmerTimer == null || !shimmerTimer.isRunning()) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        // Strip = 2×w so both transparent ends are always off-screen.
        float cx = shimmerPhase * (3 * w) - w;

        Color mid = rarity == CreatureRarity.MYTHIC
                ? new Color(255, 120, 120, 110)
                : rarity == CreatureRarity.LEGENDARY
                ? new Color(255, 210, 80,  110)
                : new Color(210, 120, 240, 100);
        Color none = new Color(mid.getRed(), mid.getGreen(), mid.getBlue(), 0);

        LinearGradientPaint lgp = new LinearGradientPaint(
                cx - w, 0, cx + w, 0,
                new float[]{0f, 0.5f, 1f},
                new Color[]{none, mid, none});
        g2.setPaint(lgp);
        g2.fillRect(0, 0, w, h);
        g2.dispose();
    }

    /** EPIC and above get a 2px frame; Common–Rare get 1px. */
    private static int borderThick(CreatureRarity r) {
        return r.ordinal() >= CreatureRarity.EPIC.ordinal() ? 2 : 1;
    }
}
