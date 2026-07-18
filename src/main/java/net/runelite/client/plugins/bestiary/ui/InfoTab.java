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
        content.add(tipRow("Dev: Enable 'Force 100% Capture Rate' in the Developer Tools config section."));

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

        strip.add(statBox("Species",  speciesVal));
        strip.add(statBox("Captures", capturesVal));
        strip.add(statBox("Level",    levelVal));
        strip.add(statBox("Kills",    killsVal));

        return strip;
    }

    private static JPanel statBox(String labelText, JLabel valueLabel) {
        JPanel box = new JPanel(new GridLayout(2, 1, 0, 2));
        box.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        box.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 2, 0, 0, ORANGE),
                new EmptyBorder(4, 6, 4, 6)));

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

        JLabel title = new JLabel("Rarity Tiers");
        title.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        title.setForeground(ORANGE);

        // Header row
        JPanel rows = new JPanel();
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.setOpaque(false);

        JPanel headerRow = tableRow("Rarity", "Chance", "XP mult", ColorScheme.MEDIUM_GRAY_COLOR);
        rows.add(headerRow);

        for (CreatureRarity r : CreatureRarity.values()) {
            double pct = r.probability * 100;
            String pctStr = pct >= 1.0
                    ? String.format("%.0f%%", pct)
                    : String.format("%.1f%%", pct);
            String xpStr = (int) r.xpMultiplier + "x";
            rows.add(tableRow("● " + r.label, pctStr, xpStr, r.displayColor));
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

        tiles.add(tile("Capture",    "Random roll on each kill. Base rate + level bonus, capped at max rate."));
        tiles.add(tile("Quality",    "0-100 score based on NPC combat stats at time of capture."));
        tiles.add(tile("XP",         "Kill XP = max(10, combatLvl×10). Captures multiply by rarity bonus."));
        tiles.add(tile("Overlay",    "Top-centre notification shown on each capture. Pokeball animation optional."));
        tiles.add(tile("Verbose",    "One chat message per capture with kill# and quality score."));
        tiles.add(tile("Batched",    "Shows 1x, 2x, 3x count immediately on each kill of same NPC+rarity."));
        tiles.add(tile("Reset",      "Use the Reset Collection button at the bottom of the panel."));

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
        termLabel.setFont(FontManager.getRunescapeFont().deriveFont(Font.BOLD));
        termLabel.setForeground(new Color(255, 200, 80));

        JTextArea defArea = new JTextArea(definition);
        defArea.setFont(FontManager.getRunescapeFont());
        defArea.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
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
