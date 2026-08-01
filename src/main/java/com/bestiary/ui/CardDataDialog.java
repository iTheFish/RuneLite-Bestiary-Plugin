package com.bestiary.ui;

import com.bestiary.model.CapturedCreature;
import com.bestiary.model.MonsterRoster;
import com.bestiary.util.CardId;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * MODELESS per-card "Data" panel (right-click → Card Info…). Consolidates everything known about a
 * single capture into tabs: Overview (metadata), Odds (the "What were the odds?" breakdown via
 * {@link OddsView}), and Reroll history (the card's past states). Room for more tabs later
 * (provenance, capture context, …).
 */
public class CardDataDialog extends JDialog {

    private static CardDataDialog current;
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DATE_SHORT =
            DateTimeFormatter.ofPattern("d/M/yy").withZone(ZoneId.systemDefault());

    public static final int TAB_EXPORT = 0, TAB_OVERVIEW = 1, TAB_ODDS = 2, TAB_GRAPH = 3, TAB_REROLLS = 4;

    /** Supplies the played account's current Hunter's Bounty flat capture-credit bonus (0 = none). */
    private static java.util.function.LongSupplier captureCreditBonus = () -> 0L;
    public static void setCaptureCreditBonus(java.util.function.LongSupplier s) {
        if (s != null) captureCreditBonus = s;
    }

    /** Current Scholar's Insight capture-XP bonus fraction (e.g. 0.25 = +25%; 0 = none). */
    private static java.util.function.DoubleSupplier captureXpBonus = () -> 0.0;
    public static void setCaptureXpBonus(java.util.function.DoubleSupplier s) {
        if (s != null) captureXpBonus = s;
    }

    /** Current Hunter's Focus flat kill-XP bonus (0 = none). */
    private static java.util.function.LongSupplier killXpFlatBonus = () -> 0L;
    public static void setKillXpFlatBonus(java.util.function.LongSupplier s) {
        if (s != null) killXpFlatBonus = s;
    }

    /** Colour for shop-upgrade bonus amounts shown in brackets. */
    private static final String BONUS_HEX = "#78d278";

    public static void open(Window owner, CapturedCreature capture) {
        open(owner, capture, TAB_EXPORT);
    }

    /** Opens the panel on a specific tab (see the TAB_* constants). */
    public static void open(Window owner, CapturedCreature capture, int tab) {
        if (current != null && current.isShowing()) current.dispose();
        current = new CardDataDialog(owner, capture);
        if (tab >= 0 && tab < current.tabs.getTabCount()) current.tabs.setSelectedIndex(tab);
        current.setVisible(true);
    }

    /** Close any open Card data dialog (called when the collection is reset / Album opens). */
    public static void disposeOpen() {
        if (current != null && current.isDisplayable()) current.dispose();
        current = null;
    }

    private final Font body;
    private final Font bodyBold;
    private final JTabbedPane tabs;
    private final JComponent bottomBar;

    private CardDataDialog(Window owner, CapturedCreature capture) {
        super(owner, "Card data — " + capture.npcName, ModalityType.MODELESS);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        float base = FontManager.getRunescapeSmallFont().getSize2D();
        this.body     = FontManager.getRunescapeSmallFont().deriveFont(base + 1f);
        this.bodyBold = body.deriveFont(Font.BOLD);

        tabs = new JTabbedPane();
        tabs.setFont(FontManager.getRunescapeSmallFont());
        tabs.setBackground(ColorScheme.DARK_GRAY_COLOR);
        tabs.setForeground(Color.WHITE);
        tabs.addTab("Export", buildExportTab(capture));   // primary: card preview + Copy/Save
        tabs.addTab("Overview", scroll(buildOverview(capture)));
        tabs.addTab("Odds", scroll(new OddsView(capture)));
        tabs.addTab("Graph", new RerollGraph(capture));   // percentile + (if rerolled) stat timeline
        tabs.addTab("Rerolls", scroll(buildRerollHistory(capture)));
        bottomBar = buildBottomBar();
        bottomBar.setVisible(false);   // Export is the first/selected tab

        // A tab's scroll pane can open part-scrolled (layout quirk) — force it back to the top.
        // The generic Copy/Save-tab bar only applies to the data tabs; the Export tab has its own.
        tabs.addChangeListener(e -> {
            bottomBar.setVisible(tabs.getSelectedIndex() != TAB_EXPORT);
            SwingUtilities.invokeLater(() -> {
                Component tc = tabs.getSelectedComponent();
                if (tc instanceof JScrollPane) ((JScrollPane) tc).getViewport().setViewPosition(new Point(0, 0));
            });
        });

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(ColorScheme.DARK_GRAY_COLOR);
        root.add(tabs, BorderLayout.CENTER);
        root.add(bottomBar, BorderLayout.SOUTH);
        setContentPane(root);
        pack();
        // pack() sizes to content preferred width (too narrow for the odds paragraphs), so fix it.
        // Tall enough to show the 2× card preview on the Export tab without scrolling.
        setSize(new Dimension(440, 740));
        setMinimumSize(new Dimension(380, 420));
        setLocationRelativeTo(owner);
    }

    /** Export tab: card preview centred above a bottom-anchored Copy/Save row (its own layout). */
    private JComponent buildExportTab(CapturedCreature capture) {
        return CardExportDialog.sharedImageService() != null
                ? new CardExportPanel(capture)
                : scroll(placeholder("Export unavailable."));
    }

    private JComponent placeholder(String text) {
        JLabel l = new JLabel(text);
        l.setFont(body);
        l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        l.setBorder(new EmptyBorder(16, 16, 16, 16));
        return l;
    }

    private JScrollPane scroll(JComponent content) {
        JScrollPane sp = new JScrollPane(content,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(null);
        sp.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        return sp;
    }

    // -------------------------------------------------------------------------
    // Export (clipboard) / Save (PNG) — this tab or all tabs
    // -------------------------------------------------------------------------

    private JComponent buildBottomBar() {
        JButton export = barButton("Copy tab", new Color(60, 90, 150));
        export.addActionListener(e -> flashAfter(export, "✓ Copied",
                () -> copy(decorate(renderTab(tabs.getSelectedIndex())))));
        JButton save = barButton("Save tab PNG", new Color(55, 110, 60));
        save.addActionListener(e -> save(decorate(renderTab(tabs.getSelectedIndex())), "card-" + tabTitle()));

        JPanel bar = new JPanel(new GridLayout(1, 2, 6, 0));
        bar.setBackground(ColorScheme.DARK_GRAY_COLOR);
        bar.setBorder(new EmptyBorder(6, 8, 8, 8));
        bar.add(export);
        bar.add(save);
        return bar;
    }

    private String tabTitle() {
        return tabs.getTitleAt(tabs.getSelectedIndex()).toLowerCase();
    }

    private JButton barButton(String text, Color bg) {
        JButton b = new JButton(text);
        b.setFont(FontManager.getRunescapeSmallFont());
        b.setBackground(bg);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        return b;
    }

    private void flashAfter(JButton b, String label, Runnable action) {
        action.run();
        String orig = b.getText();
        b.setText(label);
        new javax.swing.Timer(1400, e -> {
            b.setText(orig);
            ((javax.swing.Timer) e.getSource()).stop();
        }).start();
    }

    /** Renders one tab's content (including any scrolled-off part) to an image. */
    private BufferedImage renderTab(int index) {
        Component tab = tabs.getComponentAt(index);
        JComponent c = tab instanceof JScrollPane
                ? (JComponent) ((JScrollPane) tab).getViewport().getView()
                : (JComponent) tab;
        int w = c.getWidth() > 0 ? c.getWidth() : 420;
        int h = c.getHeight() > 0 ? c.getHeight() : Math.max(200, c.getPreferredSize().height);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(ColorScheme.DARK_GRAY_COLOR);
        g.fillRect(0, 0, w, h);
        c.printAll(g);
        g.dispose();
        return img;
    }

    private static final int FOOTER_H = 24;

    /** Wraps a rendered tab with a dashboard-style footer. */
    private BufferedImage decorate(BufferedImage c) {
        BufferedImage dst = new BufferedImage(c.getWidth(), c.getHeight() + FOOTER_H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        g.setColor(ColorScheme.DARK_GRAY_COLOR);
        g.fillRect(0, 0, dst.getWidth(), dst.getHeight());
        g.drawImage(c, 0, 0, null);
        drawFooter(g, c.getWidth(), c.getHeight());
        g.dispose();
        return dst;
    }

    /** Dashboard-style footer: orange rule + centred brand line, drawn in the FOOTER_H band at {@code top}. */
    private void drawFooter(Graphics2D g, int w, int top) {
        g.setColor(new Color(255, 165, 0, 60));
        g.fillRect(12, top + 3, w - 24, 1);
        g.setFont(FontManager.getRunescapeSmallFont());
        FontMetrics fm = g.getFontMetrics();
        String footer = "RuneLite · Bestiary Plugin";
        g.setColor(new Color(110, 110, 110));
        g.drawString(footer, (w - fm.stringWidth(footer)) / 2, top + 10 + fm.getAscent());
    }

    private void copy(BufferedImage img) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new Transferable() {
            @Override public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{DataFlavor.imageFlavor}; }
            @Override public boolean isDataFlavorSupported(DataFlavor f) { return DataFlavor.imageFlavor.equals(f); }
            @Override public Object getTransferData(DataFlavor f) { return img; }
        }, null);
    }

    private void save(BufferedImage img, String suggestedName) {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File(suggestedName + ".png"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        if (!f.getName().toLowerCase().endsWith(".png")) f = new File(f.getParentFile(), f.getName() + ".png");
        try {
            ImageIO.write(img, "png", f);
        } catch (java.io.IOException ex) {
            /* best effort — ignore */
        }
    }

    // -------------------------------------------------------------------------
    // Overview
    // -------------------------------------------------------------------------

    private JPanel buildOverview(CapturedCreature c) {
        JPanel p = column();

        JLabel title = new JLabel(c.npcName + (c.isShiny() ? "  ✦" : ""));
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(c.rarity.displayColor);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(title);
        p.add(Box.createVerticalStrut(8));

        com.bestiary.model.DifficultyTier diff =
                MonsterRoster.getDifficulty(c.npcName, c.npcCombatLevel);
        com.bestiary.model.CreatureSpecies species =
                MonsterRoster.getSpecies(c.npcName, c.npcCombatLevel);

        p.add(sectionHeader("Card"));
        p.add(kv("Rarity", c.rarity.label, c.rarity.displayColor));
        p.add(kv("Species", species.label, species.displayColor));
        p.add(kv("Difficulty", diff.label, diff.displayColor));
        p.add(kv("Power Level", String.valueOf(c.powerLevel()), Color.WHITE));
        p.add(kv("Hitpoints", String.valueOf(c.hitpoints())
                + (c.observedHp > 0 ? "  (observed)" : ""), new Color(120, 200, 120)));
        p.add(kv("Prayer", String.valueOf(c.prayer), Color.WHITE));
        p.add(kv("Shiny", c.isShiny() ? "Yes ✦" : "No",
                c.isShiny() ? new Color(255, 215, 0) : ColorScheme.LIGHT_GRAY_COLOR));

        p.add(Box.createVerticalStrut(10));
        p.add(sectionHeader("Capture"));
        p.add(kv("Caught", DATE.format(c.captureTime), Color.WHITE));
        p.add(kv("Region", c.regionName != null && !c.regionName.isEmpty() ? c.regionName : "Unknown", Color.WHITE));
        p.add(kv("Bestiary level", String.valueOf(c.captureLevel), Color.WHITE));
        p.add(kv("Kills before catch", String.valueOf(c.killsBeforeCapture), Color.WHITE));
        p.add(kv("Combat level", c.npcCombatLevel >= 0 ? String.valueOf(c.npcCombatLevel) : "—", Color.WHITE));

        // Use the ORIGINAL rarity/shiny (the first reroll snapshot) so a rerolled/upgraded card still
        // reports the reward + XP it earned when first caught, not its current form.
        com.bestiary.model.CreatureRarity capRarity =
                c.rerollHistory.isEmpty() ? c.rarity : c.rerollHistory.get(0).rarity;
        boolean capShiny = c.rerollHistory.isEmpty() ? c.isShiny() : c.rerollHistory.get(0).shiny;
        long baseCredits = com.bestiary.util.CreditCalculator.forCapture(diff, capRarity, capShiny);
        long earned = c.creditsEarned;   // true award recorded at capture (0 = legacy card)
        long bounty;
        if (earned > 0) {
            bounty = Math.max(0, earned - baseCredits);
        } else {                         // legacy: best-effort using the current Hunter's Bounty tier
            bounty = captureCreditBonus.getAsLong();
        }
        p.add(kv("Credits Earned", amountHtml(baseCredits, bounty), new Color(120, 190, 255)));

        // Kill XP: awarded on every kill of this monster (difficulty-tiered) + the Hunter's Focus
        // flat shop bonus. Not card-specific, so it reflects the current shop tier.
        long killXpBase  = com.bestiary.service.ProgressionService.killXp(diff);
        p.add(kv("Kill XP", amountHtml(killXpBase, killXpFlatBonus.getAsLong()), new Color(170, 210, 120)));

        // Capture XP: base (from the ORIGINAL rarity) + the Scholar's Insight % boost. Uses the true
        // awarded value when recorded (as-at-capture); legacy cards fall back to the current bonus.
        long capXpBase  = com.bestiary.service.ProgressionService.captureXp(c.npcCombatLevel, capRarity);
        long capXpBonus = c.xpEarned > 0
                ? Math.max(0, c.xpEarned - capXpBase)
                : Math.round(capXpBase * captureXpBonus.getAsDouble());
        p.add(kv("Capture XP", amountHtml(capXpBase, capXpBonus), new Color(170, 210, 120)));

        p.add(kv("Caught by", c.originalOwner != null && !c.originalOwner.isEmpty() ? c.originalOwner
                : (c.playerName != null && !c.playerName.isEmpty() ? c.playerName : "Unknown"),
                new Color(200, 155, 50)));
        if (com.bestiary.model.BestiaryCollection.isTradedIn(c)) {
            // Card was traded in from another of the player's accounts (#50).
            p.add(kv("Traded in — held by", c.currentOwner, new Color(120, 190, 255)));
        }
        if (c.rerollCount() > 0) {
            p.add(kv("Rerolled", c.rerollCount() + (c.rerollCount() == 1 ? " time" : " times"),
                    new Color(180, 150, 230)));
        }

        p.add(Box.createVerticalStrut(10));
        p.add(sectionHeader("Unique ID"));
        JTextField id = new JTextField(CardId.encode(MonsterRoster.getDexNumber(c.npcName), c));
        id.setEditable(false);
        id.setFont(FontManager.getRunescapeSmallFont());
        id.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        id.setForeground(new Color(150, 150, 150));
        id.setCaretPosition(0);
        id.setAlignmentX(Component.LEFT_ALIGNMENT);
        id.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        p.add(id);
        JLabel hint = new JLabel("Select + copy to share or look this card up.");
        hint.setFont(FontManager.getRunescapeSmallFont());
        hint.setForeground(new Color(120, 120, 120));
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        hint.setBorder(new EmptyBorder(2, 0, 0, 0));
        p.add(hint);

        return p;
    }

    // -------------------------------------------------------------------------
    // Reroll history
    // -------------------------------------------------------------------------

    private JPanel buildRerollHistory(CapturedCreature c) {
        JPanel p = column();

        if (c.rerollCount() == 0) {
            JLabel none = new JLabel("This card has never been rerolled — it's a raw pull.");
            none.setFont(body);
            none.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            none.setAlignmentX(Component.LEFT_ALIGNMENT);
            p.add(none);
            return p;
        }

        Set<String> rerollers = c.uniqueRerollers();
        p.add(sectionHeader("Rerolled " + c.rerollCount() + (c.rerollCount() == 1 ? " time" : " times")));
        JLabel who = new JLabel("<html><div style='width:390px'>"
                + rerollers.size() + (rerollers.size() == 1 ? " reroller: " : " rerollers: ")
                + String.join(", ", rerollers) + "</div></html>");
        who.setFont(body);
        who.setForeground(new Color(180, 150, 230));
        who.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(who);
        p.add(Box.createVerticalStrut(8));

        // Timeline table: each past state, then the current state.
        JPanel t = new JPanel(new GridBagLayout());
        t.setOpaque(false);
        t.setAlignmentX(Component.LEFT_ALIGNMENT);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(2, 0, 2, 8);
        g.anchor = GridBagConstraints.WEST;
        g.weightx = 1.0;   // fill the table width (otherwise GridBag centres the columns)

        String[] heads = {"State", "Rarity", "PWR", "Shiny", "By", "When"};
        for (int i = 0; i < heads.length; i++) {
            g.gridx = i; g.gridy = 0;
            t.add(cell(heads[i], bodyBold, new Color(140, 140, 140)), g);
        }
        // thin rule under the header so the table reads as a table, not floating text
        GridBagConstraints sep = new GridBagConstraints();
        sep.gridx = 0; sep.gridy = 1; sep.gridwidth = heads.length;
        sep.fill = GridBagConstraints.HORIZONTAL;
        sep.insets = new Insets(2, 0, 4, 0);
        JPanel rule = new JPanel();
        rule.setBackground(new Color(70, 70, 70));
        rule.setPreferredSize(new Dimension(10, 1));
        t.add(rule, sep);

        int row = 2;
        java.util.List<CapturedCreature.RerollState> h = c.rerollHistory;
        for (int i = 0; i < h.size(); i++) {
            CapturedCreature.RerollState s = h.get(i);
            String stateLabel = i == 0 ? "Original" : "Roll " + i;
            addRerollRow(t, g, row++, stateLabel, s.rarity.label, s.rarity.displayColor,
                    s.powerLevel, s.shiny, s.rerolledBy,
                    DATE_SHORT.format(Instant.ofEpochSecond(s.epoch)));
        }
        // Current (latest) state
        addRerollRow(t, g, row, "Current", c.rarity.label, c.rarity.displayColor,
                c.powerLevel(), c.isShiny(), "—", "now");

        t.setMaximumSize(new Dimension(Integer.MAX_VALUE, t.getPreferredSize().height));
        p.add(t);

        p.add(Box.createVerticalStrut(6));
        JTextArea note = new JTextArea("Each reroll re-rolls the stats & shiny at the same monster; "
                + "a non-Mythic card has a small chance to rank up.");
        note.setFont(body);
        note.setForeground(new Color(120, 120, 120));
        note.setBackground(ColorScheme.DARK_GRAY_COLOR);
        note.setOpaque(false);
        note.setEditable(false);
        note.setFocusable(false);
        note.setLineWrap(true);
        note.setWrapStyleWord(true);
        JPanel noteWrap = new JPanel(new BorderLayout());
        noteWrap.setOpaque(false);
        noteWrap.setAlignmentX(Component.LEFT_ALIGNMENT);
        noteWrap.setBorder(new EmptyBorder(6, 0, 0, 0));
        noteWrap.add(note, BorderLayout.CENTER);
        p.add(noteWrap);
        return p;
    }

    private void addRerollRow(JPanel t, GridBagConstraints g, int rowY, String state, String rarity,
                              Color rarityColor, int pwr, boolean shiny, String by, String when) {
        boolean isCurrent = "Current".equals(state);
        Font f = isCurrent ? bodyBold : body;
        g.gridy = rowY;
        g.gridx = 0; t.add(cell(state, f, isCurrent ? Color.WHITE : new Color(200, 200, 200)), g);
        g.gridx = 1; t.add(cell(rarity, f, rarityColor), g);
        g.gridx = 2; t.add(cell(String.valueOf(pwr), f, Color.WHITE), g);
        g.gridx = 3; t.add(cell(shiny ? "✦" : "–", f, shiny ? new Color(255, 215, 0) : new Color(110, 110, 110)), g);
        g.gridx = 4; t.add(cell(by, f, new Color(180, 150, 230)), g);
        g.gridx = 5; t.add(cell(when, f, new Color(140, 140, 140)), g);
    }

    // -------------------------------------------------------------------------
    // Shared helpers
    // -------------------------------------------------------------------------

    private JPanel column() {
        // Track the scroll viewport width so full-width children (the reroll table) fill it
        // instead of the panel sizing to a wide child's preferred width and clipping.
        JPanel p = new JPanel() {
            @Override public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                if (getParent() != null) d.width = getParent().getWidth();
                return d;
            }
        };
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(ColorScheme.DARK_GRAY_COLOR);
        p.setBorder(new EmptyBorder(12, 14, 12, 14));
        return p;
    }

    private JLabel sectionHeader(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setFont(bodyBold);
        l.setForeground(new Color(255, 152, 31));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(new EmptyBorder(0, 0, 3, 0));
        return l;
    }

    private JLabel cell(String text, Font font, Color colour) {
        JLabel l = new JLabel(text);
        l.setFont(font);
        l.setForeground(colour);
        return l;
    }

    /** Renders "base" or "base <green>(+bonus)</green>" (green = a shop-upgrade bonus) as an HTML value. */
    private static String amountHtml(long base, long bonus) {
        return bonus > 0
                ? "<html>" + base + " <font color='" + BONUS_HEX + "'>(+" + bonus + ")</font></html>"
                : String.valueOf(base);
    }

    private Box kv(String left, String right, Color rightColor) {
        Box row = Box.createHorizontalBox();
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, body.getSize() + 8));
        JLabel l = new JLabel(left);
        l.setFont(body);
        l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        JLabel rr = new JLabel(right);
        rr.setFont(body);
        rr.setForeground(rightColor);
        // HTML labels report an unbounded max width, so the glue can't push them flush right —
        // pin the max to the preferred size so they stay compact like plain-text values.
        rr.setMaximumSize(rr.getPreferredSize());
        row.add(l);
        row.add(Box.createHorizontalStrut(14));
        row.add(Box.createHorizontalGlue());
        row.add(rr);
        return row;
    }
}
