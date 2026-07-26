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
 * Exported image includes a bottom owner banner and the card ID.
 */
public class CardExportDialog extends JDialog {

    private static WikiImageService sharedImageService;
    private static Supplier<BestiaryCollection> collectionSupplier;
    private static Consumer<String> onCopyAction;
    private static Runnable onMutate;   // persist + refresh after favourite / album-cover changes
    private static CardExportDialog openInstance;

    /** Wire a callback that persists and refreshes the panel after a right-click mutation. */
    public static void setOnMutate(Runnable callback) {
        onMutate = callback;
    }

    /** Close any open CardExportDialog — called when Album opens. */
    public static void disposeOpen() {
        if (openInstance != null && openInstance.isDisplayable()) openInstance.dispose();
        openInstance = null;
    }

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

    /** Copy a capture card directly to clipboard without opening the export dialog. */
    public static void copyNow(Window owner, CapturedCreature capture) {
        if (sharedImageService == null || collectionSupplier == null) return;
        int dex = MonsterRoster.getDexNumber(capture.npcName);
        String capturedBy = capture.playerName != null && !capture.playerName.isEmpty()
                ? capture.playerName : "Unknown";
        String cardId = CardId.encode(dex, capture);
        AlbumCard card = new AlbumCard(dex, capture.npcName, List.of(capture),
                collectionSupplier.get(), sharedImageService);
        card.setShowQuality(true);
        card.setSize(AlbumCard.CARD_W, AlbumCard.CARD_H);

        int scale   = 3;
        int bottomH = 42;
        BufferedImage img = new BufferedImage(AlbumCard.CARD_W * scale,
                (AlbumCard.CARD_H + bottomH) * scale, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,     RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.scale(scale, scale);
        card.print(g2);
        g2.setColor(new Color(12, 12, 12));
        g2.fillRect(0, AlbumCard.CARD_H, AlbumCard.CARD_W, bottomH);
        String ownerStr = "Captured by " + capturedBy;
        drawBanner(g2, 0, AlbumCard.CARD_H, AlbumCard.CARD_W, bottomH, cardId, ownerStr, capture.rerolledBy);
        g2.dispose();
        card.removeNotify();

        BufferedImage exported = img;
        Transferable t = new Transferable() {
            @Override public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{DataFlavor.imageFlavor}; }
            @Override public boolean isDataFlavorSupported(DataFlavor f) { return DataFlavor.imageFlavor.equals(f); }
            @Override public Object getTransferData(DataFlavor f) throws UnsupportedFlavorException {
                if (!DataFlavor.imageFlavor.equals(f)) throw new UnsupportedFlavorException(f);
                return exported;
            }
        };
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(t, null);
        if (onCopyAction != null) {
            onCopyAction.accept("Card copied: " + capture.npcName + " — " + cardId);
        }
    }

    // -------------------------------------------------------------------------

    private final AlbumCard card;
    private final String cardId;
    private final String owner;
    private final String npcName;
    private final String rerolledBy;

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
        this.card.setShowQuality(true);
        this.cardId = CardId.encode(dexNumber, capture);
        this.owner  = capturedBy;
        this.rerolledBy = capture.rerolledBy;

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
        final int BOTTOM_H = 42;
        JPanel previewPanel = new JPanel() {
            { setPreferredSize(new Dimension(AlbumCard.CARD_W * PREVIEW_SCALE,
                                             (AlbumCard.CARD_H + BOTTOM_H) * PREVIEW_SCALE));
              setOpaque(false); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.scale(PREVIEW_SCALE, PREVIEW_SCALE);
                card.setSize(AlbumCard.CARD_W, AlbumCard.CARD_H);
                card.print(g2);
                g2.setColor(new Color(12, 12, 12));
                g2.fillRect(0, AlbumCard.CARD_H, AlbumCard.CARD_W, BOTTOM_H);
                String ownerStr = "Captured by " + CardExportDialog.this.owner;
                drawBanner(g2, 0, AlbumCard.CARD_H, AlbumCard.CARD_W, BOTTOM_H, cardId, ownerStr,
                        capture.rerolledBy);
                g2.dispose();
            }
        };
        imageService.requestImage(npcName, previewPanel::repaint);
        card.setShimmerCallback(previewPanel::repaint);
        previewPanel.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        previewPanel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(java.awt.event.MouseEvent e) { card.hoverStart(); }
            @Override public void mouseExited(java.awt.event.MouseEvent  e) { card.hoverStop();  }
            @Override public void mousePressed(java.awt.event.MouseEvent e)  { popup(e); }
            @Override public void mouseReleased(java.awt.event.MouseEvent e) { popup(e); }
            private void popup(java.awt.event.MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                BestiaryCollection live = collectionSupplier.get();
                JPopupMenu menu = new JPopupMenu();

                JMenuItem copy = new JMenuItem("Copy Card");
                copy.addActionListener(ev -> copyToClipboard());
                menu.add(copy);
                menu.addSeparator();

                JMenuItem fav = new JMenuItem(capture.favourite ? "✩ Remove Favourite" : "★ Favourite");
                fav.addActionListener(ev -> {
                    if (!capture.favourite && live.countFavourites() >= 20) return;
                    capture.favourite = !capture.favourite;
                    previewPanel.repaint();
                    if (onMutate != null) onMutate.run();
                });
                menu.add(fav);

                JMenuItem cover = new JMenuItem(capture.albumCover ? "Remove album cover" : "Set as album cover");
                cover.addActionListener(ev -> {
                    if (capture.albumCover) capture.albumCover = false;
                    else live.setAlbumCover(capture);
                    previewPanel.repaint();
                    if (onMutate != null) onMutate.run();
                });
                menu.add(cover);
                menu.addSeparator();

                String nickLabel = (capture.nickname != null && !capture.nickname.isEmpty())
                        ? "Rename…" : "Name capture…";
                JMenuItem name = new JMenuItem(nickLabel);
                name.addActionListener(ev -> AlbumCard.openNicknameDialog(previewPanel, capture, () -> {
                    previewPanel.repaint();
                    if (onMutate != null) onMutate.run();
                }));
                menu.add(name);

                JMenuItem save = new JMenuItem("Save PNG…");
                save.addActionListener(ev -> savePng(capture.npcName));
                menu.add(save);

                menu.show(previewPanel, e.getX(), e.getY());
            }
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

        JButton oddsBtn = new JButton("What were the odds?");
        oddsBtn.setFont(FontManager.getRunescapeSmallFont());
        oddsBtn.setBackground(new Color(60, 90, 150));
        oddsBtn.setForeground(Color.WHITE);
        oddsBtn.setFocusPainted(false);
        oddsBtn.addActionListener(e -> OddsDialog.open(this, capture));

        JPanel btnRow = new JPanel(new GridLayout(1, 2, 6, 0));
        btnRow.setOpaque(false);
        btnRow.add(copyBtn);
        btnRow.add(saveBtn);

        // Odds gets its own full-width row below the card
        JPanel oddsRow = new JPanel(new GridLayout(1, 1));
        oddsRow.setOpaque(false);
        oddsRow.add(oddsBtn);

        // Layout
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ColorScheme.DARK_GRAY_COLOR);
        content.setBorder(new EmptyBorder(12, 12, 12, 12));

        previewPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        oddsRow.setAlignmentX(Component.CENTER_ALIGNMENT);

        content.add(previewPanel);
        content.add(Box.createVerticalStrut(10));
        content.add(btnRow);
        content.add(Box.createVerticalStrut(6));
        content.add(oddsRow);

        // Track open instance; clean up shimmer registration on close
        if (openInstance != null && openInstance.isDisplayable()) openInstance.dispose();
        openInstance = this;
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent e) {
                card.removeNotify();
                if (openInstance == CardExportDialog.this) openInstance = null;
            }
        });

        setContentPane(content);
        pack();
        setLocationRelativeTo(owner);
        setVisible(true);
    }

    // -------------------------------------------------------------------------

    private BufferedImage renderCard() {
        int scale   = 3;
        int bottomH = 42;

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

        card.print(g2);

        g2.setColor(new Color(12, 12, 12));
        g2.fillRect(0, AlbumCard.CARD_H, AlbumCard.CARD_W, bottomH);
        String ownerStr = "Captured by " + owner;
        drawBanner(g2, 0, AlbumCard.CARD_H, AlbumCard.CARD_W, bottomH, cardId, ownerStr, rerolledBy);

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

    static void drawBanner(Graphics2D g2, int bX, int bY, int bannerW, int bannerH, String cardId, String ownerStr) {
        drawBanner(g2, bX, bY, bannerW, bannerH, cardId, ownerStr, "");
    }

    /** Draws the banner: UniqueID + Captured by (+ optional Rerolled by) centred, brand pinned bottom. */
    static void drawBanner(Graphics2D g2, int bX, int bY, int bannerW, int bannerH,
                           String cardId, String ownerStr, String rerolledBy) {
        // Shrink the ID font if the (now longer) ID would overflow the banner width.
        float idSize = 7f;
        while (idSize > 4f && FontManager.getRunescapeSmallFont().deriveFont(idSize)
                .getStringBounds(cardId, g2.getFontRenderContext()).getWidth() > bannerW - 6) {
            idSize -= 0.25f;
        }
        Font idFont     = FontManager.getRunescapeSmallFont().deriveFont(idSize);
        Font playerFont = FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD, 10f);
        Font brandFont  = FontManager.getRunescapeSmallFont().deriveFont(8f);
        g2.setFont(idFont);     FontMetrics ifm = g2.getFontMetrics();
        g2.setFont(playerFont); FontMetrics pfm = g2.getFontMetrics();
        g2.setFont(brandFont);  FontMetrics bfm = g2.getFontMetrics();
        // OSRS | BESTIARY pinned 2px from bottom
        String brand = "OSRS | BESTIARY";
        int brandY = bY + bannerH - bfm.getDescent() - 2;
        g2.setFont(brandFont);
        g2.setColor(new Color(110, 110, 110));
        g2.drawString(brand, bX + (bannerW - bfm.stringWidth(brand)) / 2, brandY);
        // UniqueID + Captured by (+ optional Rerolled by) centred above the brand line
        boolean rerolled = rerolledBy != null && !rerolledBy.isEmpty();
        Font reFont = FontManager.getRunescapeSmallFont().deriveFont(Font.ITALIC, 8f);
        g2.setFont(reFont); FontMetrics rfm = g2.getFontMetrics();
        int gap    = 2;
        int upperH = bannerH - bfm.getHeight() - 2;
        int blockH = ifm.getHeight() + gap + pfm.getHeight() + (rerolled ? gap + rfm.getHeight() : 0);
        int startY = bY + Math.max(2, (upperH - blockH) / 2);
        g2.setFont(idFont);
        g2.setColor(new Color(90, 90, 90));
        g2.drawString(cardId, bX + (bannerW - ifm.stringWidth(cardId)) / 2, startY + ifm.getAscent());
        g2.setFont(playerFont);
        g2.setColor(new Color(200, 155, 50));
        int y2 = startY + ifm.getHeight() + gap;
        g2.drawString(ownerStr, bX + (bannerW - pfm.stringWidth(ownerStr)) / 2, y2 + pfm.getAscent());
        if (rerolled) {
            String reStr = "Rerolled by " + rerolledBy;
            g2.setFont(reFont);
            g2.setColor(new Color(150, 120, 200));
            int y3 = y2 + pfm.getHeight() + gap;
            g2.drawString(reStr, bX + (bannerW - rfm.stringWidth(reStr)) / 2, y3 + rfm.getAscent());
        }
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
