package net.runelite.client.plugins.bestiary.ui;

import net.runelite.client.plugins.bestiary.BestiaryConfig;
import net.runelite.client.plugins.bestiary.model.BestiaryCollection;
import net.runelite.client.plugins.bestiary.model.CapturedCreature;
import net.runelite.client.plugins.bestiary.model.CreatureQuality;
import net.runelite.client.plugins.bestiary.model.CreatureRarity;
import net.runelite.client.plugins.bestiary.model.DetailSectionDefault;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MODELESS dialog showing capture history for one NPC (all rarities or a single rarity).
 * Only one instance can be visible at a time — opening a new one disposes the previous.
 */
public class CreatureDetailDialog extends JDialog {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm").withZone(ZoneId.systemDefault());

    private static final CreatureRarity[] RARITY_ORDER = {
        CreatureRarity.MYTHIC, CreatureRarity.LEGENDARY, CreatureRarity.EPIC,
        CreatureRarity.RARE,   CreatureRarity.UNCOMMON,  CreatureRarity.COMMON
    };

    private static final String[] SORT_OPTIONS = {
        "By Rarity", "Newest first", "Oldest first", "Quality (high)", "Quality (low)", "By region"
    };

    /** Ensures only one dialog is open at a time. */
    private static CreatureDetailDialog current;

    /** Set once by BestiaryPanel at startup; read for the config default on first open. */
    private static BestiaryConfig sharedConfig;

    /**
     * Session-wide collapse state — persists across dialog opens within the same RuneLite session.
     * Seeded from config on first open. When the config value changes, the session resets to
     * match the new default, then session-level toggles take over again.
     */
    private static final Set<CreatureRarity> sessionCollapsed = EnumSet.noneOf(CreatureRarity.class);
    private static boolean lastConfigCollapsed = false;

    public static void setConfig(BestiaryConfig config) {
        sharedConfig = config;
    }

    /** Called after nickname changes to persist the mutation to disk. */
    private static Runnable saveCallback;
    public static void setSaveCallback(Runnable cb) { saveCallback = cb; }

    private static final Color PB_GOLD = new Color(255, 195, 40, 220);

    private final List<CapturedCreature> captures;
    private final BestiaryCollection collection;
    private final JPanel listPanel;
    private final boolean multiRarity;
    private final int[] dialogBest;

    /** Per-dialog-instance view of the session collapse state (mutated on toggle). */
    private final Set<CreatureRarity> collapsedRarities = EnumSet.noneOf(CreatureRarity.class);

    private String currentSort;

    /**
     * @param defaultSort  Initial sort option ("By Rarity", "Newest first", etc.).
     * @param focusRarity  If non-null, this rarity section is guaranteed to be expanded on open
     *                     regardless of the session collapse state. Use when the caller was
     *                     reached via a specific rarity (e.g. clicking a rarity card or individual row).
     */
    public CreatureDetailDialog(Window owner, List<CapturedCreature> captures,
                                BestiaryCollection collection, String defaultSort,
                                CreatureRarity focusRarity) {
        super(owner, "Bestiary Detail", ModalityType.MODELESS);

        if (current != null && current.isShowing()) {
            current.dispose();
        }
        current = this;

        this.captures    = captures;
        this.collection  = collection;
        this.multiRarity = captures.stream().map(c -> c.rarity).distinct().count() > 1;
        this.dialogBest  = computeBests(captures);
        this.currentSort = defaultSort;

        // Sync session state from config. If the config value changed since last open, reset the
        // session to match the new default — then future manual toggles take over again.
        boolean configCollapsed = sharedConfig != null
                && sharedConfig.detailSectionDefault() == DetailSectionDefault.COLLAPSED;
        if (configCollapsed != lastConfigCollapsed) {
            lastConfigCollapsed = configCollapsed;
            sessionCollapsed.clear();
            if (configCollapsed) {
                sessionCollapsed.addAll(Arrays.asList(RARITY_ORDER));
            }
        }
        collapsedRarities.addAll(sessionCollapsed);

        // Always expand the focus rarity so the user can see what they clicked into
        if (focusRarity != null) {
            collapsedRarities.remove(focusRarity);
        }

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        CapturedCreature sample = captures.get(0);

        CreatureRarity accentRarity = multiRarity
                ? captures.stream().map(c -> c.rarity)
                        .max(Comparator.comparingInt(Enum::ordinal)).orElse(sample.rarity)
                : sample.rarity;

        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBackground(ColorScheme.DARK_GRAY_COLOR);
        root.setBorder(new EmptyBorder(12, 12, 12, 12));

        // Header
        JPanel header = new JPanel(new BorderLayout(0, 2));
        header.setOpaque(false);
        header.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 4, 0, 0, accentRarity.displayColor),
                new EmptyBorder(4, 8, 4, 0)));

        String titleText = multiRarity
                ? sample.npcName + " (" + captures.size() + " capture" + (captures.size() == 1 ? "" : "s") + ")"
                : sample.npcName + " — " + sample.rarity.label
                        + " (" + captures.size() + " capture" + (captures.size() == 1 ? "" : "s") + ")";
        JLabel titleLabel = new JLabel(titleText);
        titleLabel.setFont(FontManager.getRunescapeBoldFont());
        titleLabel.setForeground(Color.WHITE);

        String combatText = sample.npcCombatLevel > 0 ? "Combat level " + sample.npcCombatLevel : "Non-combat";
        String archetypeLabel = net.runelite.client.plugins.bestiary.model.MonsterRoster
                .getArchetype(sample.npcName, sample.npcCombatLevel).label;
        String subText = (multiRarity ? "All rarities  ·  " : "") + combatText + "  ·  " + archetypeLabel;
        JLabel subLabel = new JLabel(subText);
        subLabel.setFont(FontManager.getRunescapeSmallFont());
        subLabel.setForeground(accentRarity.displayColor);

        header.add(titleLabel, BorderLayout.NORTH);
        header.add(subLabel,   BorderLayout.CENTER);

        // Sort control — "By Rarity" always available regardless of single vs multi rarity
        JPanel sortRow = new JPanel(new BorderLayout(6, 0));
        sortRow.setOpaque(false);
        JLabel sortLabel = new JLabel("Sort:");
        sortLabel.setFont(FontManager.getRunescapeSmallFont());
        sortLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        JComboBox<String> sortBox = new JComboBox<>(SORT_OPTIONS);
        sortBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        sortBox.setForeground(Color.WHITE);
        sortBox.setFont(FontManager.getRunescapeSmallFont());
        sortBox.setSelectedItem(defaultSort);
        JLabel expandAll = new JLabel("Expand all");
        expandAll.setFont(FontManager.getRunescapeSmallFont());
        expandAll.setForeground(new Color(90, 140, 210));
        expandAll.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        expandAll.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (collapsedRarities.isEmpty()) {
                    collapsedRarities.addAll(Arrays.asList(RARITY_ORDER));
                    sessionCollapsed.addAll(Arrays.asList(RARITY_ORDER));
                    expandAll.setText("Expand all");
                } else {
                    collapsedRarities.clear();
                    sessionCollapsed.clear();
                    expandAll.setText("Collapse all");
                }
                sortBox.setSelectedItem("By Rarity");
                buildList("By Rarity");
            }
        });

        sortRow.add(sortLabel,  BorderLayout.WEST);
        sortRow.add(sortBox,    BorderLayout.CENTER);
        sortRow.add(expandAll,  BorderLayout.EAST);

        listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        buildList(currentSort);
        sortBox.addActionListener(e -> buildList((String) sortBox.getSelectedItem()));

        // Always reserve space for all 6 rarity headers (~28px each) + the capture rows
        int scrollH = Math.min(RARITY_ORDER.length * 28 + captures.size() * 100 + 16, 540);
        JScrollPane scroll = new JScrollPane(listPanel);
        scroll.setPreferredSize(new Dimension(400, scrollH));
        scroll.setBorder(null);
        scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);

        int kills    = collection.getKillCount(sample.npcName);
        int totalCap = collection.getCaptureCount(sample.npcName);
        String ratio = kills > 0 ? "1 in " + Math.round((double) kills / Math.max(1, totalCap)) : "—";
        JLabel statsLabel = new JLabel(String.format(
                "Total kills: %,d  |  All captures: %d  |  Kill ratio: %s", kills, totalCap, ratio));
        statsLabel.setFont(FontManager.getRunescapeSmallFont());
        statsLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        JPanel footerRow = new JPanel(new BorderLayout(8, 0));
        footerRow.setOpaque(false);
        footerRow.add(statsLabel, BorderLayout.WEST);

        if (captures.size() < totalCap) {
            JLabel showAll = new JLabel("Show all " + totalCap + " captures");
            showAll.setFont(FontManager.getRunescapeSmallFont());
            showAll.setForeground(new Color(90, 140, 210));
            showAll.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            showAll.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    List<CapturedCreature> all = collection.creatures.stream()
                            .filter(c -> c.npcName.equals(sample.npcName))
                            .collect(Collectors.toList());
                    if (!all.isEmpty()) {
                        new CreatureDetailDialog(owner, all, collection, "By Rarity", null);
                    }
                }
            });
            footerRow.add(showAll, BorderLayout.EAST);
        }

        JPanel centerBlock = new JPanel(new BorderLayout(0, 4));
        centerBlock.setOpaque(false);
        centerBlock.add(sortRow, BorderLayout.NORTH);
        centerBlock.add(scroll,  BorderLayout.CENTER);

        root.add(header,      BorderLayout.NORTH);
        root.add(centerBlock, BorderLayout.CENTER);
        root.add(footerRow,   BorderLayout.SOUTH);

        setContentPane(root);
        pack();
        setLocationRelativeTo(owner);
        setVisible(true);
        toFront();
    }

    private void buildList(String sortMode) {
        this.currentSort = sortMode;
        listPanel.removeAll();

        if ("By Rarity".equals(sortMode)) {
            int rowIndex = 1;
            for (CreatureRarity r : RARITY_ORDER) {
                List<CapturedCreature> group = captures.stream()
                        .filter(c -> c.rarity == r)
                        .sorted(Comparator.comparing((CapturedCreature c) -> c.captureTime).reversed())
                        .collect(Collectors.toList());

                boolean collapsed = collapsedRarities.contains(r);
                listPanel.add(buildRarityHeader(r, group.size(), collapsed));
                listPanel.add(Box.createVerticalStrut(2));

                if (!collapsed) {
                    for (CapturedCreature c : group) {
                        listPanel.add(buildCaptureRow(c, rowIndex++));
                        listPanel.add(Box.createVerticalStrut(4));
                    }
                    if (!group.isEmpty()) {
                        listPanel.add(Box.createVerticalStrut(4));
                    }
                }
            }
        } else {
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
            for (int i = 0; i < sorted.size(); i++) {
                listPanel.add(buildCaptureRow(sorted.get(i), i + 1));
                if (i < sorted.size() - 1) listPanel.add(Box.createVerticalStrut(4));
            }
        }

        listPanel.revalidate();
        listPanel.repaint();
    }

    private JPanel buildRarityHeader(CreatureRarity rarity, int count, boolean collapsed) {
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setBackground(new Color(35, 35, 35));
        header.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 3, 0, 0, rarity.displayColor),
                new EmptyBorder(4, 8, 4, 8)));
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        String chevron = collapsed ? "▶" : "▼";
        JLabel label = new JLabel(chevron + "  " + rarity.label.toUpperCase());
        label.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        label.setForeground(rarity.displayColor);

        JLabel cnt = new JLabel(count + " capture" + (count == 1 ? "" : "s"), SwingConstants.RIGHT);
        cnt.setFont(FontManager.getRunescapeSmallFont());
        cnt.setForeground(count == 0 ? new Color(75, 75, 75) : ColorScheme.LIGHT_GRAY_COLOR);

        header.add(label, BorderLayout.WEST);
        header.add(cnt,   BorderLayout.EAST);

        header.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (collapsedRarities.contains(rarity)) {
                    collapsedRarities.remove(rarity);
                    sessionCollapsed.remove(rarity);
                } else {
                    collapsedRarities.add(rarity);
                    sessionCollapsed.add(rarity);
                }
                buildList(currentSort);
            }
            @Override public void mouseEntered(MouseEvent e) { header.setBackground(new Color(48, 48, 48)); }
            @Override public void mouseExited(MouseEvent e)  { header.setBackground(new Color(35, 35, 35)); }
        });

        return header;
    }

    private JPanel buildCaptureRow(CapturedCreature c, int index) {
        JPanel row = new JPanel(new BorderLayout(8, 3));
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setBorder(new EmptyBorder(7, 10, 7, 10));

        JPanel topLine = new JPanel(new BorderLayout());
        topLine.setOpaque(false);

        int quality = c.quality.overallRating();
        Color qualColor = quality >= 80 ? new Color(80, 220, 80)
                        : quality >= 50 ? new Color(220, 220, 80)
                        : new Color(160, 160, 160);

        String star = c.favourite ? "★  " : "";
        String qualText = c.nickname != null && !c.nickname.isEmpty()
                ? star + "#" + index + "  \"" + c.nickname + "\"  Q:" + quality
                : star + "#" + index + "  Quality: " + quality + " / 100";
        JLabel qualLabel = new JLabel(qualText);
        qualLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        qualLabel.setForeground(qualColor);

        row.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) showRowMenu(e);
            }
            private void showRowMenu(MouseEvent e) {
                JPopupMenu menu = new JPopupMenu();

                JMenuItem favItem = new JMenuItem(c.favourite ? "✩ Remove Favourite" : "★ Favourite");
                favItem.addActionListener(ev -> {
                    c.favourite = !c.favourite;
                    buildList(currentSort);
                    if (saveCallback != null) saveCallback.run();
                });
                menu.add(favItem);

                JMenuItem exportItem = new JMenuItem("Export Card");
                exportItem.addActionListener(ev ->
                        CardExportDialog.open(CreatureDetailDialog.this, c));
                menu.add(exportItem);

                String nameLabel = (c.nickname != null && !c.nickname.isEmpty()) ? "Rename…" : "Name capture…";
                JMenuItem nameItem = new JMenuItem(nameLabel);
                nameItem.addActionListener(ev -> {
                    JTextField field = new JTextField(c.nickname != null ? c.nickname : "", 20);
                    JLabel counter   = new JLabel(field.getText().length() + " / 20");
                    counter.setFont(FontManager.getRunescapeSmallFont());
                    counter.setForeground(Color.GRAY);
                    ((AbstractDocument) field.getDocument()).setDocumentFilter(new DocumentFilter() {
                        private void update(FilterBypass fb) {
                            counter.setText(fb.getDocument().getLength() + " / 20");
                        }
                        @Override
                        public void insertString(FilterBypass fb, int off, String s, AttributeSet a)
                                throws BadLocationException {
                            if (fb.getDocument().getLength() + (s == null ? 0 : s.length()) <= 20)
                            { super.insertString(fb, off, s, a); update(fb); }
                        }
                        @Override
                        public void replace(FilterBypass fb, int off, int len, String s, AttributeSet a)
                                throws BadLocationException {
                            if (fb.getDocument().getLength() - len + (s == null ? 0 : s.length()) <= 20)
                            { super.replace(fb, off, len, s, a); update(fb); }
                        }
                        @Override
                        public void remove(FilterBypass fb, int off, int len)
                                throws BadLocationException {
                            super.remove(fb, off, len); update(fb);
                        }
                    });
                    JPanel inputPanel = new JPanel(new BorderLayout(6, 4));
                    inputPanel.add(new JLabel("Name (blank to clear):"), BorderLayout.NORTH);
                    inputPanel.add(field,   BorderLayout.CENTER);
                    inputPanel.add(counter, BorderLayout.EAST);

                    JButton okBtn     = new JButton("OK");
                    JButton cancelBtn = new JButton("Cancel");
                    JPanel btnRow = new JPanel(new GridLayout(1, 2, 4, 0));
                    btnRow.add(okBtn);
                    btnRow.add(cancelBtn);

                    JPanel content = new JPanel(new BorderLayout(0, 8));
                    content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
                    content.add(inputPanel, BorderLayout.CENTER);
                    content.add(btnRow,     BorderLayout.SOUTH);

                    JDialog nickDlg = new JDialog(CreatureDetailDialog.this, "Name Capture", ModalityType.MODELESS);
                    nickDlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
                    nickDlg.setResizable(false);
                    nickDlg.setContentPane(content);
                    nickDlg.pack();
                    nickDlg.setLocationRelativeTo(CreatureDetailDialog.this);

                    okBtn.addActionListener(ae -> {
                        String val = field.getText().trim();
                        c.nickname = val.isBlank() ? null : val;
                        buildList(currentSort);
                        if (saveCallback != null) saveCallback.run();
                        nickDlg.dispose();
                    });
                    cancelBtn.addActionListener(ae -> nickDlg.dispose());
                    field.addActionListener(ae -> okBtn.doClick());

                    nickDlg.setVisible(true);
                });
                menu.add(nameItem);
                menu.show(row, e.getX(), e.getY());
            }
        });

        // Right column: player name (gold, when known) stacked above date
        JPanel rightCol = new JPanel();
        rightCol.setLayout(new BoxLayout(rightCol, BoxLayout.Y_AXIS));
        rightCol.setOpaque(false);
        if (c.playerName != null && !c.playerName.isEmpty()) {
            JLabel capturedByLabel = new JLabel(c.playerName);
            capturedByLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
            capturedByLabel.setForeground(new Color(200, 155, 50));
            capturedByLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
            rightCol.add(capturedByLabel);
        }
        JLabel dateLabel = new JLabel(DATE_FMT.format(c.captureTime));
        dateLabel.setFont(FontManager.getRunescapeSmallFont());
        dateLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        dateLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
        rightCol.add(dateLabel);

        topLine.add(qualLabel, BorderLayout.WEST);
        topLine.add(rightCol,  BorderLayout.EAST);

        JPanel botLine = new JPanel(new BorderLayout());
        botLine.setOpaque(false);

        JLabel locLabel = new JLabel(c.regionName);
        locLabel.setFont(FontManager.getRunescapeSmallFont());
        locLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        JLabel killLabel = new JLabel("Kill #" + c.killsBeforeCapture, SwingConstants.RIGHT);
        killLabel.setFont(FontManager.getRunescapeSmallFont());
        killLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        botLine.add(locLabel,  BorderLayout.WEST);
        botLine.add(killLabel, BorderLayout.EAST);

        JPanel content = new JPanel(new GridLayout(3, 1, 0, 3));
        content.setOpaque(false);
        content.add(topLine);
        content.add(botLine);
        content.add(buildStatBars(c.quality, c.rarity.displayColor, dialogBest));

        row.add(content, BorderLayout.CENTER);
        // Prevent BoxLayout / viewport from stretching the row beyond its natural height.
        // GridLayout distributes ALL available height equally, so without this cap the stat
        // bars balloon when only 1–3 rows are visible.
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    private static JPanel buildStatBars(CreatureQuality q, Color accent, int[] globalBest) {
        JPanel p = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                int[] vals = {q.strength, q.speed, q.endurance,
                              q.intelligence, q.stealth, q.vitality};
                String[] labs = {"STR", "SPD", "END", "INT", "STL", "VIT"};
                int n      = vals.length;
                int w      = getWidth();
                int h      = getHeight();
                int gap    = 4;
                int labelH = 14;
                int barH   = h - labelH - 2;
                int slotW  = (w - gap * (n - 1)) / n;

                Font numFont   = FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD, 13f);
                Font labelFont = FontManager.getRunescapeSmallFont();

                for (int i = 0; i < n; i++) {
                    int x    = i * (slotW + gap);
                    int fill = Math.round(slotW * vals[i] / 100f);

                    g2.setColor(new Color(30, 30, 30));
                    g2.fillRoundRect(x, 0, slotW, barH, 4, 4);

                    if (fill > 0) {
                        g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 200));
                        g2.fillRoundRect(x, 0, fill, barH, 4, 4);
                    }

                    boolean isPB = globalBest != null && vals[i] >= globalBest[i];
                    if (isPB) {
                        Stroke prev = g2.getStroke();
                        g2.setStroke(new BasicStroke(2.5f));
                        g2.setColor(PB_GOLD);
                        g2.drawRoundRect(x, 0, slotW - 1, barH - 1, 4, 4);
                        g2.setStroke(prev);
                    }

                    String numStr = String.valueOf(vals[i]);
                    g2.setFont(numFont);
                    FontMetrics nfm = g2.getFontMetrics();
                    int nx = x + (slotW - nfm.stringWidth(numStr)) / 2;
                    int ny = (barH + nfm.getAscent() - nfm.getDescent()) / 2;
                    g2.setColor(new Color(0, 0, 0, 180));
                    g2.drawString(numStr, nx + 1, ny + 1);
                    g2.setColor(Color.WHITE);
                    g2.drawString(numStr, nx, ny);

                    g2.setFont(labelFont);
                    FontMetrics lfm = g2.getFontMetrics();
                    g2.setColor(ColorScheme.LIGHT_GRAY_COLOR);
                    int lx = x + (slotW - lfm.stringWidth(labs[i])) / 2;
                    g2.drawString(labs[i], lx, h - 2);
                }
                g2.dispose();
            }
        };
        p.setOpaque(false);
        p.setPreferredSize(new Dimension(0, 36));
        p.setMinimumSize(new Dimension(0, 36));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        return p;
    }

    private static int[] computeBests(List<CapturedCreature> list) {
        int[] best = new int[6];
        for (CapturedCreature c : list) {
            CreatureQuality q = c.quality;
            int[] v = {q.strength, q.speed, q.endurance, q.intelligence, q.stealth, q.vitality};
            for (int i = 0; i < 6; i++) best[i] = Math.max(best[i], v[i]);
        }
        return best;
    }
}
