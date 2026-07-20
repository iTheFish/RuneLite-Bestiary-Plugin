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

    public InfoTab(BestiaryDataService dataService, ProgressionService progressionService) {
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

        JLabel title = new JLabel("Rarity Tiers  (chance improves with level)");
        title.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        title.setForeground(ORANGE);

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
            String s1  = pct1  >= 1.0 ? String.format("%.0f%%", pct1)  : String.format("%.1f%%", pct1);
            String s99 = pct99 >= 1.0 ? String.format("%.0f%%", pct99) : String.format("%.1f%%", pct99);
            rows.add(tableRow("● " + r.label, s1, s99, r.displayColor));
        }

        outer.add(title, BorderLayout.NORTH);
        outer.add(rows,  BorderLayout.CENTER);
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
                "Scales with your Capture Level and the monster's difficulty. " +
                "Beginner: 20% (lv 1) → 60% (lv 99). Boss: 1.5% → 8%."));
        tiles.add(tile("Rarity",
                "Weighted roll on each capture. Higher Capture Level shifts weight toward rarer outcomes — " +
                "Mythic is 12× more likely at level 99 than level 1."));
        tiles.add(tile("Quality",
                "6 stats (Power, Defence, Speed, Agility, Stamina, Intellect), each 0–100. " +
                "Shaped by the monster's archetype — primary stats for that archetype roll high. " +
                "Gold labels mark primary stats in the detail dialog."));
        tiles.add(tile("XP",
                "Kill XP = max(10, combatLvl × 10). " +
                "Captures add XP × rarity multiplier (Common 1×, Rare 5×, Legendary 25×, Mythic 50×)."));
        tiles.add(tile("Overlay",
                "Configurable position (top/bottom, left/centre/right) and width via Config. " +
                "Optional Pokeball animation plays before the result is shown."));
        tiles.add(tile("Chat mode",
                "Verbose: one message per capture with kill# and quality score. " +
                "Batched: accumulates kills of the same NPC+rarity for 5 seconds of inactivity, " +
                "then sends one summary message."));
        tiles.add(tile("Album",
                "Full dex grid showing every capturable species. " +
                "Open via the 'Open Album' button in the By Monster view. " +
                "Supports name search and difficulty filter."));
        tiles.add(tile("Export",
                "Right-click any card or capture row → Export Image. " +
                "Generates a shareable PNG with a 28-character card fingerprint and your player name."));
        tiles.add(tile("Session Recap",
                "Button on the Progress tab. Shows all captures since your last login — " +
                "rarity breakdown, quality scores, and regions. Copy as text or clear the session."));
        tiles.add(tile("Reset",
                "Double-confirm via the Reset Collection button at the bottom of the panel. " +
                "Permanently deletes all captures, kills, XP, and achievements."));

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
