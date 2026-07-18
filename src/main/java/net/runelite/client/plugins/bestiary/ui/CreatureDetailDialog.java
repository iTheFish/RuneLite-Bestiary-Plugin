package net.runelite.client.plugins.bestiary.ui;

import net.runelite.client.plugins.bestiary.model.BestiaryCollection;
import net.runelite.client.plugins.bestiary.model.CapturedCreature;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * MODELESS dialog showing capture history for one NPC+rarity combination.
 * Only one instance can be visible at a time — opening a new one disposes the previous.
 */
public class CreatureDetailDialog extends JDialog {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm").withZone(ZoneId.systemDefault());

    private static final String[] SORT_OPTIONS = {
        "Newest first", "Oldest first", "Quality (high)", "Quality (low)", "By region"
    };

    /** Ensures only one dialog is open at a time. */
    private static CreatureDetailDialog current;

    private final List<CapturedCreature> captures;
    private final BestiaryCollection collection;
    private final JPanel listPanel;

    public CreatureDetailDialog(Window owner, List<CapturedCreature> captures,
                                BestiaryCollection collection) {
        super(owner, "Bestiary Detail", ModalityType.MODELESS);

        // Close any existing detail dialog before opening a new one
        if (current != null && current.isShowing()) {
            current.dispose();
        }
        current = this;

        this.captures   = captures;
        this.collection = collection;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        CapturedCreature sample = captures.get(0);

        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBackground(ColorScheme.DARK_GRAY_COLOR);
        root.setBorder(new EmptyBorder(12, 12, 12, 12));

        // Header: NPC name + rarity + capture count
        JPanel header = new JPanel(new BorderLayout(0, 2));
        header.setOpaque(false);
        header.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 4, 0, 0, sample.rarity.displayColor),
                new EmptyBorder(4, 8, 4, 0)));

        JLabel titleLabel = new JLabel(sample.npcName + " — " + sample.rarity.label
                + " (" + captures.size() + " capture" + (captures.size() == 1 ? "" : "s") + ")");
        titleLabel.setFont(FontManager.getRunescapeBoldFont());
        titleLabel.setForeground(Color.WHITE);

        String combatText = sample.npcCombatLevel > 0 ? "Combat level " + sample.npcCombatLevel : "Non-combat";
        JLabel subLabel = new JLabel(combatText);
        subLabel.setFont(FontManager.getRunescapeSmallFont());
        subLabel.setForeground(sample.rarity.displayColor);

        header.add(titleLabel, BorderLayout.NORTH);
        header.add(subLabel,   BorderLayout.CENTER);

        // Sort control
        JPanel sortRow = new JPanel(new BorderLayout(6, 0));
        sortRow.setOpaque(false);
        JLabel sortLabel = new JLabel("Sort:");
        sortLabel.setFont(FontManager.getRunescapeSmallFont());
        sortLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        JComboBox<String> sortBox = new JComboBox<>(SORT_OPTIONS);
        sortBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        sortBox.setForeground(Color.WHITE);
        sortBox.setFont(FontManager.getRunescapeSmallFont());
        sortRow.add(sortLabel, BorderLayout.WEST);
        sortRow.add(sortBox,   BorderLayout.CENTER);

        // Capture list
        listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        buildList("Newest first");
        sortBox.addActionListener(e -> buildList((String) sortBox.getSelectedItem()));

        JScrollPane scroll = new JScrollPane(listPanel);
        scroll.setPreferredSize(new Dimension(400, Math.min(captures.size() * 72 + 16, 420)));
        scroll.setBorder(null);
        scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Footer: kill / capture summary
        int kills    = collection.getKillCount(sample.npcName);
        int totalCap = collection.getCaptureCount(sample.npcName);
        String ratio = kills > 0 ? "1 in " + Math.round((double) kills / Math.max(1, totalCap)) : "—";
        JLabel footer = new JLabel(String.format(
                "Total kills: %,d  |  All captures: %d  |  Kill ratio: %s", kills, totalCap, ratio));
        footer.setFont(FontManager.getRunescapeSmallFont());
        footer.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        JPanel centerBlock = new JPanel(new BorderLayout(0, 4));
        centerBlock.setOpaque(false);
        centerBlock.add(sortRow, BorderLayout.NORTH);
        centerBlock.add(scroll,  BorderLayout.CENTER);

        root.add(header,      BorderLayout.NORTH);
        root.add(centerBlock, BorderLayout.CENTER);
        root.add(footer,      BorderLayout.SOUTH);

        setContentPane(root);
        pack();
        setLocationRelativeTo(owner);
    }

    private void buildList(String sortMode) {
        List<CapturedCreature> sorted = new ArrayList<>(captures);
        switch (sortMode) {
            case "Newest first":
                sorted.sort(Comparator.comparing((CapturedCreature c) -> c.captureTime).reversed());
                break;
            case "Oldest first":
                sorted.sort(Comparator.comparing(c -> c.captureTime));
                break;
            case "Quality (high)":
                sorted.sort(Comparator.comparingInt((CapturedCreature c) -> c.quality.overallRating()).reversed());
                break;
            case "Quality (low)":
                sorted.sort(Comparator.comparingInt(c -> c.quality.overallRating()));
                break;
            case "By region":
                sorted.sort(Comparator.comparing(c -> c.regionName));
                break;
            default:
                sorted.sort(Comparator.comparing((CapturedCreature c) -> c.captureTime).reversed());
        }

        listPanel.removeAll();
        for (int i = 0; i < sorted.size(); i++) {
            listPanel.add(buildCaptureRow(sorted.get(i), i + 1));
            if (i < sorted.size() - 1) {
                listPanel.add(Box.createVerticalStrut(4));
            }
        }
        listPanel.revalidate();
        listPanel.repaint();
    }

    private JPanel buildCaptureRow(CapturedCreature c, int index) {
        JPanel row = new JPanel(new BorderLayout(8, 3));
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setBorder(new EmptyBorder(7, 10, 7, 10));

        // Top line: quality badge + date
        JPanel topLine = new JPanel(new BorderLayout());
        topLine.setOpaque(false);

        int quality = c.quality.overallRating();
        Color qualColor = quality >= 80 ? new Color(80, 220, 80)
                        : quality >= 50 ? new Color(220, 220, 80)
                        : new Color(160, 160, 160);

        JLabel qualLabel = new JLabel("#" + index + "  Quality: " + quality + " / 100");
        qualLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        qualLabel.setForeground(qualColor);

        JLabel dateLabel = new JLabel(DATE_FMT.format(c.captureTime), SwingConstants.RIGHT);
        dateLabel.setFont(FontManager.getRunescapeSmallFont());
        dateLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        topLine.add(qualLabel, BorderLayout.WEST);
        topLine.add(dateLabel, BorderLayout.EAST);

        // Bottom line: region + kill number
        JPanel botLine = new JPanel(new BorderLayout());
        botLine.setOpaque(false);

        JLabel locLabel = new JLabel(c.regionName);
        locLabel.setFont(FontManager.getRunescapeSmallFont());
        locLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        JLabel killLabel = new JLabel("Kill #" + c.killsBeforeCapture, SwingConstants.RIGHT);
        killLabel.setFont(FontManager.getRunescapeSmallFont());
        killLabel.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);

        botLine.add(locLabel,  BorderLayout.WEST);
        botLine.add(killLabel, BorderLayout.EAST);

        JPanel content = new JPanel(new GridLayout(2, 1, 0, 2));
        content.setOpaque(false);
        content.add(topLine);
        content.add(botLine);

        row.add(content, BorderLayout.CENTER);
        return row;
    }
}
