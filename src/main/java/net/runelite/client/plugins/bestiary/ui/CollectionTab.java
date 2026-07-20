package net.runelite.client.plugins.bestiary.ui;

import net.runelite.client.plugins.bestiary.model.CapturedCreature;
import net.runelite.client.plugins.bestiary.model.CreatureRarity;
import net.runelite.client.plugins.bestiary.service.BestiaryDataService;
import net.runelite.client.plugins.bestiary.service.WikiImageService;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.IconTextField;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Collection tab with two view modes:
 * - Grouped: one card per NPC+rarity, arranged under rarity section headers
 * - Individual: one compact row per capture, flat list
 */
public class CollectionTab extends JPanel {

    private static final Color HEADER_BG = new Color(35, 35, 35);

    private final BestiaryDataService dataService;
    private final WikiImageService imageService;

    private final IconTextField searchBar;
    private final JComboBox<String> rarityFilter;
    private final JComboBox<String> sortOrder;
    private final JPanel cardContainer;

    private JToggleButton groupedBtn;
    private JToggleButton individualBtn;
    private JButton starBtn;
    private JPanel subToggleRow;

    private enum ViewMode { GROUPED, INDIVIDUAL, FAVOURITES }
    private ViewMode viewMode = ViewMode.GROUPED;
    private boolean  byMonster = false;

    // Sort options available in both modes
    private static final String[] SORT_OPTIONS = {
        "Name A-Z", "Name Z-A",
        "Newest first", "Oldest first",
        "Rarity (best)", "Rarity (worst)",
        "Quality (high)", "Quality (low)"
    };

    public CollectionTab(BestiaryDataService dataService, WikiImageService imageService) {
        this.dataService  = dataService;
        this.imageService = imageService;
        setLayout(new BorderLayout(0, 6));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(new EmptyBorder(8, 8, 8, 8));

        // --- Controls ---
        JPanel controls = new JPanel(new BorderLayout(0, 4));
        controls.setOpaque(false);

        searchBar = new IconTextField();
        searchBar.setIcon(IconTextField.Icon.SEARCH);
        searchBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        searchBar.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
        searchBar.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e)  { rebuildCards(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e)  { rebuildCards(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { rebuildCards(); }
        });

        String[] rarityOptions = buildRarityOptions();
        rarityFilter = new JComboBox<>(rarityOptions);
        rarityFilter.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        rarityFilter.setForeground(Color.WHITE);
        rarityFilter.setFont(FontManager.getRunescapeSmallFont());
        rarityFilter.addActionListener(e -> rebuildCards());

        sortOrder = new JComboBox<>(SORT_OPTIONS);
        sortOrder.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        sortOrder.setForeground(Color.WHITE);
        sortOrder.setFont(FontManager.getRunescapeSmallFont());
        sortOrder.addActionListener(e -> rebuildCards());

        JPanel filterRow = new JPanel(new GridLayout(1, 2, 4, 0));
        filterRow.setOpaque(false);
        filterRow.add(rarityFilter);
        filterRow.add(sortOrder);

        // --- Star button: Favourites toggle, sits beside search bar ---
        starBtn = new JButton("★");
        starBtn.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD).deriveFont(12f));
        starBtn.setContentAreaFilled(false);
        starBtn.setOpaque(false);
        starBtn.setFocusPainted(false);
        starBtn.setBorderPainted(false);
        starBtn.setForeground(new Color(120, 120, 120));
        starBtn.setToolTipText("Show Favourites");
        starBtn.setPreferredSize(new Dimension(26, 26));
        starBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPanel searchRow = new JPanel(new BorderLayout(4, 0));
        searchRow.setOpaque(false);
        searchRow.add(searchBar, BorderLayout.CENTER);
        searchRow.add(starBtn,   BorderLayout.EAST);

        // --- Main toggle: Grouped / Individual (2 buttons; Favourites via star) ---
        JPanel toggleRow = new JPanel(new GridLayout(1, 2, 0, 0));
        toggleRow.setOpaque(false);

        groupedBtn    = new JToggleButton("Grouped");
        individualBtn = new JToggleButton("Individual");

        styleToggleButton(groupedBtn,    true);
        styleToggleButton(individualBtn, false);

        ButtonGroup btnGroup = new ButtonGroup();
        btnGroup.add(groupedBtn);
        btnGroup.add(individualBtn);
        groupedBtn.setSelected(true);

        toggleRow.add(groupedBtn);
        toggleRow.add(individualBtn);

        // --- Sub-toggle (Grouped only): By Rarity / By Monster ---
        subToggleRow = new JPanel(new GridLayout(1, 2, 0, 0));
        subToggleRow.setOpaque(false);

        JToggleButton byRarityBtn  = new JToggleButton("By Rarity");
        JToggleButton byMonsterBtn = new JToggleButton("By Monster");

        styleToggleButton(byRarityBtn,  true);
        styleToggleButton(byMonsterBtn, false);

        ButtonGroup subGroup = new ButtonGroup();
        subGroup.add(byRarityBtn);
        subGroup.add(byMonsterBtn);
        byRarityBtn.setSelected(true);

        subToggleRow.add(byRarityBtn);
        subToggleRow.add(byMonsterBtn);

        // --- Album button: persistent, always visible below controls ---
        JButton albumBtn = new JButton("Open Bestiary Album");
        albumBtn.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        albumBtn.setBackground(new Color(55, 55, 55));
        albumBtn.setForeground(new Color(255, 165, 0));
        albumBtn.setFocusPainted(false);
        albumBtn.setBorderPainted(false);
        albumBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        albumBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        albumBtn.setToolTipText("Open the full Bestiary Album dex grid");
        albumBtn.addActionListener(e -> openAlbum(SwingUtilities.getWindowAncestor(CollectionTab.this)));

        // Action listeners
        starBtn.addActionListener(e -> {
            if (viewMode == ViewMode.FAVOURITES) {
                viewMode = ViewMode.GROUPED;
                styleToggleButton(groupedBtn,    true);
                styleToggleButton(individualBtn, false);
                groupedBtn.setSelected(true);
                subToggleRow.setVisible(true);
                starBtn.setForeground(new Color(120, 120, 120));
                rebuildCards();
            } else {
                showFavourites();
            }
        });
        groupedBtn.addActionListener(e -> {
            viewMode = ViewMode.GROUPED;
            styleToggleButton(groupedBtn,    true);
            styleToggleButton(individualBtn, false);
            starBtn.setForeground(new Color(120, 120, 120));
            subToggleRow.setVisible(true);
            rebuildCards();
        });
        individualBtn.addActionListener(e -> {
            viewMode = ViewMode.INDIVIDUAL;
            styleToggleButton(groupedBtn,    false);
            styleToggleButton(individualBtn, true);
            starBtn.setForeground(new Color(120, 120, 120));
            subToggleRow.setVisible(false);
            sortOrder.setSelectedItem("Newest first");
            rebuildCards();
        });
        byRarityBtn.addActionListener(e -> {
            byMonster = false;
            styleToggleButton(byRarityBtn,  true);
            styleToggleButton(byMonsterBtn, false);
            rebuildCards();
        });
        byMonsterBtn.addActionListener(e -> {
            byMonster = true;
            styleToggleButton(byRarityBtn,  false);
            styleToggleButton(byMonsterBtn, true);
            rebuildCards();
        });

        JPanel togglesPanel = new JPanel(new BorderLayout(0, 2));
        togglesPanel.setOpaque(false);
        togglesPanel.add(toggleRow,    BorderLayout.NORTH);
        togglesPanel.add(subToggleRow, BorderLayout.SOUTH);

        controls.add(searchRow,    BorderLayout.NORTH);
        controls.add(filterRow,    BorderLayout.CENTER);
        controls.add(togglesPanel, BorderLayout.SOUTH);

        JPanel northPanel = new JPanel(new BorderLayout(0, 4));
        northPanel.setOpaque(false);
        northPanel.add(controls,  BorderLayout.CENTER);
        northPanel.add(albumBtn,  BorderLayout.SOUTH);

        // --- Card container ---
        // Implements Scrollable so the viewport constrains its width — without this,
        // HORIZONTAL_SCROLLBAR_NEVER only hides the scrollbar but content still lays
        // out at preferred width and overflows.
        cardContainer = new ScrollablePanel();
        cardContainer.setLayout(new BoxLayout(cardContainer, BoxLayout.Y_AXIS));
        cardContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JScrollPane scroll = new JScrollPane(cardContainer);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getVerticalScrollBar().setPreferredSize(new java.awt.Dimension(0, 0));

        add(northPanel, BorderLayout.NORTH);
        add(scroll,     BorderLayout.CENTER);

        rebuildCards();
    }

    public void refresh() {
        rebuildCards();
    }

    public void showFavourites() {
        viewMode = ViewMode.FAVOURITES;
        styleToggleButton(groupedBtn,    false);
        styleToggleButton(individualBtn, false);
        starBtn.setForeground(new Color(255, 195, 40));
        subToggleRow.setVisible(false);
        sortOrder.setSelectedItem("Rarity (best)");
        rebuildCards();
    }

    public void openAlbum(Window parent) {
        Map<String, List<CapturedCreature>> byNpc = dataService.getCollection().creatures.stream()
                .collect(Collectors.groupingBy(c -> c.npcName));
        new AlbumDialog(parent, byNpc, dataService.getCollection().killCounts,
                dataService.getCollection(), imageService);
    }

    private void rebuildCards() {
        cardContainer.removeAll();

        String query          = searchBar.getText().trim().toLowerCase();
        String selectedRarity = (String) rarityFilter.getSelectedItem();
        String selectedSort   = (String) sortOrder.getSelectedItem();

        List<CapturedCreature> allCreatures = dataService.getCollection().creatures;

        // Apply search + rarity filters to raw creature list
        List<CapturedCreature> filtered = allCreatures.stream()
                .filter(c -> query.isEmpty() || c.npcName.toLowerCase().contains(query))
                .filter(c -> selectedRarity == null || "All Rarities".equals(selectedRarity)
                        || c.rarity == CreatureRarity.fromLabel(selectedRarity))
                .collect(Collectors.toList());

        if (viewMode == ViewMode.FAVOURITES) {
            buildFavouritesView(filtered, selectedSort);
        } else if (filtered.isEmpty()) {
            JLabel empty = new JLabel("No creatures match.");
            empty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            empty.setFont(FontManager.getRunescapeSmallFont());
            empty.setAlignmentX(Component.CENTER_ALIGNMENT);
            empty.setBorder(new EmptyBorder(20, 0, 0, 0));
            cardContainer.add(empty);
        } else if (viewMode == ViewMode.GROUPED && byMonster) {
            buildMonsterView(filtered, selectedSort);
        } else if (viewMode == ViewMode.GROUPED) {
            buildGroupedView(filtered, selectedSort);
        } else {
            buildIndividualView(filtered, selectedSort);
        }

        cardContainer.revalidate();
        cardContainer.repaint();
        revalidate();
        repaint();
    }

    // -------------------------------------------------------------------------
    // Grouped mode: rarity section headers → cards per NPC+rarity group
    // -------------------------------------------------------------------------

    private void buildGroupedView(List<CapturedCreature> filtered, String selectedSort) {
        // Group by npcId+rarity
        Map<String, List<CapturedCreature>> byNpcRarity = filtered.stream()
                .collect(Collectors.groupingBy(c -> c.npcName + ":" + c.rarity.ordinal()));

        List<Map.Entry<String, List<CapturedCreature>>> entries = new ArrayList<>(byNpcRarity.entrySet());
        sortEntries(entries, selectedSort);

        // Iterate from rarest to most common, emit headers + cards per rarity section
        CreatureRarity[] raritiesDesc = {
            CreatureRarity.MYTHIC, CreatureRarity.LEGENDARY, CreatureRarity.EPIC,
            CreatureRarity.RARE,   CreatureRarity.UNCOMMON,  CreatureRarity.COMMON
        };

        for (CreatureRarity rarity : raritiesDesc) {
            List<Map.Entry<String, List<CapturedCreature>>> section = entries.stream()
                    .filter(e -> e.getValue().get(0).rarity == rarity)
                    .collect(Collectors.toList());

            if (section.isEmpty()) continue;

            cardContainer.add(buildRarityHeader(rarity, section.size()));
            cardContainer.add(Box.createVerticalStrut(2));

            for (Map.Entry<String, List<CapturedCreature>> entry : section) {
                cardContainer.add(new CreatureCard(entry.getValue(), dataService.getCollection()));
                cardContainer.add(Box.createVerticalStrut(2));
            }

            cardContainer.add(Box.createVerticalStrut(4));
        }
    }

    private JPanel buildRarityHeader(CreatureRarity rarity, int count) {
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setBackground(HEADER_BG);
        header.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 4, 0, 0, rarity.displayColor),
                new EmptyBorder(5, 8, 5, 8)));
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        JLabel label = new JLabel("● " + rarity.label.toUpperCase());
        label.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        label.setForeground(rarity.displayColor);

        JLabel countLabel = new JLabel(count + " type" + (count == 1 ? "" : "s"), SwingConstants.RIGHT);
        countLabel.setFont(FontManager.getRunescapeSmallFont());
        countLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        header.add(label,      BorderLayout.WEST);
        header.add(countLabel, BorderLayout.EAST);
        return header;
    }

    private void sortEntries(List<Map.Entry<String, List<CapturedCreature>>> entries, String sort) {
        if (sort == null) return;
        switch (sort) {
            case "Name A-Z":
                entries.sort(Comparator.comparing(e -> e.getValue().get(0).npcName));
                break;
            case "Name Z-A":
                entries.sort(Comparator.comparing((Map.Entry<String, List<CapturedCreature>> e) ->
                        e.getValue().get(0).npcName).reversed());
                break;
            case "Newest first":
                entries.sort((a, b) -> latestCapture(b).compareTo(latestCapture(a)));
                break;
            case "Oldest first":
                entries.sort(Comparator.comparing(e -> earliestCapture(e.getValue())));
                break;
            case "Rarity (best)":
                entries.sort((a, b) -> b.getValue().get(0).rarity.ordinal()
                                     - a.getValue().get(0).rarity.ordinal());
                break;
            case "Rarity (worst)":
                entries.sort(Comparator.comparingInt(e -> e.getValue().get(0).rarity.ordinal()));
                break;
            case "Quality (high)":
                entries.sort((a, b) -> avgQuality(b.getValue()) - avgQuality(a.getValue()));
                break;
            case "Quality (low)":
                entries.sort(Comparator.comparingInt(e -> avgQuality(e.getValue())));
                break;
        }
    }

    // -------------------------------------------------------------------------
    // Individual mode: flat list of CaptureRow per capture
    // -------------------------------------------------------------------------

    private void buildIndividualView(List<CapturedCreature> filtered, String selectedSort) {
        List<CapturedCreature> sorted = new ArrayList<>(filtered);
        switch (selectedSort == null ? "Newest first" : selectedSort) {
            case "Name A-Z":
                sorted.sort(Comparator.comparing(c -> c.npcName));
                break;
            case "Name Z-A":
                sorted.sort(Comparator.comparing((CapturedCreature c) -> c.npcName).reversed());
                break;
            case "Newest first":
                sorted.sort(Comparator.comparing((CapturedCreature c) -> c.captureTime).reversed());
                break;
            case "Oldest first":
                sorted.sort(Comparator.comparing(c -> c.captureTime));
                break;
            case "Rarity (best)":
                sorted.sort((a, b) -> b.rarity.ordinal() - a.rarity.ordinal());
                break;
            case "Rarity (worst)":
                sorted.sort(Comparator.comparingInt(c -> c.rarity.ordinal()));
                break;
            case "Quality (high)":
                sorted.sort(Comparator.comparingInt((CapturedCreature c) -> c.quality.overallRating()).reversed());
                break;
            case "Quality (low)":
                sorted.sort(Comparator.comparingInt(c -> c.quality.overallRating()));
                break;
            default:
                sorted.sort(Comparator.comparing((CapturedCreature c) -> c.captureTime).reversed());
        }

        Runnable onFav = () -> { dataService.saveNow(); rebuildCards(); };
        for (CapturedCreature capture : sorted) {
            cardContainer.add(new CaptureRow(capture, dataService.getCollection(), onFav));
            cardContainer.add(Box.createVerticalStrut(2));
        }
    }

    // -------------------------------------------------------------------------
    // Favourites mode: all starred captures, sorted by rarity then newest first
    // -------------------------------------------------------------------------

    private void buildFavouritesView(List<CapturedCreature> all, String selectedSort) {
        List<CapturedCreature> favs = all.stream()
                .filter(c -> c.favourite)
                .collect(Collectors.toList());

        // Header
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setBackground(new Color(35, 35, 35));
        header.setBorder(BorderFactory.createCompoundBorder(
                new javax.swing.border.MatteBorder(0, 4, 0, 0, new Color(255, 195, 40)),
                new EmptyBorder(5, 8, 5, 8)));
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        JLabel headerLabel = new JLabel("★  " + favs.size() + " favourite" + (favs.size() == 1 ? "" : "s"));
        headerLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        headerLabel.setForeground(new Color(255, 195, 40));
        header.add(headerLabel, BorderLayout.WEST);
        cardContainer.add(header);
        cardContainer.add(Box.createVerticalStrut(4));

        if (favs.isEmpty()) {
            JLabel empty = new JLabel("<html><center>No favourites yet.<br>Right-click any capture to star it.</center></html>");
            empty.setForeground(new Color(100, 100, 100));
            empty.setFont(FontManager.getRunescapeSmallFont());
            empty.setAlignmentX(Component.CENTER_ALIGNMENT);
            empty.setBorder(new EmptyBorder(16, 0, 0, 0));
            cardContainer.add(empty);
            return;
        }

        switch (selectedSort == null ? "Rarity (best)" : selectedSort) {
            case "Name A-Z":    favs.sort(Comparator.comparing(c -> c.npcName)); break;
            case "Name Z-A":    favs.sort(Comparator.comparing((CapturedCreature c) -> c.npcName).reversed()); break;
            case "Newest first": favs.sort(Comparator.comparing((CapturedCreature c) -> c.captureTime).reversed()); break;
            case "Oldest first": favs.sort(Comparator.comparing(c -> c.captureTime)); break;
            case "Rarity (worst)": favs.sort(Comparator.comparingInt(c -> c.rarity.ordinal())); break;
            case "Quality (high)": favs.sort(Comparator.comparingInt((CapturedCreature c) -> c.quality.overallRating()).reversed()); break;
            case "Quality (low)":  favs.sort(Comparator.comparingInt(c -> c.quality.overallRating())); break;
            default: favs.sort((a, b) -> b.rarity.ordinal() - a.rarity.ordinal()); // Rarity (best)
        }

        Runnable onFav = () -> { dataService.saveNow(); rebuildCards(); };
        for (CapturedCreature c : favs) {
            cardContainer.add(new CaptureRow(c, dataService.getCollection(), onFav));
            cardContainer.add(Box.createVerticalStrut(2));
        }
    }

    // -------------------------------------------------------------------------
    // Monster mode: one card per unique NPC, shows total count + rarity split
    // -------------------------------------------------------------------------

    private void buildMonsterView(List<CapturedCreature> filtered, String selectedSort) {
        Map<String, List<CapturedCreature>> byNpc = filtered.stream()
                .collect(Collectors.groupingBy(c -> c.npcName));


        List<Map.Entry<String, List<CapturedCreature>>> entries =
                new ArrayList<>(byNpc.entrySet());

        switch (selectedSort == null ? "Name A-Z" : selectedSort) {
            case "Name A-Z":
                entries.sort(Comparator.comparing(Map.Entry::getKey));
                break;
            case "Name Z-A":
                entries.sort(Comparator.<Map.Entry<String, List<CapturedCreature>>, String>
                        comparing(Map.Entry::getKey).reversed());
                break;
            case "Newest first":
                entries.sort((a, b) -> latestCapture(b).compareTo(latestCapture(a)));
                break;
            case "Oldest first":
                entries.sort(Comparator.comparing(e -> earliestCapture(e.getValue())));
                break;
            case "Rarity (best)":
                entries.sort((a, b) -> maxRarity(b.getValue()).ordinal()
                                     - maxRarity(a.getValue()).ordinal());
                break;
            case "Rarity (worst)":
                entries.sort(Comparator.comparingInt(e -> maxRarity(e.getValue()).ordinal()));
                break;
            case "Quality (high)":
                entries.sort((a, b) -> avgQuality(b.getValue()) - avgQuality(a.getValue()));
                break;
            case "Quality (low)":
                entries.sort(Comparator.comparingInt(e -> avgQuality(e.getValue())));
                break;
            default:
                entries.sort(Comparator.comparing(Map.Entry::getKey));
        }

        for (Map.Entry<String, List<CapturedCreature>> e : entries) {
            cardContainer.add(new MonsterSummaryCard(
                    e.getKey(), e.getValue(), dataService.getCollection()));
            cardContainer.add(Box.createVerticalStrut(3));
        }
    }

    private static CreatureRarity maxRarity(List<CapturedCreature> captures) {
        return captures.stream()
                .map(c -> c.rarity)
                .max(Comparator.comparingInt(Enum::ordinal))
                .orElse(CreatureRarity.COMMON);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String[] buildRarityOptions() {
        CreatureRarity[] rarities = CreatureRarity.values();
        String[] options = new String[rarities.length + 1];
        options[0] = "All Rarities";
        for (int i = 0; i < rarities.length; i++) {
            options[i + 1] = rarities[i].label;
        }
        return options;
    }

    private static void styleToggleButton(JToggleButton btn, boolean active) {
        btn.setFont(FontManager.getRunescapeSmallFont());
        if (active) {
            btn.setBackground(new Color(255, 165, 0));
            btn.setForeground(Color.BLACK);
        } else {
            btn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            btn.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        }
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
    }

    private static java.time.Instant latestCapture(Map.Entry<String, List<CapturedCreature>> e) {
        return e.getValue().stream().map(c -> c.captureTime).max(Comparator.naturalOrder())
                .orElse(java.time.Instant.EPOCH);
    }

    private static java.time.Instant earliestCapture(List<CapturedCreature> captures) {
        return captures.stream().map(c -> c.captureTime).min(Comparator.naturalOrder())
                .orElse(java.time.Instant.EPOCH);
    }

    private static int avgQuality(List<CapturedCreature> captures) {
        return (int) captures.stream().mapToInt(c -> c.quality.overallRating()).average().orElse(0);
    }

    /** JPanel that tells its JScrollPane to constrain width to the viewport. */
    private static class ScrollablePanel extends JPanel implements javax.swing.Scrollable {
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(java.awt.Rectangle r, int o, int d) { return 16; }
        public int getScrollableBlockIncrement(java.awt.Rectangle r, int o, int d) { return 100; }
        public boolean getScrollableTracksViewportWidth()  { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
