package com.bestiary.ui;

import com.bestiary.model.BestiaryCollection;
import com.bestiary.model.CapturedCreature;
import com.bestiary.model.CreatureRarity;
import com.bestiary.model.CreatureSpecies;
import com.bestiary.model.DifficultyTier;
import com.bestiary.model.MonsterRoster;
import com.bestiary.service.WikiImageService;
import com.bestiary.util.CardId;
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

    /** Opens the bulk-discard screen (owner window supplied). Wired by BestiaryPanel. */
    private static java.util.function.Consumer<Window> discardOpener;
    public static void setDiscardOpener(java.util.function.Consumer<Window> c) { discardOpener = c; }

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

    private static final String[] SORT_OPTIONS = {
        "Name A–Z", "Name Z–A", "Difficulty ↑", "Difficulty ↓", "Most caught",
        "Rarity (best)", "Power (high)", "Newest first"
    };

    private final Map<String, List<CapturedCreature>> capturesByNpc;
    private final Map<String, Integer> killCounts;
    private final BestiaryCollection collection;
    private final WikiImageService imageService;

    private final List<String> fullRoster;
    private final Map<String, Integer> dexNumbers;

    private final JPanel gridPanel;
    private JScrollPane gridScroll;
    private int savedCatalogScroll = 0;
    private final JLabel countLabel;

    // Catalog bar controls
    private JComboBox<String> sortBox;
    private JToggleButton     showLockedBtn;
    private JToggleButton     capturedFirstBtn;

    // Catalog state
    private String         currentSort      = "Name A–Z";
    private boolean        capturedFirst    = true;
    private boolean        showLocked       = true;
    private String         searchTerm       = "";
    private DifficultyTier filterDifficulty = null;
    private CreatureSpecies filterSpecies   = null;

    // Detail view state
    private String              detailMonsterName    = null;
    private int                 detailPage           = 0;
    private int                 detailPageSize       = 8;
    private String              detailSort           = "Rarity (best)";
    private CreatureRarity      detailFilterRarity   = null;
    private java.time.Instant   detailFilterCapture  = null;
    private boolean             detailFilterShiny    = false;

    // Detail bar controls
    private JPanel            topBarHolder;
    private JLabel            detailTitleLabel;
    private JLabel            detailPageLabel;
    private JComboBox<String> detailSortBox;
    private JButton           prevPageBtn;
    private JButton           nextPageBtn;
    private JButton           prevMonsterBtn;
    private JButton           nextMonsterBtn;
    private JButton                  detailExportBtn;
    private CapturedCreature         detailExportCap  = null;
    private List<CapturedCreature>   detailCurrentPage = Collections.emptyList();
    /** Captured monster names in the current catalog sort order — drives Prev/Next Monster. */
    private final List<String> catalogOrder = new ArrayList<>();

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

        JPanel btnPair = new JPanel();
        btnPair.setLayout(new BoxLayout(btnPair, BoxLayout.X_AXIS));
        btnPair.setOpaque(false);
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

        // Both filter dropdowns share this fixed width so the tier box and the
        // species box beneath it line up and never resize with the dialog.
        final int FILTER_W = 100;

        String[] diffOptions = {"All tiers", "Beginner", "Easy", "Medium", "Hard", "Elite", "Boss"};
        JComboBox<String> diffBox = new JComboBox<>(diffOptions);
        diffBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        diffBox.setForeground(Color.WHITE);
        diffBox.setFont(FontManager.getRunescapeSmallFont());
        Dimension filterDim = new Dimension(FILTER_W, diffBox.getPreferredSize().height);
        diffBox.setPreferredSize(filterDim);
        diffBox.setMaximumSize(filterDim);
        diffBox.addActionListener(e -> {
            int idx = diffBox.getSelectedIndex();
            filterDifficulty = idx == 0 ? null : DifficultyTier.values()[idx - 1];
            rebuildGrid();
        });

        row2.add(searchBox, BorderLayout.CENTER);
        row2.add(diffBox,   BorderLayout.EAST);

        // Row 2b: species filter, sitting directly under the tier dropdown (same width).
        JPanel row2b = new JPanel(new BorderLayout(6, 0));
        row2b.setOpaque(false);
        row2b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        row2b.setBorder(new EmptyBorder(4, 0, 0, 0));

        String[] speciesOptions = new String[CreatureSpecies.values().length + 1];
        speciesOptions[0] = "All species";
        for (int i = 0; i < CreatureSpecies.values().length; i++) {
            speciesOptions[i + 1] = CreatureSpecies.values()[i].label;
        }
        JComboBox<String> speciesBox = new JComboBox<>(speciesOptions);
        speciesBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        speciesBox.setForeground(Color.WHITE);
        speciesBox.setFont(FontManager.getRunescapeSmallFont());
        speciesBox.setPreferredSize(filterDim);
        speciesBox.setMaximumSize(filterDim);
        speciesBox.addActionListener(e -> {
            int idx = speciesBox.getSelectedIndex();
            filterSpecies = idx == 0 ? null : CreatureSpecies.values()[idx - 1];
            rebuildGrid();
        });
        row2b.add(speciesBox, BorderLayout.EAST);

        topBar.add(row1);
        topBar.add(row2);
        topBar.add(row2b);
        if (discardOpener != null) {
            JButton discardBtn = new JButton("Discard duplicates…");
            discardBtn.setFont(FontManager.getRunescapeSmallFont());
            discardBtn.setBackground(new Color(120, 55, 55));
            discardBtn.setForeground(Color.WHITE);
            discardBtn.setFocusPainted(false);
            discardBtn.addActionListener(e -> discardOpener.accept(AlbumDialog.this));
            JPanel dRow = new JPanel(new BorderLayout());
            dRow.setOpaque(false);
            dRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
            dRow.setBorder(new EmptyBorder(4, 0, 0, 0));
            dRow.add(discardBtn, BorderLayout.WEST);
            topBar.add(dRow);
        }

        // -------------------------------------------------------------------------
        // Catalog action listeners (declared after all fields are initialised)
        // -------------------------------------------------------------------------
        sortBox.addActionListener(e -> {
            currentSort = (String) sortBox.getSelectedItem();
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

        // Prev/Next Monster — walk the catalog in its current sort order
        prevMonsterBtn = new JButton("◀");
        nextMonsterBtn = new JButton("▶");
        for (JButton nav : new JButton[]{prevMonsterBtn, nextMonsterBtn}) {
            nav.setFont(FontManager.getRunescapeSmallFont());
            nav.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            nav.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            nav.setFocusPainted(false);
            nav.setMargin(new Insets(1, 6, 1, 6));
            nav.setToolTipText("Previous / next monster in album order");
        }
        prevMonsterBtn.addActionListener(e -> navigateMonster(-1));
        nextMonsterBtn.addActionListener(e -> navigateMonster(+1));

        JPanel dRow1West = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        dRow1West.setOpaque(false);
        dRow1West.add(backBtn);
        dRow1West.add(prevMonsterBtn);
        dRow1West.add(nextMonsterBtn);

        detailTitleLabel = new JLabel();
        detailTitleLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        detailTitleLabel.setForeground(Color.WHITE);
        detailTitleLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel dRow1East = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        dRow1East.setOpaque(false);

        detailExportBtn = new JButton("Export");
        detailExportBtn.setFont(FontManager.getRunescapeSmallFont());
        detailExportBtn.setBackground(new Color(0, 120, 40));
        detailExportBtn.setForeground(Color.WHITE);
        detailExportBtn.setFocusPainted(false);
        detailExportBtn.setVisible(false);
        detailExportBtn.addActionListener(e -> {
            if (detailExportCap != null) {
                CardExportDialog.open(AlbumDialog.this, detailExportCap);
            } else {
                Map<String, Integer> pageDex = new java.util.HashMap<>();
                for (CapturedCreature cap : detailCurrentPage) {
                    pageDex.put(cap.npcName, dexNumbers.getOrDefault(cap.npcName, 0));
                }
                String label = detailMonsterName != null ? detailMonsterName : "Page";
                new PageExportDialog(AlbumDialog.this, new java.util.ArrayList<>(detailCurrentPage),
                        label, pageDex, imageService, collection);
            }
        });
        dRow1East.add(detailExportBtn);

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
            dRow1East.add(pb);
        }

        dRow1.add(dRow1West,        BorderLayout.WEST);
        dRow1.add(detailTitleLabel, BorderLayout.CENTER);
        dRow1.add(dRow1East,        BorderLayout.EAST);

        // Detail row 2: Sort | ← Prev | page X / Y | Next →
        JPanel dRow2 = new JPanel(new BorderLayout(6, 0));
        dRow2.setOpaque(false);
        dRow2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        dRow2.setBorder(new EmptyBorder(4, 0, 0, 0));

        JLabel dSortLabel = new JLabel("Sort:");
        dSortLabel.setFont(FontManager.getRunescapeSmallFont());
        dSortLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        detailSortBox = new JComboBox<>(new String[]{"Rarity (best)", "Power (high)", "Newest first"});
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

        // Wrap in a NORTH-anchored panel so the viewport can't stretch gridPanel
        // vertically (which would make GridLayout cells taller than CARD_H)
        JPanel gridWrapper = new JPanel(new BorderLayout());
        gridWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
        gridWrapper.add(gridPanel, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(gridWrapper);
        gridScroll = scroll;
        scroll.setBorder(null);
        scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

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
            @Override public void windowClosed(WindowEvent e)  { CardExportDialog.disposeOpen(); }
        });

        imageService.prefetchBatch(fullRoster, gridPanel::repaint);
        SwingUtilities.invokeLater(this::rebuildGrid);
        if (startFavourites) {
            SwingUtilities.invokeLater(this::showFavouritesDetailView);
        }
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
        if (detailMonsterName == null && gridScroll != null) {   // leaving the catalog
            savedCatalogScroll = gridScroll.getVerticalScrollBar().getValue();
        }
        detailMonsterName   = name;
        detailFilterRarity  = filterRarity;
        detailFilterCapture = filterCapture;
        detailFilterShiny   = false;
        detailPage = 0;
        detailSort = "Rarity (best)";
        detailSortBox.setSelectedItem("Rarity (best)");
        ((CardLayout) topBarHolder.getLayout()).show(topBarHolder, "DETAIL");
        rebuildGrid();
        updateMonsterNav();
    }

    /** Opens the detail pane showing all starred captures (cross-monster). */
    private void showFavouritesDetailView() {
        if (detailMonsterName == null && gridScroll != null) {   // leaving the catalog
            savedCatalogScroll = gridScroll.getVerticalScrollBar().getValue();
        }
        detailMonsterName   = "★ Favourites";   // ★ sentinel
        detailFilterRarity  = null;
        detailFilterCapture = null;
        detailFilterShiny   = false;
        detailPage          = 0;
        detailSort          = "Power (high)";
        detailSortBox.setSelectedItem("Power (high)");
        ((CardLayout) topBarHolder.getLayout()).show(topBarHolder, "DETAIL");
        rebuildGrid();
        updateMonsterNav();
    }

    /** If the Album is open, switch it to the Favourites detail view and return true. */
    public static boolean switchToFavouritesIfOpen() {
        if (current != null && current.isShowing()) {
            current.showFavouritesDetailView();
            current.toFront();
            return true;
        }
        return false;
    }

    /** Steps to the previous/next captured monster in the current catalog order. */
    private void navigateMonster(int dir) {
        if (detailMonsterName == null || detailMonsterName.startsWith("★")) return;
        if (catalogOrder.isEmpty()) return;
        int idx = catalogOrder.indexOf(detailMonsterName);
        if (idx < 0) return;
        int next = (idx + dir + catalogOrder.size()) % catalogOrder.size();
        showDetail(catalogOrder.get(next));
    }

    /** Enables Prev/Next Monster only for a real monster with siblings to move to. */
    private void updateMonsterNav() {
        boolean on = detailMonsterName != null && !detailMonsterName.startsWith("★")
                && catalogOrder.size() > 1 && catalogOrder.contains(detailMonsterName);
        if (prevMonsterBtn != null) prevMonsterBtn.setEnabled(on);
        if (nextMonsterBtn != null) nextMonsterBtn.setEnabled(on);
    }

    private void showCatalog() {
        detailMonsterName   = null;
        detailFilterRarity  = null;
        detailFilterCapture = null;
        detailFilterShiny   = false;
        ((CardLayout) topBarHolder.getLayout()).show(topBarHolder, "CATALOG");
        rebuildGrid();
        // Restore the catalog scroll position we were at before opening a card
        if (gridScroll != null) {
            SwingUtilities.invokeLater(() ->
                    gridScroll.getVerticalScrollBar().setValue(savedCatalogScroll));
        }
    }

    // -------------------------------------------------------------------------
    // Grid construction
    // -------------------------------------------------------------------------

    /** Rebuilds the album from the live collection (e.g. after discards) and refreshes the view. */
    public void refreshFromCollection() {
        Map<String, List<CapturedCreature>> grouped = collection.creatures.stream()
                .collect(Collectors.groupingBy(c -> c.npcName));
        capturesByNpc.clear();
        capturesByNpc.putAll(grouped);
        if (detailMonsterName != null && !detailMonsterName.startsWith("★")
                && !capturesByNpc.containsKey(detailMonsterName)) {
            showCatalog();            // the monster we were viewing has no captures left
        } else {
            rebuildGrid();
        }
    }

    /** Refreshes the open album (if any) from the live collection. */
    public static void refreshOpenAlbum() {
        if (current != null && current.isShowing()) current.refreshFromCollection();
    }

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
                .filter(n -> {
                    if (filterSpecies == null) return true;
                    int combat = capturesByNpc.containsKey(n) ? capturesByNpc.get(n).get(0).npcCombatLevel : 0;
                    return MonsterRoster.getSpecies(n, combat) == filterSpecies;
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

        // Remember the captured monsters in display order for Prev/Next Monster navigation
        catalogOrder.clear();
        ordered.stream().filter(capturesByNpc::containsKey).forEach(catalogOrder::add);

        // Favourites shortcut card — always shown when no search/filter active
        long favCount = capturesByNpc.values().stream().flatMap(List::stream).filter(c -> c.favourite).count();
        if (searchTerm.isEmpty() && filterDifficulty == null && filterSpecies == null) {
            gridPanel.add(buildFavouritesShortcutCard((int) favCount));
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

    private static final String FAVS_SENTINEL = "★ Favourites";  // "★ Favourites"

    private boolean isFavouritesDetail() { return FAVS_SENTINEL.equals(detailMonsterName); }

    private void buildDetailView() {
        List<CapturedCreature> allCaps = isFavouritesDetail()
                ? capturesByNpc.values().stream().flatMap(List::stream)
                    .filter(c -> c.favourite).collect(Collectors.toList())
                : capturesByNpc.getOrDefault(detailMonsterName, Collections.emptyList());

        // Apply filter
        List<CapturedCreature> filtered;
        if (detailFilterCapture != null) {
            filtered = allCaps.stream()
                    .filter(c -> c.captureTime.equals(detailFilterCapture))
                    .collect(Collectors.toList());
        } else if (detailFilterShiny) {
            filtered = allCaps.stream()
                    .filter(CapturedCreature::isShiny)
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
            case "Power (high)":
                filtered.sort(Comparator.comparingInt((CapturedCreature c) -> c.powerLevel()).reversed());
                break;
            case "Newest first":
                filtered.sort(Comparator.comparing((CapturedCreature c) -> c.captureTime, Comparator.reverseOrder()));
                break;
            default: // "Rarity (best)"
                filtered.sort(Comparator.comparingInt((CapturedCreature c) -> c.rarity.ordinal()).reversed()
                        .thenComparingInt(c -> -c.powerLevel()));
                break;
        }

        int total      = filtered.size();
        int totalPages = Math.max(1, (total + detailPageSize - 1) / detailPageSize);
        detailPage = Math.max(0, Math.min(detailPage, totalPages - 1));

        // Update header label and pagination
        if (isFavouritesDetail()) {
            String suffix = detailFilterRarity != null ? " — " + detailFilterRarity.label : "";
            detailTitleLabel.setText("★ Favourites (" + total + ")" + suffix);
            detailTitleLabel.setForeground(new Color(255, 195, 40));
        } else {
            CreatureRarity best = detailFilterRarity != null ? detailFilterRarity
                    : total > 0 ? filtered.stream().map(c -> c.rarity)
                            .max(Comparator.comparingInt(Enum::ordinal)).orElse(CreatureRarity.COMMON)
                    : CreatureRarity.COMMON;
            String suffix = detailFilterRarity != null ? " — " + detailFilterRarity.label
                    : detailFilterCapture != null ? " — single capture" : "";
            detailTitleLabel.setText(detailMonsterName + " (" + total + ")" + suffix);
            detailTitleLabel.setForeground(best.displayColor);
        }
        detailPageLabel.setText((detailPage + 1) + " / " + totalPages);
        prevPageBtn.setEnabled(detailPage > 0);
        nextPageBtn.setEnabled(detailPage < totalPages - 1);

        // Update export button — single capture → CardExportDialog; page → grid export
        if (detailFilterCapture != null && !filtered.isEmpty()) {
            detailExportCap = filtered.get(0);
            detailExportBtn.setText("Export");
        } else {
            detailExportCap = null;
            detailExportBtn.setText("Export Page");
        }
        detailExportBtn.setVisible(true);

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
        detailCurrentPage = filtered.subList(from, to);
        for (CapturedCreature cap : detailCurrentPage) {
            int dex = dexNumbers.getOrDefault(cap.npcName, 0);
            AlbumCard card = new AlbumCard(dex, cap.npcName, List.of(cap), collection, imageService);
            card.setShowQuality(true);
            card.setClickOverride(() -> CardExportDialog.open(AlbumDialog.this, cap));
            card.setCopyCallback(() -> CardExportDialog.copyNow(AlbumDialog.this, cap));
            card.setFavToggle(() -> {
                if (!cap.favourite && collection.countFavourites() >= 20) {
                    Runnable limitCb = CaptureRow.getOnFavouriteLimitReached();
                    if (limitCb != null) limitCb.run();
                    return;
                }
                cap.favourite = !cap.favourite;
                if (onFavouriteChanged != null) onFavouriteChanged.run();
            }, () -> cap.favourite);
            card.setAlbumCoverToggle(() -> {
                if (cap.albumCover) cap.albumCover = false;
                else collection.setAlbumCover(cap); // clears any other cover for this monster
                if (onFavouriteChanged != null) onFavouriteChanged.run();
            }, () -> cap.albumCover);
            card.setNicknameCallback(() -> {
                if (onFavouriteChanged != null) onFavouriteChanged.run();
                rebuildGrid();
            });
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

        // Rarity pills — always show all 6 rarities; gray out / disable ones with no captures
        Set<CreatureRarity> presentRarities = allCaps.stream()
                .map(c -> c.rarity)
                .collect(java.util.stream.Collectors.toSet());

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        row.setBackground(new Color(35, 35, 35));
        row.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(60, 60, 60)));

        boolean anyShiny = allCaps.stream().anyMatch(CapturedCreature::isShiny);

        JButton allBtn = makeRarityPill("All", detailFilterRarity == null && !detailFilterShiny, null, false);
        allBtn.addActionListener(e -> {
            detailFilterRarity = null; detailFilterShiny = false; detailPage = 0; rebuildGrid();
        });
        row.add(allBtn);

        CreatureRarity[] allRarities = {
            CreatureRarity.MYTHIC, CreatureRarity.LEGENDARY, CreatureRarity.EPIC,
            CreatureRarity.RARE,   CreatureRarity.UNCOMMON,  CreatureRarity.COMMON
        };
        for (CreatureRarity r : allRarities) {
            boolean has = presentRarities.contains(r);
            JButton rb = makeRarityPill(r.label, r == detailFilterRarity, r.displayColor, !has);
            if (has) {
                rb.addActionListener(e -> {
                    // Clicking the active filter toggles back to All
                    detailFilterRarity = (detailFilterRarity == r) ? null : r;
                    detailFilterShiny = false; detailPage = 0; rebuildGrid();
                });
            }
            row.add(rb);
        }

        // Shiny pill sits after Common (orthogonal to rarity — filters shiny captures)
        JButton shinyBtn = makeRarityPill("✦ Shiny", detailFilterShiny, new Color(255, 235, 120), !anyShiny);
        if (anyShiny) {
            shinyBtn.addActionListener(e -> {
                detailFilterShiny = !detailFilterShiny;   // clicking again toggles back to All
                detailFilterRarity = null; detailPage = 0; rebuildGrid();
            });
        }
        row.add(shinyBtn);
        return row;
    }

    private static JButton makeRarityPill(String label, boolean selected, Color rarityColor, boolean disabled) {
        JButton btn = new JButton(label);
        btn.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        btn.setMargin(new Insets(1, 6, 1, 6));
        btn.setFocusPainted(false);
        if (disabled) {
            btn.setEnabled(false);
            btn.setBackground(new Color(35, 35, 35));
            btn.setForeground(new Color(60, 60, 60));
            btn.setBorder(BorderFactory.createLineBorder(new Color(55, 55, 55), 1));
        } else if (selected) {
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

    /** Special card in the catalog grid that shortcuts to favourites mode. */
    private JPanel buildFavouritesShortcutCard(int favCount) {
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = AlbumCard.CARD_H; // fixed height, same as AlbumCard
                g2.setColor(new Color(40, 32, 8));
                g2.fillRoundRect(0, 0, w, h, 8, 8);
                g2.setColor(new Color(100, 78, 10));
                g2.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);

                int cx = w / 2;
                g2.setFont(new Font(Font.DIALOG, Font.BOLD, 28));
                FontMetrics sfm = g2.getFontMetrics();
                int starY = h / 2 - sfm.getHeight() / 2 + sfm.getAscent() - 20;
                g2.setColor(new Color(255, 195, 40));
                g2.drawString("★", cx - sfm.stringWidth("★") / 2, starY);

                g2.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
                FontMetrics tfm = g2.getFontMetrics();
                String title = "Favourites";
                g2.setColor(new Color(255, 215, 80));
                g2.drawString(title, cx - tfm.stringWidth(title) / 2, starY + sfm.getHeight() + 4);

                g2.setFont(FontManager.getRunescapeSmallFont());
                FontMetrics cfm = g2.getFontMetrics();
                String countStr = favCount + " starred";
                g2.setColor(new Color(180, 140, 40));
                g2.drawString(countStr, cx - cfm.stringWidth(countStr) / 2,
                        starY + sfm.getHeight() + 4 + tfm.getHeight() + 2);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setPreferredSize(new Dimension(AlbumCard.CARD_W, AlbumCard.CARD_H));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.setToolTipText("View your starred captures");
        card.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                showFavouritesDetailView();
            }
        });
        return card;
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
            case "Power (high)":
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
            case "Power (high)":
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
        return (int) captures.stream().mapToInt(c -> c.powerLevel()).average().orElse(0);
    }

    private static Instant latestCapture(List<CapturedCreature> captures) {
        return captures.stream().map(c -> c.captureTime)
                .max(Comparator.naturalOrder()).orElse(Instant.EPOCH);
    }

    // -------------------------------------------------------------------------
    // Detail page grid export
    // -------------------------------------------------------------------------

    private void exportPageGrid(JButton btn) {
        List<CapturedCreature> page = detailCurrentPage;
        if (page == null || page.isEmpty()) return;

        int count = page.size();
        int cols  = count == 1 ? 1 : count <= 4 ? 2 : 4;
        int rows  = (count + cols - 1) / cols;
        final int GAP      = 6;
        final int PAD      = 8;
        final int SCALE    = 2;
        final int BANNER_H = 26;
        final int SLOT_H   = AlbumCard.CARD_H + 2 + BANNER_H;

        String playerName = page.stream()
                .map(c -> c.playerName).filter(n -> n != null && !n.isEmpty())
                .findFirst().orElse("Unknown");
        String headerText = detailMonsterName != null ? detailMonsterName + " — " + count + " card" + (count == 1 ? "" : "s") : count + " cards";

        final int HEADER_H = 34;
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
        g2.setFont(FontManager.getRunescapeBoldFont());
        FontMetrics hfm = g2.getFontMetrics();
        int hx = (logW - hfm.stringWidth(headerText)) / 2;
        int hy = (HEADER_H + hfm.getAscent() - hfm.getDescent()) / 2;
        g2.setColor(page.get(0).rarity.displayColor);
        g2.drawString(headerText, hx, hy);

        Font playerFont = FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD);
        Font idFont     = FontManager.getRunescapeSmallFont();

        for (int i = 0; i < page.size(); i++) {
            CapturedCreature cap = page.get(i);
            int dex = dexNumbers.getOrDefault(cap.npcName, 0);
            AlbumCard card = new AlbumCard(dex, cap.npcName, List.of(cap), collection, imageService);
            card.setShowQuality(true);
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

            String cardId = CardId.encode(dex, cap);
            g2.setFont(idFont);
            FontMetrics ifm = g2.getFontMetrics();
            int idX = x + (AlbumCard.CARD_W - ifm.stringWidth(cardId)) / 2;
            g2.setColor(new Color(90, 90, 90));
            g2.drawString(cardId, idX, bY + ifm.getAscent() + 2);

            String capPlayer = (cap.playerName != null && !cap.playerName.isEmpty()) ? cap.playerName : playerName;
            g2.setFont(playerFont);
            FontMetrics pfm = g2.getFontMetrics();
            String capLine = "Captured by: " + capPlayer;
            int capX = x + (AlbumCard.CARD_W - pfm.stringWidth(capLine)) / 2;
            g2.setColor(new Color(200, 155, 50));
            g2.drawString(capLine, capX, bY + ifm.getHeight() + pfm.getAscent() + 1);
        }
        g2.dispose();

        BufferedImage exported = img;
        Transferable t = new Transferable() {
            @Override public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{DataFlavor.imageFlavor}; }
            @Override public boolean isDataFlavorSupported(DataFlavor f) { return DataFlavor.imageFlavor.equals(f); }
            @Override public Object getTransferData(DataFlavor f) throws java.awt.datatransfer.UnsupportedFlavorException {
                if (!DataFlavor.imageFlavor.equals(f)) throw new java.awt.datatransfer.UnsupportedFlavorException(f);
                return exported;
            }
        };
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(t, null);

        String prev = btn.getText();
        btn.setText("Copied! (" + count + ")");
        btn.setForeground(new Color(120, 200, 120));
        javax.swing.Timer timer = new javax.swing.Timer(2500, ev -> {
            btn.setText(prev);
            btn.setForeground(Color.WHITE);
        });
        timer.setRepeats(false);
        timer.start();
    }

}
