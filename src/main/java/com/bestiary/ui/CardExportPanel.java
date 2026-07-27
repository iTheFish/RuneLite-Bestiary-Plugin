package com.bestiary.ui;

import com.bestiary.model.BestiaryCollection;
import com.bestiary.model.CapturedCreature;
import com.bestiary.model.MonsterRoster;
import com.bestiary.service.WikiImageService;
import com.bestiary.util.CardId;
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
 * Reusable export view for a single capture: a 2× scaled card + banner preview with
 * Copy Image / Save PNG buttons and a right-click menu (copy / favourite / album cover /
 * rename / save). Embedded as the first tab of {@link CardDataDialog}; the shared image
 * service and collection are pulled from {@link CardExportDialog}'s static wiring.
 */
public class CardExportPanel extends JPanel {

    // Preview scale is < 2 so the card + banner + Copy/Save buttons all fit the card window
    // without scrolling. The exported PNG/clipboard image still renders at 3× (see renderCard).
    private static final double PREVIEW_SCALE = 1.7;
    private static final int BOTTOM_H = 42;

    private final AlbumCard card;
    private final String cardId;
    private final String owner;
    private final String npcName;
    private final int rerollCount;

    public CardExportPanel(CapturedCreature capture) {
        WikiImageService imageService = CardExportDialog.sharedImageService();
        java.util.function.Supplier<BestiaryCollection> collectionSupplier = CardExportDialog.collectionSupplier();
        int dexNumber = MonsterRoster.getDexNumber(capture.npcName);

        String capturedBy = capture.playerName != null && !capture.playerName.isEmpty()
                ? capture.playerName : "Unknown";
        this.npcName = capture.npcName;
        this.card    = new AlbumCard(dexNumber, capture.npcName, List.of(capture),
                collectionSupplier.get(), imageService);
        this.card.setShowQuality(true);
        this.cardId = CardId.encode(dexNumber, capture);
        this.owner  = capturedBy;
        this.rerollCount = capture.rerollCount();

        // Re-query the live collection at click time to avoid a stale snapshot.
        card.setClickOverride(() -> {
            BestiaryCollection live = collectionSupplier.get();
            List<CapturedCreature> fresh = live.creatures.stream()
                    .filter(c -> c.npcName.equals(capture.npcName))
                    .collect(Collectors.toList());
            List<CapturedCreature> list = fresh.isEmpty() ? List.of(capture) : fresh;
            new CreatureDetailDialog(window(), list, live, "By Rarity", capture.rarity).setVisible(true);
        });

        JPanel previewPanel = new JPanel() {
            { setPreferredSize(new Dimension((int) (AlbumCard.CARD_W * PREVIEW_SCALE),
                                             (int) ((AlbumCard.CARD_H + BOTTOM_H) * PREVIEW_SCALE)));
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
                String ownerStr = "Captured by " + owner;
                CardExportDialog.drawBanner(g2, 0, AlbumCard.CARD_H, AlbumCard.CARD_W, BOTTOM_H,
                        cardId, ownerStr, capture.rerollCount());
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
                Runnable onMutate = CardExportDialog.onMutate();
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
                save.addActionListener(ev -> savePng());
                menu.add(save);

                menu.show(previewPanel, e.getX(), e.getY());
            }
        });

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
        saveBtn.addActionListener(e -> { if (savePng()) flash(saveBtn, "✓ Saved!"); });

        JPanel btnRow = new JPanel(new GridLayout(1, 2, 6, 0));
        btnRow.setOpaque(false);
        btnRow.setBorder(new EmptyBorder(8, 0, 0, 0));
        btnRow.add(copyBtn);
        btnRow.add(saveBtn);

        // Preview centred in a viewport-width holder so it stays centred (and scrolls if the
        // window is short); the Copy/Save row is pinned to the bottom of the tab.
        JPanel centre = new JPanel(new GridBagLayout()) {
            @Override public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                if (getParent() != null) d.width = getParent().getWidth();
                return d;
            }
        };
        centre.setOpaque(false);
        centre.add(previewPanel, new GridBagConstraints());
        JScrollPane previewScroll = new JScrollPane(centre,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        previewScroll.setBorder(null);
        previewScroll.setOpaque(false);
        previewScroll.getViewport().setOpaque(false);
        previewScroll.getVerticalScrollBar().setUnitIncrement(16);

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(new EmptyBorder(10, 12, 10, 12));
        add(previewScroll, BorderLayout.CENTER);
        add(btnRow, BorderLayout.SOUTH);
    }

    /** Free the card's shimmer registration when this tab/panel is torn down. */
    @Override
    public void removeNotify() {
        super.removeNotify();
        card.removeNotify();
    }

    private Window window() {
        return SwingUtilities.getWindowAncestor(this);
    }

    private BufferedImage renderCard() {
        int scale   = 3;
        card.setSize(AlbumCard.CARD_W, AlbumCard.CARD_H);
        BufferedImage img = new BufferedImage(AlbumCard.CARD_W * scale,
                (AlbumCard.CARD_H + BOTTOM_H) * scale, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,     RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.scale(scale, scale);
        card.print(g2);
        g2.setColor(new Color(12, 12, 12));
        g2.fillRect(0, AlbumCard.CARD_H, AlbumCard.CARD_W, BOTTOM_H);
        String ownerStr = "Captured by " + owner;
        CardExportDialog.drawBanner(g2, 0, AlbumCard.CARD_H, AlbumCard.CARD_W, BOTTOM_H,
                cardId, ownerStr, rerollCount);
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
        java.util.function.Consumer<String> onCopy = CardExportDialog.onCopyAction();
        if (onCopy != null) {
            onCopy.accept("Card exported to clipboard for " + npcName + " — ID: " + cardId);
        }
    }

    private boolean savePng() {
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
