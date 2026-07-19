package net.runelite.client.plugins.bestiary.ui;

import net.runelite.client.plugins.bestiary.model.BestiaryCollection;
import net.runelite.client.plugins.bestiary.model.CapturedCreature;
import net.runelite.client.plugins.bestiary.model.CreatureRarity;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Resizable grid album of all captured species.
 * Columns reflow dynamically as the dialog is resized.
 */
public class AlbumDialog extends JDialog {

    private static final int DEFAULT_W = 490;
    private static final int DEFAULT_H = 580;
    private static final int CARD_GAP  = 6;
    private static final int SIDE_PAD  = 8;

    private static final String[] SORT_OPTIONS = {
        "Name A–Z", "Name Z–A", "Most caught", "Rarity (best)", "Quality (high)", "Newest first"
    };

    private final Map<String, List<CapturedCreature>> capturesByNpc;
    private final BestiaryCollection collection;
    private final Map<String, Integer> dexNumbers;
    private final JPanel gridPanel;
    private String currentSort = "Name A–Z";

    public AlbumDialog(Window owner, Map<String, List<CapturedCreature>> capturesByNpc,
                       BestiaryCollection collection) {
        super(owner, "Bestiary Album", ModalityType.MODELESS);
        this.capturesByNpc = capturesByNpc;
        this.collection    = collection;
        this.dexNumbers    = assignDexNumbers(capturesByNpc);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(true);

        // --- Top bar: sort + species count ---
        JPanel topBar = new JPanel(new BorderLayout(6, 0));
        topBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        topBar.setBorder(new EmptyBorder(6, 8, 6, 8));

        JLabel sortLabel = new JLabel("Sort:");
        sortLabel.setFont(FontManager.getRunescapeSmallFont());
        sortLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        JComboBox<String> sortBox = new JComboBox<>(SORT_OPTIONS);
        sortBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        sortBox.setForeground(Color.WHITE);
        sortBox.setFont(FontManager.getRunescapeSmallFont());
        sortBox.addActionListener(e -> {
            currentSort = (String) sortBox.getSelectedItem();
            rebuildGrid();
        });

        JLabel countLabel = new JLabel(capturesByNpc.size() + " species");
        countLabel.setFont(FontManager.getRunescapeSmallFont());
        countLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        JPanel sortRow = new JPanel(new BorderLayout(6, 0));
        sortRow.setOpaque(false);
        sortRow.add(sortLabel, BorderLayout.WEST);
        sortRow.add(sortBox,   BorderLayout.CENTER);

        topBar.add(sortRow,    BorderLayout.CENTER);
        topBar.add(countLabel, BorderLayout.EAST);

        // --- Grid ---
        gridPanel = new JPanel();
        gridPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JScrollPane scroll = new JScrollPane(gridPanel);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        // Reflow when the viewport is resized (dialog resize)
        scroll.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                rebuildGrid();
            }
        });

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(ColorScheme.DARK_GRAY_COLOR);
        root.add(topBar, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);

        setContentPane(root);
        setSize(DEFAULT_W, DEFAULT_H);
        setLocationRelativeTo(owner);

        // Build after layout is ready
        SwingUtilities.invokeLater(this::rebuildGrid);

        setVisible(true);
        toFront();
    }

    private void rebuildGrid() {
        gridPanel.removeAll();

        int viewW = gridPanel.getParent() != null
                ? gridPanel.getParent().getWidth()
                : DEFAULT_W - SIDE_PAD * 2;
        if (viewW <= 0) viewW = DEFAULT_W - SIDE_PAD * 2;

        int cols = Math.max(1, (viewW - SIDE_PAD * 2 + CARD_GAP) / (AlbumCard.CARD_W + CARD_GAP));
        gridPanel.setLayout(new GridLayout(0, cols, CARD_GAP, CARD_GAP));
        gridPanel.setBorder(new EmptyBorder(SIDE_PAD, SIDE_PAD, SIDE_PAD, SIDE_PAD));

        List<Map.Entry<String, List<CapturedCreature>>> entries =
                new ArrayList<>(capturesByNpc.entrySet());
        sortEntries(entries);

        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<String, List<CapturedCreature>> e = entries.get(i);
            int dexNum = dexNumbers.getOrDefault(e.getKey(), i + 1);
            gridPanel.add(new AlbumCard(dexNum, e.getKey(), e.getValue(), collection));
        }

        gridPanel.revalidate();
        gridPanel.repaint();
    }

    private void sortEntries(List<Map.Entry<String, List<CapturedCreature>>> entries) {
        if (currentSort == null) return;
        switch (currentSort) {
            case "Name A–Z":
                entries.sort(Comparator.comparing(Map.Entry::getKey));
                break;
            case "Name Z–A":
                entries.sort(Comparator.<Map.Entry<String, List<CapturedCreature>>, String>
                        comparing(Map.Entry::getKey).reversed());
                break;
            case "Most caught":
                entries.sort((a, b) -> b.getValue().size() - a.getValue().size());
                break;
            case "Rarity (best)":
                entries.sort((a, b) -> maxRarity(b.getValue()).ordinal()
                                     - maxRarity(a.getValue()).ordinal());
                break;
            case "Quality (high)":
                entries.sort((a, b) -> avgQuality(b.getValue()) - avgQuality(a.getValue()));
                break;
            case "Newest first":
                entries.sort((a, b) -> latestCapture(b.getValue()).compareTo(latestCapture(a.getValue())));
                break;
            default:
                entries.sort(Comparator.comparing(Map.Entry::getKey));
        }
    }

    /** Permanent dex numbers assigned by first-capture time (first discovered = #001). */
    private static Map<String, Integer> assignDexNumbers(Map<String, List<CapturedCreature>> byNpc) {
        List<Map.Entry<String, List<CapturedCreature>>> sorted = new ArrayList<>(byNpc.entrySet());
        sorted.sort(Comparator.comparing(e -> e.getValue().stream()
                .map(c -> c.captureTime)
                .min(Comparator.naturalOrder())
                .orElse(Instant.EPOCH)));
        Map<String, Integer> nums = new LinkedHashMap<>();
        for (int i = 0; i < sorted.size(); i++) {
            nums.put(sorted.get(i).getKey(), i + 1);
        }
        return nums;
    }

    private static CreatureRarity maxRarity(List<CapturedCreature> captures) {
        return captures.stream().map(c -> c.rarity)
                .max(Comparator.comparingInt(Enum::ordinal))
                .orElse(CreatureRarity.COMMON);
    }

    private static int avgQuality(List<CapturedCreature> captures) {
        return (int) captures.stream().mapToInt(c -> c.quality.overallRating()).average().orElse(0);
    }

    private static Instant latestCapture(List<CapturedCreature> captures) {
        return captures.stream().map(c -> c.captureTime)
                .max(Comparator.naturalOrder())
                .orElse(Instant.EPOCH);
    }
}
