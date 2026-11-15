package com.bestiary.util;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;

import javax.imageio.ImageIO;

/**
 * The Bestiary panel / Plugin Hub icon: the golden "B" crest, bundled as a resource
 * ({@code /com/bestiary/logo.png}) and scaled on demand. This is the single source of truth for
 * both the in-client sidebar button ({@link #render}) and the repo-root {@code icon.png} the
 * Plugin Hub lists. After replacing the bundled PNG, regenerate {@code icon.png} with the dev-only
 * {@code PanelIconExport} tool in {@code src/test} (kept out of the plugin jar).
 */
public final class PanelIcon {

    /** Bundled logo art, loaded once. */
    private static final String RESOURCE = "/com/bestiary/logo.png";
    private static BufferedImage source;

    private PanelIcon() {}

    private static synchronized BufferedImage source() {
        if (source == null) {
            try (InputStream in = PanelIcon.class.getResourceAsStream(RESOURCE)) {
                if (in != null) {
                    source = ImageIO.read(in);
                }
            } catch (Exception e) {
                // Silent: render() falls back to a drawn placeholder if the resource can't be loaded.
                source = null;
            }
        }
        return source;
    }

    /** Renders the logo at {@code s}×{@code s} pixels (ARGB), smooth-scaled from the bundled art. */
    public static BufferedImage render(int s) {
        BufferedImage icon = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = icon.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        BufferedImage src = source();
        if (src != null) {
            g.drawImage(src, 0, 0, s, s, null);
        } else {
            // Fallback if the resource is missing: a dark tile with a gold "B" so the button isn't blank.
            g.setColor(new Color(13, 22, 38));
            g.fillRect(0, 0, s, s);
            g.setColor(new Color(240, 190, 40));
            g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, Math.round(s * 0.7f)));
            java.awt.FontMetrics fm = g.getFontMetrics();
            g.drawString("B", (s - fm.stringWidth("B")) / 2f,
                    (s - fm.getHeight()) / 2f + fm.getAscent());
        }

        g.dispose();
        return icon;
    }
}
