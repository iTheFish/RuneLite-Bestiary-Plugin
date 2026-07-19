package net.runelite.client.plugins.bestiary.ui;

import net.runelite.client.plugins.bestiary.model.BestiaryCollection;
import net.runelite.client.plugins.bestiary.model.CapturedCreature;
import net.runelite.client.plugins.bestiary.model.CreatureRarity;
import net.runelite.client.plugins.bestiary.model.DifficultyTier;
import net.runelite.client.plugins.bestiary.model.MonsterRoster;
import net.runelite.client.plugins.bestiary.service.WikiImageService;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Resizable Pokédex-style grid showing every known monster — captured ones in
 * full colour, uncaptured locked slots in dark grey. Columns reflow on resize.
 */
public class AlbumDialog extends JDialog {

    private static final int DEFAULT_W = 820;
    private static final int DEFAULT_H = 820;

    /** Persists the user's last resized dimensions across opens within the same session. */
    private static Dimension savedSize = null;
    private static final int CARD_GAP  = 6;
    private static final int SIDE_PAD  = 8;

    private static final String[] SORT_OPTIONS = {
        "Name A–Z", "Name Z–A", "Difficulty ↑", "Difficulty ↓", "Most caught",
        "Rarity (best)", "Quality (high)", "Newest first"
    };

    private final Map<String, List<CapturedCreature>> capturesByNpc;
    private final Map<String, Integer> killCounts;
    private final BestiaryCollection collection;
    private final WikiImageService imageService;

    /** Full alphabetical roster (static list ∪ kill counts), deduplicated. */
    private final List<String> fullRoster;
    /** Stable dex numbers by alphabetical position. */
    private final Map<String, Integer> dexNumbers;

    private final JPanel gridPanel;
    private String  currentSort    = "Name A–Z";
    private boolean capturedFirst  = true;

    public AlbumDialog(Window owner, Map<String, List<CapturedCreature>> capturesByNpc,
                       Map<String, Integer> killCounts, BestiaryCollection collection,
                       WikiImageService imageService) {
        super(owner, "Bestiary Album", ModalityType.MODELESS);
        this.capturesByNpc = capturesByNpc;
        this.killCounts    = killCounts;
        this.collection    = collection;
        this.imageService  = imageService;

        this.fullRoster  = MonsterRoster.buildFullRoster(killCounts);
        this.dexNumbers  = MonsterRoster.assignDexNumbers(fullRoster);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(true);

        // --- Top bar ---
        JPanel topBar = new JPanel(new BorderLayout(6, 0));
        topBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        topBar.setBorder(new EmptyBorder(6, 8, 6, 8));

        // Sort
        JLabel sortLabel = new JLabel("Sort:");
        sortLabel.setFont(FontManager.getRunescapeSmallFont());
        sortLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        JComboBox<String> sortBox = new JComboBox<>(SORT_OPTIONS);
        sortBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        sortBox.setForeground(Color.WHITE);
        sortBox.setFont(FontManager.getRunescapeSmallFont());
        sortBox.addActionListener(e -> { currentSort = (String) sortBox.getSelectedItem(); rebuildGrid(); });

        JPanel sortRow = new JPanel(new BorderLayout(4, 0));
        sortRow.setOpaque(false);
        sortRow.add(sortLabel, BorderLayout.WEST);
        sortRow.add(sortBox,   BorderLayout.CENTER);

        // Captured-first toggle
        JToggleButton capturedFirstBtn = new JToggleButton();
        capturedFirstBtn.setSelected(capturedFirst);
        styleCapturedFirstBtn(capturedFirstBtn, capturedFirst);
        capturedFirstBtn.addActionListener(e -> {
            capturedFirst = capturedFirstBtn.isSelected();
            styleCapturedFirstBtn(capturedFirstBtn, capturedFirst);
            rebuildGrid();
        });

        // Species counts
        int captured = capturesByNpc.size();
        int total    = fullRoster.size();
        JLabel countLabel = new JLabel(captured + " / " + total);
        countLabel.setFont(FontManager.getRunescapeSmallFont());
        countLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        JPanel rightPanel = new JPanel(new BorderLayout(6, 0));
        rightPanel.setOpaque(false);
        rightPanel.add(capturedFirstBtn, BorderLayout.WEST);
        rightPanel.add(countLabel,       BorderLayout.EAST);

        topBar.add(sortRow,    BorderLayout.CENTER);
        topBar.add(rightPanel, BorderLayout.EAST);

        // --- Grid ---
        gridPanel = new JPanel();
        gridPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JScrollPane scroll = new JScrollPane(gridPanel);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        scroll.getViewport().addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { rebuildGrid(); }
        });

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(ColorScheme.DARK_GRAY_COLOR);
        root.add(topBar, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);

        setContentPane(root);
        setSize(savedSize != null ? savedSize : new Dimension(DEFAULT_W, DEFAULT_H));
        setLocationRelativeTo(owner);

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { savedSize = getSize(); }
        });

        SwingUtilities.invokeLater(this::rebuildGrid);
        setVisible(true);
        toFront();
    }

    // -------------------------------------------------------------------------
    // Grid construction
    // -------------------------------------------------------------------------

    private void rebuildGrid() {
        gridPanel.removeAll();

        int viewW = gridPanel.getParent() != null ? gridPanel.getParent().getWidth() : DEFAULT_W - SIDE_PAD * 2;
        if (viewW <= 0) viewW = DEFAULT_W - SIDE_PAD * 2;

        int cols = Math.max(1, (viewW - SIDE_PAD * 2 + CARD_GAP) / (AlbumCard.CARD_W + CARD_GAP));
        gridPanel.setLayout(new GridLayout(0, cols, CARD_GAP, CARD_GAP));
        gridPanel.setBorder(new EmptyBorder(SIDE_PAD, SIDE_PAD, SIDE_PAD, SIDE_PAD));

        // Split into captured vs locked, then sort each group
        List<String> capturedNames = fullRoster.stream()
                .filter(capturesByNpc::containsKey)
                .collect(Collectors.toList());
        List<String> lockedNames = fullRoster.stream()
                .filter(n -> !capturesByNpc.containsKey(n))
                .collect(Collectors.toList());

        sortNames(capturedNames, true);
        sortNames(lockedNames,   false);

        List<String> ordered;
        if (capturedFirst) {
            ordered = new ArrayList<>(capturedNames);
            ordered.addAll(lockedNames);
        } else {
            // Mix both groups under the same sort, then locked entries naturally
            // fall last for quality/rarity sorts (they have 0/null values).
            ordered = new ArrayList<>(fullRoster);
            sortAllMixed(ordered);
        }

        for (String name : ordered) {
            int dexNum = dexNumbers.getOrDefault(name, 0);
            if (capturesByNpc.containsKey(name)) {
                gridPanel.add(new AlbumCard(dexNum, name, capturesByNpc.get(name), collection, imageService));
            } else {
                int kills = killCounts.getOrDefault(name, 0);
                gridPanel.add(new AlbumCard(dexNum, name, kills, imageService));
            }
        }

        gridPanel.revalidate();
        gridPanel.repaint();
    }

    // -------------------------------------------------------------------------
    // Sorting
    // -------------------------------------------------------------------------

    /** Sort a list of captured NPC names by the current sort order. */
    private void sortNames(List<String> names, boolean isCaptured) {
        if (!isCaptured) {
            // Locked entries always sort by name
            names.sort(String.CASE_INSENSITIVE_ORDER);
            return;
        }
        switch (currentSort == null ? "Name A–Z" : currentSort) {
            case "Name Z–A":
                names.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(b, a));
                break;
            case "Difficulty ↑":
                names.sort((a, b) -> diffOrdinal(a, capturesByNpc) - diffOrdinal(b, capturesByNpc));
                break;
            case "Difficulty ↓":
                names.sort((a, b) -> diffOrdinal(b, capturesByNpc) - diffOrdinal(a, capturesByNpc));
                break;
            case "Most caught":
                names.sort((a, b) -> capturesByNpc.get(b).size() - capturesByNpc.get(a).size());
                break;
            case "Rarity (best)":
                names.sort((a, b) -> maxRarity(capturesByNpc.get(b)).ordinal()
                                   - maxRarity(capturesByNpc.get(a)).ordinal());
                break;
            case "Quality (high)":
                names.sort((a, b) -> avgQuality(capturesByNpc.get(b)) - avgQuality(capturesByNpc.get(a)));
                break;
            case "Newest first":
                names.sort((a, b) -> latestCapture(capturesByNpc.get(b))
                                        .compareTo(latestCapture(capturesByNpc.get(a))));
                break;
            default: // "Name A–Z"
                names.sort(String.CASE_INSENSITIVE_ORDER);
                break;
        }
    }

    /** Mixed sort: captured and locked entries together; locked fall last for non-name sorts. */
    private void sortAllMixed(List<String> names) {
        switch (currentSort == null ? "Name A–Z" : currentSort) {
            case "Name Z–A":
                names.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(b, a));
                break;
            case "Difficulty ↑":
                names.sort((a, b) -> diffOrdinalAny(a) - diffOrdinalAny(b));
                break;
            case "Difficulty ↓":
                names.sort((a, b) -> diffOrdinalAny(b) - diffOrdinalAny(a));
                break;
            case "Most caught":
                names.sort((a, b) -> {
                    int sa = capturesByNpc.containsKey(a) ? capturesByNpc.get(a).size() : 0;
                    int sb = capturesByNpc.containsKey(b) ? capturesByNpc.get(b).size() : 0;
                    return sb - sa;
                });
                break;
            case "Rarity (best)":
                names.sort((a, b) -> {
                    int ra = capturesByNpc.containsKey(a) ? maxRarity(capturesByNpc.get(a)).ordinal() : -1;
                    int rb = capturesByNpc.containsKey(b) ? maxRarity(capturesByNpc.get(b)).ordinal() : -1;
                    return rb - ra;
                });
                break;
            case "Quality (high)":
                names.sort((a, b) -> {
                    int qa = capturesByNpc.containsKey(a) ? avgQuality(capturesByNpc.get(a)) : 0;
                    int qb = capturesByNpc.containsKey(b) ? avgQuality(capturesByNpc.get(b)) : 0;
                    return qb - qa;
                });
                break;
            case "Newest first":
                names.sort((a, b) -> {
                    Instant ia = capturesByNpc.containsKey(a) ? latestCapture(capturesByNpc.get(a)) : Instant.EPOCH;
                    Instant ib = capturesByNpc.containsKey(b) ? latestCapture(capturesByNpc.get(b)) : Instant.EPOCH;
                    return ib.compareTo(ia);
                });
                break;
            default: // "Name A–Z"
                names.sort(String.CASE_INSENSITIVE_ORDER);
                break;
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void styleCapturedFirstBtn(JToggleButton btn, boolean active) {
        btn.setFont(FontManager.getRunescapeSmallFont());
        btn.setBorderPainted(true);
        btn.setBorder(BorderFactory.createLineBorder(Color.WHITE, 1));
        btn.setFocusPainted(false);
        btn.setOpaque(true);
        btn.setContentAreaFilled(true);
        if (active) {
            btn.setBackground(new Color(255, 165, 0));
            btn.setText("<html><b><font color='#101010'>Captured first</font></b></html>");
        } else {
            btn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            btn.setText("<html><b><font color='#B0B0B0'>Captured first</font></b></html>");
        }
    }

    private int diffOrdinal(String name, Map<String, List<CapturedCreature>> byNpc) {
        int combat = byNpc.containsKey(name) ? byNpc.get(name).get(0).npcCombatLevel : 0;
        return MonsterRoster.getDifficulty(name, combat).ordinal();
    }

    private int diffOrdinalAny(String name) {
        int combat = capturesByNpc.containsKey(name)
                ? capturesByNpc.get(name).get(0).npcCombatLevel : 0;
        return MonsterRoster.getDifficulty(name, combat).ordinal();
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
