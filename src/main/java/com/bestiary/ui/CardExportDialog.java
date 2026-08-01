package com.bestiary.ui;

import com.bestiary.model.BestiaryCollection;
import com.bestiary.model.CapturedCreature;
import com.bestiary.model.MonsterRoster;
import com.bestiary.service.WikiImageService;
import com.bestiary.util.CardId;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Card export helpers. The interactive export view now lives as the first tab of
 * {@link CardDataDialog} (see {@link CardExportPanel}); {@link #open} redirects there so every
 * existing "Export Card" menu item and card left-click lands on that tab. This class still holds
 * the shared wiring (image service / collection / callbacks), the direct-copy path
 * ({@link #copyNow}) and the shared banner renderer ({@link #drawBanner}).
 */
public final class CardExportDialog {

    private static WikiImageService sharedImageService;
    private static Supplier<BestiaryCollection> collectionSupplier;
    private static Consumer<String> onCopyAction;
    private static Runnable onMutate;   // persist + refresh after favourite / album-cover changes

    private CardExportDialog() {}

    /** Wire a callback that persists and refreshes the panel after a right-click mutation. */
    public static void setOnMutate(Runnable callback) {
        onMutate = callback;
    }

    /** Close the open export view — now delegated to the Card data dialog that hosts it. */
    public static void disposeOpen() {
        CardDataDialog.disposeOpen();
    }

    public static void setShared(WikiImageService imgSvc, Supplier<BestiaryCollection> supplier) {
        sharedImageService = imgSvc;
        collectionSupplier = supplier;
    }

    public static void setOnCopy(Consumer<String> callback) {
        onCopyAction = callback;
    }

    // Package-private accessors so CardExportPanel can reuse the shared wiring.
    static WikiImageService sharedImageService()               { return sharedImageService; }
    static Supplier<BestiaryCollection> collectionSupplier()    { return collectionSupplier; }
    static Consumer<String> onCopyAction()                      { return onCopyAction; }
    static Runnable onMutate()                                  { return onMutate; }

    /** Open the export view for a single capture — the Card data dialog on its Export tab. */
    public static void open(Window owner, CapturedCreature capture) {
        if (sharedImageService == null || collectionSupplier == null) return;
        CardDataDialog.open(owner, capture, CardDataDialog.TAB_EXPORT);
    }

    /** Copy a capture card directly to clipboard without opening any view. */
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
        drawBanner(g2, 0, AlbumCard.CARD_H, AlbumCard.CARD_W, bottomH, cardId, ownerStr, capture.rerollCount(), capture.dev);
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

    static void drawBanner(Graphics2D g2, int bX, int bY, int bannerW, int bannerH, String cardId, String ownerStr) {
        drawBanner(g2, bX, bY, bannerW, bannerH, cardId, ownerStr, 0, false);
    }

    static void drawBanner(Graphics2D g2, int bX, int bY, int bannerW, int bannerH,
                           String cardId, String ownerStr, int rerollCount) {
        drawBanner(g2, bX, bY, bannerW, bannerH, cardId, ownerStr, rerollCount, false);
    }

    /** Draws the banner: UniqueID + Captured by (+ optional DEV / Rerolled tag) centred, brand pinned bottom. */
    static void drawBanner(Graphics2D g2, int bX, int bY, int bannerW, int bannerH,
                           String cardId, String ownerStr, int rerollCount, boolean dev) {
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
        // UniqueID + Captured by (+ optional DEV / Rerolled tag) centred above the brand line
        boolean rerolled = rerollCount > 0;
        String reStr;
        Color tagColor;
        String rerollStr = rerolled ? "Rerolled " + rerollCount + (rerollCount == 1 ? " time" : " times") : null;
        if (dev && rerolled)      { reStr = "DEV · " + rerollStr; tagColor = new Color(90, 180, 255); }
        else if (dev)             { reStr = "DEV";                tagColor = new Color(90, 180, 255); }
        else if (rerolled)        { reStr = rerollStr;           tagColor = new Color(150, 120, 200); }
        else                      { reStr = null;                tagColor = null; }
        boolean hasTag = reStr != null;
        Font reFont = FontManager.getRunescapeSmallFont().deriveFont(Font.ITALIC, 8f);
        g2.setFont(reFont); FontMetrics rfm = g2.getFontMetrics();
        int gap    = 2;
        int upperH = bannerH - bfm.getHeight() - 2;
        int blockH = ifm.getHeight() + gap + pfm.getHeight() + (hasTag ? gap + rfm.getHeight() : 0);
        int startY = bY + Math.max(2, (upperH - blockH) / 2);
        g2.setFont(idFont);
        g2.setColor(new Color(90, 90, 90));
        g2.drawString(cardId, bX + (bannerW - ifm.stringWidth(cardId)) / 2, startY + ifm.getAscent());
        g2.setFont(playerFont);
        g2.setColor(new Color(200, 155, 50));
        int y2 = startY + ifm.getHeight() + gap;
        g2.drawString(ownerStr, bX + (bannerW - pfm.stringWidth(ownerStr)) / 2, y2 + pfm.getAscent());
        if (hasTag) {
            g2.setFont(reFont);
            g2.setColor(tagColor);
            int y3 = y2 + pfm.getHeight() + gap;
            g2.drawString(reStr, bX + (bannerW - rfm.stringWidth(reStr)) / 2, y3 + rfm.getAscent());
        }
    }
}
