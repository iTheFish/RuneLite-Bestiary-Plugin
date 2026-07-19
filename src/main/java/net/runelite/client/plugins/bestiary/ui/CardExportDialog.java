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

/**
 * Modal dialog showing a single capture as an album card, with options to
 * copy the card image to clipboard or save it as a PNG.
 * Card ID (28-char fixed fingerprint) is displayed beneath the card.
 */
public class CardExportDialog extends JDialog {

    private static String playerName = "";
    private static WikiImageService sharedImageService;
    private static BestiaryCollection sharedCollection;

    /** Called from BestiaryPlugin.onGameStateChanged when LOGGED_IN. */
    public static void setPlayerName(String name) {
        playerName = (name != null) ? name : "";
    }

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

    public CardExportDialog(Window owner, CapturedCreature capture,
                            BestiaryCollection collection,
                            WikiImageService imageService,
                            int dexNumber) {
        super(owner, "Export Card — " + capture.npcName, ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        // Build card (single capture for exact stat display)
        AlbumCard card = new AlbumCard(dexNumber, capture.npcName,
                List.of(capture), collection, imageService);

        // Card ID
        String id = CardId.encode(dexNumber, capture, playerName);
        JLabel idLabel = new JLabel(id);
        idLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        idLabel.setForeground(new Color(160, 160, 160));
        idLabel.setHorizontalAlignment(SwingConstants.CENTER);

        // Buttons
        JButton copyBtn = new JButton("Copy Image");
        copyBtn.setFont(FontManager.getRunescapeSmallFont());
        copyBtn.setBackground(new Color(255, 153, 0));
        copyBtn.setForeground(Color.BLACK);
        copyBtn.setFocusPainted(false);
        copyBtn.addActionListener(e -> copyToClipboard(card));

        JButton saveBtn = new JButton("Save PNG…");
        saveBtn.setFont(FontManager.getRunescapeSmallFont());
        saveBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        saveBtn.setForeground(Color.WHITE);
        saveBtn.setFocusPainted(false);
        saveBtn.addActionListener(e -> savePng(card, capture.npcName));

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
        btnRow.setAlignmentX(Component.CENTER_ALIGNMENT);

        content.add(card);
        content.add(Box.createVerticalStrut(8));
        content.add(idLabel);
        content.add(Box.createVerticalStrut(10));
        content.add(btnRow);

        setContentPane(content);
        pack();
        setLocationRelativeTo(owner);
        setVisible(true);
    }

    // -------------------------------------------------------------------------

    private static BufferedImage renderCard(AlbumCard card) {
        int scale = 3;
        card.setSize(AlbumCard.CARD_W, AlbumCard.CARD_H);
        BufferedImage img = new BufferedImage(
                AlbumCard.CARD_W * scale, AlbumCard.CARD_H * scale,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,     RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.scale(scale, scale);
        card.print(g2);
        g2.dispose();
        return img;
    }

    private void copyToClipboard(AlbumCard card) {
        BufferedImage img = renderCard(card);
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

    private void savePng(AlbumCard card, String npcName) {
        JFileChooser chooser = new JFileChooser();
        String fileName = "bestiary_" + npcName.toLowerCase().replace(" ", "_") + ".png";
        chooser.setSelectedFile(new File(System.getProperty("user.home"), fileName));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                BufferedImage img = renderCard(card);
                ImageIO.write(img, "PNG", chooser.getSelectedFile());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to save: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
