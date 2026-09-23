package com.bestiary.util;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;

import javax.imageio.ImageIO;

/**
 * The Bestiary panel / Plugin Hub icon: the golden "B" crest, bundled as a resource
 * ({@code /com/bestiary/logo.png}) and scaled on demand. This is the single source of truth for
 * both the in-client sidebar button ({@link #render}) and the repo-root {@code icon.png} the
 * Plugin Hub lists ({@link #main}). Replace the bundled PNG and regenerate the file — see {@link #main}.
 *
 * <p>Intentionally free of external dependencies (no logging framework) so {@link #main} can be run
 * standalone from the compiled classes without a runtime classpath.
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
                System.err.println("Failed to load bundled logo " + RESOURCE + ": " + e);
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

    /**
     * Writes the icon to a PNG so the repo-root {@code icon.png} (the Plugin Hub listing image)
     * always matches the in-client sidebar icon. Run after replacing the bundled logo:
     * {@code java -cp build/classes/java/main;build/resources/main com.bestiary.util.PanelIcon icon.png}
     */
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        String out = args.length > 0 ? args[0] : "icon.png";
        // The Plugin Hub caps the listing icon at 48x72px, so render at 48. The in-client sidebar
        // button doesn't use this file — it calls render(s) at the nav-button size directly.
        ImageIO.write(render(48), "png", new File(out));
        System.out.println("Wrote " + out);
    }
}
