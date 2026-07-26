package com.bestiary.ui;

import com.bestiary.model.CapturedCreature;
import com.bestiary.model.MonsterRoster;
import com.bestiary.util.CardId;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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

    public static void open(Window owner, CapturedCreature capture) {
        if (current != null && current.isShowing()) current.dispose();
        current = new CardDataDialog(owner, capture);
        current.setVisible(true);
    }

    private final Font body;
    private final Font bodyBold;

    private CardDataDialog(Window owner, CapturedCreature capture) {
        super(owner, "Card data — " + capture.npcName, ModalityType.MODELESS);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        float base = FontManager.getRunescapeSmallFont().getSize2D();
        this.body     = FontManager.getRunescapeSmallFont().deriveFont(base + 1f);
        this.bodyBold = body.deriveFont(Font.BOLD);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(FontManager.getRunescapeSmallFont());
        tabs.setBackground(ColorScheme.DARK_GRAY_COLOR);
        tabs.setForeground(Color.WHITE);
        tabs.addTab("Overview", scroll(buildOverview(capture)));
        tabs.addTab("Odds", scroll(new OddsView(capture)));
        tabs.addTab("Rerolls", scroll(buildRerollHistory(capture)));

        setContentPane(tabs);
        setPreferredSize(new Dimension(400, 460));
        pack();
        setMinimumSize(new Dimension(340, 300));
        setLocationRelativeTo(owner);
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

        p.add(sectionHeader("Card"));
        p.add(kv("Rarity", c.rarity.label, c.rarity.displayColor));
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
        p.add(kv("Captured by", c.playerName != null && !c.playerName.isEmpty() ? c.playerName : "Unknown",
                new Color(200, 155, 50)));
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
        JLabel who = new JLabel(rerollers.size() + (rerollers.size() == 1 ? " reroller: " : " rerollers: ")
                + String.join(", ", rerollers));
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
        g.insets = new Insets(1, 0, 1, 12);
        g.anchor = GridBagConstraints.WEST;

        String[] heads = {"State", "Rarity", "PWR", "Shiny", "Pray", "By", "When"};
        for (int i = 0; i < heads.length; i++) {
            g.gridx = i; g.gridy = 0;
            t.add(cell(heads[i], bodyBold, new Color(140, 140, 140)), g);
        }

        int row = 1;
        java.util.List<CapturedCreature.RerollState> h = c.rerollHistory;
        for (int i = 0; i < h.size(); i++) {
            CapturedCreature.RerollState s = h.get(i);
            String stateLabel = i == 0 ? "Original" : "Roll " + i;
            addRerollRow(t, g, row++, stateLabel, s.rarity.label, s.rarity.displayColor,
                    s.powerLevel, s.shiny, s.prayer, s.rerolledBy,
                    DATE.format(Instant.ofEpochSecond(s.epoch)));
        }
        // Current (latest) state
        addRerollRow(t, g, row, "Current", c.rarity.label, c.rarity.displayColor,
                c.powerLevel(), c.isShiny(), c.prayer, "—", "now");

        t.setMaximumSize(new Dimension(Integer.MAX_VALUE, t.getPreferredSize().height));
        p.add(t);

        p.add(Box.createVerticalStrut(6));
        JLabel note = new JLabel("<html><div style='width:340px'><i>Each reroll re-rolls the stats & "
                + "shiny at the same monster; a non-Mythic card has a small chance to rank up.</i></div></html>");
        note.setFont(body);
        note.setForeground(new Color(120, 120, 120));
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(note);
        return p;
    }

    private void addRerollRow(JPanel t, GridBagConstraints g, int rowY, String state, String rarity,
                              Color rarityColor, int pwr, boolean shiny, int prayer, String by, String when) {
        boolean isCurrent = "Current".equals(state);
        Font f = isCurrent ? bodyBold : body;
        g.gridy = rowY;
        g.gridx = 0; t.add(cell(state, f, isCurrent ? Color.WHITE : new Color(200, 200, 200)), g);
        g.gridx = 1; t.add(cell(rarity, f, rarityColor), g);
        g.gridx = 2; t.add(cell(String.valueOf(pwr), f, Color.WHITE), g);
        g.gridx = 3; t.add(cell(shiny ? "✦" : "–", f, shiny ? new Color(255, 215, 0) : new Color(110, 110, 110)), g);
        g.gridx = 4; t.add(cell(String.valueOf(prayer), f, new Color(170, 170, 170)), g);
        g.gridx = 5; t.add(cell(by, f, new Color(180, 150, 230)), g);
        g.gridx = 6; t.add(cell(when, f, new Color(140, 140, 140)), g);
    }

    // -------------------------------------------------------------------------
    // Shared helpers
    // -------------------------------------------------------------------------

    private JPanel column() {
        JPanel p = new JPanel();
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
        row.add(l);
        row.add(Box.createHorizontalStrut(14));
        row.add(Box.createHorizontalGlue());
        row.add(rr);
        return row;
    }
}
