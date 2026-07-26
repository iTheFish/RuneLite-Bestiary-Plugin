package com.bestiary.ui;

import com.bestiary.BestiaryConfig;
import com.bestiary.model.CapturedCreature;
import com.bestiary.model.OverlayPos;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;

/**
 * In-game overlay for capture notifications, driven by a queue so rapid kills
 * all play in order (no dropped animations).
 *
 * Each queued event is one of:
 *  - CAPTURE (animated): an OSRS-style containment jar flies in, shakes, its cork
 *    pops with a sparkle burst, then a styled card is revealed (shiny cards shimmer
 *    gold). Simple mode skips the jar.
 *  - MISS: the jar flies in, shakes, then the cork pops off and the essence escapes
 *    upward and fades — "Got away!".
 *  - LEVEL_UP: a glowing "Capture Level N!" banner.
 */
@Singleton
public class BestiaryOverlay extends Overlay {

    private int panelW = 200;

    // Shared phase timings (ms)
    private static final long FLY_MS      = 500;
    private static final long SHAKE_END   = 1500;   // cumulative
    private static final long OPEN_END    = 1950;   // cumulative (capture crack-open)
    private static final long REVEAL_MS   = 3500;   // card shown after open
    private static final long BURST_MS    = 550;    // miss explosion
    private static final long MISS_TEXT_MS= 750;    // "Got away!" after burst
    private static final long LEVELUP_MS  = 3000;
    private static final int  MAX_QUEUE   = 12;

    private enum Kind { CAPTURE, MISS, LEVEL_UP }

    private static final class Event {
        final Kind kind;
        final CapturedCreature capture; // CAPTURE only
        final int level;                // LEVEL_UP only
        final boolean animated;         // CAPTURE: false = simple (no ball)
        Instant start;                  // set when it becomes active
        Event(Kind k, CapturedCreature c, int lvl, boolean anim) {
            kind = k; capture = c; level = lvl; animated = anim;
        }
    }

    private final Deque<Event> queue = new ArrayDeque<>();
    private final Random rng = new Random();

    @Inject
    public BestiaryOverlay(Client client, BestiaryConfig config) {
        setPriority(OverlayPriority.LOW);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        applyConfig(config);
    }

    public void applyConfig(BestiaryConfig config) {
        setPosition(toOverlayPosition(config.overlayPosition()));
        panelW = config.overlayWidth();
    }

    private static OverlayPosition toOverlayPosition(OverlayPos pos) {
        switch (pos) {
            case TOP_LEFT:     return OverlayPosition.TOP_LEFT;
            case TOP_RIGHT:    return OverlayPosition.TOP_RIGHT;
            case BOTTOM_LEFT:  return OverlayPosition.BOTTOM_LEFT;
            case BOTTOM_RIGHT: return OverlayPosition.BOTTOM_RIGHT;
            default:           return OverlayPosition.TOP_CENTER;
        }
    }

    // --- Public API ---

    private void enqueue(Event e) {
        if (queue.size() >= MAX_QUEUE) return;   // drop if flooded
        queue.addLast(e);
    }

    /** Simple mode: reveal the card for a few seconds (no ball animation). */
    public void showCapture(CapturedCreature creature) {
        enqueue(new Event(Kind.CAPTURE, creature, 0, false));
    }

    /** Animated mode: ball sequence, then reveal (capture) or burst (miss). */
    public void startCaptureSequence(CapturedCreature result, boolean showMisses) {
        if (result == null) {
            if (showMisses) enqueue(new Event(Kind.MISS, null, 0, true));
        } else {
            enqueue(new Event(Kind.CAPTURE, result, 0, true));
        }
    }

    /** Queue a level-up banner. */
    public void enqueueLevelUp(int newLevel) {
        enqueue(new Event(Kind.LEVEL_UP, null, newLevel, true));
    }

    private long totalDuration(Event e) {
        switch (e.kind) {
            case CAPTURE:  return (e.animated ? OPEN_END : 0) + REVEAL_MS;
            case MISS:     return SHAKE_END + BURST_MS + MISS_TEXT_MS;
            case LEVEL_UP: return LEVELUP_MS;
            default:       return 0;
        }
    }

    // --- Rendering ---

    @Override
    public Dimension render(Graphics2D g) {
        Event e = queue.peekFirst();
        while (e != null) {
            if (e.start == null) e.start = Instant.now();
            long elapsed = Duration.between(e.start, Instant.now()).toMillis();
            if (elapsed > totalDuration(e)) {   // finished — advance to next
                queue.pollFirst();
                e = queue.peekFirst();
                continue;
            }
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            switch (e.kind) {
                case CAPTURE:  return renderCapture(g, e, elapsed);
                case MISS:     return renderMiss(g, elapsed);
                case LEVEL_UP: return renderLevelUp(g, e.level, elapsed);
            }
        }
        return null;
    }

    private Dimension renderCapture(Graphics2D g, Event e, long elapsed) {
        int cx = panelW / 2;
        if (e.animated && elapsed < OPEN_END) {
            drawJarAnim(g, cx, 34, elapsed, e.capture.rarity.displayColor);
            // Sparkle burst as the jar's cork pops
            if (elapsed > SHAKE_END) {
                double p = (elapsed - SHAKE_END) / (double) (OPEN_END - SHAKE_END);
                drawSparkleBurst(g, cx, 34, p, e.capture.rarity.displayColor);
            }
            return new Dimension(panelW, 74);
        }
        long revealElapsed = elapsed - (e.animated ? OPEN_END : 0);
        double reveal = Math.min(1.0, revealElapsed / 220.0); // slide/scale in
        return drawCardReveal(g, e.capture, reveal, revealElapsed);
    }

    private Dimension renderMiss(Graphics2D g, long elapsed) {
        int cx = panelW / 2;
        if (elapsed < SHAKE_END) {
            drawJarAnim(g, cx, 34, elapsed, new Color(120, 120, 130));
            return new Dimension(panelW, 74);
        }
        if (elapsed < SHAKE_END + BURST_MS) {
            double p = (elapsed - SHAKE_END) / (double) BURST_MS;
            drawJarBurst(g, cx, 34, p);
            return new Dimension(panelW, 74);
        }
        // "Got away!" fade
        double p = (elapsed - SHAKE_END - BURST_MS) / (double) MISS_TEXT_MS;
        int alpha = (int) (255 * (1.0 - Math.max(0, p - 0.6) / 0.4));
        g.setColor(new Color(190, 190, 190, Math.max(0, Math.min(255, alpha))));
        g.setFont(getFont(Font.BOLD, 15));
        drawCentered(g, "Got away!", cx, 40);
        return new Dimension(panelW, 60);
    }

    private Dimension renderLevelUp(Graphics2D g, int level, long elapsed) {
        double p = elapsed / (double) LEVELUP_MS;
        int fade = (int) (255 * Math.min(1.0, Math.min(p / 0.15, (1.0 - p) / 0.2)));
        fade = Math.max(0, Math.min(255, fade));
        int h = 56;
        // Glowing plate
        g.setColor(new Color(20, 16, 4, (int) (fade * 0.85)));
        g.fillRoundRect(4, 8, panelW - 8, h - 12, 12, 12);
        Color gold = new Color(255, 190, 40, fade);
        g.setColor(gold);
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(4, 8, panelW - 8, h - 12, 12, 12);
        // Twinkles either side
        drawSparkleBurst(g, 20, 8 + (h - 12) / 2, (p * 2) % 1.0, gold);
        drawSparkleBurst(g, panelW - 20, 8 + (h - 12) / 2, (p * 2 + 0.5) % 1.0, gold);
        g.setColor(new Color(255, 210, 90, fade));
        g.setFont(getFont(Font.BOLD, 16));
        drawCentered(g, "Capture Level " + level + "!", panelW / 2, 26);
        g.setColor(new Color(210, 200, 160, fade));
        g.setFont(getFont(Font.PLAIN, 11));
        drawCentered(g, "Level up!", panelW / 2, 42);
        return new Dimension(panelW, h);
    }

    // --- Drawing helpers ---

    /**
     * Fly-in + shake motion for the containment jar. During the crack-open window
     * (SHAKE_END..OPEN_END) the cork lifts. Essence inside is tinted by {@code essence}.
     */
    private void drawJarAnim(Graphics2D g, int cx, int cy, long elapsed, Color essence) {
        int bx;
        if (elapsed < FLY_MS) {
            double t = elapsed / (double) FLY_MS;
            bx = (int) (t * cx);
        } else {
            double t = (elapsed - FLY_MS) / (double) (SHAKE_END - FLY_MS);
            double wobble = Math.sin(t * Math.PI * 5) * 9 * (1.0 - t);
            bx = cx + (int) wobble;
        }
        double openAmount = elapsed <= SHAKE_END ? 0.0
                : Math.min(1.0, (elapsed - SHAKE_END) / (double) (OPEN_END - SHAKE_END));
        drawJar(g, bx, cy, 1f, essence, openAmount, elapsed / 300.0);
    }

    /**
     * OSRS-style collection jar: a tall cylindrical glass body (tinted by the capture's
     * rarity essence, with cylinder shading + an open rim) and a chunky dark-grey
     * hexagonal stopper that lifts as {@code openAmount} rises.
     */
    private void drawJar(Graphics2D g, int bx, int cy, float alpha, Color essence,
                         double openAmount, double phase) {
        Composite orig = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        int bw = 22, bh = 26, bx0 = bx - bw / 2;
        int topY = cy - 10, rimH = 8;

        Color hi   = mix(essence, Color.WHITE, 0.55);            // lit centre
        Color edge = mix(essence, new Color(40, 40, 60), 0.35);  // shaded sides
        Color hiT   = new Color(hi.getRed(), hi.getGreen(), hi.getBlue(), 220);
        Color edgeT = new Color(edge.getRed(), edge.getGreen(), edge.getBlue(), 220);

        // Bottom ellipse
        g.setColor(edgeT);
        g.fillOval(bx0, topY + bh - rimH / 2, bw, rimH);
        // Cylinder wall with left→centre→right shading
        g.setPaint(new LinearGradientPaint(bx0, 0, bx0 + bw, 0,
                new float[]{0f, 0.5f, 1f}, new Color[]{edgeT, hiT, edgeT}));
        g.fillRect(bx0, topY, bw, bh);

        // Swirling essence specks inside
        Color spark = new Color(Math.min(255, essence.getRed() + 70),
                Math.min(255, essence.getGreen() + 70), Math.min(255, essence.getBlue() + 70),
                Math.min(255, (int) (120 + 120 * openAmount)));
        g.setColor(spark);
        for (int i = 0; i < 3; i++) {
            double a = phase + i * 2.094;
            int ox = bx + (int) (Math.cos(a) * 5);
            int oy = cy + 4 + (int) (Math.sin(a) * 7);
            g.fillOval(ox - 2, oy - 2, 4, 4);
        }

        // Open rim (darker mouth) + side/highlight lines
        g.setColor(mix(edge, Color.BLACK, 0.25));
        g.fillOval(bx0, topY - rimH / 2, bw, rimH);
        g.setColor(new Color(235, 242, 252, 150));
        g.setStroke(new BasicStroke(1.5f));
        g.drawOval(bx0, topY - rimH / 2, bw, rimH);
        g.drawLine(bx0, topY, bx0, topY + bh);
        g.drawLine(bx0 + bw, topY, bx0 + bw, topY + bh);
        g.setColor(new Color(255, 255, 255, 90));
        g.drawLine(bx0 + 5, topY + 3, bx0 + 5, topY + bh - 4);

        // Chunky dark-grey hexagonal stopper (lifts on open)
        int lift = (int) (openAmount * 10);
        int lidW = bw + 4, lidX = bx - lidW / 2, lidY = topY - rimH / 2 - 11 - lift;
        int[] hx = {lidX, lidX + lidW / 4, lidX + 3 * lidW / 4, lidX + lidW, lidX + 3 * lidW / 4, lidX + lidW / 4};
        int[] hy = {lidY + 7, lidY + 2, lidY + 2, lidY + 7, lidY + 12, lidY + 12};
        g.setColor(new Color(64, 64, 72));                         // skirt shadow
        g.fillRect(lidX + 2, lidY + 8, lidW - 4, 5);
        g.setColor(new Color(98, 98, 108));                        // body
        g.fillPolygon(hx, hy, 6);
        int[] hx2 = {lidX + 2, lidX + lidW / 4 + 1, lidX + 3 * lidW / 4 - 1, lidX + lidW - 2, lidX + 3 * lidW / 4 - 1, lidX + lidW / 4 + 1};
        int[] hy2 = {lidY + 6, lidY + 3, lidY + 3, lidY + 6, lidY + 9, lidY + 9};
        g.setColor(new Color(124, 124, 136));                      // top facet highlight
        g.fillPolygon(hx2, hy2, 6);
        g.setColor(new Color(38, 38, 44));
        g.setStroke(new BasicStroke(1.5f));
        g.drawPolygon(hx, hy, 6);

        g.setComposite(orig);
    }

    private static Color mix(Color a, Color b, double t) {
        return new Color(
                (int) (a.getRed()   * (1 - t) + b.getRed()   * t),
                (int) (a.getGreen() * (1 - t) + b.getGreen() * t),
                (int) (a.getBlue()  * (1 - t) + b.getBlue()  * t));
    }

    /** A ring of small sparkles expanding outward and fading. */
    private void drawSparkleBurst(Graphics2D g, int cx, int cy, double p, Color tint) {
        p = Math.max(0, Math.min(1, p));
        int n = 8;
        int alpha = (int) (255 * (1.0 - p));
        Color c = new Color(tint.getRed(), tint.getGreen(), tint.getBlue(), Math.max(0, alpha));
        g.setColor(c);
        g.setStroke(new BasicStroke(1.5f));
        double radius = 6 + p * 20;
        for (int i = 0; i < n; i++) {
            double ang = (Math.PI * 2 * i / n) + p * 0.6;
            int x = cx + (int) (Math.cos(ang) * radius);
            int y = cy + (int) (Math.sin(ang) * radius);
            int s = 3 - (int) (p * 2);
            if (s < 1) s = 1;
            g.drawLine(x - s, y, x + s, y);
            g.drawLine(x, y - s, x, y + s);
        }
    }

    /** The stopper pops off and the trapped essence escapes upward and fades — a "miss". */
    private void drawJarBurst(Graphics2D g, int cx, int cy, double p) {
        int alpha = (int) (255 * (1.0 - p));
        int bw = 22, bh = 26, bx0 = cx - bw / 2, topY = cy - 10, rimH = 8;

        // Empty glass cylinder, fading
        Composite orig = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, 1f - (float) p * 0.7f)));
        g.setColor(new Color(150, 160, 200, 110));
        g.fillOval(bx0, topY + bh - rimH / 2, bw, rimH);
        g.setColor(new Color(178, 188, 226, 120));
        g.fillRect(bx0, topY, bw, bh);
        g.setColor(new Color(70, 75, 110, 150));
        g.fillOval(bx0, topY - rimH / 2, bw, rimH);   // open mouth
        g.setColor(new Color(235, 242, 252, 130));
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine(bx0, topY, bx0, topY + bh);
        g.drawLine(bx0 + bw, topY, bx0 + bw, topY + bh);
        g.setComposite(orig);

        // Stopper tumbles up and to the side
        int lift = (int) (11 + p * 26);
        int lidW = bw + 4, lidX = cx - lidW / 2 + (int) (p * 10), lidY = topY - 20 - lift;
        int[] hx = {lidX, lidX + lidW / 4, lidX + 3 * lidW / 4, lidX + lidW, lidX + 3 * lidW / 4, lidX + lidW / 4};
        int[] hy = {lidY + 7, lidY + 2, lidY + 2, lidY + 7, lidY + 12, lidY + 12};
        g.setColor(new Color(98, 98, 108, Math.max(0, alpha)));
        g.fillPolygon(hx, hy, 6);

        // Escaping essence wisps rising out of the mouth
        g.setColor(new Color(205, 210, 225, Math.max(0, alpha)));
        for (int i = 0; i < 6; i++) {
            double ang = -Math.PI / 2 + (i - 2.5) * 0.28;
            int rise = (int) (p * 30);
            int x = cx + (int) (Math.cos(ang) * (4 + p * 12));
            int y = topY - rise + (int) (Math.sin(ang) * 2);
            int s = Math.max(1, 3 - (int) (p * 2));
            g.fillOval(x - s, y - s, s * 2, s * 2);
        }
    }

    /** Draws a styled "card reveal": rarity frame, name, quality, shiny shimmer. */
    private Dimension drawCardReveal(Graphics2D g, CapturedCreature c, double reveal, long revealElapsed) {
        int w = panelW;
        int h = 92;
        // Slide up + fade in
        int dy = (int) ((1.0 - reveal) * 14);
        Composite orig = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) reveal));

        Color rc = c.rarity.displayColor;
        // Plate
        g.setColor(new Color(18, 18, 18, 235));
        g.fillRoundRect(3, 4 + dy, w - 6, h - 8, 10, 10);
        if (c.isShiny()) {
            g.setPaint(new GradientPaint(0, 4 + dy, new Color(255, 224, 120, 70),
                    0, h, new Color(255, 196, 60, 18)));
            g.fillRoundRect(3, 4 + dy, w - 6, h - 8, 10, 10);
        }
        // Frame (thicker for higher rarity)
        int fw = c.rarity.ordinal() >= 3 ? 3 : 2;
        g.setColor(rc);
        g.setStroke(new BasicStroke(fw));
        g.drawRoundRect(3, 4 + dy, w - 6, h - 8, 10, 10);

        int tx = w / 2;
        // Header
        g.setColor(rc);
        g.setFont(getFont(Font.BOLD, 14));
        String header = (c.isShiny() ? "✦ SHINY " : "") + c.rarity.label + " Captured!";
        drawCentered(g, header, tx, 24 + dy);
        // Name
        g.setColor(Color.WHITE);
        g.setFont(getFont(Font.BOLD, 13));
        drawCentered(g, c.npcName, tx, 44 + dy);
        // Power Level
        int q = c.powerLevel();
        Color qcol = q >= 80 ? new Color(90, 220, 90) : q >= 50 ? new Color(220, 220, 90) : new Color(180, 180, 180);
        g.setColor(qcol);
        g.setFont(getFont(Font.BOLD, 13));
        drawCentered(g, "Power  " + q + "   ·   " + c.hitpoints() + " HP", tx, 64 + dy);
        // Hint
        g.setColor(new Color(150, 150, 150));
        g.setFont(getFont(Font.PLAIN, 10));
        drawCentered(g, "View it in the Bestiary panel", tx, 80 + dy);

        // Shiny sparkles keep twinkling during the reveal
        if (c.isShiny()) {
            double sp = (revealElapsed % 1200) / 1200.0;
            drawSparkleBurst(g, 16, 20 + dy, sp, new Color(255, 240, 170));
            drawSparkleBurst(g, w - 16, 60 + dy, (sp + 0.5) % 1.0, new Color(255, 240, 170));
        }
        g.setComposite(orig);
        return new Dimension(w, h);
    }

    private Font getFont(int style, int size) {
        return new Font(Font.SANS_SERIF, style, size);
    }

    private void drawCentered(Graphics2D g, String s, int cx, int baselineY) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, cx - fm.stringWidth(s) / 2, baselineY);
    }
}
