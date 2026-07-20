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
import java.text.NumberFormat;
import java.util.Locale;

public class InfoTab extends JPanel {

    private static final Color ORANGE = new Color(255, 165, 0);
    private static final Color GREEN  = new Color(80, 200, 80);
    private static final NumberFormat FMT = NumberFormat.getNumberInstance(Locale.UK);

    private final BestiaryDataService dataService;
    private final ProgressionService progressionService;

    // Live stat labels
    private final JLabel speciesVal  = statValue("0");
    private final JLabel capturesVal = statValue("0");
    private final JLabel levelVal    = statValue("1");
    private final JLabel killsVal    = statValue("0");

    public InfoTab(BestiaryDataService dataService, ProgressionService progressionService,
                   Runnable openAlbum, Runnable openFavourites, Runnable openRecap) {
        this.dataService        = dataService;
        this.progressionService = progressionService;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Override getPreferredSize so BoxLayout uses the viewport's actual width,
        // allowing JTextArea children to wrap at the panel boundary.
        JPanel content = new JPanel() {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                if (getParent() != null) d.width = getParent().getWidth();
                return d;
            }
        };
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ColorScheme.DARK_GRAY_COLOR);
        content.setBorder(new EmptyBorder(6, 6, 6, 6));

        // Live stats strip
        content.add(buildStatsStrip());
        content.add(Box.createVerticalStrut(6));
        content.add(buildShortcutRow(openAlbum, openFavourites, openRecap));
        content.add(Box.createVerticalStrut(10));

        // Rarity quick-reference table
        content.add(buildRarityTable());
        content.add(Box.createVerticalStrut(10));

        // Slim info tiles
        content.add(buildInfoTiles());
        content.add(Box.createVerticalStrut(8));

        // Dev tip
        content.add(tipRow("Dev: Use 'Capture Rate Override' (FORCE_100 / FORCE_0) and 'Force Rarity' in the Developer Tools config section for testing."));

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(0, 0));
        add(scroll, BorderLayout.CENTER);

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
    // Live stats strip  (4 boxes in one row)
    // -------------------------------------------------------------------------

    private JPanel buildStatsStrip() {
        JPanel strip = new JPanel(new GridLayout(1, 4, 4, 0));
        strip.setOpaque(false);
        strip.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        strip.setAlignmentX(LEFT_ALIGNMENT);

        strip.add(statBox("Species", speciesVal));
        strip.add(statBox("Caught",  capturesVal));
        strip.add(statBox("Level",   levelVal));
        strip.add(statBox("Kills",   killsVal));

        return strip;
    }

    private static JPanel statBox(String labelText, JLabel valueLabel) {
        JPanel box = new JPanel(new GridLayout(2, 1, 0, 2));
        box.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        box.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 2, 0, 0, ORANGE),
                new EmptyBorder(4, 3, 4, 3)));

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
        l.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD).deriveFont(13f));
        l.setForeground(ORANGE);
        return l;
    }

    private static JPanel buildShortcutRow(Runnable openAlbum, Runnable openFavourites, Runnable openRecap) {
        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setOpaque(false);
        container.setAlignmentX(LEFT_ALIGNMENT);

        JPanel topRow = new JPanel(new GridLayout(1, 2, 4, 0));
        topRow.setOpaque(false);
        topRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        topRow.setAlignmentX(LEFT_ALIGNMENT);
        topRow.add(shortcutBtn("Open Album",   ORANGE,                  openAlbum));
        topRow.add(shortcutBtn("Favourites",   new Color(220, 180, 60), openFavourites));

        JButton recapBtn = shortcutBtn("Session Recap", new Color(120, 200, 120), openRecap);
        recapBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        recapBtn.setAlignmentX(LEFT_ALIGNMENT);

        container.add(topRow);
        container.add(Box.createVerticalStrut(4));
        container.add(recapBtn);
        return container;
    }

    private static JButton shortcutBtn(String text, Color fg, Runnable action) {
        JButton btn = new JButton(text);
        btn.setFont(FontManager.getRunescapeSmallFont());
        btn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        btn.setForeground(fg);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
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

        JLabel subtitle = new JLabel("Catch chance improves with your Capture Level");
        subtitle.setFont(FontManager.getRunescapeSmallFont());
        subtitle.setForeground(new Color(120, 120, 120));
        subtitle.setToolTipText("Catch chance improves with your Capture Level");

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
    // Slim info tiles (term | definition layout)
    // -------------------------------------------------------------------------

    private JPanel buildInfoTiles() {
        JPanel outer = new JPanel(new BorderLayout(0, 4));
        outer.setOpaque(false);
        outer.setAlignmentX(LEFT_ALIGNMENT);
        outer.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 3, 0, 0, ORANGE),
                new EmptyBorder(3, 8, 3, 0)));

        JLabel title = new JLabel("How It Works");
        title.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        title.setForeground(ORANGE);

        JPanel tiles = new JPanel();
        tiles.setLayout(new BoxLayout(tiles, BoxLayout.Y_AXIS));
        tiles.setOpaque(false);

        tiles.add(tile("Catch rate",
                "Each kill rolls a capture attempt. The chance depends on two things: " +
                "the monster's difficulty tier and your current Capture Level.\n\n" +
                "Beginner monsters (cows, goblins): 20% at level 1, rising to 60% at level 99.\n" +
                "Easy (lesser demons, skeletons): 15% → 50%.\n" +
                "Medium (hill giants, moss giants): 10% → 40%.\n" +
                "Hard (hellhounds, gargoyles): 6% → 28%.\n" +
                "Elite (Adamant/Rune dragons): 3% → 15%.\n" +
                "Boss (Cerberus, Callisto, etc.): 1.5% → 8%."));
        tiles.add(tile("Rarity",
                "When a capture succeeds, a second weighted roll picks the rarity. " +
                "At level 1 the weights match the base percentages shown in the table above. " +
                "Each level shifts weight toward rarer outcomes.\n\n" +
                "Example: Mythic goes from 0.1% at level 1 to 1.5% at level 99 — " +
                "about 15 times more likely. Common drops from 75% to 46% over the same range."));
        tiles.add(tile("Quality",
                "Every capture has six individual stats, each scored 0–100:\n" +
                "Strength, Speed, Endurance, Intelligence, Stealth, Vitality.\n\n" +
                "Which stats roll high depends on the monster's archetype " +
                "(e.g. a Brute rolls high Strength; a Nimble rolls high Speed and Stealth). " +
                "Higher rarities shift all stats toward the top — a Mythic capture will generally " +
                "score much higher than a Common of the same species.\n\n" +
                "Gold-outlined bars in the detail dialog mark your personal bests."));
        tiles.add(tile("XP",
                "You earn experience from kills and captures.\n\n" +
                "Kill XP = maximum of 10 or (combat level × 10). " +
                "A level 50 enemy gives 500 XP per kill; a level 1 enemy gives at least 10 XP.\n\n" +
                "Captures add a bonus on top: the kill XP multiplied by the rarity. " +
                "Common: 1×, Uncommon: 2×, Rare: 5×, Epic: 10×, Legendary: 25×, Mythic: 50×.\n\n" +
                "Example: catching a Rare goblin (level 2, kill XP = 20) gives 20 × 5 = 100 bonus XP."));
        tiles.add(tile("Overlay",
                "A small notification panel appears on screen each time a capture succeeds. " +
                "Position (top-left, top-centre, top-right, bottom-left, bottom-right) and width " +
                "(150–300 px) are configurable in the RuneLite Config panel under Bestiary.\n\n" +
                "An optional Pokeball-style animation can play on every kill attempt before " +
                "the result is revealed — toggle 'Show Capture Animation' in Config."));
        tiles.add(tile("Chat notifications",
                "Two modes, selected in Config under 'Chat Notification Mode':\n\n" +
                "Verbose — one chat message per capture showing the rarity, NPC name, kill number, " +
                "and quality score. The kill number ensures no two messages are identical " +
                "(RuneLite silently drops duplicate messages).\n\n" +
                "Batched — if you kill the same NPC+rarity multiple times in quick succession, " +
                "messages are held for 5 seconds of inactivity then sent as one summary " +
                "(e.g. '3× Common Goblin captured!  Kill #42  Q:28, 35, 41')."));
        tiles.add(tile("Album",
                "A full dex grid showing every capturable species in the game. " +
                "Open it via the 'Open Album' button at the top of the By Monster view.\n\n" +
                "Each card shows the species image, average stats, combat level, difficulty tier, " +
                "and rarity dots for all rarities you have caught. " +
                "Use the search box or difficulty dropdown to filter the grid."));
        tiles.add(tile("Export",
                "Right-click any card in the Collection or Album views, or right-click a capture " +
                "row in the detail dialog → 'Export Card'.\n\n" +
                "Opens a preview of the capture as a large card. " +
                "Copy to clipboard or save as a PNG. " +
                "Each card has a unique 28-character fingerprint encoding the species, " +
                "all six stat values, rarity, and your player name."));
        tiles.add(tile("Session Recap",
                "A button on the Progress tab shows all captures made since you last logged in.\n\n" +
                "The recap lists every capture with its rarity (colour-coded), quality score, " +
                "region, and time. A rarity pill summary at the top shows your totals at a glance.\n\n" +
                "'Copy Summary' places the list on your clipboard, formatted as a code block " +
                "so it displays cleanly when pasted into Discord or a text editor."));
        tiles.add(tile("Reset Collection",
                "The 'Reset Collection' button at the bottom of the panel permanently deletes " +
                "all captures, kill counts, XP, levels, and achievements. " +
                "You will be asked to confirm twice before anything is erased."));

        outer.add(title, BorderLayout.NORTH);
        outer.add(tiles, BorderLayout.CENTER);
        return outer;
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
