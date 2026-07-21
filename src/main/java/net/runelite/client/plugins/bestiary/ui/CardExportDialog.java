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
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Modal dialog showing a single capture as an album card, with options to
 * copy the card image to clipboard or save it as a PNG.
 * Exported image includes a bottom owner banner and the 28-char card ID.
 */
public class CardExportDialog extends JDialog {

    private static WikiImageService sharedImageService;
    private static Supplier<BestiaryCollection> collectionSupplier;
    private static Consumer<String> onCopyAction;

    public static void setShared(WikiImageService imgSvc, Supplier<BestiaryCollection> supplier) {
        sharedImageService = imgSvc;
        collectionSupplier = supplier;
    }

    public static void setOnCopy(Consumer<String> callback) {
        onCopyAction = callback;
    }

    /** Open export dialog for a single capture (uses shared service/collection). */
    public static void open(Window owner, CapturedCreature capture) {
        if (sharedImageService == null || collectionSupplier == null) return;
        int dex = MonsterRoster.getDexNumber(capture.npcName);
        new CardExportDialog(owner, capture, collectionSupplier.get(), sharedImageService, dex);
    }

    // -------------------------------------------------------------------------

    private final AlbumCard card;
    private final String cardId;
    private final String owner;
    private final String npcName;

    public CardExportDialog(Window owner, CapturedCreature capture,
                            BestiaryCollection collection,
                            WikiImageService imageService,
                            int dexNumber) {
        super(owner, "Export Card — " + capture.npcName, ModalityType.MODELESS);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        String capturedBy = capture.playerName != null && !capture.playerName.isEmpty()
                ? capture.playerName : "Unknown";
        this.npcName = capture.npcName;
        this.card    = new AlbumCard(dexNumber, capture.npcName, List.of(capture), collection, imageService);
        this.cardId = CardId.encode(dexNumber, capture);
        this.owner  = capturedBy;

        // Re-query the live collection at click time to avoid stale snapshot
        card.setClickOverride(() -> {
            BestiaryCollection live = collectionSupplier.get();
            List<CapturedCreature> fresh = live.creatures.stream()
                    .filter(c -> c.npcName.equals(capture.npcName))
                    .collect(Collectors.toList());
            List<CapturedCreature> list = fresh.isEmpty() ? List.of(capture) : fresh;
            new CreatureDetailDialog(CardExportDialog.this, list, live,
                    "By Rarity", capture.rarity).setVisible(true);
        });

        // 2× scaled preview panel — same content (card + banner) as the actual export
        final int PREVIEW_SCALE = 2;
        JPanel previewPanel = new JPanel() {
            { setPreferredSize(new Dimension(AlbumCard.CARD_W * PREVIEW_SCALE,
                                             (AlbumCard.CARD_H + 28) * PREVIEW_SCALE));
              setOpaque(false); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.scale(PREVIEW_SCALE, PREVIEW_SCALE);
                card.setSize(AlbumCard.CARD_W, AlbumCard.CARD_H);
                card.print(g2);
                // Banner (mirrors renderCard)
                g2.setColor(new Color(12, 12, 12));
                g2.fillRect(0, AlbumCard.CARD_H, AlbumCard.CARD_W, 28);
                g2.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 6));
                FontMetrics idFm = g2.getFontMetrics();
                g2.setColor(new Color(90, 90, 90));
                g2.drawString(cardId, (AlbumCard.CARD_W - idFm.stringWidth(cardId)) / 2, AlbumCard.CARD_H + 9);
                g2.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD, 8f));
                FontMetrics ownerFm = g2.getFontMetrics();
                g2.setColor(new Color(200, 155, 50));
                String ownerStr = "Captured by " + CardExportDialog.this.owner;
                g2.drawString(ownerStr, (AlbumCard.CARD_W - ownerFm.stringWidth(ownerStr)) / 2, AlbumCard.CARD_H + 22);
                g2.dispose();
            }
        };
        imageService.requestImage(npcName, previewPanel::repaint);
        card.setShimmerCallback(previewPanel::repaint);
        previewPanel.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        previewPanel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(java.awt.event.MouseEvent e) { card.hoverStart(); }
            @Override public void mouseExited(java.awt.event.MouseEvent  e) { card.hoverStop();  }
        });

        // Buttons
        JButton copyBtn = new JButton("Copy Image");
        copyBtn.setFont(FontManager.getRunescapeSmallFont());
        copyBtn.setBackground(new Color(255, 153, 0));
        copyBtn.setForeground(Color.BLACK);
        copyBtn.setFocusPainted(false);
        copyBtn.addActionListener(e -> { copyToClipboard(); flash(copyBtn, "✓ Copied!"); });

        JButton saveBtn = new JButton("Save PNG…");
        saveBtn.setFont(FontManager.getRunescapeSmallFont());
        saveBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        saveBtn.setForeground(Color.WHITE);
        saveBtn.setFocusPainted(false);
        saveBtn.addActionListener(e -> { if (savePng(capture.npcName)) flash(saveBtn, "✓ Saved!"); });

        JPanel btnRow = new JPanel(new GridLayout(1, 2, 6, 0));
        btnRow.setOpaque(false);
        btnRow.add(copyBtn);
        btnRow.add(saveBtn);

        // Layout
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ColorScheme.DARK_GRAY_COLOR);
        content.setBorder(new EmptyBorder(12, 12, 12, 12));

        previewPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnRow.setAlignmentX(Component.CENTER_ALIGNMENT);

        content.add(previewPanel);
        content.add(Box.createVerticalStrut(10));
        content.add(btnRow);

        // Clean up card's shimmer registration when dialog closes
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent e) { card.removeNotify(); }
        });

        setContentPane(content);
        pack();
        setLocationRelativeTo(owner);
        setVisible(true);
    }

    // -------------------------------------------------------------------------

    private BufferedImage renderCard() {
        int scale   = 3;
        int bottomH = 28; // ID + captured-by banner below card

        card.setSize(AlbumCard.CARD_W, AlbumCard.CARD_H);

        BufferedImage img = new BufferedImage(
                AlbumCard.CARD_W * scale,
                (AlbumCard.CARD_H + bottomH) * scale,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,     RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.scale(scale, scale);

        // Card (nickname is rendered on the card header by AlbumCard.paintComponent)
        card.print(g2);

        // Bottom banner: card ID + captured-by
        g2.setColor(new Color(12, 12, 12));
        g2.fillRect(0, AlbumCard.CARD_H, AlbumCard.CARD_W, bottomH);

        g2.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 6));
        FontMetrics idFm = g2.getFontMetrics();
        g2.setColor(new Color(90, 90, 90));
        g2.drawString(cardId,
                (AlbumCard.CARD_W - idFm.stringWidth(cardId)) / 2,
                AlbumCard.CARD_H + 9);

        g2.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD, 8f));
        FontMetrics ownerFm = g2.getFontMetrics();
        g2.setColor(new Color(200, 155, 50));
        String ownerStr = "Captured by " + owner;
        g2.drawString(ownerStr,
                (AlbumCard.CARD_W - ownerFm.stringWidth(ownerStr)) / 2,
                AlbumCard.CARD_H + 22);

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
        if (onCopyAction != null) {
            onCopyAction.accept("Card exported to clipboard for " + npcName + " — ID: " + cardId);
        }
    }

    private boolean savePng(String npcName) {
        JFileChooser chooser = new JFileChooser();
        String fileName = "bestiary_" + npcName.toLowerCase().replace(" ", "_") + ".png";
        chooser.setSelectedFile(new File(System.getProperty("user.home"), fileName));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                ImageIO.write(renderCard(), "PNG", chooser.getSelectedFile());
                return true;
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to save: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
        return false;
    }

    private static void flash(JButton btn, String label) {
        String orig = btn.getText();
        btn.setText(label);
        btn.setEnabled(false);
        new javax.swing.Timer(1500, e -> {
            btn.setText(orig);
            btn.setEnabled(true);
            ((javax.swing.Timer) e.getSource()).stop();
        }).start();
    }
}
