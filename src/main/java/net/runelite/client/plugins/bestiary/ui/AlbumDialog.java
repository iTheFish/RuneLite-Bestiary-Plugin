package net.runelite.client.plugins.bestiary.ui;

import net.runelite.client.plugins.bestiary.model.BestiaryCollection;
import net.runelite.client.plugins.bestiary.model.CapturedCreature;
import net.runelite.client.plugins.bestiary.model.CreatureRarity;
import net.runelite.client.plugins.bestiary.model.DifficultyTier;
import net.runelite.client.plugins.bestiary.model.MonsterRoster;
import net.runelite.client.plugins.bestiary.service.WikiImageService;
import net.runelite.client.plugins.bestiary.util.CardId;
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
 * Clicking a captured card drills into a paginated detail view for that monster.
 */
public class AlbumDialog extends JDialog {

    private static final int DEFAULT_W = 820;
    private static final int DEFAULT_H = 820;

    private static AlbumDialog current = null;
    private static Dimension savedSize = null;
    private static Runnable onFavouriteChanged;
    public static void setOnFavouriteChanged(Runnable r) { onFavouriteChanged = r; }

    /** Callback set by CollectionTab so cards can open the Album to a monster's detail view. */
    private static java.util.function.Consumer<String> openDetailCallback;
    public static void setOpenDetailCallback(java.util.function.Consumer<String> cb) { openDetailCallback = cb; }

    // Pending filter state — set before calling the callback, consumed in focusDetail
    private static CreatureRarity          pendingFilterRarity  = null;
    private static java.time.Instant       pendingFilterCapture = null;

    /** Open detail for a monster with all rarities shown (By Creature). */
    public static void requestOpenDetail(String monsterName) {
        pendingFilterRarity = null; pendingFilterCapture = null;
        if (openDetailCallback != null) openDetailCallback.accept(monsterName);
    }

    /** Open detail pre-filtered to a specific rarity (By Rarity). */
    public static void requestOpenDetail(String monsterName, CreatureRarity rarity) {
        pendingFilterRarity = rarity; pendingFilterCapture = null;
        if (openDetailCallback != null) openDetailCallback.accept(monsterName);
    }

    /** Open detail showing only the capture matching captureTime (Individual). */
    public static void requestOpenDetail(String monsterName, java.time.Instant captureTime) {
        pendingFilterRarity = null; pendingFilterCapture = captureTime;
        if (openDetailCallback != null) openDetailCallback.accept(monsterName);
    }

    /** Switch to detail view if the dialog is already open; returns true if switched. */
    public static boolean focusDetail(String name) {
        if (current != null && current.isShowing()) {
            CreatureRarity r = pendingFilterRarity;
            java.time.Instant t = pendingFilterCapture;
            pendingFilterRarity = null; pendingFilterCapture = null;
            current.showDetail(name, r, t);
            current.toFront();
            return true;
        }
        return false;
    }

    private static final int CARD_GAP = 6;
    private static final int SIDE_PAD = 8;

    private static final String SORT_FAVOURITES = "★ Favourites";
    private static final String[] SORT_OPTIONS = {
        "Name A–Z", "Name Z–A", "Difficulty ↑", "Difficulty ↓", "Most caught",
        "Rarity (best)", "Quality (high)", "Newest first", SORT_FAVOURITES
    };

    private final Map<String, List<CapturedCreature>> capturesByNpc;
    private final Map<String, Integer> killCounts;
    private final BestiaryCollection collection;
    private final WikiImageService imageService;

    private final List<String> fullRoster;
    private final Map<String, Integer> dexNumbers;

    private final JPanel gridPanel;
    private final JLabel countLabel;

    // Catalog bar controls
    private JComboBox<String> sortBox;
    private JToggleButton     favOnlyBtn;
    private JToggleButton     showLockedBtn;
    private JToggleButton     capturedFirstBtn;
    private JPanel            exportRow;
    private JLabel            exportHint;

    // Catalog state
    private String         currentSort      = "Name A–Z";
    private boolean        capturedFirst    = true;
    private boolean        favouritesOnly   = false;
    private boolean        showLocked       = true;
    private String         searchTerm       = "";
    private DifficultyTier filterDifficulty = null;

    // Detail view state
    private String              detailMonsterName    = null;
    private int                 detailPage           = 0;
    private int                 detailPageSize       = 8;
    private String              detailSort           = "Rarity (best)";
    private CreatureRarity      detailFilterRarity   = null;
    private java.time.Instant   detailFilterCapture  = null;

    // Detail bar controls
    private JPanel            topBarHolder;
    private JLabel            detailTitleLabel;
    private JLabel            detailPageLabel;
    private JComboBox<String> detailSortBox;
    private JButton           prevPageBtn;
    private JButton           nextPageBtn;

    public AlbumDialog(Window owner, Map<String, List<CapturedCreature>> capturesByNpc,
                       Map<String, Integer> killCounts, BestiaryCollection collection,
                       WikiImageService imageService, boolean startFavourites) {
        super(owner, "Bestiary Album", ModalityType.MODELESS);
        if (current != null && current.isShowing()) current.dispose();
        current = this;
        this.capturesByNpc = capturesByNpc;
        this.killCounts    = killCounts;
        this.collection    = collection;
        this.imageService  = imageService;
        this.fullRoster    = MonsterRoster.buildFullRoster(killCounts);
        this.dexNumbers    = MonsterRoster.assignDexNumbers(fullRoster);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(true);

        // -------------------------------------------------------------------------
        // Catalog top bar
        // -------------------------------------------------------------------------
        JPanel topBar = new JPanel();
        topBar.setLayout(new BoxLayout(topBar, BoxLayout.Y_AXIS));
        topBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        topBar.setBorder(new EmptyBorder(6, 8, 6, 8));

        // Row 1: sort + (★ toggle · captured-first) + count
        JPanel row1 = new JPanel(new BorderLayout(6, 0));
        row1.setOpaque(false);
        row1.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        JLabel sortLabel = new JLabel("Sort:");
        sortLabel.setFont(FontManager.getRunescapeSmallFont());
        sortLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        sortBox = new JComboBox<>(SORT_OPTIONS);
        sortBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        sortBox.setForeground(Color.WHITE);
        sortBox.setFont(FontManager.getRunescapeSmallFont());

        JPanel sortRow = new JPanel(new BorderLayout(4, 0));
        sortRow.setOpaque(false);
        sortRow.add(sortLabel, BorderLayout.WEST);
        sortRow.add(sortBox,   BorderLayout.CENTER);

        showLockedBtn = new JToggleButton();
        showLockedBtn.setSelected(showLocked);
        styleShowLockedBtn(showLockedBtn, showLocked);

        capturedFirstBtn = new JToggleButton();
        capturedFirstBtn.setSelected(capturedFirst);
        styleCapturedFirstBtn(capturedFirstBtn, capturedFirst);
        capturedFirstBtn.setPreferredSize(new Dimension(90, 22));
        capturedFirstBtn.setMinimumSize(new Dimension(90, 22));

        countLabel = new JLabel(capturesByNpc.size() + " / " + fullRoster.size());
        countLabel.setFont(FontManager.getRunescapeSmallFont());
        countLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        countLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        countLabel.setPreferredSize(new Dimension(130, 16));
        countLabel.setMinimumSize(new Dimension(130, 16));

        favOnlyBtn = new JToggleButton("★");
        favOnlyBtn.setFont(new Font(Font.DIALOG, Font.BOLD, 12));
        favOnlyBtn.setMargin(new Insets(0, 0, 0, 0));
        favOnlyBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        favOnlyBtn.setForeground(new Color(90, 90, 90));
        favOnlyBtn.setFocusPainted(false);
        favOnlyBtn.setBorder(BorderFactory.createLineBorder(new Color(70, 70, 70), 1));
        favOnlyBtn.setPreferredSize(new Dimension(26, 26));
        favOnlyBtn.setToolTipText("Show only starred captures");

        JPanel btnPair = new JPanel();
        btnPair.setLayout(new BoxLayout(btnPair, BoxLayout.X_AXIS));
        btnPair.setOpaque(false);
        btnPair.add(favOnlyBtn);
        btnPair.add(Box.createHorizontalStrut(4));
        btnPair.add(showLockedBtn);
        btnPair.add(Box.createHorizontalStrut(4));
        btnPair.add(capturedFirstBtn);

        JPanel rightPanel = new JPanel(new BorderLayout(6, 0));
        rightPanel.setOpaque(false);
        rightPanel.add(btnPair,    BorderLayout.WEST);
        rightPanel.add(countLabel, BorderLayout.EAST);

        row1.add(sortRow,    BorderLayout.CENTER);
        row1.add(rightPanel, BorderLayout.EAST);

        // Row 2: search + difficulty filter
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

        // Row 3: favourites banner + export — hidden until favourites mode is active
        exportRow = new JPanel(new BorderLayout(6, 0));
        exportRow.setOpaque(false);
        exportRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        exportRow.setBorder(new EmptyBorder(4, 0, 0, 0));
        exportRow.setVisible(false);

        JButton exportGridBtn = new JButton("Export ★ Grid");
        exportGridBtn.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        exportGridBtn.setMargin(new Insets(0, 6, 0, 6));
        exportGridBtn.setBackground(new Color(45, 38, 10));
        exportGridBtn.setForeground(new Color(255, 195, 40));
        exportGridBtn.setFocusPainted(false);
        exportGridBtn.setBorderPainted(false);
        exportGridBtn.setToolTipText("<html>Export starred captures as a grid — copies to clipboard.<br>"
                + "1 card: 1×1 &nbsp;|&nbsp; 2–4 cards: 2 cols &nbsp;|&nbsp; 5–9: 3×3 &nbsp;|&nbsp; 10–20: 4 cols</html>");
        exportGridBtn.addActionListener(e -> exportFavouritesGrid(exportGridBtn));

        exportHint = new JLabel("★ Viewing starred captures only");
        exportHint.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        exportHint.setForeground(new Color(255, 195, 40));

        exportRow.add(exportHint,    BorderLayout.CENTER);
        exportRow.add(exportGridBtn, BorderLayout.EAST);

        topBar.add(row1);
        topBar.add(row2);
        topBar.add(exportRow);

        // -------------------------------------------------------------------------
        // Catalog action listeners (declared after all fields are initialised)
        // -------------------------------------------------------------------------
        sortBox.addActionListener(e -> {
            String sel = (String) sortBox.getSelectedItem();
            if (SORT_FAVOURITES.equals(sel)) {
                favouritesOnly = true;
                currentSort    = "Newest first";
                favOnlyBtn.setSelected(true);
                favOnlyBtn.setForeground(new Color(255, 195, 40));
                favOnlyBtn.setBackground(new Color(45, 38, 10));
                exportRow.setVisible(true);
            } else {
                currentSort = sel;
            }
            rebuildGrid();
        });

        showLockedBtn.addActionListener(e -> {
            showLocked = showLockedBtn.isSelected();
            styleShowLockedBtn(showLockedBtn, showLocked);
            if (showLocked) {
                capturedFirstBtn.setEnabled(true);
                capturedFirstBtn.setContentAreaFilled(true);
                capturedFirstBtn.setBorderPainted(true);
                styleCapturedFirstBtn(capturedFirstBtn, capturedFirst);
            } else {
                capturedFirstBtn.setEnabled(false);
                capturedFirstBtn.setContentAreaFilled(false);
                capturedFirstBtn.setBorderPainted(false);
                capturedFirstBtn.setText(" ");
            }
            rebuildGrid();
        });

        capturedFirstBtn.addActionListener(e -> {
            capturedFirst = capturedFirstBtn.isSelected();
            styleCapturedFirstBtn(capturedFirstBtn, capturedFirst);
            rebuildGrid();
        });

        favOnlyBtn.addActionListener(e -> {
            favouritesOnly = favOnlyBtn.isSelected();
            favOnlyBtn.setForeground(favouritesOnly ? new Color(255, 195, 40) : new Color(90, 90, 90));
            favOnlyBtn.setBackground(favouritesOnly ? new Color(45, 38, 10) : ColorScheme.DARKER_GRAY_COLOR);
            exportRow.setVisible(favouritesOnly);
            if (!favouritesOnly && SORT_FAVOURITES.equals(sortBox.getSelectedItem())) {
                sortBox.setSelectedItem("Name A–Z");
                currentSort = "Name A–Z";
            }
            rebuildGrid();
        });

        // -------------------------------------------------------------------------
        // Detail top bar
        // -------------------------------------------------------------------------
        JPanel detailBar = new JPanel();
        detailBar.setLayout(new BoxLayout(detailBar, BoxLayout.Y_AXIS));
        detailBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        detailBar.setBorder(new EmptyBorder(6, 8, 6, 8));

        // Detail row 1: Back | Monster name + count | Page size [8][12][16]
        JPanel dRow1 = new JPanel(new BorderLayout(6, 0));
        dRow1.setOpaque(false);
        dRow1.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        JButton backBtn = new JButton("← Back");
        backBtn.setFont(FontManager.getRunescapeSmallFont());
        backBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        backBtn.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        backBtn.setFocusPainted(false);
        backBtn.addActionListener(e -> showCatalog());

        detailTitleLabel = new JLabel();
        detailTitleLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        detailTitleLabel.setForeground(Color.WHITE);
        detailTitleLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel pageSizePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        pageSizePanel.setOpaque(false);
        ButtonGroup pageSizeGroup = new ButtonGroup();
        for (int ps : new int[]{8, 12, 16}) {
            final int size = ps;
            JToggleButton pb = new JToggleButton(String.valueOf(ps));
            pb.setFont(FontManager.getRunescapeSmallFont());
            pb.setMargin(new Insets(1, 5, 1, 5));
            pb.setFocusPainted(false);
            pb.setSelected(ps == detailPageSize);
            pb.addActionListener(e -> {
                detailPageSize = size;
                detailPage = 0;
                rebuildGrid();
            });
            pageSizeGroup.add(pb);
            pageSizePanel.add(pb);
        }

        dRow1.add(backBtn,        BorderLayout.WEST);
        dRow1.add(detailTitleLabel, BorderLayout.CENTER);
        dRow1.add(pageSizePanel,  BorderLayout.EAST);

        // Detail row 2: Sort | ← Prev | page X / Y | Next →
        JPanel dRow2 = new JPanel(new BorderLayout(6, 0));
        dRow2.setOpaque(false);
        dRow2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        dRow2.setBorder(new EmptyBorder(4, 0, 0, 0));

        JLabel dSortLabel = new JLabel("Sort:");
        dSortLabel.setFont(FontManager.getRunescapeSmallFont());
        dSortLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        detailSortBox = new JComboBox<>(new String[]{"Rarity (best)", "Quality (high)", "Newest first"});
        detailSortBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        detailSortBox.setForeground(Color.WHITE);
        detailSortBox.setFont(FontManager.getRunescapeSmallFont());
        detailSortBox.addActionListener(e -> {
            detailSort = (String) detailSortBox.getSelectedItem();
            detailPage = 0;
            rebuildGrid();
        });

        JPanel dSortRow = new JPanel(new BorderLayout(4, 0));
        dSortRow.setOpaque(false);
        dSortRow.add(dSortLabel,    BorderLayout.WEST);
        dSortRow.add(detailSortBox, BorderLayout.CENTER);

        prevPageBtn = new JButton("← Prev");
        prevPageBtn.setFont(FontManager.getRunescapeSmallFont());
        prevPageBtn.setForeground(Color.WHITE);
        prevPageBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        prevPageBtn.setFocusPainted(false);
        prevPageBtn.addActionListener(e -> { if (detailPage > 0) { detailPage--; rebuildGrid(); } });

        detailPageLabel = new JLabel("1 / 1");
        detailPageLabel.setFont(FontManager.getRunescapeSmallFont());
        detailPageLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        detailPageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        detailPageLabel.setPreferredSize(new Dimension(50, 16));

        nextPageBtn = new JButton("Next →");
        nextPageBtn.setFont(FontManager.getRunescapeSmallFont());
        nextPageBtn.setForeground(Color.WHITE);
        nextPageBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        nextPageBtn.setFocusPainted(false);
        nextPageBtn.addActionListener(e -> { detailPage++; rebuildGrid(); });

        JPanel pageNavPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        pageNavPanel.setOpaque(false);
        pageNavPanel.add(prevPageBtn);
        pageNavPanel.add(detailPageLabel);
        pageNavPanel.add(nextPageBtn);

        dRow2.add(dSortRow,     BorderLayout.CENTER);
        dRow2.add(pageNavPanel, BorderLayout.EAST);

        detailBar.add(dRow1);
        detailBar.add(dRow2);

        // -------------------------------------------------------------------------
        // Top-bar card holder (catalog vs detail)
        // -------------------------------------------------------------------------
        topBarHolder = new JPanel(new CardLayout());
        topBarHolder.add(topBar,   "CATALOG");
        topBarHolder.add(detailBar, "DETAIL");

        // -------------------------------------------------------------------------
        // Grid + scroll
        // -------------------------------------------------------------------------
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
        root.add(topBarHolder, BorderLayout.NORTH);
        root.add(scroll,       BorderLayout.CENTER);

        setContentPane(root);
        setSize(savedSize != null ? savedSize : new Dimension(DEFAULT_W, DEFAULT_H));
        setLocationRelativeTo(owner);

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { savedSize = getSize(); }
        });

        if (startFavourites) {
            favouritesOnly = true;
            favOnlyBtn.setSelected(true);
            favOnlyBtn.setForeground(new Color(255, 195, 40));
            favOnlyBtn.setBackground(new Color(45, 38, 10));
            exportRow.setVisible(true);
        }

        imageService.prefetchBatch(fullRoster, gridPanel::repaint);
        SwingUtilities.invokeLater(this::rebuildGrid);
        setVisible(true);
        toFront();
    }

    // -------------------------------------------------------------------------
    // Detail view navigation
    // -------------------------------------------------------------------------

    public void showDetail(String name) {
        showDetail(name, null, null);
    }

    public void showDetail(String name, CreatureRarity filterRarity, java.time.Instant filterCapture) {
        detailMonsterName   = name;
        detailFilterRarity  = filterRarity;
        detailFilterCapture = filterCapture;
        detailPage = 0;
        detailSort = "Rarity (best)";
        detailSortBox.setSelectedItem("Rarity (best)");
        ((CardLayout) topBarHolder.getLayout()).show(topBarHolder, "DETAIL");
        rebuildGrid();
    }

    private void showCatalog() {
        detailMonsterName   = null;
        detailFilterRarity  = null;
        detailFilterCapture = null;
        ((CardLayout) topBarHolder.getLayout()).show(topBarHolder, "CATALOG");
        rebuildGrid();
    }

    // -------------------------------------------------------------------------
    // Grid construction
    // -------------------------------------------------------------------------

    private void rebuildGrid() {
        gridPanel.removeAll();

        if (detailMonsterName != null) {
            buildDetailView();
            return;
        }

        int viewW = gridPanel.getParent() != null ? gridPanel.getParent().getWidth() : DEFAULT_W - SIDE_PAD * 2;
        if (viewW <= 0) viewW = DEFAULT_W - SIDE_PAD * 2;

        int cols = Math.max(1, (viewW - SIDE_PAD * 2 + CARD_GAP) / (AlbumCard.CARD_W + CARD_GAP));
        gridPanel.setLayout(new GridLayout(0, cols, CARD_GAP, CARD_GAP));
        gridPanel.setBorder(new EmptyBorder(SIDE_PAD, SIDE_PAD, SIDE_PAD, SIDE_PAD));

        // ---- Favourites mode: one card per individual starred capture ----
        if (favouritesOnly) {
            String lc = searchTerm.toLowerCase();
            List<CapturedCreature> starred = capturesByNpc.values().stream()
                    .flatMap(List::stream)
                    .filter(c -> c.favourite)
                    .filter(c -> lc.isEmpty() || c.npcName.toLowerCase().contains(lc))
                    .filter(c -> filterDifficulty == null
                            || MonsterRoster.getDifficulty(c.npcName, c.npcCombatLevel) == filterDifficulty)
                    .sorted(favouriteCapSort())
                    .collect(Collectors.toList());

            int n = starred.size();
            countLabel.setForeground(n > 0 ? new Color(255, 195, 40) : new Color(170, 80, 80));
            countLabel.setText("★ " + n + " starred");

            for (CapturedCreature cap : starred) {
                int dex = dexNumbers.getOrDefault(cap.npcName, 0);
                AlbumCard card = new AlbumCard(dex, cap.npcName, List.of(cap), collection, imageService);
                card.setUnfavouriteCallback(() -> {
                    cap.favourite = false;
                    rebuildGrid();
                    if (onFavouriteChanged != null) onFavouriteChanged.run();
                });
                gridPanel.add(card);
            }

            gridPanel.revalidate();
            gridPanel.repaint();
            return;
        }

        // ---- Normal catalog mode ----
        countLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        String lc = searchTerm.toLowerCase();
        List<String> visible = fullRoster.stream()
                .filter(n -> lc.isEmpty() || n.toLowerCase().contains(lc))
                .filter(n -> {
                    if (filterDifficulty == null) return true;
                    int combat = capturesByNpc.containsKey(n) ? capturesByNpc.get(n).get(0).npcCombatLevel : 0;
                    return MonsterRoster.getDifficulty(n, combat) == filterDifficulty;
                })
                .collect(Collectors.toList());

        List<String> capturedNames = visible.stream()
                .filter(capturesByNpc::containsKey).collect(Collectors.toList());
        List<String> lockedNames = showLocked ? visible.stream()
                .filter(n -> !capturesByNpc.containsKey(n)).collect(Collectors.toList())
                : new ArrayList<>();

        long total = showLocked ? visible.size() : capturedNames.size();
        countLabel.setText(capturedNames.size() + " / " + total
                + (visible.size() < fullRoster.size() ? " (filtered)" : ""));

        sortNames(capturedNames, true);

        List<String> ordered;
        if (!showLocked) {
            ordered = capturedNames;
        } else if (capturedFirst) {
            sortNames(lockedNames, false);
            ordered = new ArrayList<>(capturedNames);
            ordered.addAll(lockedNames);
        } else {
            ordered = new ArrayList<>(visible);
            sortAllMixed(ordered);
        }

        for (String name : ordered) {
            int dexNum = dexNumbers.getOrDefault(name, 0);
            if (capturesByNpc.containsKey(name)) {
                AlbumCard card = new AlbumCard(dexNum, name, capturesByNpc.get(name), collection, imageService);
                card.setClickOverride(() -> showDetail(name));
                gridPanel.add(card);
            } else if (showLocked) {
                int kills = killCounts.getOrDefault(name, 0);
                gridPanel.add(new AlbumCard(dexNum, name, kills, imageService));
            }
        }

        gridPanel.revalidate();
        gridPanel.repaint();
    }

    private void buildDetailView() {
        List<CapturedCreature> allCaps = capturesByNpc.getOrDefault(detailMonsterName, Collections.emptyList());

        // Apply filter
        List<CapturedCreature> filtered;
        if (detailFilterCapture != null) {
            filtered = allCaps.stream()
                    .filter(c -> c.captureTime.equals(detailFilterCapture))
                    .collect(Collectors.toList());
        } else if (detailFilterRarity != null) {
            filtered = allCaps.stream()
                    .filter(c -> c.rarity == detailFilterRarity)
                    .collect(Collectors.toList());
        } else {
            filtered = new ArrayList<>(allCaps);
        }

        // Sort
        switch (detailSort == null ? "Rarity (best)" : detailSort) {
            case "Quality (high)":
                filtered.sort(Comparator.comparingInt((CapturedCreature c) -> c.quality.overallRating()).reversed());
                break;
            case "Newest first":
                filtered.sort(Comparator.comparing((CapturedCreature c) -> c.captureTime, Comparator.reverseOrder()));
                break;
            default: // "Rarity (best)"
                filtered.sort(Comparator.comparingInt((CapturedCreature c) -> c.rarity.ordinal()).reversed()
                        .thenComparingInt(c -> -c.quality.overallRating()));
                break;
        }

        int total      = filtered.size();
        int totalPages = Math.max(1, (total + detailPageSize - 1) / detailPageSize);
        detailPage = Math.max(0, Math.min(detailPage, totalPages - 1));

        // Update header label and pagination
        CreatureRarity best = detailFilterRarity != null ? detailFilterRarity
                : total > 0 ? filtered.stream().map(c -> c.rarity)
                        .max(Comparator.comparingInt(Enum::ordinal)).orElse(CreatureRarity.COMMON)
                : CreatureRarity.COMMON;
        String suffix = detailFilterRarity != null ? " — " + detailFilterRarity.label
                : detailFilterCapture != null ? " — single capture" : "";
        detailTitleLabel.setText(detailMonsterName + " (" + total + ")" + suffix);
        detailTitleLabel.setForeground(best.displayColor);
        detailPageLabel.setText((detailPage + 1) + " / " + totalPages);
        prevPageBtn.setEnabled(detailPage > 0);
        nextPageBtn.setEnabled(detailPage < totalPages - 1);

        // Use BorderLayout for gridPanel so filter row sits above the card grid
        gridPanel.setLayout(new BorderLayout());
        gridPanel.setBorder(null);

        JPanel filterRow = buildDetailFilterRow(allCaps);
        if (filterRow != null) gridPanel.add(filterRow, BorderLayout.NORTH);

        int viewW = gridPanel.getParent() != null ? gridPanel.getParent().getWidth() : DEFAULT_W - SIDE_PAD * 2;
        if (viewW <= 0) viewW = DEFAULT_W - SIDE_PAD * 2;
        int cols = Math.max(1, (viewW - SIDE_PAD * 2 + CARD_GAP) / (AlbumCard.CARD_W + CARD_GAP));

        JPanel cardsPanel = new JPanel(new GridLayout(0, cols, CARD_GAP, CARD_GAP));
        cardsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        cardsPanel.setBorder(new EmptyBorder(SIDE_PAD, SIDE_PAD, SIDE_PAD, SIDE_PAD));

        int from = detailPage * detailPageSize;
        int to   = Math.min(from + detailPageSize, total);
        for (CapturedCreature cap : filtered.subList(from, to)) {
            int dex = dexNumbers.getOrDefault(cap.npcName, 0);
            AlbumCard card = new AlbumCard(dex, cap.npcName, List.of(cap), collection, imageService);
            card.setShowQuality(true);
            card.setClickOverride(() -> CardExportDialog.open(AlbumDialog.this, cap));
            card.setCopyCallback(() -> CardExportDialog.copyNow(AlbumDialog.this, cap));
            cardsPanel.add(card);
        }
        gridPanel.add(cardsPanel, BorderLayout.CENTER);

        gridPanel.revalidate();
        gridPanel.repaint();
    }

    /** Filter row shown above the card grid in detail mode. Returns null if not needed. */
    private JPanel buildDetailFilterRow(List<CapturedCreature> allCaps) {
        // Individual capture filter — show "Show all N" link
        if (detailFilterCapture != null) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
            row.setBackground(new Color(35, 35, 35));
            row.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(60, 60, 60)));
            JLabel lbl = new JLabel("Showing specific capture");
            lbl.setFont(FontManager.getRunescapeSmallFont());
            lbl.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            JButton showAll = new JButton("Show all " + allCaps.size());
            showAll.setFont(FontManager.getRunescapeSmallFont());
            showAll.setForeground(new Color(200, 153, 0));
            showAll.setBackground(new Color(35, 35, 35));
            showAll.setFocusPainted(false);
            showAll.setBorderPainted(false);
            showAll.addActionListener(e -> { detailFilterCapture = null; detailPage = 0; rebuildGrid(); });
            row.add(lbl);
            row.add(showAll);
            return row;
        }

        // Rarity pills — only when monster has 2+ distinct rarities
        Set<CreatureRarity> rarities = allCaps.stream()
                .map(c -> c.rarity)
                .collect(java.util.stream.Collectors.toCollection(
                        () -> new java.util.TreeSet<>(Comparator.comparingInt((CreatureRarity r) -> r.ordinal()).reversed())));
        if (rarities.size() < 2) return null;

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        row.setBackground(new Color(35, 35, 35));
        row.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(60, 60, 60)));

        JButton allBtn = makeRarityPill("All", detailFilterRarity == null, null);
        allBtn.addActionListener(e -> { detailFilterRarity = null; detailPage = 0; rebuildGrid(); });
        row.add(allBtn);

        for (CreatureRarity r : rarities) {
            JButton rb = makeRarityPill(r.label, r == detailFilterRarity, r.displayColor);
            rb.addActionListener(e -> { detailFilterRarity = r; detailPage = 0; rebuildGrid(); });
            row.add(rb);
        }
        return row;
    }

    private static JButton makeRarityPill(String label, boolean selected, Color rarityColor) {
        JButton btn = new JButton(label);
        btn.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        btn.setMargin(new Insets(1, 6, 1, 6));
        btn.setFocusPainted(false);
        if (selected) {
            btn.setBackground(rarityColor != null ? rarityColor : new Color(200, 200, 200));
            btn.setForeground(Color.BLACK);
            btn.setBorderPainted(false);
        } else {
            btn.setBackground(new Color(50, 50, 50));
            btn.setForeground(rarityColor != null ? rarityColor : Color.WHITE);
            btn.setBorder(BorderFactory.createLineBorder(
                    rarityColor != null ? rarityColor.darker() : new Color(120, 120, 120), 1));
        }
        return btn;
    }

    /** Sort comparator for individual starred captures based on current sort setting. */
    private Comparator<CapturedCreature> favouriteCapSort() {
        switch (currentSort == null ? "Newest first" : currentSort) {
            case "Name Z–A":
                return (a, b) -> String.CASE_INSENSITIVE_ORDER.compare(b.npcName, a.npcName);
            case "Rarity (best)":
                return Comparator.comparingInt((CapturedCreature c) -> c.rarity.ordinal()).reversed();
            case "Quality (high)":
                return Comparator.comparingInt((CapturedCreature c) -> c.quality.overallRating()).reversed();
            case "Name A–Z":
                return Comparator.comparing((CapturedCreature c) -> c.npcName, String.CASE_INSENSITIVE_ORDER);
            default: // "Newest first"
                return Comparator.comparing((CapturedCreature c) -> c.captureTime, Comparator.reverseOrder());
        }
    }

    // -------------------------------------------------------------------------
    // Sorting (catalog mode)
    // -------------------------------------------------------------------------

    private void sortNames(List<String> names, boolean isCaptured) {
        if (!isCaptured) { names.sort(String.CASE_INSENSITIVE_ORDER); return; }
        switch (currentSort == null ? "Name A–Z" : currentSort) {
            case "Name Z–A":
                names.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(b, a)); break;
            case "Difficulty ↑":
                names.sort((a, b) -> diffOrdinal(a, capturesByNpc) - diffOrdinal(b, capturesByNpc)); break;
            case "Difficulty ↓":
                names.sort((a, b) -> diffOrdinal(b, capturesByNpc) - diffOrdinal(a, capturesByNpc)); break;
            case "Most caught":
                names.sort((a, b) -> capturesByNpc.get(b).size() - capturesByNpc.get(a).size()); break;
            case "Rarity (best)":
                names.sort((a, b) -> maxRarity(capturesByNpc.get(b)).ordinal()
                                   - maxRarity(capturesByNpc.get(a)).ordinal()); break;
            case "Quality (high)":
                names.sort((a, b) -> avgQuality(capturesByNpc.get(b)) - avgQuality(capturesByNpc.get(a))); break;
            case "Newest first":
                names.sort((a, b) -> latestCapture(capturesByNpc.get(b))
                                        .compareTo(latestCapture(capturesByNpc.get(a)))); break;
            default: names.sort(String.CASE_INSENSITIVE_ORDER); break;
        }
    }

    private void sortAllMixed(List<String> names) {
        switch (currentSort == null ? "Name A–Z" : currentSort) {
            case "Name Z–A":
                names.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(b, a)); break;
            case "Difficulty ↑":
                names.sort((a, b) -> diffOrdinalAny(a) - diffOrdinalAny(b)); break;
            case "Difficulty ↓":
                names.sort((a, b) -> diffOrdinalAny(b) - diffOrdinalAny(a)); break;
            case "Most caught":
                names.sort((a, b) -> {
                    int sa = capturesByNpc.containsKey(a) ? capturesByNpc.get(a).size() : 0;
                    int sb = capturesByNpc.containsKey(b) ? capturesByNpc.get(b).size() : 0;
                    return sb - sa;
                }); break;
            case "Rarity (best)":
                names.sort((a, b) -> {
                    int ra = capturesByNpc.containsKey(a) ? maxRarity(capturesByNpc.get(a)).ordinal() : -1;
                    int rb = capturesByNpc.containsKey(b) ? maxRarity(capturesByNpc.get(b)).ordinal() : -1;
                    return rb - ra;
                }); break;
            case "Quality (high)":
                names.sort((a, b) -> {
                    int qa = capturesByNpc.containsKey(a) ? avgQuality(capturesByNpc.get(a)) : 0;
                    int qb = capturesByNpc.containsKey(b) ? avgQuality(capturesByNpc.get(b)) : 0;
                    return qb - qa;
                }); break;
            case "Newest first":
                names.sort((a, b) -> {
                    Instant ia = capturesByNpc.containsKey(a) ? latestCapture(capturesByNpc.get(a)) : Instant.EPOCH;
                    Instant ib = capturesByNpc.containsKey(b) ? latestCapture(capturesByNpc.get(b)) : Instant.EPOCH;
                    return ib.compareTo(ia);
                }); break;
            default: names.sort(String.CASE_INSENSITIVE_ORDER); break;
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void styleShowLockedBtn(JToggleButton btn, boolean active) {
        btn.setFont(FontManager.getRunescapeSmallFont());
        btn.setBorderPainted(true);
        btn.setBorder(BorderFactory.createLineBorder(Color.WHITE, 1));
        btn.setFocusPainted(false);
        btn.setOpaque(true);
        btn.setContentAreaFilled(true);
        if (active) {
            btn.setBackground(new Color(30, 80, 150));
            btn.setText("<html><b><font color='#FFFFFF'>Show Locked</font></b></html>");
        } else {
            btn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            btn.setText("<html><b><font color='#808080'>Show Locked</font></b></html>");
        }
    }

    private static void styleCapturedFirstBtn(JToggleButton btn, boolean active) {
        btn.setFont(FontManager.getRunescapeSmallFont());
        btn.setBorderPainted(true);
        btn.setBorder(BorderFactory.createLineBorder(Color.WHITE, 1));
        btn.setFocusPainted(false);
        btn.setOpaque(true);
        btn.setContentAreaFilled(true);
        if (active) {
            btn.setBackground(new Color(190, 110, 20));
            btn.setText("<html><b><font color='#FFFFFF'>Captured First</font></b></html>");
        } else {
            btn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            btn.setText("<html><b><font color='#B0B0B0'>Captured First</font></b></html>");
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
                .max(Comparator.comparingInt(Enum::ordinal)).orElse(CreatureRarity.COMMON);
    }

    private static int avgQuality(List<CapturedCreature> captures) {
        return (int) captures.stream().mapToInt(c -> c.quality.overallRating()).average().orElse(0);
    }

    private static Instant latestCapture(List<CapturedCreature> captures) {
        return captures.stream().map(c -> c.captureTime)
                .max(Comparator.naturalOrder()).orElse(Instant.EPOCH);
    }

    // -------------------------------------------------------------------------
    // Favourites grid export
    // -------------------------------------------------------------------------

    private void exportFavouritesGrid(JButton btn) {
        List<CapturedCreature> starred = capturesByNpc.values().stream()
                .flatMap(List::stream)
                .filter(c -> c.favourite)
                .sorted(Comparator.comparing((CapturedCreature c) -> c.captureTime, Comparator.reverseOrder()))
                .collect(Collectors.toList());

        if (starred.isEmpty()) {
            btn.setText("No starred captures!");
            btn.setForeground(new Color(200, 100, 100));
            javax.swing.Timer t = new javax.swing.Timer(2000, ev -> {
                btn.setText("Export ★ Grid");
                btn.setForeground(new Color(255, 195, 40));
            });
            t.setRepeats(false);
            t.start();
            return;
        }

        int count = starred.size();
        int cols  = count == 1 ? 1 : count <= 4 ? 2 : count <= 9 ? 3 : 4;
        int rows  = (count + cols - 1) / cols;
        final int GAP      = 6;
        final int PAD      = 8;
        final int SCALE    = 2;
        final int HEADER_H = 34;
        final int BANNER_H = 26;
        final int SLOT_H   = AlbumCard.CARD_H + 2 + BANNER_H;

        String playerName = starred.stream()
                .map(c -> c.playerName).filter(n -> n != null && !n.isEmpty())
                .findFirst().orElse("Unknown");

        int logW = cols * AlbumCard.CARD_W + (cols - 1) * GAP + PAD * 2;
        int logH = HEADER_H + PAD + rows * SLOT_H + (rows - 1) * GAP + PAD;

        BufferedImage img = new BufferedImage(logW * SCALE, logH * SCALE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,     RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);
        g2.scale(SCALE, SCALE);

        g2.setColor(new Color(18, 18, 18));
        g2.fillRect(0, 0, logW, logH);

        g2.setColor(new Color(28, 28, 28));
        g2.fillRect(0, 0, logW, HEADER_H);
        String headerText = playerName + "'s Favourites";
        g2.setFont(FontManager.getRunescapeBoldFont());
        FontMetrics hfm = g2.getFontMetrics();
        int hx = (logW - hfm.stringWidth(headerText)) / 2;
        int hy = (HEADER_H + hfm.getAscent() - hfm.getDescent()) / 2;
        g2.setColor(new Color(255, 195, 40));
        g2.drawString(headerText, hx, hy);

        Font playerFont = FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD);
        Font idFont     = FontManager.getRunescapeSmallFont();

        for (int i = 0; i < starred.size(); i++) {
            CapturedCreature cap = starred.get(i);
            int dex = dexNumbers.getOrDefault(cap.npcName, 0);
            AlbumCard card = new AlbumCard(dex, cap.npcName, List.of(cap), collection, imageService);
            card.setSize(AlbumCard.CARD_W, AlbumCard.CARD_H);

            int col = i % cols;
            int row = i / cols;
            int x   = PAD + col * (AlbumCard.CARD_W + GAP);
            int y   = HEADER_H + PAD + row * (SLOT_H + GAP);

            Graphics2D cardG2 = (Graphics2D) g2.create();
            cardG2.translate(x, y);
            card.print(cardG2);
            cardG2.dispose();

            int bY = y + AlbumCard.CARD_H + 2;
            g2.setColor(new Color(25, 25, 25));
            g2.fillRoundRect(x, bY, AlbumCard.CARD_W, BANNER_H, 4, 4);

            String capPlayer = (cap.playerName != null && !cap.playerName.isEmpty()) ? cap.playerName : "Unknown";
            String capLine = "Captured by: " + capPlayer;
            g2.setFont(playerFont);
            FontMetrics pfm = g2.getFontMetrics();
            int capX = x + (AlbumCard.CARD_W - pfm.stringWidth(capLine)) / 2;
            g2.setColor(new Color(200, 155, 50));
            g2.drawString(capLine, capX, bY + pfm.getAscent() + 1);

            String cardId = CardId.encode(dex, cap);
            g2.setFont(idFont);
            FontMetrics ifm = g2.getFontMetrics();
            int idX = x + (AlbumCard.CARD_W - ifm.stringWidth(cardId)) / 2;
            g2.setColor(new Color(90, 90, 90));
            g2.drawString(cardId, idX, bY + pfm.getHeight() + ifm.getAscent() - 1);
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
