package net.runelite.client.plugins.bestiary.ui;

import net.runelite.client.plugins.bestiary.model.BestiaryCollection;
import net.runelite.client.plugins.bestiary.model.CreatureRarity;
import net.runelite.client.plugins.bestiary.service.BestiaryDataService;
import net.runelite.client.plugins.bestiary.service.ProgressionService;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The Info tab: a persistent header (live stat boxes + shortcut buttons) over a set of
 * category sub-tabs. Each sub-tab swaps the scrollable content below it (invisible
 * scrollbar), so the reference material reads as tidy sections instead of one long
 * uncategorised wall of text.
 */
public class InfoTab extends JPanel {

    private static final Color ORANGE = new Color(255, 165, 0);
    private static final Color GREEN  = new Color(80, 200, 80);
    private static final NumberFormat FMT = NumberFormat.getNumberInstance(Locale.UK);

    private final BestiaryDataService dataService;
    private final ProgressionService  progressionService;
    private final Consumer<DashboardDialog.DashView> openDashboard;
    private final Consumer<DashboardDialog.DashView> exportDashboard;

    // Live stat labels
    private final JLabel speciesVal  = statValue("0");
    private final JLabel capturesVal = statValue("0");
    private final JLabel levelVal    = statValue("1");
    private final JLabel killsVal    = statValue("0");

    // Category sub-tabs
    private final JPanel contentCards = new JPanel(new CardLayout());
    private final List<JToggleButton> catButtons = new ArrayList<>();

    public InfoTab(BestiaryDataService dataService, ProgressionService progressionService,
                   Runnable openAlbum, Runnable openFavourites, Runnable openRecap,
                   Runnable openCatchRates,
                   Consumer<DashboardDialog.DashView> openDashboard,
                   Consumer<DashboardDialog.DashView> exportDashboard) {
        this.dataService        = dataService;
        this.progressionService = progressionService;
        this.openDashboard      = openDashboard;
        this.exportDashboard    = exportDashboard;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Persistent header: live stats + shortcuts + the category bar
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(ColorScheme.DARK_GRAY_COLOR);
        header.setBorder(new EmptyBorder(6, 6, 4, 6));
        header.add(buildStatsStrip());
        header.add(Box.createVerticalStrut(6));
        header.add(buildShortcutRow(openAlbum, openFavourites, openRecap, openCatchRates));
        header.add(Box.createVerticalStrut(8));
        header.add(headerDivider());
        header.add(Box.createVerticalStrut(6));
        header.add(buildSubTabBar());
        add(header, BorderLayout.NORTH);

        // One scrollable card per category
        contentCards.setBackground(ColorScheme.DARK_GRAY_COLOR);
        addCategory(0, this::fillCapturing);
        addCategory(1, this::fillCards);
        addCategory(2, this::fillEconomy);
        addCategory(3, this::fillProgress);
        addCategory(4, this::fillAlerts);
        add(contentCards, BorderLayout.CENTER);

        selectCategory(0);
        refresh();
    }

    public void refresh() {
        BestiaryCollection col = dataService.getCollection();
        speciesVal.setText(String.valueOf(col.uniqueSpeciesCount()));
        capturesVal.setText(FMT.format(col.totalCaptures()));
        levelVal.setText(String.valueOf(progressionService.getLevel()));
        killsVal.setText(FMT.format(col.totalKills()));
    }

    // -------------------------------------------------------------------------
    // Category sub-tabs
    // -------------------------------------------------------------------------

    private static final String[] CATEGORIES = {"Capturing", "Cards", "Economy", "Progress", "Alerts"};

    private JPanel buildSubTabBar() {
        JPanel bar = new JPanel();
        bar.setLayout(new BoxLayout(bar, BoxLayout.Y_AXIS));
        bar.setOpaque(false);
        bar.setAlignmentX(LEFT_ALIGNMENT);

        // 3 on top, 2 below — keeps labels readable in the narrow side panel.
        JPanel row1 = new JPanel(new GridLayout(1, 3, 4, 0));
        JPanel row2 = new JPanel(new GridLayout(1, 2, 4, 0));
        row1.setOpaque(false); row2.setOpaque(false);
        row1.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        row2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        row1.setAlignmentX(LEFT_ALIGNMENT); row2.setAlignmentX(LEFT_ALIGNMENT);

        for (int i = 0; i < CATEGORIES.length; i++) {
            final int idx = i;
            JToggleButton b = new JToggleButton(CATEGORIES[i]);
            b.setFont(FontManager.getRunescapeSmallFont());
            b.setFocusPainted(false);
            b.setBorderPainted(false);
            b.setMargin(new Insets(2, 2, 2, 2));
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            b.addActionListener(e -> selectCategory(idx));
            styleTab(b, false);
            catButtons.add(b);
            (i < 3 ? row1 : row2).add(b);
        }

        bar.add(row1);
        bar.add(Box.createVerticalStrut(4));
        bar.add(row2);
        return bar;
    }

    private void selectCategory(int idx) {
        ((CardLayout) contentCards.getLayout()).show(contentCards, "cat" + idx);
        for (int i = 0; i < catButtons.size(); i++) {
            styleTab(catButtons.get(i), i == idx);
            catButtons.get(i).setSelected(i == idx);
        }
    }

    /** Orange rule separating the header block from the category tabs. */
    private static JComponent headerDivider() {
        JSeparator s = new JSeparator();
        s.setForeground(new Color(255, 165, 0, 90));
        s.setBackground(ColorScheme.DARK_GRAY_COLOR);
        s.setAlignmentX(LEFT_ALIGNMENT);
        s.setMaximumSize(new Dimension(Integer.MAX_VALUE, 2));
        return s;
    }

    private static void styleTab(JToggleButton b, boolean active) {
        b.setOpaque(true);
        b.setBackground(active ? ORANGE : ColorScheme.DARKER_GRAY_COLOR);
        b.setForeground(active ? new Color(30, 30, 30) : ColorScheme.LIGHT_GRAY_COLOR);
    }

    /** Builds a scrollable (invisible scrollbar) content card and registers it under "cat{idx}". */
    private void addCategory(int idx, Consumer<JPanel> fill) {
        JPanel content = new JPanel() {
            @Override
            public Dimension getPreferredSize() {
                // Track the viewport width so JTextArea tiles wrap at the panel edge.
                Dimension d = super.getPreferredSize();
                if (getParent() != null) d.width = getParent().getWidth();
                return d;
            }
        };
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ColorScheme.DARK_GRAY_COLOR);
        content.setBorder(new EmptyBorder(6, 6, 8, 6));
        fill.accept(content);

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(0, 0));
        contentCards.add(scroll, "cat" + idx);
    }

    // -------------------------------------------------------------------------
    // Category content
    // -------------------------------------------------------------------------

    private void fillCapturing(JPanel c) {
        c.add(buildRarityTable());
        JPanel catchHint = noteArea("Click 'Catch Rates' above to see your current chances.",
                new Color(205, 205, 205));
        catchHint.setBorder(new EmptyBorder(3, 11, 0, 0));
        c.add(catchHint);
        c.add(Box.createVerticalStrut(8));
        c.add(sectionTitle("How capturing works"));
        c.add(tile("Catch rate",
                "Each kill rolls a capture attempt. The chance depends on two things: " +
                "the monster's difficulty tier and your current Capture Level.\n\n" +
                "Beginner (cows, goblins): 20% at level 1, rising to 60% at level 99.\n" +
                "Easy (lesser demons, skeletons): 15% → 50%.\n" +
                "Medium (hill giants, moss giants): 10% → 40%.\n" +
                "Hard (hellhounds, gargoyles): 6% → 28%.\n" +
                "Elite (Adamant/Rune dragons): 3% → 15%.\n" +
                "Boss (Cerberus, Callisto, etc.): 1.5% → 8%.\n\n" +
                "Only catalogued roster monsters are tracked — off-roster NPCs are ignored."));
        c.add(tile("Rarity",
                "When a capture succeeds, a second weighted roll picks the rarity. " +
                "At level 1 the weights match the base percentages in the table above; " +
                "each level shifts weight toward rarer outcomes.\n\n" +
                "Example: Mythic goes from 0.1% at level 1 to ~1.5% at level 99 — " +
                "about 15× more likely. Common drops from ~75% to ~46% over the same range."));
        c.add(tile("Shiny",
                "After rarity, a third independent roll decides whether the capture is shiny. " +
                "It is orthogonal to rarity — any rarity can be shiny, from a Common to a Mythic.\n\n" +
                "The chance scales with your level: 0.2% at level 1 up to 2% at level 99.\n\n" +
                "A shiny always rolls near-max stats (the top of its band), gets a golden card " +
                "with twinkling sparkles, and is announced in chat with a ✦ SHINY ✦ marker."));
    }

    private void fillCards(JPanel c) {
        c.add(sectionTitle("Reading a card"));
        c.add(tile("Power Level",
                "Power Level is a card's headline number. It blends the monster's real " +
                "Hitpoints (a factual OSRS attribute, shown on the card) with the seven rolled stats.\n\n" +
                "Power Level = average of the 7 stats + HP ÷ 6.\n\n" +
                "The stat average stays on the 1–99 scale, while HP is added separately so it " +
                "separates the difficulty tiers: HP adds about +13 at 80 HP, +40 at 250 HP, and " +
                "+165 at 1000 HP. The rolled stats are mostly flavour — HP drives power."));
        c.add(tile("Stats & class",
                "Every capture rolls seven stats — Attack, Strength, Defence, Magic, Ranged, Agility " +
                "and Prayer (Prayer rolls on a smaller scale). The " +
                "monster's combat class decides which tend to roll high — a Warrior favours " +
                "Attack/Strength, a Marksman favours Ranged, an Occultist favours Magic, and so on.\n\n" +
                "Higher rarities lift the whole roll toward 99, and bands overlap — so a lucky Rare " +
                "can beat an unlucky Epic."));
        c.add(tile("Album",
                "A full dex grid of every capturable species. Open it via 'Open Album' (in all " +
                "Collection views and on this tab).\n\n" +
                "Clicking a species card opens a detail view of all your captures of it, paginated " +
                "(8 / 12 / 16 per page) with a sort dropdown and rarity filter. Each catalog card " +
                "shows the species image, combat level, difficulty tier, and rarity dots for " +
                "rarities you have caught. Search or filter by difficulty to narrow the catalog."));
        c.add(tile("Favourites",
                "Right-click any card or row → 'Add to Favourites' to star it (up to 20). " +
                "Remove a star the same way.\n\n" +
                "The ★ Favourites button in the Collection header shows all starred cards. In the " +
                "Album, a ★ Favourites shortcut opens a detail view of every starred capture."));
        c.add(tile("Export",
                "Right-click any card in Collection, Favourites, or Album → 'Export Card'.\n\n" +
                "Opens a scaled preview. Copy to clipboard or save as PNG. Each footer shows the " +
                "card's unique ID, the player who captured it, a 'Rerolled N times' line if it has " +
                "been rerolled, and the OSRS | Bestiary stamp.\n\n" +
                "In the Album detail view, 'Export Page' saves the current page as a grid image."));
    }

    private void fillEconomy(JPanel c) {
        c.add(sectionTitle("Credits & the shop"));
        c.add(tile("Bestiary Credits",
                "You earn Bestiary Credits on every successful capture. The award scales with " +
                "difficulty × rarity, and a shiny doubles it.\n\n" +
                "Rough guide: a Beginner Common is worth a couple of credits; a Boss Mythic is " +
                "worth about 480 (960 if shiny).\n\n" +
                "Spend them on the Card Reroller below — more shop features are on the way."));
        c.add(tile("Card Reroller",
                "Right-click a card → 'Reroll (shop)…' to re-roll its stats and shiny at the " +
                "same monster and rarity — a chance to improve a roll or hit a shiny.\n\n" +
                "The cost scales with the card's difficulty × rarity (shiny doesn't change it): from " +
                "25 credits for a Beginner Common up to 4,000 for a Boss Mythic.\n\n" +
                "A shiny stays shiny. Non-Mythic cards have a 5% chance to rank up one rarity. Your " +
                "favourite, nickname and album cover are kept. A rerolled card is marked " +
                "'Rerolled N times' and shows a before/after result with a 'What were the odds?' " +
                "breakdown — remember those odds describe a raw pull, not a rerolled card."));
        c.add(tile("Discard",
                "Don't want a card? Right-click → 'Discard…' to trade it for credits — the refund is " +
                "its base capture value, and shinies add a flat bonus. From the Album you can " +
                "multi-select to discard several at once.\n\n" +
                "Discarding is permanent: the card is removed from your collection."));
        c.add(tile("Shop",
                "The Shop tab is where credits are spent. The Card Reroller is the first tool; the " +
                "wider shop economy (passive unlocks, more tools) is still being built."));
    }

    private void fillProgress(JPanel c) {
        c.add(sectionTitle("Progress & stats"));
        c.add(tile("XP & levels",
                "You earn experience from kills and captures. Your Capture Level runs 1–99 " +
                "(with virtual levels beyond).\n\n" +
                "Kill XP = max of 10 or (combat level × 10). A level 50 enemy gives 500 XP per kill.\n\n" +
                "Captures add a bonus: the kill XP × the rarity multiplier — Common 1×, Uncommon 2×, " +
                "Rare 5×, Epic 10×, Legendary 25×, Mythic 50×.\n\n" +
                "Example: a Rare goblin (level 2, kill XP 20) gives 20 × 5 = 100 bonus XP."));
        c.add(tile("Dashboards",
                "The four stat boxes at the top of this tab are clickable — each opens a dashboard: " +
                "Progression, Kills, Species and Caught.\n\n" +
                "They break down your collection with bar charts and top-10 tables. Right-click a " +
                "box to copy that dashboard as a shareable card image."));
        c.add(tile("Session Recap",
                "A button on the Progress tab shows every capture made since you last logged in, " +
                "with rarity (colour-coded), Power Level, region and time, plus a rarity summary.\n\n" +
                "'Copy Summary' places the list on your clipboard as a code block so it pastes " +
                "cleanly into Discord."));
    }

    private void fillAlerts(JPanel c) {
        c.add(sectionTitle("Notifications & data"));
        c.add(tile("Capture overlay",
                "A small notification panel appears on screen each time a capture succeeds. " +
                "Position and width are configurable in the RuneLite Config panel under Bestiary.\n\n" +
                "An optional collection-jar animation can play on every kill attempt before the " +
                "result is revealed — toggle 'Show Capture Animation' in Config. Rapid kills queue " +
                "so every result still plays."));
        c.add(tile("Chat notifications",
                "Two modes, selected in Config under 'Chat Notification Mode':\n\n" +
                "Verbose — one message per capture with rarity, NPC name, kill number and Power " +
                "Level. The kill number keeps messages unique (RuneLite drops duplicates).\n\n" +
                "Batched — repeated NPC+rarity kills are held for 5 seconds of inactivity then sent " +
                "as one summary (e.g. '3× Common Goblin captured!  Kill #42  PWR:28, 35, 41'). " +
                "Shinies always announce immediately."));
        c.add(tile("Reset Collection",
                "The 'Reset Collection' button at the bottom of the panel permanently deletes all " +
                "captures, kill counts, XP, levels and achievements. You are asked to confirm twice."));
        c.add(Box.createVerticalStrut(4));
        c.add(tipRow("Dev: use 'Capture Rate Override' (FORCE_100 / FORCE_0), 'Force Rarity' and " +
                "'Always Roll Shiny' in the Developer Tools config section for testing."));
    }

    // -------------------------------------------------------------------------
    // Live stats strip  (4 boxes in one row)
    // -------------------------------------------------------------------------

    private JPanel buildStatsStrip() {
        JPanel strip = new JPanel(new GridLayout(2, 2, 4, 4));
        strip.setOpaque(false);
        strip.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        strip.setAlignmentX(LEFT_ALIGNMENT);

        strip.add(clickable(statBox("Level",   levelVal,    false), DashboardDialog.DashView.PROGRESSION));
        strip.add(clickable(statBox("Kills",   killsVal,    true),  DashboardDialog.DashView.KILLS));
        strip.add(clickable(statBox("Species", speciesVal,  false), DashboardDialog.DashView.SPECIES));
        strip.add(clickable(statBox("Caught",  capturesVal, true),  DashboardDialog.DashView.CAUGHT));

        return strip;
    }

    private JPanel clickable(JPanel panel, DashboardDialog.DashView view) {
        panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        panel.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    if (openDashboard != null) openDashboard.accept(view);
                } else if (e.getButton() == MouseEvent.BUTTON3) {
                    JPopupMenu menu = new JPopupMenu();
                    JMenuItem open = new JMenuItem("Open Dashboard — " + view.label);
                    open.addActionListener(ev -> { if (openDashboard != null) openDashboard.accept(view); });
                    JMenuItem copy = new JMenuItem("Copy " + view.label + " Card");
                    copy.addActionListener(ev -> { if (exportDashboard != null) exportDashboard.accept(view); });
                    menu.add(open);
                    menu.add(copy);
                    menu.show(panel, e.getX(), e.getY());
                }
            }
        });
        return panel;
    }

    private static JPanel statBox(String labelText, JLabel valueLabel, boolean rightAccent) {
        JPanel box = new JPanel(new GridLayout(2, 1, 0, 2));
        box.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        box.setBorder(BorderFactory.createCompoundBorder(
                rightAccent ? new MatteBorder(0, 3, 0, 3, ORANGE)
                            : new MatteBorder(0, 3, 0, 0, ORANGE),
                new EmptyBorder(8, 6, 6, 6)));

        JLabel label = new JLabel(labelText, SwingConstants.CENTER);
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        valueLabel.setHorizontalAlignment(SwingConstants.CENTER);

        box.add(valueLabel);
        box.add(label);
        return box;
    }

    private static JLabel statValue(String text) {
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setFont(FontManager.getRunescapeBoldFont());
        l.setForeground(ORANGE);
        return l;
    }

    private static JPanel buildShortcutRow(Runnable openAlbum, Runnable openFavourites,
                                            Runnable openRecap, Runnable openCatchRates) {
        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setOpaque(false);
        container.setAlignmentX(LEFT_ALIGNMENT);

        // Full-width Open Album (top), Favourites + Catch Rates (middle), full-width Session Recap (bottom).
        JPanel albumRow = new JPanel(new GridLayout(1, 1));
        albumRow.setOpaque(false);
        albumRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        albumRow.setAlignmentX(LEFT_ALIGNMENT);
        albumRow.add(blockBtn("Open Album", ORANGE, openAlbum));

        JPanel midRow = new JPanel(new GridLayout(1, 2, 4, 0));
        midRow.setOpaque(false);
        midRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        midRow.setAlignmentX(LEFT_ALIGNMENT);
        midRow.add(blockBtn("★ Favourites", new Color(220, 180, 60), openFavourites));
        JButton catchBtn = blockBtn(" Catch Rates", new Color(100, 180, 220), openCatchRates);
        final int iD = 13;
        catchBtn.setIcon(new Icon() {
            @Override public int getIconWidth()  { return iD; }
            @Override public int getIconHeight() { return iD; }
            @Override public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.fillOval(x, y, iD, iD);
                g2.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
                FontMetrics fm = g2.getFontMetrics();
                g2.setColor(new Color(30, 30, 30));
                String ch = "i";
                g2.drawString(ch, x + (iD - fm.stringWidth(ch)) / 2,
                        y + (iD + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        });
        catchBtn.setIconTextGap(3);
        midRow.add(catchBtn);

        JPanel recapRow = new JPanel(new GridLayout(1, 1));
        recapRow.setOpaque(false);
        recapRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        recapRow.setAlignmentX(LEFT_ALIGNMENT);
        recapRow.add(blockBtn("Session Recap", new Color(120, 200, 120), openRecap));

        container.add(albumRow);
        container.add(Box.createVerticalStrut(4));
        container.add(midRow);
        container.add(Box.createVerticalStrut(4));
        container.add(recapRow);
        return container;
    }

    /** A chunky, header-style shortcut button (orange left accent, like the stat boxes). */
    private static JButton blockBtn(String text, Color fg, Runnable action) {
        JButton btn = new JButton(text);
        btn.setFont(FontManager.getRunescapeSmallFont());
        btn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        btn.setForeground(fg);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 3, 0, 0, ORANGE),
                new EmptyBorder(4, 6, 4, 6)));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addActionListener(e -> action.run());
        return btn;
    }

    // -------------------------------------------------------------------------
    // Rarity quick-reference table
    // -------------------------------------------------------------------------

    private JPanel buildRarityTable() {
        JPanel outer = new JPanel(new BorderLayout(0, 4));
        outer.setOpaque(false);
        outer.setAlignmentX(LEFT_ALIGNMENT);
        outer.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 3, 0, 0, ORANGE),
                new EmptyBorder(3, 8, 3, 0)));

        JLabel title = new JLabel("Rarity Tiers");
        title.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        title.setForeground(ORANGE);

        JTextArea subtitle = new JTextArea("Catch chance improves with your Capture Level.");
        subtitle.setFont(FontManager.getRunescapeSmallFont());
        subtitle.setForeground(new Color(190, 190, 190));
        subtitle.setBackground(ColorScheme.DARK_GRAY_COLOR);
        subtitle.setOpaque(false);
        subtitle.setEditable(false);
        subtitle.setFocusable(false);
        subtitle.setLineWrap(true);
        subtitle.setWrapStyleWord(true);

        JPanel titleBlock = new JPanel(new BorderLayout(0, 1));
        titleBlock.setOpaque(false);
        titleBlock.add(title,    BorderLayout.NORTH);
        titleBlock.add(subtitle, BorderLayout.CENTER);

        // Pre-compute level-99 normalised percentages
        // Multipliers mirror RarityRoller: COMMON 0.50, UNCOMMON 1.30, RARE 2.00,
        // EPIC 4.00, LEGENDARY 8.00, MYTHIC 12.0
        double[] mult99 = {0.50, 1.30, 2.00, 4.00, 8.00, 12.0};
        CreatureRarity[] rarities = CreatureRarity.values();
        double total99 = 0.0;
        double[] w99 = new double[rarities.length];
        for (int i = 0; i < rarities.length; i++) {
            w99[i] = rarities[i].probability * mult99[i];
            total99 += w99[i];
        }

        JPanel rows = new JPanel();
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.setOpaque(false);

        rows.add(tableRow("Rarity", "Lv 1", "Lv 99", new Color(200, 200, 200)));

        for (int i = 0; i < rarities.length; i++) {
            CreatureRarity r = rarities[i];
            double pct1  = r.probability * 100;
            double pct99 = w99[i] / total99 * 100;
            String s1  = pct1  >= 10.0 ? String.format("%.0f%%", pct1)  : String.format("%.1f%%", pct1);
            String s99 = pct99 >= 10.0 ? String.format("%.0f%%", pct99) : String.format("%.1f%%", pct99);
            rows.add(tableRow("● " + r.label, s1, s99, r.displayColor));
        }

        outer.add(titleBlock, BorderLayout.NORTH);
        outer.add(rows,       BorderLayout.CENTER);
        return outer;
    }

    private static JPanel tableRow(String col1, String col2, String col3, Color color) {
        JPanel row = new JPanel(new GridLayout(1, 3, 0, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));

        JLabel l1 = new JLabel(col1);
        JLabel l2 = new JLabel(col2, SwingConstants.CENTER);
        JLabel l3 = new JLabel(col3, SwingConstants.RIGHT);

        for (JLabel l : new JLabel[]{l1, l2, l3}) {
            l.setFont(FontManager.getRunescapeSmallFont());
            l.setForeground(color);
        }

        row.add(l1);
        row.add(l2);
        row.add(l3);
        return row;
    }

    // -------------------------------------------------------------------------
    // Shared tile / section helpers
    // -------------------------------------------------------------------------

    private static JLabel sectionTitle(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        l.setForeground(ORANGE);
        l.setAlignmentX(LEFT_ALIGNMENT);
        l.setBorder(new EmptyBorder(0, 0, 4, 0));
        return l;
    }

    /** A wrapping, label-style note (JTextArea so long text reflows at the panel width). */
    private static JPanel noteArea(String text, Color colour) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setAlignmentX(LEFT_ALIGNMENT);

        JTextArea a = new JTextArea(text);
        a.setFont(FontManager.getRunescapeSmallFont());
        a.setForeground(colour);
        a.setBackground(ColorScheme.DARK_GRAY_COLOR);
        a.setOpaque(false);
        a.setEditable(false);
        a.setFocusable(false);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);

        panel.add(a, BorderLayout.CENTER);
        return panel;
    }

    private static JPanel tile(String term, String definition) {
        // Title on NORTH, JTextArea on CENTER — BorderLayout gives CENTER full width
        // so lineWrap fires correctly without needing a fixed pixel width.
        JPanel panel = new JPanel(new BorderLayout(0, 2));
        panel.setOpaque(false);
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.setBorder(new EmptyBorder(4, 0, 5, 0));

        JLabel termLabel = new JLabel(term);
        termLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        termLabel.setForeground(new Color(255, 200, 80));

        JTextArea defArea = new JTextArea(definition);
        defArea.setFont(FontManager.getRunescapeSmallFont());
        defArea.setForeground(new Color(210, 210, 210));
        defArea.setBackground(ColorScheme.DARK_GRAY_COLOR);
        defArea.setEditable(false);
        defArea.setFocusable(false);
        defArea.setLineWrap(true);
        defArea.setWrapStyleWord(true);
        defArea.setBorder(new EmptyBorder(0, 6, 0, 0));

        panel.add(termLabel, BorderLayout.NORTH);
        panel.add(defArea,   BorderLayout.CENTER);
        return panel;
    }

    private static JPanel tipRow(String text) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 3, 0, 0, GREEN),
                new EmptyBorder(3, 8, 3, 0)));

        JLabel label = new JLabel("<html>" + text + "</html>");
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setForeground(GREEN);
        panel.add(label, BorderLayout.CENTER);
        return panel;
    }
}
