package com.bestiary.ui;

import com.bestiary.model.CapturedCreature;
import com.bestiary.service.BestiaryDataService;
import com.bestiary.service.BestiaryStore;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * MODELESS bulk-transfer screen (#50, "Mockup B"): tick the cards to send, pick one of your other
 * accounts as the target, and move them there. The card leaves this collection and is deposited in
 * the target account's file (works even if that account is offline). Mirrors {@link DiscardDialog}:
 * owned by the album window, so it is auto-disposed when the album closes.
 */
public class TransferDialog extends JDialog {

    private static TransferDialog current;

    private final BestiaryDataService dataService;
    private final Runnable onDone;

    private final JComboBox<BestiaryStore.AccountRef> targetCombo = new JComboBox<>();
    private final JPanel cardsPanel = new JPanel();
    private final List<CardRow> rows = new ArrayList<>();
    private JTextField searchField;
    private JLabel emptyNote;
    private JButton sendBtn;

    /** A checkbox bound to the capture it would transfer. */
    private static final class CardRow {
        final JCheckBox box;
        final CapturedCreature card;
        CardRow(JCheckBox box, CapturedCreature card) { this.box = box; this.card = card; }
    }

    public static void open(Window owner, BestiaryDataService dataService, Runnable onDone) {
        if (current != null && current.isShowing()) current.dispose();
        current = new TransferDialog(owner, dataService, onDone);
        current.setVisible(true);
    }

    /** Rebuilds the open transfer dialog (e.g. after cards changed elsewhere). */
    public static void refreshOpen() {
        if (current != null && current.isShowing()) current.rebuild();
    }

    private TransferDialog(Window owner, BestiaryDataService dataService, Runnable onDone) {
        super(owner, "Transfer cards", ModalityType.MODELESS);
        this.dataService = dataService;
        this.onDone = onDone;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(ColorScheme.DARK_GRAY_COLOR);
        root.setBorder(new EmptyBorder(12, 14, 12, 14));

        JLabel title = new JLabel("Send cards to another account");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(Color.WHITE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(title);

        JLabel help = new JLabel("<html><div style='width:320px'>Tick the cards to send and choose one "
                + "of your other accounts. They leave this collection and appear in the target account "
                + "(favourites, nicknames and provenance travel with them).</div></html>");
        help.setFont(FontManager.getRunescapeSmallFont());
        help.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        help.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(help);

        root.add(Box.createVerticalStrut(8));
        root.add(header("SEND TO"));
        targetCombo.setFont(FontManager.getRunescapeSmallFont());
        targetCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        targetCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        targetCombo.setRenderer(new AccountRenderer());
        targetCombo.addActionListener(e -> updateSendButton());
        root.add(targetCombo);

        emptyNote = new JLabel("No other accounts found on this machine. Log into another character first.");
        emptyNote.setFont(FontManager.getRunescapeSmallFont());
        emptyNote.setForeground(new Color(210, 160, 90));
        emptyNote.setAlignmentX(Component.LEFT_ALIGNMENT);
        emptyNote.setBorder(new EmptyBorder(4, 1, 0, 0));
        emptyNote.setVisible(false);
        root.add(emptyNote);

        root.add(Box.createVerticalStrut(8));
        JPanel cardHdr = new JPanel(new BorderLayout());
        cardHdr.setOpaque(false);
        cardHdr.setAlignmentX(Component.LEFT_ALIGNMENT);
        cardHdr.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        cardHdr.add(header("CARDS TO SEND"), BorderLayout.WEST);
        JPanel selRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        selRow.setOpaque(false);
        selRow.add(smallBtn("All",  () -> setAllChecked(true)));
        selRow.add(smallBtn("None", () -> setAllChecked(false)));
        cardHdr.add(selRow, BorderLayout.EAST);
        root.add(cardHdr);

        // Search filter — the card list can get very long late game.
        searchField = new JTextField();
        searchField.setFont(FontManager.getRunescapeSmallFont());
        searchField.setAlignmentX(Component.LEFT_ALIGNMENT);
        searchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        searchField.setToolTipText("Filter cards by monster name");
        searchField.putClientProperty("JTextField.placeholderText", "Search cards…");
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e)  { applyFilter(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e)  { applyFilter(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
        });
        root.add(searchField);
        root.add(Box.createVerticalStrut(4));

        cardsPanel.setLayout(new BoxLayout(cardsPanel, BoxLayout.Y_AXIS));
        cardsPanel.setOpaque(false);
        JScrollPane scroll = new JScrollPane(cardsPanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 60)));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setPreferredSize(new Dimension(360, 240));
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 240));
        root.add(scroll);

        root.add(Box.createVerticalStrut(8));
        sendBtn = new JButton("Send");
        sendBtn.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        sendBtn.setBackground(new Color(60, 110, 150));
        sendBtn.setForeground(Color.WHITE);
        sendBtn.setFocusPainted(false);
        sendBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        sendBtn.addActionListener(e -> doSend());
        root.add(sendBtn);

        setContentPane(root);
        rebuild();
        pack();
        setLocationRelativeTo(owner);
    }

    private JLabel header(String text) {
        JLabel l = new JLabel(text);
        l.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        l.setForeground(new Color(255, 152, 31));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(new EmptyBorder(0, 0, 3, 0));
        return l;
    }

    private JButton smallBtn(String text, Runnable action) {
        JButton b = new JButton(text);
        b.setFont(FontManager.getRunescapeSmallFont());
        b.setMargin(new Insets(0, 6, 0, 6));
        b.setFocusPainted(false);
        b.addActionListener(e -> action.run());
        return b;
    }

    /** Rebuilds the target list and the card checkboxes from the current collection. */
    private void rebuild() {
        // Target accounts
        BestiaryStore.AccountRef previouslySelected = (BestiaryStore.AccountRef) targetCombo.getSelectedItem();
        targetCombo.removeAllItems();
        List<BestiaryStore.AccountRef> others = dataService.listOtherAccounts();
        for (BestiaryStore.AccountRef a : others) targetCombo.addItem(a);
        if (previouslySelected != null) {
            for (BestiaryStore.AccountRef a : others) {
                if (a.hash == previouslySelected.hash) { targetCombo.setSelectedItem(a); break; }
            }
        }
        boolean haveTargets = !others.isEmpty();
        targetCombo.setVisible(haveTargets);
        emptyNote.setVisible(!haveTargets);

        // Card checkboxes — rarity desc, then Power Level desc
        cardsPanel.removeAll();
        rows.clear();
        List<CapturedCreature> cards = new ArrayList<>(dataService.getCollection().creatures);
        cards.sort((a, b) -> {
            int r = Integer.compare(b.rarity.ordinal(), a.rarity.ordinal());
            return r != 0 ? r : Integer.compare(b.powerLevel(), a.powerLevel());
        });
        for (CapturedCreature c : cards) {
            String star = c.favourite ? "★ " : "";
            String shiny = c.isShiny() ? "✦ " : "";
            JCheckBox cb = new JCheckBox(shiny + star + c.rarity.label + "  " + c.npcName
                    + "  · PWR " + c.powerLevel());
            cb.setOpaque(false);
            cb.setForeground(c.rarity.displayColor);
            cb.setFont(FontManager.getRunescapeSmallFont());
            cb.setAlignmentX(Component.LEFT_ALIGNMENT);
            cb.addActionListener(e -> updateSendButton());
            cardsPanel.add(cb);
            rows.add(new CardRow(cb, c));
        }
        if (rows.isEmpty()) {
            JLabel none = new JLabel("This collection has no cards to send.");
            none.setFont(FontManager.getRunescapeSmallFont());
            none.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            none.setBorder(new EmptyBorder(6, 4, 0, 0));
            cardsPanel.add(none);
        }
        applyFilter();
        cardsPanel.revalidate();
        cardsPanel.repaint();
        updateSendButton();
    }

    /** Shows only card rows whose monster name matches the search box (case-insensitive). */
    private void applyFilter() {
        String q = searchField == null ? "" : searchField.getText().trim().toLowerCase(java.util.Locale.ROOT);
        for (CardRow r : rows) {
            boolean match = q.isEmpty() || r.card.npcName.toLowerCase(java.util.Locale.ROOT).contains(q);
            r.box.setVisible(match);
        }
        cardsPanel.revalidate();
        cardsPanel.repaint();
    }

    private void setAllChecked(boolean checked) {
        // Only affects the currently-visible (filtered) rows.
        for (CardRow r : rows) if (r.box.isVisible()) r.box.setSelected(checked);
        updateSendButton();
    }

    private List<CapturedCreature> selected() {
        List<CapturedCreature> out = new ArrayList<>();
        for (CardRow r : rows) if (r.box.isSelected()) out.add(r.card);
        return out;
    }

    private void updateSendButton() {
        int n = selected().size();
        BestiaryStore.AccountRef target = (BestiaryStore.AccountRef) targetCombo.getSelectedItem();
        boolean canSend = n > 0 && target != null;
        sendBtn.setEnabled(canSend);
        sendBtn.setText(n == 0 ? "Select cards to send"
                : "Send " + n + " → " + (target != null ? accountName(target) : "…"));
    }

    private void doSend() {
        List<CapturedCreature> sel = selected();
        BestiaryStore.AccountRef target = (BestiaryStore.AccountRef) targetCombo.getSelectedItem();
        if (sel.isEmpty() || target == null) return;
        String name = accountName(target);
        int choice = JOptionPane.showConfirmDialog(this,
                "Send " + sel.size() + " card" + (sel.size() == 1 ? "" : "s") + " to " + name
                        + "?\nThey will leave this collection.",
                "Confirm transfer", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;

        int moved = dataService.transferCards(sel, target.hash, target.rsn);
        if (moved == 0) {
            JOptionPane.showMessageDialog(this,
                    "Could not transfer the cards (target account file could not be written).",
                    "Transfer failed", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (onDone != null) onDone.run();   // refresh panel + album
        rebuild();
    }

    private static String accountName(BestiaryStore.AccountRef a) {
        return a.rsn != null && !a.rsn.isEmpty() ? a.rsn : "Account " + a.hash;
    }

    /** Renders an account row as its RSN (fallback to the hash). */
    private static final class AccountRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof BestiaryStore.AccountRef) {
                setText(accountName((BestiaryStore.AccountRef) value));
            }
            return this;
        }
    }
}
