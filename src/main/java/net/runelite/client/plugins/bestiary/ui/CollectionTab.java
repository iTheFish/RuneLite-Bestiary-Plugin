package net.runelite.client.plugins.bestiary.ui;

import net.runelite.client.plugins.bestiary.model.CapturedCreature;
import net.runelite.client.plugins.bestiary.model.CreatureRarity;
import net.runelite.client.plugins.bestiary.service.BestiaryDataService;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.IconTextField;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Collection tab: searchable, filterable list of captured species grouped by NPC + rarity.
 */
public class CollectionTab extends JPanel {

    private final BestiaryDataService dataService;

    private final IconTextField searchBar;
    private final JComboBox<String> rarityFilter;
    private final JComboBox<String> sortOrder;
    private final JPanel cardContainer;

    public CollectionTab(BestiaryDataService dataService) {
        this.dataService = dataService;
        setLayout(new BorderLayout(0, 6));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(new EmptyBorder(8, 8, 8, 8));

        // Search + filters
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
        rarityFilter.addActionListener(e -> rebuildCards());

        String[] sortOptions = { "Name A-Z", "Name Z-A", "Newest first", "Oldest first",
                                 "Rarity (best)", "Rarity (worst)" };
        sortOrder = new JComboBox<>(sortOptions);
        sortOrder.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        sortOrder.setForeground(Color.WHITE);
        sortOrder.addActionListener(e -> rebuildCards());

        JPanel filterRow = new JPanel(new GridLayout(1, 2, 4, 0));
        filterRow.setOpaque(false);
        filterRow.add(rarityFilter);
        filterRow.add(sortOrder);

        controls.add(searchBar, BorderLayout.NORTH);
        controls.add(filterRow, BorderLayout.CENTER);

        // Card container
        cardContainer = new JPanel();
        cardContainer.setLayout(new BoxLayout(cardContainer, BoxLayout.Y_AXIS));
        cardContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JScrollPane scroll = new JScrollPane(cardContainer);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

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

        // Group by npcId + rarity so each card is one species/rarity combination
        Map<String, List<CapturedCreature>> byNpcRarity = dataService.getCollection().creatures.stream()
                .collect(Collectors.groupingBy(c -> c.npcId + ":" + c.rarity.ordinal()));

        List<Map.Entry<String, List<CapturedCreature>>> entries = new ArrayList<>(byNpcRarity.entrySet());

        // Filter by search query (match on NPC name)
        if (!query.isEmpty()) {
            entries.removeIf(e -> !e.getValue().get(0).npcName.toLowerCase().contains(query));
        }

        // Filter by rarity (all captures in a group share the same rarity)
        if (selectedRarity != null && !"All Rarities".equals(selectedRarity)) {
            CreatureRarity filterRarity = CreatureRarity.fromLabel(selectedRarity);
            entries.removeIf(e -> e.getValue().get(0).rarity != filterRarity);
        }

        // Sort
        if (selectedSort != null) {
            switch (selectedSort) {
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
            }
        }

        if (entries.isEmpty()) {
            JLabel empty = new JLabel("No creatures match.");
            empty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            empty.setFont(FontManager.getRunescapeSmallFont());
            empty.setAlignmentX(Component.CENTER_ALIGNMENT);
            empty.setBorder(new EmptyBorder(20, 0, 0, 0));
            cardContainer.add(empty);
        } else {
            for (Map.Entry<String, List<CapturedCreature>> entry : entries) {
                cardContainer.add(new CreatureCard(entry.getValue(), dataService.getCollection()));
                cardContainer.add(Box.createVerticalStrut(2));
            }
        }

        cardContainer.revalidate();
        cardContainer.repaint();
        revalidate();
        repaint();
    }

    private static String[] buildRarityOptions() {
        CreatureRarity[] rarities = CreatureRarity.values();
        String[] options = new String[rarities.length + 1];
        options[0] = "All Rarities";
        for (int i = 0; i < rarities.length; i++) {
            options[i + 1] = rarities[i].label;
        }
        return options;
    }

    private static java.time.Instant latestCapture(Map.Entry<String, List<CapturedCreature>> e) {
        return e.getValue().stream().map(c -> c.captureTime).max(Comparator.naturalOrder())
                .orElse(java.time.Instant.EPOCH);
    }

    private static java.time.Instant earliestCapture(List<CapturedCreature> captures) {
        return captures.stream().map(c -> c.captureTime).min(Comparator.naturalOrder())
                .orElse(java.time.Instant.EPOCH);
    }
}
