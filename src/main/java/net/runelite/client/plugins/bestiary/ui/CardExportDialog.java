package net.runelite.client.plugins.bestiary.ui;

import net.runelite.client.plugins.bestiary.model.BestiaryCollection;
import net.runelite.client.plugins.bestiary.model.CapturedCreature;
import net.runelite.client.plugins.bestiary.model.MonsterRoster;
import net.runelite.client.plugins.bestiary.service.WikiImageService;
import net.runelite.client.plugins.bestiary.util.CardId;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Modal dialog showing a single capture as an album card, with options to
 * copy the card image to clipboard or save it as a PNG.
 * Exported image includes a bottom owner banner and the 28-char card ID.
 */
public class CardExportDialog extends JDialog {

    private static WikiImageService sharedImageService;
    private static BestiaryCollection sharedCollection;

    public static void setShared(WikiImageService imgSvc, BestiaryCollection collection) {
        sharedImageService = imgSvc;
        sharedCollection = collection;
    }

    /** Open export dialog for a single capture (uses shared service/collection). */
    public static void open(Window owner, CapturedCreature capture) {
        if (sharedImageService == null || sharedCollection == null) return;
        int dex = MonsterRoster.getDexNumber(capture.npcName);
        new CardExportDialog(owner, capture, sharedCollection, sharedImageService, dex);
    }

    // -------------------------------------------------------------------------

    private final AlbumCard card;
    private final String cardId;
    private final String owner;
    private final String nickname; // null if not set

    public CardExportDialog(Window owner, CapturedCreature capture,
                            BestiaryCollection collection,
                            WikiImageService imageService,
                            int dexNumber) {
        super(owner, "Export Card — " + capture.npcName, ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        String capturedBy = capture.playerName != null && !capture.playerName.isEmpty()
                ? capture.playerName : "Unknown";
        boolean hasNickname = capture.nickname != null && !capture.nickname.isEmpty();

        this.card     = new AlbumCard(dexNumber, capture.npcName, List.of(capture), collection, imageService);
        this.cardId   = CardId.encode(dexNumber, capture);
        this.owner    = capturedBy;
        this.nickname = hasNickname ? capture.nickname : null;

        // Wire up the card click to open all captures for this NPC, not just this one
        List<CapturedCreature> allNpcCaptures = collection.creatures.stream()
                .filter(c -> c.npcName.equals(capture.npcName))
                .collect(Collectors.toList());
        if (allNpcCaptures.size() > 1) {
            card.setDetailCaptures(allNpcCaptures);
        }

        // Nickname label (shown only when set)
        JLabel nickLabel = null;
        if (hasNickname) {
            nickLabel = new JLabel("\"" + capture.nickname + "\"");
            nickLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD | Font.ITALIC, 12f));
            nickLabel.setForeground(new Color(220, 170, 60));
            nickLabel.setHorizontalAlignment(SwingConstants.CENTER);
            nickLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        }

        // Card ID label
        JLabel idLabel = new JLabel(cardId);
        idLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
        idLabel.setForeground(new Color(130, 130, 130));
        idLabel.setHorizontalAlignment(SwingConstants.CENTER);

        // Owner label
        JLabel ownerLabel = new JLabel("Captured by " + this.owner);
        ownerLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        ownerLabel.setForeground(new Color(220, 170, 60));
        ownerLabel.setHorizontalAlignment(SwingConstants.CENTER);

        // Buttons
        JButton copyBtn = new JButton("Copy Image");
        copyBtn.setFont(FontManager.getRunescapeSmallFont());
        copyBtn.setBackground(new Color(255, 153, 0));
        copyBtn.setForeground(Color.BLACK);
        copyBtn.setFocusPainted(false);
        copyBtn.addActionListener(e -> copyToClipboard());

        JButton saveBtn = new JButton("Save PNG…");
        saveBtn.setFont(FontManager.getRunescapeSmallFont());
        saveBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        saveBtn.setForeground(Color.WHITE);
        saveBtn.setFocusPainted(false);
        saveBtn.addActionListener(e -> savePng(capture.npcName));

        JPanel btnRow = new JPanel(new GridLayout(1, 2, 6, 0));
        btnRow.setOpaque(false);
        btnRow.add(copyBtn);
        btnRow.add(saveBtn);

        // Layout
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ColorScheme.DARK_GRAY_COLOR);
        content.setBorder(new EmptyBorder(12, 12, 12, 12));

        card.setAlignmentX(Component.CENTER_ALIGNMENT);
        idLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        ownerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnRow.setAlignmentX(Component.CENTER_ALIGNMENT);

        content.add(card);
        if (nickLabel != null) {
            content.add(Box.createVerticalStrut(4));
            content.add(nickLabel);
        }
        content.add(Box.createVerticalStrut(6));
        content.add(idLabel);
        content.add(Box.createVerticalStrut(2));
        content.add(ownerLabel);
        content.add(Box.createVerticalStrut(10));
        content.add(btnRow);

        setContentPane(content);
        pack();
        setLocationRelativeTo(owner);
        setVisible(true);
    }

    // -------------------------------------------------------------------------

    private BufferedImage renderCard() {
        int scale    = 3;
        int topH     = nickname != null ? 22 : 0; // nickname banner above card
        int bottomH  = 28;                         // ID + captured-by banner below card

        card.setSize(AlbumCard.CARD_W, AlbumCard.CARD_H);

        BufferedImage img = new BufferedImage(
                AlbumCard.CARD_W * scale,
                (topH + AlbumCard.CARD_H + bottomH) * scale,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,     RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.scale(scale, scale);

        // Top banner — nickname (only when set)
        if (nickname != null) {
            g2.setColor(new Color(12, 12, 12));
            g2.fillRect(0, 0, AlbumCard.CARD_W, topH);
            g2.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD | Font.ITALIC, 8f));
            FontMetrics nickFm = g2.getFontMetrics();
            g2.setColor(new Color(200, 155, 50));
            String nickStr = "\"" + nickname + "\"";
            g2.drawString(nickStr,
                    (AlbumCard.CARD_W - nickFm.stringWidth(nickStr)) / 2,
                    topH - 5);
        }

        // Paint card offset below the top banner
        g2.translate(0, topH);
        card.print(g2);
        g2.translate(0, -topH);

        // Bottom banner
        int cardBottom = topH + AlbumCard.CARD_H;
        g2.setColor(new Color(12, 12, 12));
        g2.fillRect(0, cardBottom, AlbumCard.CARD_W, bottomH);

        // Card ID (small, dim)
        g2.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 6));
        FontMetrics idFm = g2.getFontMetrics();
        g2.setColor(new Color(90, 90, 90));
        g2.drawString(cardId,
                (AlbumCard.CARD_W - idFm.stringWidth(cardId)) / 2,
                cardBottom + 9);

        // "Captured by" owner (gold)
        g2.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD, 8f));
        FontMetrics ownerFm = g2.getFontMetrics();
        g2.setColor(new Color(200, 155, 50));
        String ownerStr = "Captured by " + owner;
        g2.drawString(ownerStr,
                (AlbumCard.CARD_W - ownerFm.stringWidth(ownerStr)) / 2,
                cardBottom + 22);

        g2.dispose();
        return img;
    }

    private void copyToClipboard() {
        BufferedImage img = renderCard();
        Transferable t = new Transferable() {
            @Override public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{DataFlavor.imageFlavor}; }
            @Override public boolean isDataFlavorSupported(DataFlavor f) { return f.equals(DataFlavor.imageFlavor); }
            @Override public Object getTransferData(DataFlavor f) throws UnsupportedFlavorException {
                if (!f.equals(DataFlavor.imageFlavor)) throw new UnsupportedFlavorException(f);
                return img;
            }
        };
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(t, null);
        JOptionPane.showMessageDialog(this, "Card image copied to clipboard!", "Copied", JOptionPane.INFORMATION_MESSAGE);
    }

    private void savePng(String npcName) {
        JFileChooser chooser = new JFileChooser();
        String fileName = "bestiary_" + npcName.toLowerCase().replace(" ", "_") + ".png";
        chooser.setSelectedFile(new File(System.getProperty("user.home"), fileName));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                ImageIO.write(renderCard(), "PNG", chooser.getSelectedFile());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to save: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
