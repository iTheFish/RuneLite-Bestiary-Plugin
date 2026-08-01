package com.bestiary.ui;

import com.bestiary.model.CapturedCreature;
import com.bestiary.model.DifficultyTier;
import com.bestiary.service.BestiaryDataService;
import com.bestiary.service.ProgressionService;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * The "Packs" tab (#135): buy a Random Card of a chosen difficulty. Each purchase spends credits,
 * generates a card of that tier, and reveals it TCG-style — the card lands face-down (hover shows
 * its rarity) and flips to the full card on click.
 */
public class RandomCardsTab extends JPanel {

    private static final NumberFormat FMT = NumberFormat.getNumberInstance(Locale.UK);

    private final BestiaryDataService dataService;
    private final ProgressionService  progressionService;
    private final Runnable onChange;

    private final JLabel creditsLabel = new JLabel();
    private final java.util.List<JButton> buyButtons = new java.util.ArrayList<>();
    private final JPanel revealArea = new JPanel(new BorderLayout());

    public RandomCardsTab(BestiaryDataService dataService, ProgressionService progressionService,
                          Runnable onChange) {
        this.dataService        = dataService;
        this.progressionService = progressionService;
        this.onChange           = onChange;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBackground(ColorScheme.DARK_GRAY_COLOR);
        top.setBorder(new EmptyBorder(10, 10, 6, 10));

        JLabel title = new JLabel("Random Cards");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(new Color(255, 165, 0));
        title.setAlignmentX(LEFT_ALIGNMENT);
        top.add(title);

        JLabel blurb = new JLabel("<html><div style='width:210px'>Open a random card of a chosen "
                + "difficulty. Rarity, shiny and stats are rolled fresh — you could pull anything.</div></html>");
        blurb.setFont(FontManager.getRunescapeSmallFont());
        blurb.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        blurb.setAlignmentX(LEFT_ALIGNMENT);
        top.add(Box.createVerticalStrut(3));
        top.add(blurb);

        creditsLabel.setFont(FontManager.getRunescapeSmallFont());
        creditsLabel.setForeground(new Color(120, 190, 255));
        creditsLabel.setAlignmentX(LEFT_ALIGNMENT);
        top.add(Box.createVerticalStrut(6));
        top.add(creditsLabel);

        top.add(Box.createVerticalStrut(8));
        for (DifficultyTier tier : DifficultyTier.values()) {
            top.add(buildBuyRow(tier));
            top.add(Box.createVerticalStrut(4));
        }

        add(top, BorderLayout.NORTH);

        revealArea.setBackground(ColorScheme.DARK_GRAY_COLOR);
        revealArea.setBorder(new EmptyBorder(4, 10, 10, 10));
        showRevealHint();
        add(revealArea, BorderLayout.CENTER);

        refresh();
    }

    private JPanel buildBuyRow(DifficultyTier tier) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

        JButton buy = new JButton(tier.label + "  —  " + FMT.format(BestiaryDataService.randomPackCost(tier)) + " cr");
        buy.setFont(FontManager.getRunescapeSmallFont());
        buy.setFocusPainted(false);
        buy.setForeground(Color.WHITE);
        buy.setBackground(tier.displayColor.darker());
        buy.addActionListener(e -> openPack(tier));
        buyButtons.add(buy);

        row.add(buy, BorderLayout.CENTER);
        return row;
    }

    private void openPack(DifficultyTier tier) {
        long cost = BestiaryDataService.randomPackCost(tier);
        if (dataService.getCredits() < cost) {
            revealArea.removeAll();
            revealArea.add(centeredMessage("Not enough credits — need " + FMT.format(cost) + " cr."), BorderLayout.CENTER);
            revealArea.revalidate();
            revealArea.repaint();
            return;
        }
        CapturedCreature card = dataService.openRandomPack(tier, progressionService.getLevel());
        if (card == null) return;
        if (onChange != null) onChange.run();   // refresh credits + collection elsewhere
        refresh();
        showFaceDown(card);
    }

    /** Face-down reveal: hover shows the rarity, click flips to the full card. */
    private void showFaceDown(CapturedCreature card) {
        revealArea.removeAll();
        FaceDownCard back = new FaceDownCard(card, () -> showRevealed(card));
        JPanel holder = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 6));
        holder.setOpaque(false);
        holder.add(back);
        revealArea.add(holder, BorderLayout.NORTH);
        revealArea.revalidate();
        revealArea.repaint();
    }

    private void showRevealed(CapturedCreature card) {
        revealArea.removeAll();
        if (CardExportDialog.sharedImageService() != null) {
            JPanel holder = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 6));
            holder.setOpaque(false);
            holder.add(new CardExportPanel(card));
            revealArea.add(holder, BorderLayout.CENTER);
        } else {
            revealArea.add(centeredMessage(card.rarity.label + " " + card.npcName
                    + (card.isShiny() ? " ✦" : "")), BorderLayout.CENTER);
        }
        revealArea.revalidate();
        revealArea.repaint();
    }

    private void showRevealHint() {
        revealArea.removeAll();
        revealArea.add(centeredMessage("Buy a pack above to open a card."), BorderLayout.NORTH);
    }

    private JLabel centeredMessage(String text) {
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setFont(FontManager.getRunescapeSmallFont());
        l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        l.setBorder(new EmptyBorder(10, 0, 0, 0));
        return l;
    }

    public void refresh() {
        long credits = dataService.getCredits();
        creditsLabel.setText("Credits: " + FMT.format(credits));
        boolean viewing = dataService.isViewing();
        int i = 0;
        for (DifficultyTier tier : DifficultyTier.values()) {
            if (i >= buyButtons.size()) break;
            boolean affordable = credits >= BestiaryDataService.randomPackCost(tier);
            buyButtons.get(i).setEnabled(!viewing && affordable);
            i++;
        }
    }

    /**
     * A face-down card the size of a real card: a "?" back with the rarity revealed on hover
     * (tooltip) and the full card revealed on click.
     */
    private static final class FaceDownCard extends JComponent {
        private FaceDownCard(CapturedCreature card, Runnable onReveal) {
            setPreferredSize(new Dimension(AlbumCard.CARD_W, AlbumCard.CARD_H));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText("Hover reveals rarity: " + card.rarity.label
                    + (card.isShiny() ? " ✦ SHINY" : "") + "  ·  click to reveal");
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { onReveal.run(); }
            });
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g2.setColor(new Color(24, 26, 32));
            g2.fillRoundRect(0, 0, w - 1, h - 1, 14, 14);
            g2.setColor(new Color(90, 100, 120));
            g2.setStroke(new BasicStroke(2f));
            g2.drawRoundRect(1, 1, w - 3, h - 3, 14, 14);
            g2.setColor(new Color(150, 165, 190));
            g2.setFont(FontManager.getRunescapeBoldFont().deriveFont(48f));
            FontMetrics fm = g2.getFontMetrics();
            String q = "?";
            g2.drawString(q, (w - fm.stringWidth(q)) / 2, h / 2 + fm.getAscent() / 2 - 6);
            g2.setFont(FontManager.getRunescapeSmallFont());
            FontMetrics sfm = g2.getFontMetrics();
            String hint = "Click to reveal";
            g2.setColor(new Color(120, 130, 150));
            g2.drawString(hint, (w - sfm.stringWidth(hint)) / 2, h - 14);
            g2.dispose();
        }
    }
}
