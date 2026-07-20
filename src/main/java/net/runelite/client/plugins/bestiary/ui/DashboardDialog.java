package net.runelite.client.plugins.bestiary.ui;

import net.runelite.client.plugins.bestiary.model.Achievement;
import net.runelite.client.plugins.bestiary.model.BestiaryCollection;
import net.runelite.client.plugins.bestiary.model.CapturedCreature;
import net.runelite.client.plugins.bestiary.model.CreatureRarity;
import net.runelite.client.plugins.bestiary.model.DifficultyTier;
import net.runelite.client.plugins.bestiary.model.MonsterRoster;
import net.runelite.client.plugins.bestiary.service.BestiaryDataService;
import net.runelite.client.plugins.bestiary.service.ProgressionService;
import net.runelite.client.plugins.bestiary.util.XpTable;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.geom.Arc2D;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class DashboardDialog extends JDialog {

    public enum DashView {
        PROGRESSION("Progression"),
        KILLS      ("Kills"),
        SPECIES    ("Species"),
        CAUGHT     ("Caught");

        public final String label;
        DashView(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    private static final Color ORANGE = new Color(255, 165, 0);
    private static final Color GOLD   = new Color(255, 195, 40);
    private static final Color BG     = new Color(18,  18,  18);
    private static final Color SECT   = new Color(28,  28,  28);
    private static final Color CARD   = new Color(38,  38,  38);
    private static final Color DIM    = new Color(65,  65,  65);
    private static final Color TEXT   = new Color(220, 220, 220);
    private static final Color MUTED  = new Color(115, 115, 115);
    private static final NumberFormat FMT = NumberFormat.getNumberInstance(Locale.UK);

    private static DashboardDialog current;

    private final BestiaryDataService  dataService;
    private final ProgressionService   progressionService;
    private final JPanel               contentArea;
    private final CardLayout           cardLayout;

    // -------------------------------------------------------------------------
    // Public entry point
    // -------------------------------------------------------------------------

    public static void open(Window owner, BestiaryDataService ds, ProgressionService ps, DashView view) {
        if (current != null && current.isShowing()) current.dispose();
        current = new DashboardDialog(owner, ds, ps, view);
    }

    private DashboardDialog(Window owner, BestiaryDataService ds, ProgressionService ps, DashView initial) {
        super(owner, "Bestiary Dashboard", ModalityType.MODELESS);
        this.dataService        = ds;
        this.progressionService = ps;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(true);

        // Top bar
        JPanel topBar = new JPanel(new BorderLayout(10, 0));
        topBar.setBackground(SECT);
        topBar.setBorder(new EmptyBorder(10, 14, 10, 14));

        JLabel title = new JLabel("BESTIARY DASHBOARD");
        title.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        title.setForeground(ORANGE);

        JComboBox<DashView> viewBox = new JComboBox<>(DashView.values());
        viewBox.setBackground(CARD);
        viewBox.setForeground(Color.WHITE);
        viewBox.setFont(FontManager.getRunescapeSmallFont());
        viewBox.setSelectedItem(initial);

        topBar.add(title,   BorderLayout.WEST);
        topBar.add(viewBox, BorderLayout.EAST);

        // Content
        cardLayout  = new CardLayout();
        contentArea = new JPanel(cardLayout);
        contentArea.setBackground(BG);

        for (DashView v : DashView.values()) {
            contentArea.add(scroll(buildView(v)), v.name());
        }
        cardLayout.show(contentArea, initial.name());

        viewBox.addActionListener(e -> {
            DashView sel = (DashView) viewBox.getSelectedItem();
            if (sel != null) cardLayout.show(contentArea, sel.name());
        });

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG);
        root.add(topBar,      BorderLayout.NORTH);
        root.add(contentArea, BorderLayout.CENTER);

        setContentPane(root);
        setSize(520, 720);
        setLocationRelativeTo(owner);
        setVisible(true);
        toFront();
    }

    // =========================================================================
    // PROGRESSION VIEW — the home dashboard
    // =========================================================================

    private JPanel buildProgressionView() {
        JPanel root = col();

        BestiaryCollection col    = dataService.getCollection();
        int    level              = progressionService.getLevel();
        long   totalXp            = progressionService.getTotalXp();
        long   levelStart         = XpTable.xpForLevel(level);
        long   levelEnd           = XpTable.xpForLevel(Math.min(level + 1, 100));
        long   xpInLevel          = totalXp - levelStart;
        long   xpSpan             = Math.max(1, levelEnd - levelStart);
        long   xpLeft             = progressionService.getXpToNextLevel();
        Set<Achievement> unlocked = progressionService.getState().unlockedAchievements;

        root.add(buildXpHero(level, totalXp, xpInLevel, xpSpan, xpLeft));
        root.add(gap(10));
        root.add(sectionHeader("OVERVIEW"));
        root.add(buildOverviewGrid(col));
        root.add(gap(10));
        root.add(sectionHeader("RARITY BREAKDOWN"));
        root.add(buildRarityBars(col));
        root.add(gap(10));
        root.add(sectionHeader("ACHIEVEMENTS  —  " + unlocked.size() + " / " + Achievement.values().length));
        root.add(buildAchievementGrid(unlocked));
        root.add(gap(16));
        return root;
    }

    private JPanel buildXpHero(int level, long totalXp, long xpInLevel, long xpSpan, long xpLeft) {
        JPanel hero = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = g2(g);
                int w = getWidth(), h = getHeight();

                // Background gradient
                g2.setPaint(new GradientPaint(0, 0, new Color(32, 32, 32), 0, h, BG));
                g2.fillRect(0, 0, w, h);

                // Arc dimensions
                int arcD = 170, arcX = (w - arcD) / 2, arcY = 18;
                float progress = xpSpan > 0 ? (float) xpInLevel / xpSpan : 0f;

                // Track
                g2.setStroke(new BasicStroke(13f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(new Color(48, 48, 48));
                g2.draw(new Arc2D.Double(arcX, arcY, arcD, arcD, -220, 260, Arc2D.OPEN));

                // Glow
                if (progress > 0.01f) {
                    g2.setStroke(new BasicStroke(22f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.setColor(new Color(255, 140, 0, 35));
                    g2.draw(new Arc2D.Double(arcX, arcY, arcD, arcD, -220, (int)(-260 * progress), Arc2D.OPEN));
                }

                // Progress
                if (progress > 0.01f) {
                    g2.setStroke(new BasicStroke(13f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.setPaint(new GradientPaint(arcX, arcY, new Color(255, 100, 0),
                            arcX + arcD, arcY + arcD, GOLD));
                    g2.draw(new Arc2D.Double(arcX, arcY, arcD, arcD, -220, (int)(-260 * progress), Arc2D.OPEN));
                }

                // Level number (centre of arc)
                int cx = arcX + arcD / 2, cy = arcY + arcD / 2 + 6;
                Font bigFont = FontManager.getRunescapeBoldFont().deriveFont(52f);
                g2.setFont(bigFont);
                FontMetrics bfm = g2.getFontMetrics();
                String lvl = String.valueOf(level);
                int lx = cx - bfm.stringWidth(lvl) / 2;
                int ly = cy + bfm.getAscent() / 2 - 6;
                g2.setColor(new Color(0, 0, 0, 130));
                g2.drawString(lvl, lx + 2, ly + 2);
                g2.setColor(level >= 99 ? GOLD : Color.WHITE);
                g2.drawString(lvl, lx, ly);

                // "LEVEL" caption
                g2.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
                FontMetrics sfm = g2.getFontMetrics();
                String cap = "CAPTURE LEVEL";
                g2.setColor(MUTED);
                g2.drawString(cap, cx - sfm.stringWidth(cap) / 2, arcY + arcD / 2 - 34);

                // XP bar
                int barY = arcY + arcD + 14, barH = 10, padX = 28, barW = w - padX * 2;
                g2.setColor(new Color(40, 40, 40));
                g2.fillRoundRect(padX, barY, barW, barH, 6, 6);
                int fill = (int)(barW * progress);
                if (fill > 2) {
                    g2.setPaint(new GradientPaint(padX, barY, new Color(255, 110, 0), padX + fill, barY, GOLD));
                    g2.fillRoundRect(padX, barY, fill, barH, 6, 6);
                }

                // XP labels
                g2.setFont(FontManager.getRunescapeSmallFont());
                sfm = g2.getFontMetrics();
                int labelY = barY + barH + 14;
                g2.setColor(TEXT);
                g2.drawString(FMT.format(totalXp) + " XP total", padX, labelY);
                String nextStr = level >= 100 ? "Max level!" : FMT.format(xpLeft) + " XP to level " + (level + 1);
                g2.setColor(MUTED);
                g2.drawString(nextStr, padX + barW - sfm.stringWidth(nextStr), labelY);

                g2.dispose();
            }
        };
        hero.setOpaque(false);
        hero.setPreferredSize(new Dimension(0, 260));
        hero.setMaximumSize(new Dimension(Integer.MAX_VALUE, 260));
        return hero;
    }

    private JPanel buildOverviewGrid(BestiaryCollection col) {
        JPanel grid = new JPanel(new GridLayout(1, 4, 6, 0));
        grid.setOpaque(false);
        grid.setBorder(new EmptyBorder(0, 12, 0, 12));
        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));

        int kills = col.totalKills(), caps = col.totalCaptures();
        String ratio = caps > 0 ? String.format("%.1f:1", (double) kills / caps) : "—";

        grid.add(miniCard("Species",  String.valueOf(col.uniqueSpeciesCount())));
        grid.add(miniCard("Captured", FMT.format(caps)));
        grid.add(miniCard("Kills",    FMT.format(kills)));
        grid.add(miniCard("K : C",    ratio));
        return grid;
    }

    private JPanel buildRarityBars(BestiaryCollection col) {
        JPanel panel = col();
        panel.setBorder(new EmptyBorder(0, 12, 0, 12));

        CreatureRarity[] order = {
            CreatureRarity.MYTHIC, CreatureRarity.LEGENDARY, CreatureRarity.EPIC,
            CreatureRarity.RARE,   CreatureRarity.UNCOMMON,  CreatureRarity.COMMON
        };
        Map<CreatureRarity, Long> counts = col.creatures.stream()
                .collect(Collectors.groupingBy(c -> c.rarity, Collectors.counting()));
        int total    = col.totalCaptures();
        int maxCount = counts.values().stream().mapToInt(Long::intValue).max().orElse(1);

        for (CreatureRarity r : order) {
            long cnt = counts.getOrDefault(r, 0L);
            String pct = total > 0 ? String.format("%.0f%%", cnt * 100.0 / total) : "0%";
            panel.add(barRow(r.label, r.displayColor, (int) cnt, maxCount, cnt + "  " + pct));
            panel.add(gap(4));
        }
        return panel;
    }

    private JPanel buildAchievementGrid(Set<Achievement> unlocked) {
        JPanel grid = new JPanel(new GridLayout(0, 2, 5, 5));
        grid.setOpaque(false);
        grid.setBorder(new EmptyBorder(0, 12, 0, 12));

        for (Achievement a : Achievement.values()) {
            boolean on = unlocked.contains(a);
            JPanel badge = new JPanel(new BorderLayout(6, 0));
            badge.setBackground(on ? new Color(36, 36, 36) : new Color(22, 22, 22));
            badge.setBorder(new EmptyBorder(6, 8, 6, 8));

            JLabel icon = new JLabel(on ? "✓" : "○");
            icon.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
            icon.setForeground(on ? a.chatColor : DIM);

            JPanel txt = new JPanel(new GridLayout(2, 1, 0, 1));
            txt.setOpaque(false);

            JLabel tl = new JLabel(a.title);
            tl.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
            tl.setForeground(on ? a.chatColor : DIM);

            JLabel dl = new JLabel(a.description);
            dl.setFont(FontManager.getRunescapeSmallFont().deriveFont(9f));
            dl.setForeground(on ? MUTED : new Color(42, 42, 42));

            txt.add(tl); txt.add(dl);
            badge.add(icon, BorderLayout.WEST);
            badge.add(txt,  BorderLayout.CENTER);
            grid.add(badge);
        }
        return grid;
    }

    // =========================================================================
    // KILLS VIEW
    // =========================================================================

    private JPanel buildKillsView() {
        JPanel root = col();
        BestiaryCollection col = dataService.getCollection();

        root.add(heroStat(FMT.format(col.totalKills()), "TOTAL KILLS", ORANGE));
        root.add(gap(10));
        root.add(sectionHeader("TOP KILLS BY SPECIES"));

        List<Map.Entry<String, Integer>> sorted = col.killCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(15)
                .collect(Collectors.toList());

        JPanel bars = col();
        bars.setBorder(new EmptyBorder(0, 12, 0, 12));
        if (sorted.isEmpty()) {
            bars.add(emptyNote("No kills recorded yet."));
        } else {
            int max = sorted.get(0).getValue();
            for (Map.Entry<String, Integer> e : sorted) {
                bars.add(barRow(e.getKey(), ORANGE, e.getValue(), max, FMT.format(e.getValue())));
                bars.add(gap(4));
            }
        }
        root.add(bars);
        root.add(gap(10));
        root.add(sectionHeader("KILLS PER CAPTURE  (best → worst)"));

        List<String> capNames = col.captureCountByNpc.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .sorted(Comparator.comparingDouble(e ->
                        (double) col.getKillCount(e.getKey()) / Math.max(1, e.getValue())))
                .map(Map.Entry::getKey)
                .limit(12)
                .collect(Collectors.toList());

        JPanel ratioPanel = col();
        ratioPanel.setBorder(new EmptyBorder(0, 12, 0, 12));
        if (capNames.isEmpty()) {
            ratioPanel.add(emptyNote("No captures yet."));
        } else {
            int maxR = capNames.stream()
                    .mapToInt(n -> col.getKillCount(n) / Math.max(1, col.getCaptureCount(n)))
                    .max().orElse(1);
            for (String name : capNames) {
                int k = col.getKillCount(name), c = Math.max(1, col.getCaptureCount(name));
                int r = k / c;
                ratioPanel.add(barRow(name, new Color(90, 170, 110), r, maxR, "1 in " + r));
                ratioPanel.add(gap(4));
            }
        }
        root.add(ratioPanel);
        root.add(gap(16));
        return root;
    }

    // =========================================================================
    // SPECIES VIEW
    // =========================================================================

    private JPanel buildSpeciesView() {
        JPanel root = col();
        BestiaryCollection col    = dataService.getCollection();
        List<String> roster       = MonsterRoster.buildFullRoster(col.killCounts);
        int total                 = roster.size();
        int captured              = (int) col.uniqueSpeciesCount();
        float pct                 = total > 0 ? (float) captured / total : 0f;

        root.add(buildCompletionHero(captured, total, pct));
        root.add(gap(10));
        root.add(sectionHeader("COMPLETION BY DIFFICULTY"));

        // Count roster entries and captured species per difficulty tier
        Map<DifficultyTier, Integer> rosterByDiff = new EnumMap<>(DifficultyTier.class);
        for (DifficultyTier t : DifficultyTier.values()) rosterByDiff.put(t, 0);
        for (String name : roster) {
            int cb = col.creatures.stream().filter(c -> c.npcName.equals(name))
                    .findFirst().map(c -> c.npcCombatLevel).orElse(0);
            rosterByDiff.merge(MonsterRoster.getDifficulty(name, cb), 1, Integer::sum);
        }

        Map<DifficultyTier, Set<String>> capByDiff = new EnumMap<>(DifficultyTier.class);
        for (DifficultyTier t : DifficultyTier.values()) capByDiff.put(t, new HashSet<>());
        for (CapturedCreature c : col.creatures) {
            capByDiff.get(MonsterRoster.getDifficulty(c.npcName, c.npcCombatLevel)).add(c.npcName);
        }

        JPanel diffPanel = col();
        diffPanel.setBorder(new EmptyBorder(0, 12, 0, 12));
        for (DifficultyTier tier : DifficultyTier.values()) {
            int cap = capByDiff.get(tier).size(), ttl = rosterByDiff.getOrDefault(tier, 0);
            diffPanel.add(barRow(tier.label, tier.displayColor, cap, Math.max(ttl, 1), cap + " / " + ttl));
            diffPanel.add(gap(4));
        }
        root.add(diffPanel);
        root.add(gap(10));

        // Captured species list
        root.add(sectionHeader("CAPTURED SPECIES  (" + captured + ")"));
        JPanel specList = col();
        specList.setBorder(new EmptyBorder(0, 12, 0, 12));

        if (col.creatures.isEmpty()) {
            specList.add(emptyNote("No species captured yet."));
        } else {
            col.creatures.stream()
                    .collect(Collectors.groupingBy(c -> c.npcName))
                    .entrySet().stream()
                    .sorted(Comparator.comparing(e -> e.getKey().toLowerCase()))
                    .forEach(e -> {
                        CreatureRarity rarest = e.getValue().stream()
                                .map(c -> c.rarity).max(Comparator.comparingInt(Enum::ordinal))
                                .orElse(CreatureRarity.COMMON);
                        JPanel row = new JPanel(new BorderLayout(6, 0));
                        row.setBackground(CARD);
                        row.setBorder(BorderFactory.createCompoundBorder(
                                new MatteBorder(0, 3, 0, 0, rarest.displayColor),
                                new EmptyBorder(4, 8, 4, 8)));
                        JLabel nl = new JLabel("● " + e.getKey());
                        nl.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
                        nl.setForeground(rarest.displayColor);
                        JLabel cl = new JLabel(e.getValue().size() + " caught", SwingConstants.RIGHT);
                        cl.setFont(FontManager.getRunescapeSmallFont());
                        cl.setForeground(MUTED);
                        row.add(nl, BorderLayout.WEST);
                        row.add(cl, BorderLayout.EAST);
                        specList.add(row);
                        specList.add(gap(3));
                    });
        }
        root.add(specList);
        root.add(gap(16));
        return root;
    }

    // =========================================================================
    // CAUGHT VIEW
    // =========================================================================

    private JPanel buildCaughtView() {
        JPanel root = col();
        BestiaryCollection col = dataService.getCollection();
        int total = col.totalCaptures();

        root.add(heroStat(FMT.format(total), "TOTAL CAPTURES", ORANGE));
        root.add(gap(10));
        root.add(sectionHeader("BY RARITY"));
        root.add(buildRarityBars(col));
        root.add(gap(10));
        root.add(sectionHeader("TOP 10 BY QUALITY"));

        JPanel topList = col();
        topList.setBorder(new EmptyBorder(0, 12, 0, 12));

        List<CapturedCreature> best = col.creatures.stream()
                .sorted(Comparator.comparingInt((CapturedCreature c) -> c.quality.overallRating()).reversed())
                .limit(10)
                .collect(Collectors.toList());

        if (best.isEmpty()) {
            topList.add(emptyNote("No captures yet."));
        } else {
            for (int i = 0; i < best.size(); i++) {
                CapturedCreature c = best.get(i);
                JPanel row = new JPanel(new BorderLayout(8, 0));
                row.setBackground(CARD);
                row.setBorder(BorderFactory.createCompoundBorder(
                        new MatteBorder(0, 3, 0, 0, c.rarity.displayColor),
                        new EmptyBorder(6, 8, 6, 8)));

                JPanel left = new JPanel(new GridLayout(2, 1, 0, 2));
                left.setOpaque(false);
                JLabel nl = new JLabel((i + 1) + ".  " + c.npcName);
                nl.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
                nl.setForeground(c.rarity.displayColor);
                JLabel rl = new JLabel(c.rarity.label
                        + (c.nickname != null && !c.nickname.isEmpty() ? "  \"" + c.nickname + "\"" : ""));
                rl.setFont(FontManager.getRunescapeSmallFont());
                rl.setForeground(MUTED);
                left.add(nl); left.add(rl);

                JLabel ql = new JLabel("Q:" + c.quality.overallRating());
                ql.setFont(FontManager.getRunescapeFont().deriveFont(Font.BOLD));
                ql.setForeground(qualColor(c.quality.overallRating()));

                row.add(left, BorderLayout.CENTER);
                row.add(ql,   BorderLayout.EAST);
                topList.add(row);
                topList.add(gap(3));
            }
        }
        root.add(topList);
        root.add(gap(10));
        root.add(sectionHeader("AVERAGE QUALITY BY RARITY"));

        JPanel avgPanel = col();
        avgPanel.setBorder(new EmptyBorder(0, 12, 0, 12));
        Map<CreatureRarity, Double> avg = col.creatures.stream().collect(
                Collectors.groupingBy(c -> c.rarity,
                        Collectors.averagingInt(c -> c.quality.overallRating())));

        boolean any = false;
        for (CreatureRarity r : new CreatureRarity[]{
                CreatureRarity.MYTHIC, CreatureRarity.LEGENDARY, CreatureRarity.EPIC,
                CreatureRarity.RARE,   CreatureRarity.UNCOMMON,  CreatureRarity.COMMON}) {
            if (!avg.containsKey(r)) continue;
            int a = (int) Math.round(avg.get(r));
            avgPanel.add(barRow(r.label, r.displayColor, a, 100, "avg " + a));
            avgPanel.add(gap(4));
            any = true;
        }
        if (!any) avgPanel.add(emptyNote("No captures yet."));
        root.add(avgPanel);
        root.add(gap(16));
        return root;
    }

    // =========================================================================
    // Custom-painted hero components
    // =========================================================================

    private JPanel heroStat(String value, String caption, Color accent) {
        JPanel p = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = g2(g);
                int w = getWidth(), h = getHeight();
                g2.setPaint(new GradientPaint(0, 0, new Color(34, 34, 34), 0, h, BG));
                g2.fillRect(0, 0, w, h);
                g2.setColor(accent);
                g2.fillRect(0, 0, 4, h);

                g2.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
                FontMetrics sfm = g2.getFontMetrics();
                g2.setColor(MUTED);
                g2.drawString(caption, 20, 22);

                g2.setFont(FontManager.getRunescapeBoldFont().deriveFont(36f));
                FontMetrics bfm = g2.getFontMetrics();
                int vy = 22 + sfm.getHeight() + bfm.getAscent();
                g2.setColor(new Color(0, 0, 0, 110));
                g2.drawString(value, 21, vy + 2);
                g2.setColor(accent);
                g2.drawString(value, 20, vy);
                g2.dispose();
            }
        };
        p.setOpaque(false);
        p.setPreferredSize(new Dimension(0, 90));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        return p;
    }

    private JPanel buildCompletionHero(int captured, int total, float pct) {
        JPanel p = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = g2(g);
                int w = getWidth(), h = getHeight();
                g2.setPaint(new GradientPaint(0, 0, new Color(30, 30, 30), 0, h, BG));
                g2.fillRect(0, 0, w, h);

                int arcD = 150, arcX = (w - arcD) / 2, arcY = 16;

                g2.setStroke(new BasicStroke(14f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(new Color(46, 46, 46));
                g2.draw(new Arc2D.Double(arcX, arcY, arcD, arcD, -220, 260, Arc2D.OPEN));

                if (pct > 0.001f) {
                    g2.setStroke(new BasicStroke(22f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.setColor(new Color(70, 185, 70, 35));
                    g2.draw(new Arc2D.Double(arcX, arcY, arcD, arcD, -220, (int)(-260 * pct), Arc2D.OPEN));

                    g2.setStroke(new BasicStroke(14f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.setPaint(new GradientPaint(arcX, arcY, new Color(50, 160, 50),
                            arcX + arcD, arcY + arcD, new Color(90, 220, 90)));
                    g2.draw(new Arc2D.Double(arcX, arcY, arcD, arcD, -220, (int)(-260 * pct), Arc2D.OPEN));
                }

                int cx = arcX + arcD / 2, cy = arcY + arcD / 2;
                String pctStr = String.format("%.1f%%", pct * 100f);
                g2.setFont(FontManager.getRunescapeBoldFont().deriveFont(28f));
                FontMetrics fm = g2.getFontMetrics();
                g2.setColor(new Color(90, 220, 90));
                g2.drawString(pctStr, cx - fm.stringWidth(pctStr) / 2, cy + fm.getAscent() / 2 - 8);

                String sub = captured + " / " + total + " species";
                g2.setFont(FontManager.getRunescapeSmallFont());
                fm = g2.getFontMetrics();
                g2.setColor(MUTED);
                g2.drawString(sub, cx - fm.stringWidth(sub) / 2, cy + 16);

                g2.dispose();
            }
        };
        p.setOpaque(false);
        p.setPreferredSize(new Dimension(0, 210));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 210));
        return p;
    }

    // =========================================================================
    // Shared bar row — custom-painted label + gradient fill + right value
    // =========================================================================

    private JPanel barRow(String label, Color color, int value, int max, String right) {
        return new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = g2(g);
                int w = getWidth(), h = getHeight();

                Font sf = FontManager.getRunescapeSmallFont();
                g2.setFont(sf);
                FontMetrics fm = g2.getFontMetrics();

                int labelW  = 116;
                int rightW  = fm.stringWidth(right) + 4;
                int barX    = labelW + 6, barW = w - labelW - rightW - 14;
                int barH    = 8, barY = (h - barH) / 2 + 1;
                int base    = (h + fm.getAscent() - fm.getDescent()) / 2;

                // Dot + label
                g2.setColor(color);
                g2.drawString("● ", 0, base);
                int dotW = fm.stringWidth("● ");
                String name = label;
                while (name.length() > 0 && fm.stringWidth(name) > labelW - dotW - 4)
                    name = name.substring(0, name.length() - 1);
                if (name.length() < label.length()) name += "…";
                g2.setColor(TEXT);
                g2.drawString(name, dotW, base);

                // Track
                g2.setColor(new Color(42, 42, 42));
                g2.fillRoundRect(barX, barY, barW, barH, 4, 4);

                // Fill
                if (max > 0 && value > 0) {
                    int fill = Math.max(4, (int)((long) barW * value / max));
                    g2.setPaint(new GradientPaint(barX, 0,
                            new Color(color.getRed()/2, color.getGreen()/2, color.getBlue()/2),
                            barX + fill, 0, color));
                    g2.fillRoundRect(barX, barY, fill, barH, 4, 4);
                }

                // Right text
                g2.setColor(MUTED);
                g2.setFont(sf);
                g2.drawString(right, w - fm.stringWidth(right), base);
                g2.dispose();
            }
        };
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private JPanel buildView(DashView v) {
        switch (v) {
            case KILLS:   return buildKillsView();
            case SPECIES: return buildSpeciesView();
            case CAUGHT:  return buildCaughtView();
            default:      return buildProgressionView();
        }
    }

    private JScrollPane scroll(JPanel content) {
        // Let the content panel report its preferred width as viewport width
        JPanel wrapper = new JPanel() {
            @Override public Dimension getPreferredSize() {
                Dimension d = content.getPreferredSize();
                if (getParent() != null) d.width = getParent().getWidth();
                return d;
            }
        };
        wrapper.setLayout(new BorderLayout());
        wrapper.setBackground(BG);
        wrapper.add(content, BorderLayout.NORTH);

        JScrollPane sp = new JScrollPane(wrapper);
        sp.setBorder(null);
        sp.getViewport().setBackground(BG);
        sp.getVerticalScrollBar().setUnitIncrement(18);
        sp.getVerticalScrollBar().setPreferredSize(new Dimension(6, 0));
        sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        return sp;
    }

    private JPanel sectionHeader(String text) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(SECT);
        p.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 3, 0, 0, ORANGE),
                new EmptyBorder(6, 10, 6, 10)));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        JLabel l = new JLabel(text);
        l.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        l.setForeground(ORANGE);
        p.add(l, BorderLayout.WEST);
        return p;
    }

    private JPanel miniCard(String label, String value) {
        JPanel card = new JPanel(new GridLayout(2, 1, 0, 2));
        card.setBackground(CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 2, 0, 0, ORANGE),
                new EmptyBorder(5, 7, 5, 7)));
        JLabel vl = new JLabel(value, SwingConstants.CENTER);
        vl.setFont(FontManager.getRunescapeFont().deriveFont(Font.BOLD));
        vl.setForeground(ORANGE);
        JLabel ll = new JLabel(label, SwingConstants.CENTER);
        ll.setFont(FontManager.getRunescapeSmallFont());
        ll.setForeground(MUTED);
        card.add(vl); card.add(ll);
        return card;
    }

    private JPanel col() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(BG);
        return p;
    }

    private Component gap(int h) { return Box.createVerticalStrut(h); }

    private JPanel emptyNote(String text) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(8, 16, 8, 16));
        JLabel l = new JLabel(text);
        l.setFont(FontManager.getRunescapeSmallFont());
        l.setForeground(DIM);
        p.add(l);
        return p;
    }

    private static Color qualColor(int q) {
        return q >= 80 ? new Color(80, 220, 80) : q >= 50 ? new Color(220, 220, 80) : new Color(155, 155, 155);
    }

    private static Graphics2D g2(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);
        return g2;
    }
}
