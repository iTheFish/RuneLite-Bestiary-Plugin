package net.runelite.client.plugins.bestiary.ui;

import net.runelite.client.plugins.bestiary.model.BestiaryCollection;
import net.runelite.client.plugins.bestiary.model.CapturedCreature;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Preview dialog for page grid export. Shows a scaled preview of the rendered
 * grid with column count selector and Copy / Save PNG actions.
 */
public class PageExportDialog extends JDialog {

    private static final int GAP      = 6;
    private static final int PAD      = 8;
    private static final int SCALE    = 2;
    private static final int BANNER_H = 42;
    private static final int PREVIEW_MAX_W = 420;

    private final List<CapturedCreature> captures;
    private final String monsterName;
    private final Map<String, Integer> dexNumbers;
    private final WikiImageService imageService;
    private final BestiaryCollection collection;

    private int selectedCols;
    private final JLabel previewLabel;
    private final JScrollPane previewScroll;
    private JButton activeCopyBtn;

    public PageExportDialog(Window parent, List<CapturedCreature> captures, String monsterName,
                            Map<String, Integer> dexNumbers, WikiImageService imageService,
                            BestiaryCollection collection) {
        super(parent, "Export Page — " + monsterName, ModalityType.MODELESS);
        this.captures    = captures;
        this.monsterName = monsterName;
        this.dexNumbers  = dexNumbers;
        this.imageService = imageService;
        this.collection  = collection;

        int count = captures.size();
        this.selectedCols = defaultCols(count);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        // Preview label inside a scroll pane
        previewLabel = new JLabel();
        previewLabel.setHorizontalAlignment(SwingConstants.CENTER);
        previewLabel.setBackground(new Color(18, 18, 18));
        previewLabel.setOpaque(true);
        previewScroll = new JScrollPane(previewLabel);
        previewScroll.setBorder(BorderFactory.createLineBorder(new Color(40, 40, 40)));
        previewScroll.getVerticalScrollBar().setUnitIncrement(16);

        // Column selector row
        JPanel colRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        colRow.setOpaque(false);
        int[] colOpts = colOptions(count);
        if (colOpts.length > 1) {
            JLabel colLabel = new JLabel("Cols:");
            colLabel.setFont(FontManager.getRunescapeSmallFont());
            colLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            colRow.add(colLabel);
            ButtonGroup bg = new ButtonGroup();
            for (int cols : colOpts) {
                JToggleButton btn = colBtn(cols, cols == selectedCols);
                bg.add(btn);
                colRow.add(btn);
            }
        }

        // Action buttons
        activeCopyBtn = new JButton("Copy");
        activeCopyBtn.setFont(FontManager.getRunescapeSmallFont());
        activeCopyBtn.setBackground(new Color(0, 80, 160));
        activeCopyBtn.setForeground(Color.WHITE);
        activeCopyBtn.setFocusPainted(false);
        activeCopyBtn.addActionListener(e -> copyGrid());

        JButton saveBtn = new JButton("Save PNG");
        saveBtn.setFont(FontManager.getRunescapeSmallFont());
        saveBtn.setBackground(new Color(0, 120, 40));
        saveBtn.setForeground(Color.WHITE);
        saveBtn.setFocusPainted(false);
        saveBtn.addActionListener(e -> saveGrid());

        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        actionRow.setOpaque(false);
        actionRow.add(activeCopyBtn);
        actionRow.add(saveBtn);

        JPanel bottomBar = new JPanel(new BorderLayout(8, 0));
        bottomBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        bottomBar.setBorder(new EmptyBorder(4, 4, 4, 4));
        bottomBar.add(colRow,    BorderLayout.WEST);
        bottomBar.add(actionRow, BorderLayout.EAST);

        JPanel content = new JPanel(new BorderLayout(0, 6));
        content.setBackground(ColorScheme.DARK_GRAY_COLOR);
        content.setBorder(new EmptyBorder(8, 8, 8, 8));
        content.add(previewScroll, BorderLayout.CENTER);
        content.add(bottomBar,     BorderLayout.SOUTH);

        // Fix the scroll pane size to the tallest possible layout (fewest cols = most rows)
        // so the dialog never resizes when the user changes column count.
        int minCols = colOpts.length > 0 ? colOpts[0] : selectedCols;
        BufferedImage tallest = renderGrid(minCols);
        double fixScale = Math.min(1.0, (double) PREVIEW_MAX_W / tallest.getWidth());
        int fixedW = (int) (tallest.getWidth()  * fixScale);
        int fixedH = Math.min((int) (tallest.getHeight() * fixScale), 600);
        previewScroll.setPreferredSize(new Dimension(fixedW + 4, fixedH + 4));

        setContentPane(content);
        updatePreview();
        pack();
        setLocationRelativeTo(parent);
        setVisible(true);
    }

    private JToggleButton colBtn(int cols, boolean selected) {
        JToggleButton btn = new JToggleButton(cols + " cols");
        btn.setFont(FontManager.getRunescapeSmallFont());
        btn.setSelected(selected);
        btn.setFocusPainted(false);
        btn.addActionListener(e -> { selectedCols = cols; updatePreview(); });
        return btn;
    }

    private void updatePreview() {
        BufferedImage grid = renderGrid(selectedCols);
        double scale = Math.min(1.0, (double) PREVIEW_MAX_W / grid.getWidth());
        int pw = (int) (grid.getWidth()  * scale);
        int ph = (int) (grid.getHeight() * scale);
        Image scaled = grid.getScaledInstance(pw, ph, Image.SCALE_SMOOTH);
        previewLabel.setIcon(new ImageIcon(scaled));
        previewLabel.setPreferredSize(new Dimension(pw, ph));
        previewLabel.revalidate();
        previewLabel.repaint();
    }

    private void copyGrid() {
        BufferedImage img = renderGrid(selectedCols);
        Transferable t = new Transferable() {
            @Override public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{DataFlavor.imageFlavor}; }
            @Override public boolean isDataFlavorSupported(DataFlavor f) { return DataFlavor.imageFlavor.equals(f); }
            @Override public Object getTransferData(DataFlavor f) throws UnsupportedFlavorException {
                if (!DataFlavor.imageFlavor.equals(f)) throw new UnsupportedFlavorException(f);
                return img;
            }
        };
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(t, null);
        flashButton(activeCopyBtn, "Copied!");
    }

    private void saveGrid() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File(monsterName.replaceAll("[^a-zA-Z0-9_\\- ]", "") + "_export.png"));
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PNG image", "png"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File out = fc.getSelectedFile();
        if (!out.getName().toLowerCase().endsWith(".png")) out = new File(out.getPath() + ".png");
        try {
            ImageIO.write(renderGrid(selectedCols), "PNG", out);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Save failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void flashButton(JButton btn, String msg) {
        String prev = btn.getText();
        btn.setText(msg);
        btn.setForeground(new Color(120, 200, 120));
        javax.swing.Timer t = new javax.swing.Timer(2000, e -> {
            btn.setText(prev);
            btn.setForeground(Color.WHITE);
        });
        t.setRepeats(false);
        t.start();
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    private BufferedImage renderGrid(int cols) {
        int count    = captures.size();
        int rows     = (count + cols - 1) / cols;
        final int SLOT_H   = AlbumCard.CARD_H + 2 + BANNER_H;
        final int HEADER_H = 34;

        String playerName = captures.stream()
                .map(c -> c.playerName).filter(n -> n != null && !n.isEmpty())
                .findFirst().orElse("Unknown");
        String headerText = monsterName + " — " + count + " card" + (count == 1 ? "" : "s");

        int logW = cols * AlbumCard.CARD_W + (cols - 1) * GAP + PAD * 2;
        int logH = HEADER_H + PAD + rows * SLOT_H + (rows - 1) * GAP + PAD;

        BufferedImage img = new BufferedImage(logW * SCALE, logH * SCALE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,     RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);
        g2.scale(SCALE, SCALE);

        // Background + header
        g2.setColor(new Color(18, 18, 18));
        g2.fillRect(0, 0, logW, logH);
        g2.setColor(new Color(28, 28, 28));
        g2.fillRect(0, 0, logW, HEADER_H);
        g2.setFont(FontManager.getRunescapeBoldFont());
        FontMetrics hfm = g2.getFontMetrics();
        g2.setColor(captures.isEmpty() ? Color.WHITE : captures.get(0).rarity.displayColor);
        g2.drawString(headerText, (logW - hfm.stringWidth(headerText)) / 2,
                (HEADER_H + hfm.getAscent() - hfm.getDescent()) / 2);

        for (int i = 0; i < count; i++) {
            CapturedCreature cap = captures.get(i);
            int dex = dexNumbers.getOrDefault(cap.npcName, 0);
            AlbumCard card = new AlbumCard(dex, cap.npcName, List.of(cap), collection, imageService);
            card.setShowQuality(true);
            card.setSize(AlbumCard.CARD_W, AlbumCard.CARD_H);

            int col = i % cols;
            int row = i / cols;
            int x   = PAD + col * (AlbumCard.CARD_W + GAP);
            int y   = HEADER_H + PAD + row * (SLOT_H + GAP);

            Graphics2D cardG2 = (Graphics2D) g2.create();
            cardG2.translate(x, y);
            card.print(cardG2);
            cardG2.dispose();

            int bY = y + AlbumCard.CARD_H + 2;
            g2.setColor(new Color(25, 25, 25));
            g2.fillRoundRect(x, bY, AlbumCard.CARD_W, BANNER_H, 4, 4);

            String capBy = (cap.playerName != null && !cap.playerName.isEmpty()) ? cap.playerName : playerName;
            String cardId = CardId.encode(dex, cap);
            CardExportDialog.drawBanner(g2, x, bY, AlbumCard.CARD_W, BANNER_H, cardId, "Captured by " + capBy);
        }

        g2.dispose();
        return img;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static int defaultCols(int count) {
        if (count <= 1) return 1;
        if (count <= 4) return 2;
        return 4;
    }

    private static int[] colOptions(int count) {
        if (count <= 1) return new int[]{1};
        List<Integer> opts = new ArrayList<>();
        if (count >= 2) opts.add(2);
        if (count >= 3) opts.add(3);
        if (count >= 4) opts.add(4);
        return opts.stream().mapToInt(i -> i).toArray();
    }
}
