package com.bestiary.ui;

import com.bestiary.model.Achievement;
import com.bestiary.model.BestiaryCollection;
import com.bestiary.model.CapturedCreature;
import com.bestiary.model.CreatureRarity;
import com.bestiary.model.CreatureSpecies;
import com.bestiary.model.DifficultyTier;
import com.bestiary.model.MonsterRoster;
import com.bestiary.model.ShopUpgrade;
import com.bestiary.service.BestiaryDataService;
import com.bestiary.service.ProgressionService;
import com.bestiary.util.XpTable;
import net.runelite.client.ui.FontManager;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.geom.Arc2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class DashboardDialog extends JDialog {

    public enum DashView {
        PROGRESSION("Progression"),
        ECONOMY    ("Economy"),
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
    private DashView                   activeView;

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
        this.activeView         = initial;

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

        JButton copyBtn = topBarBtn("Copy", "Copy as image to clipboard");
        JButton saveBtn = topBarBtn("Save", "Save as PNG");

        JPopupMenu copyMenu = new JPopupMenu();
        JMenuItem copyThis = new JMenuItem("Just this view");
        JMenuItem copyAll  = new JMenuItem("All views");
        copyThis.addActionListener(e -> { exportView(copyBtn); flashMenuItem(copyThis, "✓ Copied!"); });
        copyAll.addActionListener(e  -> { copyImageToClipboard(renderAllCard(dataService, progressionService)); flashButton(copyBtn, "✓ Copied!"); flashMenuItem(copyAll, "✓ Copied!"); });
        copyMenu.add(copyThis);
        copyMenu.add(copyAll);
        copyBtn.addActionListener(e -> copyMenu.show(copyBtn, 0, copyBtn.getHeight()));

        JPopupMenu saveMenu = new JPopupMenu();
        JMenuItem saveThis = new JMenuItem("Just this view");
        JMenuItem saveAll  = new JMenuItem("All views");
        saveThis.addActionListener(e -> { exportSingle(saveBtn); flashMenuItem(saveThis, "✓ Saved!"); });
        saveAll.addActionListener(e  -> { exportAll(saveBtn); flashMenuItem(saveAll, "✓ Saved!"); });
        saveMenu.add(saveThis);
        saveMenu.add(saveAll);
        saveBtn.addActionListener(e -> saveMenu.show(saveBtn, 0, saveBtn.getHeight()));

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setOpaque(false);
        right.add(copyBtn);
        right.add(saveBtn);
        right.add(viewBox);

        topBar.add(title, BorderLayout.WEST);
        topBar.add(right, BorderLayout.EAST);

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
            if (sel != null) {
                activeView = sel;
                cardLayout.show(contentArea, sel.name());
            }
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
        long   levelEnd           = XpTable.xpForLevel(Math.min(level + 1, XpTable.MAX_VIRTUAL_LEVEL));
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
                int barY = arcY + arcD + 24, barH = 10, padX = 28, barW = w - padX * 2;
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
        JPanel grid = new JPanel(new GridLayout(1, 5, 6, 0));
        grid.setOpaque(false);
        grid.setBorder(new EmptyBorder(0, 12, 0, 12));
        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));

        // "Caught" = lifetime captures (your hunting); "Cards" = currently held (album contents).
        // K:C uses lifetime so transfers/discards and traded-in cards don't distort it.
        long caught = col.lifetimeCaptures;
        int  held   = col.totalCaptures();
        int  kills  = col.totalKills();
        String ratio = caught > 0 ? String.format("%.1f:1", (double) kills / caught) : "—";

        grid.add(miniCard("Species", String.valueOf(col.uniqueSpeciesCount())));
        grid.add(miniCard("Caught",  FMT.format(caught)));
        grid.add(miniCard("Cards",   FMT.format(held)));
        grid.add(miniCard("Kills",   FMT.format(kills)));
        grid.add(miniCard("K : C",   ratio, true));
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
            panel.add(barRow(r.label, r.displayColor, (int) cnt, maxCount, FMT.format(cnt), pct));
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
    // ECONOMY VIEW — credits earned/spent + reroll activity + owned unlocks
    // =========================================================================

    private JPanel buildEconomyView() {
        JPanel root = col();
        BestiaryCollection col = dataService.getCollection();

        root.add(heroStat(FMT.format(col.credits), "CREDIT BALANCE", GOLD));
        root.add(gap(10));

        root.add(sectionHeader("CREDITS"));
        JPanel cgrid = new JPanel(new GridLayout(1, 3, 6, 0));
        cgrid.setOpaque(false);
        cgrid.setBorder(new EmptyBorder(0, 12, 0, 12));
        cgrid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        cgrid.add(miniCard("Earned",  FMT.format(col.lifetimeCreditsEarned)));
        cgrid.add(miniCard("Spent",   FMT.format(col.lifetimeCreditsSpent)));
        cgrid.add(miniCard("Balance", FMT.format(col.credits), true));
        root.add(cgrid);
        root.add(gap(10));

        root.add(sectionHeader("REROLLS"));
        long cardsRerolled = col.creatures.stream().filter(c -> c.rerollCount() > 0).count();
        JPanel rgrid = new JPanel(new GridLayout(1, 3, 6, 0));
        rgrid.setOpaque(false);
        rgrid.setBorder(new EmptyBorder(0, 12, 0, 12));
        rgrid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        rgrid.add(miniCard("Rerolls",  FMT.format(col.totalRerolls())));
        rgrid.add(miniCard("Cards",    FMT.format(cardsRerolled)));
        rgrid.add(miniCard("Rank-ups / Shiny+",
                countRankUps(col) + " / " + countShinyGained(col), true));
        root.add(rgrid);
        root.add(gap(10));

        root.add(sectionHeader("SHOP UPGRADES"));
        JPanel bars = col();
        bars.setBorder(new EmptyBorder(0, 12, 4, 12));
        for (ShopUpgrade u : ShopUpgrade.values()) {
            bars.add(upgradePipRow(u, dataService.getUpgradeTier(u)));
        }
        root.add(bars);
        root.add(gap(16));
        return root;
    }

    /** A "Upgrade name .... ●●●○○" row: filled gold pips for owned tiers, dark for the rest. */
    private JPanel upgradePipRow(ShopUpgrade u, int owned) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(2, 0, 2, 0));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));

        JLabel name = new JLabel(u.title);
        name.setFont(FontManager.getRunescapeSmallFont());
        name.setForeground(TEXT);

        JPanel pips = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        pips.setOpaque(false);
        for (int i = 0; i < u.maxTier; i++) pips.add(new DashPip(i < owned));

        row.add(name, BorderLayout.WEST);
        row.add(pips, BorderLayout.EAST);
        return row;
    }

    /** Small round tier indicator for the Economy view's shop-upgrade rows. */
    private static final class DashPip extends JComponent {
        private final boolean on;
        DashPip(boolean on) { this.on = on; setPreferredSize(new Dimension(11, 11)); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(on ? GOLD : new Color(70, 70, 70));
            g2.fillOval(0, 0, 10, 10);
            g2.dispose();
        }
    }

    /** Number of cards that are shiny now but were non-shiny in an earlier (pre-reroll) state. */
    private static int countShinyGained(BestiaryCollection col) {
        int n = 0;
        for (CapturedCreature c : col.creatures) {
            if (!c.isShiny()) continue;
            for (CapturedCreature.RerollState s : c.rerollHistory) {
                if (!s.shiny) { n++; break; }
            }
        }
        return n;
    }

    /** Total reroll operations that bumped a card up a rarity, across the collection. */
    private static int countRankUps(BestiaryCollection col) {
        int n = 0;
        for (CapturedCreature c : col.creatures) {
            for (CapturedCreature.RerollState s : c.rerollHistory) {
                if (s.rarity != null && s.rarity.ordinal() < c.rarity.ordinal()) n++;
            }
        }
        return n;
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

        root.add(heroStat(String.format("%.1f%%", pct * 100f) + "  (" + captured + " / " + total + ")",
                "DEX COMPLETION", new Color(80, 200, 80)));
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
            diffPanel.add(barRow(tier.label, tier.displayColor, cap, Math.max(ttl, 1), cap + "/" + ttl, ""));
            diffPanel.add(gap(4));
        }
        root.add(diffPanel);
        root.add(gap(10));
        root.add(sectionHeader("COMPLETION BY CREATURE TYPE"));

        Map<CreatureSpecies, Integer> rosterBySpecies = new EnumMap<>(CreatureSpecies.class);
        for (CreatureSpecies sp : CreatureSpecies.values()) rosterBySpecies.put(sp, 0);
        for (String name : roster) {
            int cb = col.creatures.stream().filter(c -> c.npcName.equals(name))
                    .findFirst().map(c -> c.npcCombatLevel).orElse(0);
            rosterBySpecies.merge(MonsterRoster.getSpecies(name, cb), 1, Integer::sum);
        }

        Map<CreatureSpecies, Set<String>> capBySpecies = new EnumMap<>(CreatureSpecies.class);
        for (CreatureSpecies sp : CreatureSpecies.values()) capBySpecies.put(sp, new HashSet<>());
        for (CapturedCreature c : col.creatures) {
            capBySpecies.get(MonsterRoster.getSpecies(c.npcName, c.npcCombatLevel)).add(c.npcName);
        }

        List<CreatureSpecies> sortedTypes = Arrays.stream(CreatureSpecies.values())
                .filter(sp -> rosterBySpecies.getOrDefault(sp, 0) > 0)
                .sorted(Comparator.comparingInt((CreatureSpecies sp) -> capBySpecies.get(sp).size()).reversed())
                .collect(Collectors.toList());

        JPanel typePanel = col();
        typePanel.setBorder(new EmptyBorder(0, 12, 0, 12));
        for (CreatureSpecies sp : sortedTypes) {
            int cap2 = capBySpecies.get(sp).size(), ttl2 = rosterBySpecies.getOrDefault(sp, 0);
            typePanel.add(barRow(sp.label, sp.displayColor, cap2, Math.max(ttl2, 1), cap2 + "/" + ttl2, ""));
            typePanel.add(gap(4));
        }
        root.add(typePanel);
        root.add(gap(10));
        root.add(sectionHeader("TOP 5 CREATURES  (captures · kills as tiebreaker)"));
        root.add(buildTopSpeciesSection(col));
        root.add(gap(10));

        // Captured creatures list
        root.add(sectionHeader("CAPTURED CREATURES  (" + captured + ")"));
        JPanel specList = col();
        specList.setBorder(new EmptyBorder(0, 12, 0, 12));

        if (col.creatures.isEmpty()) {
            specList.add(emptyNote("No creatures captured yet."));
        } else {
            col.creatures.stream()
                    .collect(Collectors.groupingBy(c -> c.npcName))
                    .entrySet().stream()
                    .sorted(Comparator.comparingInt((Map.Entry<String, List<CapturedCreature>> e) -> e.getValue().size()).reversed())
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

        // Headline = lifetime captures (your hunting). The analytics below are over held cards.
        root.add(heroStat(FMT.format(col.lifetimeCaptures), "CREATURES CAUGHT", ORANGE));
        root.add(gap(2));
        root.add(sectionHeader(FMT.format(col.totalCaptures()) + " CARDS HELD"));
        long sent = col.lifetimeCardsSent, received = col.tradedInCount();
        if (sent > 0 || received > 0) {
            root.add(gap(2));
            root.add(sectionHeader(FMT.format(sent) + " SENT  ·  " + FMT.format(received) + " RECEIVED"));
        }
        root.add(gap(8));
        root.add(sectionHeader("AVERAGE POWER BY RARITY"));

        JPanel avgPanel = col();
        avgPanel.setBorder(new EmptyBorder(0, 12, 0, 12));
        Map<CreatureRarity, Double> avg = col.creatures.stream().collect(
                Collectors.groupingBy(c -> c.rarity,
                        Collectors.averagingInt(c -> c.powerLevel())));

        // Scale bars to the strongest rarity's average (Power Level can exceed 100, so a flat 100
        // scale overflows and barely differentiates). Relative bars make the spread readable.
        int scaleMax = 1;
        for (Double v : avg.values()) scaleMax = Math.max(scaleMax, (int) Math.round(v));
        boolean any = false;
        for (CreatureRarity r : new CreatureRarity[]{
                CreatureRarity.MYTHIC, CreatureRarity.LEGENDARY, CreatureRarity.EPIC,
                CreatureRarity.RARE,   CreatureRarity.UNCOMMON,  CreatureRarity.COMMON}) {
            if (!avg.containsKey(r)) continue;
            int a = (int) Math.round(avg.get(r));
            avgPanel.add(barRow(r.label, r.displayColor, a, scaleMax, String.valueOf(a), ""));
            avgPanel.add(gap(4));
            any = true;
        }
        if (!any) avgPanel.add(emptyNote("No captures yet."));
        root.add(avgPanel);
        root.add(gap(10));
        root.add(sectionHeader("TOP 10 BY POWER"));

        JPanel topList = col();
        topList.setBorder(new EmptyBorder(0, 12, 0, 12));

        List<CapturedCreature> best = col.creatures.stream()
                .sorted(Comparator.comparingInt((CapturedCreature c) -> c.powerLevel()).reversed())
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

                JPanel left = new JPanel(new GridLayout(0, 1, 0, 1));
                left.setOpaque(false);
                JLabel nl = new JLabel((i + 1) + ".  " + c.npcName);
                nl.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
                nl.setForeground(c.rarity.displayColor);
                JLabel rl = new JLabel(c.rarity.label
                        + (c.nickname != null && !c.nickname.isEmpty() ? "  \"" + c.nickname + "\"" : ""));
                rl.setFont(FontManager.getRunescapeSmallFont());
                rl.setForeground(MUTED);
                JLabel cl = new JLabel("Kill #" + c.killsBeforeCapture + "  ·  Lv." + c.captureLevel);
                cl.setFont(FontManager.getRunescapeSmallFont());
                cl.setForeground(new Color(210, 210, 210));
                left.add(nl); left.add(rl); left.add(cl);

                // Provenance / reroll tags (original owner is enough reference; no traded-in tag)
                java.util.List<String> tags = new java.util.ArrayList<>();
                if (c.originalOwner != null && !c.originalOwner.isEmpty()) tags.add("by " + c.originalOwner);
                if (c.rerollCount() > 0) tags.add("rerolled " + c.rerollCount() + "×");
                if (!tags.isEmpty()) {
                    JLabel tl = new JLabel("◈ " + String.join("  ·  ", tags));
                    tl.setFont(FontManager.getRunescapeSmallFont());
                    tl.setForeground(new Color(150, 170, 200));
                    left.add(tl);
                }

                JLabel ql = new JLabel("PWR:" + c.powerLevel());
                ql.setFont(FontManager.getRunescapeFont().deriveFont(Font.BOLD));
                ql.setForeground(qualColor(c.powerLevel()));

                row.add(left, BorderLayout.CENTER);
                row.add(ql,   BorderLayout.EAST);
                topList.add(row);
                topList.add(gap(3));
            }
        }
        root.add(topList);
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
    // Top-5 species rarity breakdown table (live view)
    // =========================================================================

    private JPanel buildTopSpeciesSection(BestiaryCollection col) {
        JPanel root = col();
        root.setBorder(new EmptyBorder(0, 12, 0, 12));

        Map<String, List<CapturedCreature>> bySpecies = col.creatures.stream()
                .collect(Collectors.groupingBy(c -> c.npcName));
        List<Map.Entry<String, List<CapturedCreature>>> top5 = bySpecies.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, List<CapturedCreature>> e) -> e.getValue().size()).reversed()
                    .thenComparingInt(e -> -col.getKillCount(e.getKey())))
                .limit(5).collect(Collectors.toList());

        if (top5.isEmpty()) {
            root.add(emptyNote("No captures yet."));
            return root;
        }

        CreatureRarity[] rarOrder = {CreatureRarity.COMMON, CreatureRarity.UNCOMMON, CreatureRarity.RARE,
                CreatureRarity.EPIC, CreatureRarity.LEGENDARY, CreatureRarity.MYTHIC};
        String[] rarAbbr = {"C", "U", "R", "E", "L", "M"};
        final int killsColW = 40, rarColW = 22;

        JPanel header = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = g2(g);
                int w = getWidth(), h = getHeight();
                int nameW = w - killsColW - rarOrder.length * rarColW;
                Font sf = FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD);
                g2.setFont(sf);
                FontMetrics fm = g2.getFontMetrics();
                int base = (h + fm.getAscent() - fm.getDescent()) / 2;
                g2.setColor(MUTED);
                g2.drawString("Name", 0, base);
                g2.setColor(ORANGE);
                String kh = "Kills";
                g2.drawString(kh, nameW + killsColW - fm.stringWidth(kh), base);
                for (int i = 0; i < rarAbbr.length; i++) {
                    g2.setColor(rarOrder[i].displayColor);
                    g2.drawString(rarAbbr[i], nameW + killsColW + i * rarColW + (rarColW - fm.stringWidth(rarAbbr[i])) / 2, base);
                }
                g2.dispose();
            }
        };
        header.setOpaque(false);
        header.setPreferredSize(new Dimension(0, 18));
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        root.add(header);
        JPanel divider = new JPanel();
        divider.setBackground(new Color(60, 60, 60));
        divider.setPreferredSize(new Dimension(0, 1));
        divider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        root.add(divider);
        root.add(gap(3));

        for (Map.Entry<String, List<CapturedCreature>> e : top5) {
            List<CapturedCreature> caps = e.getValue();
            Map<CreatureRarity, Long> rarCounts = caps.stream()
                    .collect(Collectors.groupingBy(c -> c.rarity, Collectors.counting()));
            CreatureRarity rarest = caps.stream().map(c -> c.rarity)
                    .max(Comparator.comparingInt(Enum::ordinal)).orElse(CreatureRarity.COMMON);
            String specName = e.getKey();
            String killsStr = FMT.format(col.getKillCount(specName));

            JPanel row = new JPanel() {
                @Override protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = g2(g);
                    int w = getWidth(), h = getHeight();
                    int nameW = w - killsColW - rarOrder.length * rarColW;
                    Font sf = FontManager.getRunescapeSmallFont();
                    g2.setFont(sf);
                    FontMetrics fm = g2.getFontMetrics();
                    int base = (h + fm.getAscent() - fm.getDescent()) / 2;

                    String disp = "● " + specName;
                    while (fm.stringWidth(disp) > nameW - 4 && disp.length() > 3)
                        disp = disp.substring(0, disp.length() - 1);
                    g2.setColor(rarest.displayColor);
                    g2.drawString(disp, 0, base);

                    g2.setColor(ORANGE);
                    g2.drawString(killsStr, nameW + killsColW - fm.stringWidth(killsStr) - 2, base);

                    for (int i = 0; i < rarOrder.length; i++) {
                        long cnt = rarCounts.getOrDefault(rarOrder[i], 0L);
                        String cs = cnt > 0 ? String.valueOf(cnt) : "–";
                        g2.setColor(cnt > 0 ? rarOrder[i].displayColor : new Color(55, 55, 55));
                        g2.drawString(cs, nameW + killsColW + i * rarColW + (rarColW - fm.stringWidth(cs)) / 2, base);
                    }
                    g2.dispose();
                }
            };
            row.setOpaque(false);
            row.setPreferredSize(new Dimension(0, 22));
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
            root.add(row);
            root.add(gap(3));
        }
        return root;
    }

    private JPanel buildMostHuntedSection(BestiaryCollection col) {
        JPanel root = col();
        root.setBorder(new EmptyBorder(0, 12, 0, 12));

        List<Map.Entry<String, Integer>> top5 = col.killCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5).collect(Collectors.toList());

        if (top5.isEmpty()) {
            root.add(emptyNote("No kills recorded yet."));
            return root;
        }

        CreatureRarity[] rarOrder = {CreatureRarity.COMMON, CreatureRarity.UNCOMMON, CreatureRarity.RARE,
                CreatureRarity.EPIC, CreatureRarity.LEGENDARY, CreatureRarity.MYTHIC};
        String[] rarAbbr = {"C", "U", "R", "E", "L", "M"};
        final int killsColW = 40, rarColW = 22;

        JPanel header = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = g2(g);
                int w = getWidth(), h = getHeight();
                int nameW = w - killsColW - rarOrder.length * rarColW;
                Font sf = FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD);
                g2.setFont(sf);
                FontMetrics fm = g2.getFontMetrics();
                int base = (h + fm.getAscent() - fm.getDescent()) / 2;
                g2.setColor(MUTED);
                g2.drawString("Name", 0, base);
                g2.setColor(ORANGE);
                String kh = "Kills";
                g2.drawString(kh, nameW + killsColW - fm.stringWidth(kh), base);
                for (int i = 0; i < rarAbbr.length; i++) {
                    g2.setColor(rarOrder[i].displayColor);
                    g2.drawString(rarAbbr[i], nameW + killsColW + i * rarColW + (rarColW - fm.stringWidth(rarAbbr[i])) / 2, base);
                }
                g2.dispose();
            }
        };
        header.setOpaque(false);
        header.setPreferredSize(new Dimension(0, 18));
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        root.add(header);

        JPanel divider = new JPanel();
        divider.setBackground(new Color(60, 60, 60));
        divider.setPreferredSize(new Dimension(0, 1));
        divider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        root.add(divider);
        root.add(gap(3));

        for (Map.Entry<String, Integer> e : top5) {
            String npcName = e.getKey();
            int kills = e.getValue();
            List<CapturedCreature> caps = col.creatures.stream()
                    .filter(c -> c.npcName.equals(npcName))
                    .collect(Collectors.toList());
            Map<CreatureRarity, Long> rarCounts = caps.stream()
                    .collect(Collectors.groupingBy(c -> c.rarity, Collectors.counting()));
            CreatureRarity rarest = caps.isEmpty() ? null :
                    caps.stream().map(c -> c.rarity).max(Comparator.comparingInt(Enum::ordinal)).orElse(null);
            Color nameColor = rarest != null ? rarest.displayColor : DIM;
            String killsStr = FMT.format(kills);

            JPanel row = new JPanel() {
                @Override protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = g2(g);
                    int w = getWidth(), h = getHeight();
                    int nameW = w - killsColW - rarOrder.length * rarColW;
                    Font sf = FontManager.getRunescapeSmallFont();
                    g2.setFont(sf);
                    FontMetrics fm = g2.getFontMetrics();
                    int base = (h + fm.getAscent() - fm.getDescent()) / 2;
                    String disp = "● " + npcName;
                    while (fm.stringWidth(disp) > nameW - 4 && disp.length() > 3)
                        disp = disp.substring(0, disp.length() - 1);
                    g2.setColor(nameColor);
                    g2.drawString(disp, 0, base);
                    g2.setColor(ORANGE);
                    g2.drawString(killsStr, nameW + killsColW - fm.stringWidth(killsStr) - 2, base);
                    for (int i = 0; i < rarOrder.length; i++) {
                        long cnt = rarCounts.getOrDefault(rarOrder[i], 0L);
                        String cs = cnt > 0 ? String.valueOf(cnt) : "–";
                        g2.setColor(cnt > 0 ? rarOrder[i].displayColor : new Color(55, 55, 55));
                        g2.drawString(cs, nameW + killsColW + i * rarColW + (rarColW - fm.stringWidth(cs)) / 2, base);
                    }
                    g2.dispose();
                }
            };
            row.setOpaque(false);
            row.setPreferredSize(new Dimension(0, 22));
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
            root.add(row);
            root.add(gap(3));
        }
        return root;
    }

    // =========================================================================
    // Shared bar row — custom-painted label + gradient fill + right value
    // =========================================================================

    private JPanel barRow(String label, Color color, int value, int max, String before, String after) {
        return new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = g2(g);
                int w = getWidth(), h = getHeight();

                Font sf = FontManager.getRunescapeSmallFont();
                g2.setFont(sf);
                FontMetrics fm = g2.getFontMetrics();

                int labelW   = 110;
                int beforeW  = 50;
                int afterW   = after.isEmpty() ? 0 : fm.stringWidth(after) + 8;
                int barX     = labelW + beforeW + 4;
                int barW     = w - barX - afterW - 8;
                int barH     = 8, barY = (h - barH) / 2 + 1;
                int base     = (h + fm.getAscent() - fm.getDescent()) / 2;

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

                // Before text (right-aligned in before column, bright white)
                if (!before.isEmpty()) {
                    g2.setColor(Color.WHITE);
                    g2.drawString(before, labelW + beforeW - fm.stringWidth(before) - 2, base);
                }

                // Track
                g2.setColor(new Color(42, 42, 42));
                g2.fillRoundRect(barX, barY, barW, barH, 4, 4);

                // Fill (clamped to the track so a value > max can never spill out of bounds)
                if (max > 0 && value > 0) {
                    int fill = Math.min(barW, Math.max(4, (int)((long) barW * value / max)));
                    g2.setPaint(new GradientPaint(barX, 0,
                            new Color(color.getRed()/2, color.getGreen()/2, color.getBlue()/2),
                            barX + fill, 0, color));
                    g2.fillRoundRect(barX, barY, fill, barH, 4, 4);
                }

                // After text (light accent colour)
                if (!after.isEmpty()) {
                    g2.setColor(new Color(170, 200, 230));
                    g2.drawString(after, w - afterW + 4, base);
                }
                g2.dispose();
            }
        };
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private JPanel buildView(DashView v) {
        switch (v) {
            case ECONOMY: return buildEconomyView();
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
        JPanel p = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = g2(g);
                g2.setPaint(new GradientPaint(0, 0, new Color(44, 44, 44), 0, getHeight(), new Color(22, 22, 22)));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(ORANGE);
                g2.fillRect(0, 0, 3, getHeight());
                g2.dispose();
            }
        };
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(6, 10, 6, 10));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        JLabel l = new JLabel(text);
        l.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        l.setForeground(ORANGE);
        p.add(l, BorderLayout.WEST);
        return p;
    }

    private JPanel miniCard(String label, String value) {
        return miniCard(label, value, false);
    }

    private JPanel miniCard(String label, String value, boolean rightAccent) {
        JPanel card = new JPanel(new GridLayout(2, 1, 0, 2));
        card.setBackground(CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 2, 0, rightAccent ? 2 : 0, ORANGE),
                new EmptyBorder(5, 7, 5, 7)));
        JLabel vl = new JLabel(value, SwingConstants.CENTER);
        vl.setFont(FontManager.getRunescapeBoldFont().deriveFont(15f));
        vl.setForeground(ORANGE);
        JLabel ll = new JLabel(label, SwingConstants.CENTER);
        ll.setFont(FontManager.getRunescapeSmallFont());
        ll.setForeground(TEXT);
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
        // Single source of truth for Power Level banding (see AlbumCard.powerColor).
        return AlbumCard.powerColor(q);
    }

    private static Graphics2D g2(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);
        return g2;
    }

    // =========================================================================
    // EXPORT — buttons + public clipboard helper
    // =========================================================================

    private static JButton topBarBtn(String label, String tip) {
        JButton btn = new JButton(label);
        btn.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        btn.setBackground(new Color(45, 45, 45));
        btn.setForeground(ORANGE);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setToolTipText(tip);
        return btn;
    }

    private void exportView(JButton btn) {
        copyImageToClipboard(renderCard(dataService, progressionService, activeView));
        flashButton(btn, "✓ Copied!");
    }

    private void exportSingle(JButton btn) {
        BufferedImage img = renderCard(dataService, progressionService, activeView);
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(System.getProperty("user.home"),
                "bestiary_" + activeView.name().toLowerCase() + ".png"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                ImageIO.write(img, "PNG", chooser.getSelectedFile());
                flashButton(btn, "✓ Saved!");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Save failed: " + ex.getMessage());
            }
        }
    }

    private void exportAll(JButton btn) {
        BufferedImage img = renderAllCard(dataService, progressionService);
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(System.getProperty("user.home"), "bestiary_dashboard.png"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                ImageIO.write(img, "PNG", chooser.getSelectedFile());
                flashButton(btn, "✓ Saved!");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Save failed: " + ex.getMessage());
            }
        }
    }

    private static void copyImageToClipboard(BufferedImage img) {
        Transferable t = new Transferable() {
            @Override public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{DataFlavor.imageFlavor}; }
            @Override public boolean isDataFlavorSupported(DataFlavor f) { return f.equals(DataFlavor.imageFlavor); }
            @Override public Object getTransferData(DataFlavor f) throws UnsupportedFlavorException {
                if (!f.equals(DataFlavor.imageFlavor)) throw new UnsupportedFlavorException(f);
                return img;
            }
        };
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(t, null);
    }

    /** Public — called from InfoTab right-click without opening the dialog. */
    public static void copyViewToClipboard(BestiaryDataService ds, ProgressionService ps, DashView view) {
        copyImageToClipboard(renderCard(ds, ps, view));
    }

    private static void flashButton(JButton btn, String flashText) {
        String orig = btn.getText();
        btn.setText(flashText);
        btn.setForeground(new Color(80, 220, 80));
        javax.swing.Timer timer = new javax.swing.Timer(1400, e -> { btn.setText(orig); btn.setForeground(ORANGE); });
        timer.setRepeats(false);
        timer.start();
    }

    private static void flashMenuItem(JMenuItem item, String flashText) {
        String orig = item.getText();
        item.setText(flashText);
        javax.swing.Timer timer = new javax.swing.Timer(1400, e -> item.setText(orig));
        timer.setRepeats(false);
        timer.start();
    }

    // =========================================================================
    // CARD RENDERERS — all static so they can be called without a dialog instance
    // =========================================================================

    public static BufferedImage renderCard(BestiaryDataService ds, ProgressionService ps, DashView view) {
        switch (view) {
            case ECONOMY: return renderEconomyCard(ds);
            case SPECIES: return renderSpeciesCard(ds);
            case CAUGHT:  return renderCaughtCard(ds);
            default:      return renderProgressionCard(ds, ps);
        }
    }

    private static BufferedImage renderAllCard(BestiaryDataService ds, ProgressionService ps) {
        BufferedImage prog    = renderProgressionCard(ds, ps);
        BufferedImage economy = renderEconomyCard(ds);
        BufferedImage species = renderSpeciesCard(ds);
        BufferedImage caught  = renderCaughtCard(ds);

        int gap   = 10, pad = 16;
        int colW  = Math.max(prog.getWidth(), species.getWidth());
        int row1H = Math.max(prog.getHeight(),    economy.getHeight());
        int row2H = Math.max(species.getHeight(), caught.getHeight());

        // Pad shorter cards to row height so footers align
        prog    = padCardToHeight(prog,    row1H);
        economy = padCardToHeight(economy, row1H);
        species = padCardToHeight(species, row2H);
        caught  = padCardToHeight(caught,  row2H);

        int W = pad * 2 + colW * 2 + gap;
        int H = pad * 2 + row1H + gap + row2H;

        BufferedImage all = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = all.createGraphics();
        g.setColor(new Color(10, 10, 10));
        g.fillRect(0, 0, W, H);
        g.drawImage(prog,    pad,              pad,               null);
        g.drawImage(economy, pad + colW + gap, pad,               null);
        g.drawImage(species, pad,              pad + row1H + gap, null);
        g.drawImage(caught,  pad + colW + gap, pad + row1H + gap, null);
        g.dispose();
        return all;
    }

    private static BufferedImage padCardToHeight(BufferedImage src, int targetH) {
        if (src.getHeight() >= targetH) return src;
        int W = src.getWidth();
        BufferedImage dst = new BufferedImage(W, targetH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = cardGraphics(dst);
        // Background fill matching card base colour
        g.setColor(new Color(14, 14, 14));
        g.fillRoundRect(0, 0, W, targetH, 16, 16);
        // Copy content above the old footer
        int contentH = src.getHeight() - 36;
        g.drawImage(src, 0, 0, W, contentH, 0, 0, W, contentH, null);
        g.dispose();
        // Re-draw footer pinned to the new bottom
        Graphics2D gf = cardGraphics(dst);
        drawCardFooter(gf, targetH - 36, W, 24);
        gf.dispose();
        return dst;
    }

    // ---- shared card base ----

    private static String resolveAccount(BestiaryDataService ds) {
        // The active account name — data is scoped per account, so this is the collection's owner.
        String name = ds.getActiveAccountName();
        return name != null && !name.isEmpty() ? name : "Unknown";
    }

    private static String todayStr() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("d MMM yyyy"));
    }

    private static Graphics2D cardGraphics(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        return g;
    }

    /** Draws the card background + top accent bar. Returns y position after the header block. */
    private static int drawCardBase(Graphics2D g, int W, int H, String account, String viewLabel, String date, int PAD) {
        // Background
        g.setColor(new Color(14, 14, 14));
        g.fillRoundRect(0, 0, W, H, 16, 16);
        float[] fr = {0f, 0.6f, 1f};
        Color[] vc = {new Color(30, 30, 30), new Color(18, 18, 18), new Color(10, 10, 10)};
        g.setPaint(new RadialGradientPaint(W / 2f, H / 3f, Math.max(W, H) * 0.75f, fr, vc));
        g.fillRoundRect(0, 0, W, H, 16, 16);

        // Top accent
        int accentH = 4;
        g.setPaint(new GradientPaint(0, 0, new Color(255, 120, 0), W, 0, new Color(255, 210, 60)));
        g.fillRoundRect(0, 0, W, accentH + 8, 8, 8);
        g.setColor(new Color(14, 14, 14));
        g.fillRect(0, accentH, W, 8);

        int y = accentH + PAD;

        // BESTIARY + view label left; date right
        g.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD | Font.ITALIC));
        FontMetrics sfm = g.getFontMetrics();
        g.setColor(ORANGE);
        g.drawString("BESTIARY  ·  " + viewLabel, PAD, y + sfm.getAscent());
        g.setFont(FontManager.getRunescapeSmallFont());
        sfm = g.getFontMetrics();
        g.setColor(MUTED);
        g.drawString(date, W - PAD - sfm.stringWidth(date), y + sfm.getAscent());
        y += sfm.getHeight() + 4;

        // Account name
        g.setFont(FontManager.getRunescapeBoldFont().deriveFont(20f));
        FontMetrics nameFm = g.getFontMetrics();
        g.setColor(GOLD);
        g.drawString(account, PAD, y + nameFm.getAscent());
        y += nameFm.getHeight() + 6;

        // Gold underline
        g.setColor(new Color(255, 195, 40, 55));
        g.fillRect(PAD, y, W - PAD * 2, 1);
        return y + 12;
    }

    /** Big hero number with label below. Returns new y. */
    private static int drawHeroStat(Graphics2D g, String value, String label, Color accent, int y, int W, int PAD) {
        g.setPaint(new GradientPaint(0, y, new Color(30, 30, 30), 0, y + 70, new Color(18, 18, 18)));
        g.fillRect(0, y, W, 70);
        g.setColor(accent);
        g.fillRect(0, y, 4, 70);

        g.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        FontMetrics sfm = g.getFontMetrics();
        g.setColor(TEXT);
        g.drawString(label, PAD + 10, y + 18);

        g.setFont(FontManager.getRunescapeBoldFont().deriveFont(36f));
        FontMetrics bfm = g.getFontMetrics();
        int vy = y + 18 + sfm.getHeight() + bfm.getAscent() - 4;
        g.setColor(new Color(0, 0, 0, 110));
        g.drawString(value, PAD + 11, vy + 2);
        g.setColor(accent);
        g.drawString(value, PAD + 10, vy);
        return y + 70 + 12;
    }

    // ---- PROGRESSION card ----

    private static BufferedImage renderProgressionCard(BestiaryDataService ds, ProgressionService ps) {
        BestiaryCollection col    = ds.getCollection();
        int    level              = ps.getLevel();
        long   totalXp            = ps.getTotalXp();
        long   levelStart         = XpTable.xpForLevel(level);
        long   levelEnd           = XpTable.xpForLevel(Math.min(level + 1, XpTable.MAX_VIRTUAL_LEVEL));
        long   xpInLevel          = totalXp - levelStart;
        long   xpSpan             = Math.max(1, levelEnd - levelStart);
        long   xpLeft             = ps.getXpToNextLevel();
        Set<Achievement> unlocked = ps.getState().unlockedAchievements;

        String account = resolveAccount(ds);
        String date    = todayStr();

        final int W   = 480;
        final int PAD = 24;

        // Pre-measure section heights to set total card height
        int kills = col.totalKills(), held = col.totalCaptures();
        long caught = col.lifetimeCaptures;
        String ratio = caught > 0 ? String.format("%.1f:1", (double) kills / caught) : "—";
        Map<CreatureRarity, Long> rarityCounts = col.creatures.stream()
                .collect(Collectors.groupingBy(c -> c.rarity, Collectors.counting()));
        // Export only the top 16 "most impressive" achievements (by credit reward) so the card
        // doesn't grow unbounded once a player has unlocked a lot (#115).
        final int ACH_EXPORT_CAP = 16;
        List<Achievement> unlockedList = Arrays.stream(Achievement.values())
                .filter(unlocked::contains)
                .sorted(Comparator.comparingInt((Achievement a) -> a.creditReward).reversed())
                .limit(ACH_EXPORT_CAP)
                .collect(Collectors.toList());
        int achRows = (int) Math.ceil(unlockedList.size() / 2.0);
        if (unlockedList.isEmpty()) achRows = 1;

        // Fixed layout heights
        int topBarH  = 4;
        int hdrH     = 60;   // BESTIARY + account + date
        int arcH     = 230;  // XP arc + level number
        int xpBarH   = 42;   // XP bar + label
        int sepH     = 28;   // section header
        int overH    = 60;   // 4 stat boxes
        int rarH     = 6 * 22 + 8; // 6 rarity bars
        int achGridH = achRows * 22 + 8;
        int footerH  = 36;
        int H = topBarH + hdrH + arcH + xpBarH + sepH + overH + 8
                + sepH + rarH + 8 + sepH + achGridH + footerH + PAD * 2;

        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = cardGraphics(img);

        int y = drawCardBase(g, W, H, account, "PROGRESSION", date, PAD);

        // --- XP Arc ---
        float progress = xpSpan > 0 ? (float) xpInLevel / xpSpan : 0f;
        int arcD = 170, arcX = (W - arcD) / 2, arcY = y;

        // Glow behind track
        g.setColor(new Color(255, 165, 0, 12));
        g.setStroke(new BasicStroke(28f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Arc2D.Double(arcX - 6, arcY - 6, arcD + 12, arcD + 12, -220, 260, Arc2D.OPEN));

        // Track
        g.setStroke(new BasicStroke(14f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(42, 42, 42));
        g.draw(new Arc2D.Double(arcX, arcY, arcD, arcD, -220, 260, Arc2D.OPEN));

        // Fill glow
        if (progress > 0.01f) {
            g.setStroke(new BasicStroke(22f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(255, 140, 0, 30));
            g.draw(new Arc2D.Double(arcX, arcY, arcD, arcD, -220, (int)(-260 * progress), Arc2D.OPEN));
        }
        // Fill
        if (progress > 0.01f) {
            g.setStroke(new BasicStroke(14f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setPaint(new GradientPaint(arcX, arcY, new Color(255, 100, 0),
                    arcX + arcD, arcY + arcD, GOLD));
            g.draw(new Arc2D.Double(arcX, arcY, arcD, arcD, -220, (int)(-260 * progress), Arc2D.OPEN));
        }

        // Level text in centre
        int cx = arcX + arcD / 2, cy = arcY + arcD / 2;
        g.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        FontMetrics sfm = g.getFontMetrics();
        String capLabel = "CAPTURE LEVEL";
        g.setColor(new Color(165, 165, 165));
        g.drawString(capLabel, cx - sfm.stringWidth(capLabel) / 2, cy - 30);

        g.setFont(FontManager.getRunescapeBoldFont().deriveFont(56f));
        FontMetrics lvlFm = g.getFontMetrics();
        String lvlStr = String.valueOf(level);
        int lvlX = cx - lvlFm.stringWidth(lvlStr) / 2;
        int lvlY = cy + lvlFm.getAscent() / 2 - 4;
        g.setColor(new Color(0, 0, 0, 120));
        g.drawString(lvlStr, lvlX + 2, lvlY + 2);
        g.setColor(level >= 99 ? GOLD : Color.WHITE);
        g.drawString(lvlStr, lvlX, lvlY);

        // Pct label below level number — more vertical space so it doesn't crowd the number
        g.setFont(FontManager.getRunescapeSmallFont());
        sfm = g.getFontMetrics();
        String pctStr = String.format("%.1f%% to next level", progress * 100f);
        if (level >= 99) pctStr = "MAX LEVEL";
        g.setColor(new Color(200, 200, 200));
        g.drawString(pctStr, cx - sfm.stringWidth(pctStr) / 2, cy + 32);

        y += arcD + 26;

        // --- XP bar ---
        int barX = PAD + 10, barW = W - (PAD + 10) * 2, barH2 = 10;
        g.setColor(new Color(36, 36, 36));
        g.fillRoundRect(barX, y, barW, barH2, 6, 6);
        int fill = (int)(barW * progress);
        if (fill > 4) {
            g.setPaint(new GradientPaint(barX, y, new Color(255, 100, 0), barX + fill, y, GOLD));
            g.fillRoundRect(barX, y, fill, barH2, 6, 6);
        }
        y += barH2 + 10;

        // XP labels
        g.setFont(FontManager.getRunescapeSmallFont());
        sfm = g.getFontMetrics();
        g.setColor(new Color(210, 210, 210));
        g.drawString(FMT.format(totalXp) + " XP total", barX, y + sfm.getAscent());
        String nextStr = level >= 99 ? "Max level!" : FMT.format(xpLeft) + " XP to level " + (level + 1);
        g.setColor(new Color(170, 200, 230));
        g.drawString(nextStr, barX + barW - sfm.stringWidth(nextStr), y + sfm.getAscent());
        y += sfm.getHeight() + PAD;

        // --- OVERVIEW section ---
        y = drawCardSectionHeader(g, "OVERVIEW", y, W, PAD);
        y += 6;
        int boxGap = 4;
        int boxW  = (W - PAD * 2 - boxGap * 4) / 5, boxH = 48;
        String[][] stats = {
            {"Level",  String.valueOf(level)},
            {"Kills",  FMT.format(kills)},
            {"Caught", FMT.format(caught)},
            {"Cards",  FMT.format(held)},
            {"K : C",  ratio}
        };
        for (int i = 0; i < 5; i++) {
            int bx = PAD + i * (boxW + boxGap);
            g.setPaint(new GradientPaint(bx, y, new Color(38, 38, 38), bx, y + boxH, new Color(26, 26, 26)));
            g.fillRoundRect(bx, y, boxW, boxH, 6, 6);
            g.setColor(ORANGE);
            g.fillRect(bx, y, 3, boxH);
            if (i == 3) g.fillRect(bx + boxW - 3, y, 3, boxH);

            g.setFont(FontManager.getRunescapeBoldFont().deriveFont(17f));
            FontMetrics bfm = g.getFontMetrics();
            String val = stats[i][1];
            g.setColor(ORANGE);
            g.drawString(val, bx + (boxW - bfm.stringWidth(val)) / 2, y + 24);
            g.setFont(FontManager.getRunescapeSmallFont());
            sfm = g.getFontMetrics();
            g.setColor(TEXT);
            String lbl = stats[i][0];
            g.drawString(lbl, bx + (boxW - sfm.stringWidth(lbl)) / 2, y + 40);
        }
        y += boxH + PAD;

        // --- RARITY BREAKDOWN section ---
        y = drawCardSectionHeader(g, "RARITY BREAKDOWN", y, W, PAD);
        y += 6;
        CreatureRarity[] rarOrder = {
            CreatureRarity.MYTHIC, CreatureRarity.LEGENDARY, CreatureRarity.EPIC,
            CreatureRarity.RARE,   CreatureRarity.UNCOMMON,  CreatureRarity.COMMON
        };
        int maxRar = rarityCounts.values().stream().mapToInt(Long::intValue).max().orElse(1);
        int total2 = col.totalCaptures();
        for (CreatureRarity r : rarOrder) {
            long cnt = rarityCounts.getOrDefault(r, 0L);
            String pct = total2 > 0 ? String.format("%.1f%%", cnt * 100.0 / total2) : "0%";
            y = drawCardBarRow(g, r.label, r.displayColor, (int) cnt, maxRar,
                    FMT.format(cnt), pct, y, PAD, W);
        }
        y += 8;

        // --- ACHIEVEMENTS section ---
        String achHeader = "ACHIEVEMENTS  —  " + unlocked.size() + " / " + Achievement.values().length
                + (unlocked.size() > unlockedList.size() ? "   (top " + unlockedList.size() + ")" : "");
        y = drawCardSectionHeader(g, achHeader, y, W, PAD);
        y += 6;
        if (unlockedList.isEmpty()) {
            g.setFont(FontManager.getRunescapeSmallFont());
            sfm = g.getFontMetrics();
            g.setColor(DIM);
            g.drawString("No achievements unlocked yet", PAD + 6, y + sfm.getAscent());
            y += 22;
        } else {
            int colW = (W - PAD * 2 - 6) / 2;
            for (int i = 0; i < unlockedList.size(); i += 2) {
                Achievement a = unlockedList.get(i);
                g.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
                sfm = g.getFontMetrics();
                g.setColor(a.chatColor);
                g.drawString("✓ " + a.title, PAD + 4, y + sfm.getAscent());
                if (i + 1 < unlockedList.size()) {
                    Achievement b = unlockedList.get(i + 1);
                    g.setColor(b.chatColor);
                    g.drawString("✓ " + b.title, PAD + colW + 8, y + sfm.getAscent());
                }
                y += 22;
            }
        }
        y += 8;

        // --- Footer ---
        drawCardFooter(g, H - 36, W, PAD);
        g.dispose();
        return img;
    }

    private static int drawCardSectionHeader(Graphics2D g, String text, int y, int W, int PAD) {
        g.setPaint(new GradientPaint(PAD, y, new Color(44, 44, 44), PAD, y + 22, new Color(22, 22, 22)));
        g.fillRoundRect(PAD, y, W - PAD * 2, 22, 4, 4);
        g.setColor(ORANGE);
        g.fillRect(PAD, y, 3, 22);
        g.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(ORANGE);
        g.drawString(text, PAD + 8, y + (22 + fm.getAscent() - fm.getDescent()) / 2);
        return y + 22;
    }

    /**
     * before = value right-aligned in fixed column before the bar (bright white, e.g. "42")
     * after  = optional label after the bar in accent blue-white (e.g. "2.5%"), or ""
     */
    private static int drawCardBarRow(Graphics2D g, String label, Color color,
                                      int value, int max, String before, String after,
                                      int y, int PAD, int W) {
        g.setFont(FontManager.getRunescapeSmallFont());
        FontMetrics fm = g.getFontMetrics();
        int rowH       = 22;
        int dotW       = fm.stringWidth("● ");
        int labelW     = 100;
        int beforeColW = 52;
        int afterW     = after.isEmpty() ? 0 : fm.stringWidth(after) + 8;
        int barX       = PAD + labelW + beforeColW + 4;
        int barW       = W - barX - PAD - afterW;
        int barH       = 8;
        int barY       = y + (rowH - barH) / 2;
        int base       = y + (rowH + fm.getAscent() - fm.getDescent()) / 2;

        g.setColor(color);
        g.drawString("●", PAD + 2, base);
        g.setColor(TEXT);
        String lbl = label;
        while (lbl.length() > 0 && fm.stringWidth(lbl) > labelW - dotW - 4)
            lbl = lbl.substring(0, lbl.length() - 1);
        if (lbl.length() < label.length()) lbl += "…";
        g.drawString(lbl, PAD + dotW + 2, base);

        if (!before.isEmpty()) {
            g.setColor(Color.WHITE);
            g.drawString(before, PAD + labelW + beforeColW - fm.stringWidth(before), base);
        }

        g.setColor(new Color(36, 36, 36));
        g.fillRoundRect(barX, barY, barW, barH, 4, 4);
        if (max > 0 && value > 0) {
            int fill = Math.min(barW, Math.max(4, (int)((long) barW * value / max)));
            g.setPaint(new GradientPaint(barX, barY,
                    new Color(color.getRed() / 2, color.getGreen() / 2, color.getBlue() / 2),
                    barX + fill, barY, color));
            g.fillRoundRect(barX, barY, fill, barH, 4, 4);
        }

        if (!after.isEmpty()) {
            g.setFont(FontManager.getRunescapeSmallFont());
            fm = g.getFontMetrics();
            g.setColor(new Color(170, 200, 230));
            g.drawString(after, W - PAD - fm.stringWidth(after), base);
        }
        return y + rowH + 2;
    }

    private static int drawCardFooter(Graphics2D g, int y, int W, int PAD) {
        g.setColor(new Color(255, 165, 0, 35));
        g.fillRect(PAD, y, W - PAD * 2, 1);
        y += 8;
        g.setFont(FontManager.getRunescapeSmallFont());
        FontMetrics fm = g.getFontMetrics();
        String footer = "RuneLite · Bestiary Plugin";
        g.setColor(new Color(75, 75, 75));
        g.drawString(footer, (W - fm.stringWidth(footer)) / 2, y + fm.getAscent());
        return y + fm.getHeight() + 4;
    }

    // ---- ECONOMY card ----

    private static BufferedImage renderEconomyCard(BestiaryDataService ds) {
        BestiaryCollection col = ds.getCollection();
        String account = resolveAccount(ds), date = todayStr();

        long cardsRerolled = col.creatures.stream().filter(c -> c.rerollCount() > 0).count();
        ShopUpgrade[] ups  = ShopUpgrade.values();
        final int W = 480, PAD = 24;
        int upRows = ups.length;
        int H = 4 + PAD + 60 + 12 + 70 + 12
                + 22 + 6 + 48 + 8               // credits (mini-card row)
                + 22 + 6 + 48 + 8               // rerolls (mini-card row)
                + 22 + 6 + upRows * 20 + 8      // shop upgrade pip rows
                + 36 + PAD;

        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = cardGraphics(img);

        int y = drawCardBase(g, W, H, account, "ECONOMY", date, PAD);
        y = drawHeroStat(g, FMT.format(col.credits), "CREDIT BALANCE", GOLD, y, W, PAD);
        y += 12;

        y = drawCardSectionHeader(g, "CREDITS", y, W, PAD);
        y += 6;
        y = drawStatBoxRow(g, new String[][]{
                {"Earned",  FMT.format(col.lifetimeCreditsEarned)},
                {"Spent",   FMT.format(col.lifetimeCreditsSpent)},
                {"Balance", FMT.format(col.credits)}
        }, y, PAD, W);
        y += 8;

        y = drawCardSectionHeader(g, "REROLLS", y, W, PAD);
        y += 6;
        y = drawStatBoxRow(g, new String[][]{
                {"Rerolls", FMT.format(col.totalRerolls())},
                {"Cards",   FMT.format(cardsRerolled)},
                {"Rank-ups / Shiny+", countRankUps(col) + " / " + countShinyGained(col)}
        }, y, PAD, W);
        y += 8;

        y = drawCardSectionHeader(g, "SHOP UPGRADES", y, W, PAD);
        y += 6;
        for (ShopUpgrade u : ups) {
            y = drawCardPipRow(g, u.title, ds.getUpgradeTier(u), u.maxTier, y, PAD, W);
        }
        y += 8;

        drawCardFooter(g, H - 36, W, PAD);
        g.dispose();
        return img;
    }

    /**
     * Draws a row of evenly-sized "value over label" mini-card boxes (the same look as the live
     * dashboard's stat cards), so export cards match the on-screen view. Returns the y below the row.
     */
    private static int drawStatBoxRow(Graphics2D g, String[][] stats, int y, int PAD, int W) {
        int n = stats.length, boxGap = 4, boxH = 48;
        int boxW = (W - PAD * 2 - boxGap * (n - 1)) / n;
        for (int i = 0; i < n; i++) {
            int bx = PAD + i * (boxW + boxGap);
            g.setPaint(new GradientPaint(bx, y, new Color(38, 38, 38), bx, y + boxH, new Color(26, 26, 26)));
            g.fillRoundRect(bx, y, boxW, boxH, 6, 6);
            g.setColor(ORANGE);
            g.fillRect(bx, y, 3, boxH);

            g.setFont(FontManager.getRunescapeBoldFont().deriveFont(17f));
            FontMetrics bfm = g.getFontMetrics();
            String val = stats[i][1];
            g.setColor(ORANGE);
            g.drawString(val, bx + (boxW - bfm.stringWidth(val)) / 2, y + 24);

            g.setFont(FontManager.getRunescapeSmallFont());
            FontMetrics sfm = g.getFontMetrics();
            g.setColor(TEXT);
            String lbl = stats[i][0];
            g.drawString(lbl, bx + (boxW - sfm.stringWidth(lbl)) / 2, y + 40);
        }
        return y + boxH;
    }

    /** Draws a "label ....... ●●●○○" row with filled gold pips for owned tiers. Returns the new y. */
    private static int drawCardPipRow(Graphics2D g, String label, int owned, int max, int y, int PAD, int W) {
        g.setFont(FontManager.getRunescapeSmallFont());
        FontMetrics fm = g.getFontMetrics();
        g.setColor(new Color(210, 210, 210));
        g.drawString(label, PAD + 4, y + fm.getAscent());
        int d = 10, gap = 5;
        int totalW = max * d + (max - 1) * gap;
        int x = W - PAD - totalW;
        int cy = y + fm.getAscent() / 2 - d / 2;
        for (int i = 0; i < max; i++) {
            g.setColor(i < owned ? GOLD : new Color(70, 70, 70));
            g.fillOval(x + i * (d + gap), cy, d, d);
        }
        return y + 20;
    }

    // ---- SPECIES card ----

    private static BufferedImage renderSpeciesCard(BestiaryDataService ds) {
        BestiaryCollection col    = ds.getCollection();
        List<String> roster       = MonsterRoster.buildFullRoster(col.killCounts);
        int captured              = (int) col.uniqueSpeciesCount(), total = roster.size();
        float pct                 = total > 0 ? (float) captured / total : 0f;
        String account = resolveAccount(ds), date = todayStr();

        // Difficulty completion
        Map<DifficultyTier, Integer> rosterByDiff = new EnumMap<>(DifficultyTier.class);
        Map<DifficultyTier, Set<String>> capByDiff = new EnumMap<>(DifficultyTier.class);
        for (DifficultyTier t : DifficultyTier.values()) { rosterByDiff.put(t, 0); capByDiff.put(t, new HashSet<>()); }
        for (String name : roster) {
            int cb = col.creatures.stream().filter(c -> c.npcName.equals(name)).findFirst().map(c -> c.npcCombatLevel).orElse(0);
            rosterByDiff.merge(MonsterRoster.getDifficulty(name, cb), 1, Integer::sum);
        }
        for (CapturedCreature c : col.creatures)
            capByDiff.get(MonsterRoster.getDifficulty(c.npcName, c.npcCombatLevel)).add(c.npcName);

        // Species type completion (exclude types with no roster entries)
        Map<CreatureSpecies, Integer> rosterBySp = new EnumMap<>(CreatureSpecies.class);
        Map<CreatureSpecies, Set<String>> capBySp = new EnumMap<>(CreatureSpecies.class);
        for (CreatureSpecies sp : CreatureSpecies.values()) { rosterBySp.put(sp, 0); capBySp.put(sp, new HashSet<>()); }
        for (String name : roster) {
            int cb = col.creatures.stream().filter(c -> c.npcName.equals(name)).findFirst().map(c -> c.npcCombatLevel).orElse(0);
            rosterBySp.merge(MonsterRoster.getSpecies(name, cb), 1, Integer::sum);
        }
        for (CapturedCreature c : col.creatures)
            capBySp.get(MonsterRoster.getSpecies(c.npcName, c.npcCombatLevel)).add(c.npcName);
        List<CreatureSpecies> sortedSpTypes = Arrays.stream(CreatureSpecies.values())
                .filter(sp -> rosterBySp.getOrDefault(sp, 0) > 0)
                .sorted(Comparator.comparingInt((CreatureSpecies sp) -> capBySp.get(sp).size()).reversed())
                .limit(5)
                .collect(Collectors.toList());

        // Top 5 species by capture count
        Map<String, List<CapturedCreature>> bySpecies = col.creatures.stream()
                .collect(Collectors.groupingBy(c -> c.npcName));
        List<Map.Entry<String, List<CapturedCreature>>> top5 = bySpecies.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, List<CapturedCreature>> e) -> e.getValue().size()).reversed())
                .limit(5).collect(Collectors.toList());

        final int W = 480, PAD = 24;
        int diffTiers = DifficultyTier.values().length;
        int top5Rows = Math.max(1, top5.size());
        int H = 4 + PAD + 60 + 12 + 70 + 12 + 12
               + 22 + 6 + diffTiers * 22 + 8
               + 22 + 6 + sortedSpTypes.size() * 22 + 8
               + 22 + 6 + 21 + top5Rows * 20 + 8
               + 36 + PAD;

        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = cardGraphics(img);

        int y = drawCardBase(g, W, H, account, "SPECIES", date, PAD);
        y = drawHeroStat(g, String.format("%.1f%%", pct * 100f) + "  (" + captured + " / " + total + ")",
                "DEX COMPLETION", new Color(80, 200, 80), y, W, PAD);
        y += 12;

        // Completion by difficulty
        y = drawCardSectionHeader(g, "COMPLETION BY DIFFICULTY", y, W, PAD);
        y += 6;
        for (DifficultyTier tier : DifficultyTier.values()) {
            int cap = capByDiff.get(tier).size(), ttl = rosterByDiff.getOrDefault(tier, 0);
            y = drawCardBarRow(g, tier.label, tier.displayColor, cap, Math.max(ttl, 1), cap + "/" + ttl, "", y, PAD, W);
        }
        y += 8;

        // Completion by creature type (top 5 by captured count)
        y = drawCardSectionHeader(g, "COMPLETION BY CREATURE TYPE", y, W, PAD);
        y += 6;
        for (CreatureSpecies sp : sortedSpTypes) {
            int cap = capBySp.get(sp).size(), ttl = rosterBySp.getOrDefault(sp, 0);
            y = drawCardBarRow(g, sp.label, sp.displayColor, cap, Math.max(ttl, 1), cap + "/" + ttl, "", y, PAD, W);
        }
        y += 8;

        // Top 5 monsters — rarity breakdown table
        y = drawCardSectionHeader(g, "TOP 5 CREATURES", y, W, PAD);
        y += 6;
        CreatureRarity[] rarOrder = {CreatureRarity.COMMON, CreatureRarity.UNCOMMON, CreatureRarity.RARE,
                CreatureRarity.EPIC, CreatureRarity.LEGENDARY, CreatureRarity.MYTHIC};
        String[] rarAbbr = {"C", "U", "R", "E", "L", "M"};
        int killsColW2 = 44, rarColW2 = 22;
        int firstColW = W - PAD * 2 - killsColW2 - rarOrder.length * rarColW2;
        g.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        FontMetrics hfm = g.getFontMetrics();
        g.setColor(MUTED);
        g.drawString("Name", PAD, y + hfm.getAscent());
        g.setColor(ORANGE);
        String khdr = "Kills";
        g.drawString(khdr, PAD + firstColW + killsColW2 - hfm.stringWidth(khdr), y + hfm.getAscent());
        for (int i = 0; i < rarAbbr.length; i++) {
            g.setColor(rarOrder[i].displayColor);
            g.drawString(rarAbbr[i], PAD + firstColW + killsColW2 + i * rarColW2 + (rarColW2 - hfm.stringWidth(rarAbbr[i])) / 2, y + hfm.getAscent());
        }
        y += 16;
        g.setColor(new Color(60, 60, 60));
        g.fillRect(PAD, y, W - PAD * 2, 1);
        y += 5;
        if (top5.isEmpty()) {
            g.setFont(FontManager.getRunescapeSmallFont()); g.setColor(DIM);
            g.drawString("No captures yet", PAD + 6, y + g.getFontMetrics().getAscent()); y += 20;
        } else {
            g.setFont(FontManager.getRunescapeSmallFont());
            FontMetrics rfm = g.getFontMetrics();
            for (Map.Entry<String, List<CapturedCreature>> e : top5) {
                List<CapturedCreature> caps = e.getValue();
                Map<CreatureRarity, Long> rarCounts = caps.stream()
                        .collect(Collectors.groupingBy(c -> c.rarity, Collectors.counting()));
                CreatureRarity rarest = caps.stream().map(c -> c.rarity)
                        .max(Comparator.comparingInt(Enum::ordinal)).orElse(CreatureRarity.COMMON);
                String disp = e.getKey();
                while (disp.length() > 0 && rfm.stringWidth("● " + disp) > firstColW - 4)
                    disp = disp.substring(0, disp.length() - 1);
                if (disp.length() < e.getKey().length()) disp += "…";
                g.setColor(rarest.displayColor);
                g.drawString("● " + disp, PAD, y + rfm.getAscent());
                String ks = FMT.format(col.getKillCount(e.getKey()));
                g.setColor(ORANGE);
                g.drawString(ks, PAD + firstColW + killsColW2 - rfm.stringWidth(ks), y + rfm.getAscent());
                for (int i = 0; i < rarOrder.length; i++) {
                    long cnt = rarCounts.getOrDefault(rarOrder[i], 0L);
                    String cs = cnt > 0 ? String.valueOf(cnt) : "–";
                    g.setColor(cnt > 0 ? rarOrder[i].displayColor : DIM);
                    g.drawString(cs, PAD + firstColW + killsColW2 + i * rarColW2 + (rarColW2 - rfm.stringWidth(cs)) / 2, y + rfm.getAscent());
                }
                y += 20;
            }
        }
        y += 8;
        drawCardFooter(g, H - 36, W, PAD);
        g.dispose();
        return img;
    }

    // ---- CAUGHT card ----

    private static BufferedImage renderCaughtCard(BestiaryDataService ds) {
        BestiaryCollection col = ds.getCollection();
        String account = resolveAccount(ds), date = todayStr();

        Map<CreatureRarity, Double> avgQuality = col.creatures.stream().collect(
                Collectors.groupingBy(c -> c.rarity,
                        Collectors.averagingInt(c -> c.powerLevel())));
        List<CapturedCreature> top10 = col.creatures.stream()
                .sorted(Comparator.comparingInt((CapturedCreature c) -> c.powerLevel()).reversed())
                .limit(10).collect(Collectors.toList());

        final int W = 480, PAD = 24;
        int avgRows = 6, topRows = Math.max(1, top10.size());
        int heldH = 20;   // held / sent / received subtitle under the hero
        int H = 4 + PAD + 60 + 12 + 70 + heldH + 12 + 22 + 6 + avgRows * 24 + 8
                + 22 + 6 + topRows * 48 + 8 + 36 + PAD;

        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = cardGraphics(img);

        int y = drawCardBase(g, W, H, account, "CAUGHT", date, PAD);
        y = drawHeroStat(g, FMT.format(col.lifetimeCaptures), "CREATURES CAUGHT", ORANGE, y, W, PAD);
        // Held / sent / received subtitle, centered under the hero.
        g.setFont(FontManager.getRunescapeSmallFont());
        FontMetrics hfm = g.getFontMetrics();
        String heldLine = FMT.format(col.totalCaptures()) + " cards held";
        long sentN = col.lifetimeCardsSent, recvN = col.tradedInCount();
        if (sentN > 0 || recvN > 0) {
            heldLine += "    ·    " + FMT.format(sentN) + " sent    ·    " + FMT.format(recvN) + " received";
        }
        g.setColor(new Color(185, 185, 185));
        g.drawString(heldLine, (W - hfm.stringWidth(heldLine)) / 2, y + hfm.getAscent());
        y += heldH + 12;

        CreatureRarity[] rarOrder = {CreatureRarity.MYTHIC, CreatureRarity.LEGENDARY,
                CreatureRarity.EPIC, CreatureRarity.RARE, CreatureRarity.UNCOMMON, CreatureRarity.COMMON};

        y = drawCardSectionHeader(g, "AVERAGE POWER BY RARITY", y, W, PAD);
        y += 6;
        // Scale to the strongest rarity's average so bars compare (Power Level can exceed 100).
        int avgScaleMax = 1;
        for (Double v : avgQuality.values()) avgScaleMax = Math.max(avgScaleMax, (int) Math.round(v));
        for (CreatureRarity r : rarOrder) {
            if (!avgQuality.containsKey(r)) {
                y = drawCardBarRow(g, r.label, r.displayColor, 0, avgScaleMax, "—", "", y, PAD, W);
            } else {
                int avg = (int) Math.round(avgQuality.get(r));
                y = drawCardBarRow(g, r.label, r.displayColor, avg, avgScaleMax, String.valueOf(avg), "", y, PAD, W);
            }
        }
        y += 8;

        y = drawCardSectionHeader(g, "TOP 10 BY POWER", y, W, PAD);
        y += 6;
        if (top10.isEmpty()) {
            g.setFont(FontManager.getRunescapeSmallFont()); g.setColor(DIM);
            g.drawString("No captures yet", PAD + 6, y + g.getFontMetrics().getAscent()); y += 24;
        } else {
            for (int i = 0; i < top10.size(); i++) {
                CapturedCreature c = top10.get(i);
                int q = c.powerLevel();
                // Name line
                g.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
                FontMetrics fm = g.getFontMetrics();
                g.setColor(c.rarity.displayColor);
                g.drawString((i + 1) + ".  " + c.npcName, PAD + 4, y + fm.getAscent());
                String qs = "PWR:" + q;
                g.setFont(FontManager.getRunescapeSmallFont());
                fm = g.getFontMetrics();
                g.setColor(qualColor(q));
                g.drawString(qs, W - PAD - fm.stringWidth(qs), y + fm.getAscent());
                y += 15;
                // Context line
                g.setColor(new Color(210, 210, 210));
                String ctx = c.rarity.label + "  ·  Kill #" + c.killsBeforeCapture + "  ·  Lv." + c.captureLevel;
                g.drawString(ctx, PAD + 4, y + fm.getAscent());
                y += 15;
                // Provenance line — captured by + reroll count
                g.setColor(new Color(150, 170, 200));
                String owner = c.originalOwner != null && !c.originalOwner.isEmpty() ? c.originalOwner : account;
                String prov = "by " + owner + (c.rerollCount() > 0 ? "    ·    rerolled " + c.rerollCount() + "×" : "");
                g.drawString(prov, PAD + 4, y + fm.getAscent());
                y += 18;
            }
        }
        y += 8;
        drawCardFooter(g, H - 36, W, PAD);
        g.dispose();
        return img;
    }
}
