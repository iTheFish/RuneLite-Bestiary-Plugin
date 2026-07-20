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
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
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

    /** Prevents multiple album windows from stacking — same pattern as CreatureDetailDialog. */
    private static AlbumDialog current = null;

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
    private final JLabel countLabel;
    private String        currentSort      = "Name A–Z";
    private boolean       capturedFirst    = true;
    private boolean       favouritesOnly   = false;
    private String        searchTerm       = "";
    private DifficultyTier filterDifficulty = null; // null = All tiers

    public AlbumDialog(Window owner, Map<String, List<CapturedCreature>> capturesByNpc,
                       Map<String, Integer> killCounts, BestiaryCollection collection,
                       WikiImageService imageService) {
        super(owner, "Bestiary Album", ModalityType.MODELESS);
        if (current != null && current.isShowing()) {
            current.dispose();
        }
        current = this;
        this.capturesByNpc = capturesByNpc;
        this.killCounts    = killCounts;
        this.collection    = collection;
        this.imageService  = imageService;

        this.fullRoster  = MonsterRoster.buildFullRoster(killCounts);
        this.dexNumbers  = MonsterRoster.assignDexNumbers(fullRoster);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(true);

        // --- Top bar (two rows) ---
        JPanel topBar = new JPanel();
        topBar.setLayout(new BoxLayout(topBar, BoxLayout.Y_AXIS));
        topBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        topBar.setBorder(new EmptyBorder(6, 8, 6, 8));

        // Row 1: sort + captured-first toggle + count
        JPanel row1 = new JPanel(new BorderLayout(6, 0));
        row1.setOpaque(false);
        row1.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

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

        JToggleButton capturedFirstBtn = new JToggleButton();
        capturedFirstBtn.setSelected(capturedFirst);
        styleCapturedFirstBtn(capturedFirstBtn, capturedFirst);
        capturedFirstBtn.addActionListener(e -> {
            capturedFirst = capturedFirstBtn.isSelected();
            styleCapturedFirstBtn(capturedFirstBtn, capturedFirst);
            rebuildGrid();
        });

        countLabel = new JLabel(capturesByNpc.size() + " / " + fullRoster.size());
        countLabel.setFont(FontManager.getRunescapeSmallFont());
        countLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        countLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        // Fixed width so text changes don't cause row layout shifts
        countLabel.setPreferredSize(new Dimension(130, 16));
        countLabel.setMinimumSize(new Dimension(130, 16));

        // ★ favourites-only toggle
        JToggleButton favOnlyBtn = new JToggleButton("★");
        favOnlyBtn.setFont(new Font(Font.DIALOG, Font.BOLD, 12));
        favOnlyBtn.setMargin(new Insets(0, 0, 0, 0));
        favOnlyBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        favOnlyBtn.setForeground(new Color(90, 90, 90));
        favOnlyBtn.setFocusPainted(false);
        favOnlyBtn.setBorder(BorderFactory.createLineBorder(new Color(70, 70, 70), 1));
        favOnlyBtn.setPreferredSize(new Dimension(26, 26));
        favOnlyBtn.setToolTipText("Show only starred monsters");
        favOnlyBtn.addActionListener(e -> {
            favouritesOnly = favOnlyBtn.isSelected();
            favOnlyBtn.setForeground(favouritesOnly ? new Color(255, 195, 40) : new Color(90, 90, 90));
            favOnlyBtn.setBackground(favouritesOnly ? new Color(45, 38, 10) : ColorScheme.DARKER_GRAY_COLOR);
            rebuildGrid();
        });

        JPanel btnPair = new JPanel();
        btnPair.setLayout(new BoxLayout(btnPair, BoxLayout.X_AXIS));
        btnPair.setOpaque(false);
        btnPair.add(favOnlyBtn);
        btnPair.add(Box.createHorizontalStrut(4));
        btnPair.add(capturedFirstBtn);

        JPanel rightPanel = new JPanel(new BorderLayout(6, 0));
        rightPanel.setOpaque(false);
        rightPanel.add(btnPair,    BorderLayout.WEST);
        rightPanel.add(countLabel, BorderLayout.EAST);

        row1.add(sortRow,    BorderLayout.CENTER);
        row1.add(rightPanel, BorderLayout.EAST);

        // Row 2: search box + difficulty filter
        JPanel row2 = new JPanel(new BorderLayout(6, 0));
        row2.setOpaque(false);
        row2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        row2.setBorder(new EmptyBorder(4, 0, 0, 0));

        JTextField searchBox = new JTextField();
        searchBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        searchBox.setForeground(Color.WHITE);
        searchBox.setCaretColor(Color.WHITE);
        searchBox.setFont(FontManager.getRunescapeSmallFont());
        searchBox.putClientProperty("JTextField.placeholderText", "Search monsters…");
        searchBox.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void update() { searchTerm = searchBox.getText().trim(); rebuildGrid(); }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e)  { update(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e)  { update(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { update(); }
        });

        String[] diffOptions = {"All tiers", "Beginner", "Easy", "Medium", "Hard", "Elite", "Boss"};
        JComboBox<String> diffBox = new JComboBox<>(diffOptions);
        diffBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        diffBox.setForeground(Color.WHITE);
        diffBox.setFont(FontManager.getRunescapeSmallFont());
        diffBox.setPreferredSize(new Dimension(100, diffBox.getPreferredSize().height));
        diffBox.addActionListener(e -> {
            int idx = diffBox.getSelectedIndex();
            filterDifficulty = idx == 0 ? null : DifficultyTier.values()[idx - 1];
            rebuildGrid();
        });

        row2.add(searchBox, BorderLayout.CENTER);
        row2.add(diffBox,   BorderLayout.EAST);

        // Row 3: export favourites grid
        JPanel row3 = new JPanel(new BorderLayout(6, 0));
        row3.setOpaque(false);
        row3.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        row3.setBorder(new EmptyBorder(4, 0, 0, 0));

        JButton exportGridBtn = new JButton("Export ★ Grid");
        exportGridBtn.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        exportGridBtn.setMargin(new Insets(0, 6, 0, 6));
        exportGridBtn.setBackground(new Color(45, 38, 10));
        exportGridBtn.setForeground(new Color(255, 195, 40));
        exportGridBtn.setFocusPainted(false);
        exportGridBtn.setBorderPainted(false);
        exportGridBtn.setToolTipText("Export up to 9 starred monsters as a 3×3 grid image — copies to clipboard");
        exportGridBtn.addActionListener(e -> exportFavouritesGrid(exportGridBtn));

        JLabel exportHint = new JLabel("Up to 9 starred captures · copies to clipboard");
        exportHint.setFont(FontManager.getRunescapeSmallFont());
        exportHint.setForeground(new Color(75, 75, 75));

        row3.add(exportHint,    BorderLayout.CENTER);
        row3.add(exportGridBtn, BorderLayout.EAST);

        topBar.add(row1);
        topBar.add(row2);
        topBar.add(row3);

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

        imageService.prefetchBatch(fullRoster, gridPanel::repaint);
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

        // Apply search + difficulty + favourites filters
        String lc = searchTerm.toLowerCase();
        List<String> visible = fullRoster.stream()
                .filter(n -> lc.isEmpty() || n.toLowerCase().contains(lc))
                .filter(n -> {
                    if (filterDifficulty == null) return true;
                    int combat = capturesByNpc.containsKey(n) ? capturesByNpc.get(n).get(0).npcCombatLevel : 0;
                    return MonsterRoster.getDifficulty(n, combat) == filterDifficulty;
                })
                .filter(n -> !favouritesOnly || (capturesByNpc.containsKey(n)
                        && capturesByNpc.get(n).stream().anyMatch(c -> c.favourite)))
                .collect(Collectors.toList());

        long visibleCaptured = visible.stream().filter(capturesByNpc::containsKey).count();
        countLabel.setText(visibleCaptured + " / " + visible.size()
                + (visible.size() < fullRoster.size() ? " (filtered)" : ""));

        // Split into captured vs locked, then sort each group
        List<String> capturedNames = visible.stream()
                .filter(capturesByNpc::containsKey)
                .collect(Collectors.toList());
        List<String> lockedNames = visible.stream()
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
            ordered = new ArrayList<>(visible);
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

    // -------------------------------------------------------------------------
    // Favourites grid export
    // -------------------------------------------------------------------------

    private void exportFavouritesGrid(JButton btn) {
        List<String> favNpcs = capturesByNpc.keySet().stream()
                .filter(n -> capturesByNpc.get(n).stream().anyMatch(c -> c.favourite))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .limit(9)
                .collect(Collectors.toList());

        if (favNpcs.isEmpty()) {
            btn.setText("No favourites!");
            btn.setForeground(new Color(200, 100, 100));
            javax.swing.Timer t = new javax.swing.Timer(2000, ev -> {
                btn.setText("Export ★ Grid");
                btn.setForeground(new Color(255, 195, 40));
            });
            t.setRepeats(false);
            t.start();
            return;
        }

        int count = favNpcs.size();
        int cols  = Math.min(3, count);
        int rows  = (count + cols - 1) / cols;
        final int GAP = 6, PAD = 8, SCALE = 2;

        int logW = cols * AlbumCard.CARD_W + (cols - 1) * GAP + PAD * 2;
        int logH = rows * AlbumCard.CARD_H + (rows - 1) * GAP + PAD * 2;

        BufferedImage img = new BufferedImage(logW * SCALE, logH * SCALE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,     RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);
        g2.scale(SCALE, SCALE);

        g2.setColor(new Color(18, 18, 18));
        g2.fillRect(0, 0, logW, logH);

        for (int i = 0; i < favNpcs.size(); i++) {
            String name = favNpcs.get(i);
            List<CapturedCreature> caps = capturesByNpc.get(name);
            int dex = dexNumbers.getOrDefault(name, 0);

            AlbumCard card = new AlbumCard(dex, name, caps, collection, imageService);
            card.setSize(AlbumCard.CARD_W, AlbumCard.CARD_H);

            int col = i % cols;
            int row = i / cols;
            int x   = PAD + col * (AlbumCard.CARD_W + GAP);
            int y   = PAD + row * (AlbumCard.CARD_H + GAP);

            Graphics2D cardG2 = (Graphics2D) g2.create();
            cardG2.translate(x, y);
            card.print(cardG2);
            cardG2.dispose();
        }
        g2.dispose();

        BufferedImage exported = img;
        Transferable t = new Transferable() {
            @Override public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{DataFlavor.imageFlavor}; }
            @Override public boolean isDataFlavorSupported(DataFlavor f) { return DataFlavor.imageFlavor.equals(f); }
            @Override public Object getTransferData(DataFlavor f) throws UnsupportedFlavorException {
                if (!DataFlavor.imageFlavor.equals(f)) throw new UnsupportedFlavorException(f);
                return exported;
            }
        };
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(t, null);

        btn.setText("Copied! (" + count + " cards)");
        btn.setForeground(new Color(120, 200, 120));
        javax.swing.Timer timer = new javax.swing.Timer(2500, ev -> {
            btn.setText("Export ★ Grid");
            btn.setForeground(new Color(255, 195, 40));
        });
        timer.setRepeats(false);
        timer.start();
    }
}
