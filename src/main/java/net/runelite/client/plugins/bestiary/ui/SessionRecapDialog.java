package net.runelite.client.plugins.bestiary.ui;

import net.runelite.client.plugins.bestiary.model.CapturedCreature;
import net.runelite.client.plugins.bestiary.model.CreatureRarity;
import net.runelite.client.plugins.bestiary.service.SessionTracker;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MODELESS dialog showing all captures logged since the last login.
 * Only one instance can be open at a time.
 */
public class SessionRecapDialog extends JDialog {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    private static final CreatureRarity[] RARITY_ORDER = {
        CreatureRarity.MYTHIC, CreatureRarity.LEGENDARY, CreatureRarity.EPIC,
        CreatureRarity.RARE,   CreatureRarity.UNCOMMON,  CreatureRarity.COMMON
    };

    private static SessionRecapDialog current;

    public static void open(Window owner, SessionTracker tracker) {
        if (current != null && current.isShowing()) {
            current.dispose();
        }
        current = new SessionRecapDialog(owner, tracker);
    }

    private SessionRecapDialog(Window owner, SessionTracker tracker) {
        super(owner, "Session Recap", ModalityType.MODELESS);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(true);
        build(tracker);
    }

    private void build(SessionTracker tracker) {
        List<CapturedCreature> captures = new ArrayList<>(tracker.getCaptures());

        long speciesCount = captures.stream().map(c -> c.npcName).distinct().count();
        Map<CreatureRarity, Long> rarityCounts = captures.stream()
                .collect(Collectors.groupingBy(c -> c.rarity, Collectors.counting()));
        CapturedCreature best = captures.stream()
                .max(Comparator.comparingInt((CapturedCreature c) -> c.rarity.ordinal())
                        .thenComparingInt(c -> c.quality.overallRating()))
                .orElse(null);

        // --- Header ---
        JPanel header = new JPanel(new BorderLayout(0, 3));
        header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        header.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 4, 0, 0, new Color(255, 165, 0)),
                new EmptyBorder(10, 12, 10, 12)));

        JLabel titleLabel = new JLabel("Session Recap");
        titleLabel.setFont(FontManager.getRunescapeBoldFont().deriveFont(16f));
        titleLabel.setForeground(new Color(255, 165, 0));

        String subText = captures.isEmpty()
                ? "No captures this session yet"
                : captures.size() + " capture" + (captures.size() == 1 ? "" : "s")
                  + "  ·  " + speciesCount + " species"
                  + (best != null ? "  ·  Best: " + best.npcName + " " + best.rarity.label : "");
        JLabel subLabel = new JLabel(subText);
        subLabel.setFont(FontManager.getRunescapeSmallFont());
        subLabel.setForeground(best != null ? best.rarity.displayColor : ColorScheme.LIGHT_GRAY_COLOR);

        long xpGained  = tracker.getXpGained();
        int  killCount = tracker.getKillCount();
        String statsLine = killCount + " kill" + (killCount == 1 ? "" : "s")
                + "  ·  " + java.text.NumberFormat.getNumberInstance(java.util.Locale.UK).format(xpGained) + " XP";
        JLabel statsLabel = new JLabel(statsLine);
        statsLabel.setFont(FontManager.getRunescapeSmallFont());
        statsLabel.setForeground(new Color(130, 180, 130));

        JPanel subPanel = new JPanel(new GridLayout(2, 1, 0, 1));
        subPanel.setOpaque(false);
        subPanel.add(subLabel);
        subPanel.add(statsLabel);

        header.add(titleLabel, BorderLayout.NORTH);
        header.add(subPanel,   BorderLayout.CENTER);

        // --- Rarity pills ---
        JPanel pillRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        pillRow.setBackground(new Color(28, 28, 28));
        pillRow.setBorder(new EmptyBorder(4, 8, 6, 8));

        if (captures.isEmpty()) {
            JLabel empty = new JLabel("Go capture something to see your stats here.");
            empty.setFont(FontManager.getRunescapeSmallFont());
            empty.setForeground(new Color(70, 70, 70));
            pillRow.add(empty);
        } else {
            for (CreatureRarity r : RARITY_ORDER) {
                long n = rarityCounts.getOrDefault(r, 0L);
                if (n == 0) continue;
                pillRow.add(new RarityPill(r, (int) n));
            }
        }

        // --- Capture list ---
        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        if (captures.isEmpty()) {
            JLabel noCaptures = new JLabel("  No captures recorded this session.");
            noCaptures.setFont(FontManager.getRunescapeSmallFont());
            noCaptures.setForeground(new Color(70, 70, 70));
            noCaptures.setBorder(new EmptyBorder(16, 0, 16, 0));
            noCaptures.setAlignmentX(Component.LEFT_ALIGNMENT);
            listPanel.add(noCaptures);
        } else {
            for (int i = 0; i < captures.size(); i++) {
                listPanel.add(buildCaptureRow(captures.get(i), i + 1, i % 2 == 1));
            }
        }

        int listH = Math.min(captures.size() * 34 + 8, 400);
        JScrollPane scroll = new JScrollPane(listPanel);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setPreferredSize(new Dimension(500, Math.max(60, listH)));

        // --- Footer ---
        JButton copyBtn = new JButton("Copy Summary");
        copyBtn.setFont(FontManager.getRunescapeSmallFont());
        copyBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        copyBtn.setForeground(Color.WHITE);
        copyBtn.setFocusPainted(false);
        copyBtn.setEnabled(!captures.isEmpty());
        copyBtn.addActionListener(e -> {
            copyTextSummary(captures, speciesCount, rarityCounts);
            copyBtn.setText("Copied!");
            copyBtn.setForeground(new Color(120, 200, 120));
            javax.swing.Timer t = new javax.swing.Timer(2000, ev -> {
                copyBtn.setText("Copy Summary");
                copyBtn.setForeground(Color.WHITE);
            });
            t.setRepeats(false);
            t.start();
        });

        JButton clearBtn = new JButton("Clear Session");
        clearBtn.setFont(FontManager.getRunescapeSmallFont());
        clearBtn.setBackground(new Color(80, 20, 20));
        clearBtn.setForeground(new Color(220, 100, 100));
        clearBtn.setFocusPainted(false);
        clearBtn.setEnabled(!captures.isEmpty());
        clearBtn.addActionListener(e -> {
            tracker.clear();
            dispose();
        });

        JPanel footer = new JPanel(new GridLayout(1, 2, 6, 0));
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(8, 8, 8, 8));
        footer.add(copyBtn);
        footer.add(clearBtn);

        // --- Root ---
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel north = new JPanel(new BorderLayout());
        north.setOpaque(false);
        north.add(header,  BorderLayout.NORTH);
        north.add(pillRow, BorderLayout.CENTER);

        root.add(north,  BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);

        setContentPane(root);
        pack();
        setLocationRelativeTo(getOwner());
        setVisible(true);
        toFront();
    }

    private static JPanel buildCaptureRow(CapturedCreature c, int index, boolean altRow) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setBackground(altRow ? new Color(30, 30, 30) : ColorScheme.DARKER_GRAY_COLOR);
        row.setBorder(new EmptyBorder(5, 10, 5, 10));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

        // Left: index + NPC name (rarity color)
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        left.setOpaque(false);

        JLabel indexLabel = new JLabel(String.format("#%-3d", index));
        indexLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        indexLabel.setForeground(new Color(80, 80, 80));

        JLabel nameLabel = new JLabel(c.npcName);
        nameLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        nameLabel.setForeground(c.rarity.displayColor);

        JLabel rarLabel = new JLabel("— " + c.rarity.label);
        rarLabel.setFont(FontManager.getRunescapeSmallFont());
        rarLabel.setForeground(new Color(
                c.rarity.displayColor.getRed(),
                c.rarity.displayColor.getGreen(),
                c.rarity.displayColor.getBlue(), 160));

        left.add(indexLabel);
        left.add(nameLabel);
        left.add(rarLabel);

        // Right: quality + region + time
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);

        int q = c.quality.overallRating();
        Color qCol = q >= 80 ? new Color(80, 220, 80) : q >= 50 ? new Color(220, 220, 80) : new Color(160, 160, 160);
        JLabel qLabel = new JLabel("Q:" + q);
        qLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        qLabel.setForeground(qCol);

        JLabel regionLabel = new JLabel(c.regionName);
        regionLabel.setFont(FontManager.getRunescapeSmallFont());
        regionLabel.setForeground(new Color(110, 110, 110));

        JLabel timeLabel = new JLabel(TIME_FMT.format(c.captureTime));
        timeLabel.setFont(FontManager.getRunescapeSmallFont());
        timeLabel.setForeground(new Color(80, 80, 80));
        timeLabel.setPreferredSize(new Dimension(34, 16));

        right.add(qLabel);
        right.add(regionLabel);
        right.add(timeLabel);

        row.add(left,  BorderLayout.WEST);
        row.add(right, BorderLayout.EAST);
        return row;
    }

    private void copyTextSummary(List<CapturedCreature> captures, long species,
                                 Map<CreatureRarity, Long> counts) {
        // Build the body first so we can measure the longest NPC name for column alignment
        int maxNameLen = captures.stream().mapToInt(c -> c.npcName.length()).max().orElse(10);
        int nameCol = Math.max(maxNameLen + 2, 14);

        StringBuilder body = new StringBuilder();
        body.append("=== Bestiary Session Recap ===\n");
        body.append(captures.size()).append(captures.size() == 1 ? " capture" : " captures")
            .append(", ").append(species).append(" species\n");

        // Rarity breakdown on one line
        StringBuilder rarLine = new StringBuilder();
        for (CreatureRarity r : RARITY_ORDER) {
            long n = counts.getOrDefault(r, 0L);
            if (n > 0) {
                if (rarLine.length() > 0) rarLine.append("  ");
                rarLine.append(r.label).append(" x").append(n);
            }
        }
        body.append(rarLine).append("\n\n");

        String rowFmt = "#%-3d  %-" + nameCol + "s %-10s Q:%-3d  %s\n";
        for (int i = 0; i < captures.size(); i++) {
            CapturedCreature c = captures.get(i);
            body.append(String.format(rowFmt,
                    i + 1, c.npcName, c.rarity.label, c.quality.overallRating(), c.regionName));
        }

        // Wrap in triple backticks so Discord/Slack render it as a monospaced code block
        String full = "```\n" + body + "```";
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(full), null);
    }

    // -------------------------------------------------------------------------
    // Rarity pill — rounded badge with rarity colour border + fill
    // -------------------------------------------------------------------------

    private static class RarityPill extends JComponent {
        private final CreatureRarity rarity;
        private final String text;

        RarityPill(CreatureRarity r, int count) {
            this.rarity = r;
            this.text   = r.label + " x" + count;
            setOpaque(false);
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
            int w = (fm != null ? fm.stringWidth(text) : 70) + 20;
            return new Dimension(w, 22);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();
            Color c = rarity.displayColor;
            g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 45));
            g2.fillRoundRect(0, 0, w, h, 8, 8);
            g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 180));
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);

            g2.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(rarity.displayColor);
            g2.drawString(text,
                    (w - fm.stringWidth(text)) / 2,
                    (h + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }
    }
}
