package net.runelite.client.plugins.bestiary.ui;

import net.runelite.client.plugins.bestiary.model.CapturedCreature;
import net.runelite.client.plugins.bestiary.model.CreatureRarity;
import net.runelite.client.plugins.bestiary.service.BestiaryDataService;
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

    private final IconTextField searchBar;
    private final JComboBox<String> rarityFilter;
    private final JComboBox<String> sortOrder;
    private final JPanel cardContainer;

    private enum ViewMode { GROUPED, INDIVIDUAL }
    private ViewMode viewMode = ViewMode.GROUPED;
    private boolean  byMonster = false;

    // Sort options available in both modes
    private static final String[] SORT_OPTIONS = {
        "Name A-Z", "Name Z-A",
        "Newest first", "Oldest first",
        "Rarity (best)", "Rarity (worst)",
        "Quality (high)", "Quality (low)"
    };

    public CollectionTab(BestiaryDataService dataService) {
        this.dataService = dataService;
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

        // --- Main toggle: Grouped / Individual ---
        JPanel toggleRow = new JPanel(new GridLayout(1, 2, 0, 0));
        toggleRow.setOpaque(false);

        JToggleButton groupedBtn    = new JToggleButton("Grouped");
        JToggleButton individualBtn = new JToggleButton("Individual");

        styleToggleButton(groupedBtn,    true);
        styleToggleButton(individualBtn, false);

        ButtonGroup btnGroup = new ButtonGroup();
        btnGroup.add(groupedBtn);
        btnGroup.add(individualBtn);
        groupedBtn.setSelected(true);

        toggleRow.add(groupedBtn);
        toggleRow.add(individualBtn);

        // --- Sub-toggle (Grouped only): By Rarity / By Monster ---
        JPanel subToggleRow = new JPanel(new GridLayout(1, 2, 0, 0));
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

        // Action listeners
        groupedBtn.addActionListener(e -> {
            viewMode = ViewMode.GROUPED;
            styleToggleButton(groupedBtn,    true);
            styleToggleButton(individualBtn, false);
            subToggleRow.setVisible(true);
            rebuildCards();
        });
        individualBtn.addActionListener(e -> {
            viewMode = ViewMode.INDIVIDUAL;
            styleToggleButton(groupedBtn,    false);
            styleToggleButton(individualBtn, true);
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

        controls.add(searchBar,    BorderLayout.NORTH);
        controls.add(filterRow,    BorderLayout.CENTER);
        controls.add(togglesPanel, BorderLayout.SOUTH);

        // --- Card container ---
        cardContainer = new JPanel();
        cardContainer.setLayout(new BoxLayout(cardContainer, BoxLayout.Y_AXIS));
        cardContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JScrollPane scroll = new JScrollPane(cardContainer);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getVerticalScrollBar().setPreferredSize(new java.awt.Dimension(0, 0));

        add(controls, BorderLayout.NORTH);
        add(scroll,   BorderLayout.CENTER);

        rebuildCards();
    }

    public void refresh() {
        rebuildCards();
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

        if (filtered.isEmpty()) {
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

        for (CapturedCreature capture : sorted) {
            cardContainer.add(new CaptureRow(capture, dataService.getCollection()));
            cardContainer.add(Box.createVerticalStrut(2));
        }
    }

    // -------------------------------------------------------------------------
    // Monster mode: one card per unique NPC, shows total count + rarity split
    // -------------------------------------------------------------------------

    private void buildMonsterView(List<CapturedCreature> filtered, String selectedSort) {
        Map<String, List<CapturedCreature>> byNpc = filtered.stream()
                .collect(Collectors.groupingBy(c -> c.npcName));

        // Album view button — fixed at top of monster list
        JButton albumBtn = new JButton("Open Bestiary Album");
        albumBtn.setFont(FontManager.getRunescapeBoldFont());
        albumBtn.setBackground(new Color(55, 55, 55));
        albumBtn.setForeground(new Color(255, 165, 0));
        albumBtn.setFocusPainted(false);
        albumBtn.setBorderPainted(false);
        albumBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        albumBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        albumBtn.setPreferredSize(new Dimension(180, 40));
        albumBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        albumBtn.addActionListener(e -> new AlbumDialog(
                SwingUtilities.getWindowAncestor(CollectionTab.this), byNpc, dataService.getCollection()));
        cardContainer.add(Box.createVerticalStrut(4));
        cardContainer.add(albumBtn);
        cardContainer.add(Box.createVerticalStrut(8));

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
}
