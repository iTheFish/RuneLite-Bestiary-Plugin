package com.bestiary.util;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

/**
 * The Bestiary panel / Plugin Hub icon: a Mythic-shiny collection jar (red essence, grey stopper,
 * shiny sparkle) with a white "B". Drawn purely with AWT so it doubles as the single source of
 * truth for both the in-client sidebar button ({@link #render}) and the repo-root {@code icon.png}
 * the Plugin Hub lists ({@link #main}). Regenerate the file after any tweak here — see {@link #main}.
 */
public final class PanelIcon {

    private PanelIcon() {}

    /** Renders the jar icon at {@code s}×{@code s} pixels (ARGB). */
    public static BufferedImage render(int s) {
        BufferedImage icon = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = icon.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        // Stopper / lid
        float sw = s * 0.44f, sh = s * 0.15f, sx = (s - sw) / 2f, sy = s * 0.05f;
        RoundRectangle2D stop = new RoundRectangle2D.Float(sx, sy, sw, sh, s * 0.05f, s * 0.05f);
        g.setColor(new Color(120, 120, 128));
        g.fill(stop);
        g.setColor(new Color(80, 80, 88));
        g.setStroke(new BasicStroke(s * 0.02f));
        g.draw(stop);

        // Jar body (glass) with Mythic-red essence filling most of it
        float bx = s * 0.13f, bw = s * 0.74f, by = s * 0.19f, bh = s * 0.76f, arc = s * 0.16f;
        RoundRectangle2D body = new RoundRectangle2D.Float(bx, by, bw, bh, arc, arc);
        Shape oldClip = g.getClip();
        g.setClip(body);
        float essTop = by + bh * 0.16f;
        g.setPaint(new GradientPaint(0, essTop, new Color(225, 55, 60), 0, by + bh, new Color(150, 16, 32)));
        g.fill(new Rectangle2D.Float(bx, essTop, bw, bh));
        g.setClip(oldClip);
        g.setColor(new Color(210, 230, 250, 32));   // glass sheen
        g.fill(body);
        g.setColor(new Color(200, 215, 235));
        g.setStroke(new BasicStroke(s * 0.05f));
        g.draw(body);

        // Letter B — large, white, soft shadow; top sits just under the liquid line
        float fontSize = bw * 0.95f;
        g.setFont(new Font("SansSerif", Font.BOLD, Math.round(fontSize)));
        FontMetrics fm = g.getFontMetrics();
        float cx = bx + bw / 2f, cy = by + bh * 0.51f;
        float tx = cx - fm.stringWidth("B") / 2f;
        float ty = cy + (fm.getAscent() - fm.getDescent()) / 2f;
        g.setColor(new Color(0, 0, 0, 140));
        g.drawString("B", tx + fontSize * 0.045f, ty + fontSize * 0.045f);
        g.setColor(new Color(252, 252, 252));
        g.drawString("B", tx, ty);

        // Shiny sparkle
        drawSparkle(g, s * 0.82f, s * 0.31f, s * 0.10f, new Color(255, 255, 238));

        g.dispose();
        return icon;
    }

    /** A 4-point sparkle centred at (cx,cy). */
    private static void drawSparkle(Graphics2D g, float cx, float cy, float r, Color c) {
        g.setColor(c);
        Path2D p = new Path2D.Float();
        float t = r * 0.34f;
        p.moveTo(cx, cy - r); p.lineTo(cx + t, cy - t); p.lineTo(cx + r, cy); p.lineTo(cx + t, cy + t);
        p.lineTo(cx, cy + r); p.lineTo(cx - t, cy + t); p.lineTo(cx - r, cy); p.lineTo(cx - t, cy - t);
        p.closePath();
        g.fill(p);
    }

    /**
     * Writes the icon to a PNG so the repo-root {@code icon.png} (the Plugin Hub listing image)
     * always matches the in-client sidebar icon. Run after changing {@link #render}:
     * {@code javac -d out src/main/java/com/bestiary/util/PanelIcon.java && java -cp out com.bestiary.util.PanelIcon icon.png}
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
