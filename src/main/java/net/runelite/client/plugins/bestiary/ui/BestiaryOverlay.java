package net.runelite.client.plugins.bestiary.ui;

import net.runelite.client.plugins.bestiary.BestiaryConfig;
import net.runelite.client.plugins.bestiary.model.CapturedCreature;
import net.runelite.client.plugins.bestiary.model.OverlayPos;
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
 *  - CAPTURE (animated): ball flies in, shakes, cracks open with a sparkle burst,
 *    then reveals a styled card (shiny cards shimmer gold). Simple mode skips the ball.
 *  - MISS: ball flies in, shakes, then bursts open empty with a puff — "Got away!".
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
            drawBall(g, cx, 34, elapsed, false);
            // Sparkle burst as the ball cracks open
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
            drawBall(g, cx, 34, elapsed, false);
            return new Dimension(panelW, 74);
        }
        if (elapsed < SHAKE_END + BURST_MS) {
            double p = (elapsed - SHAKE_END) / (double) BURST_MS;
            drawExplosion(g, cx, 34, p);
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

    /** Draws the poké-style ball. During fly it slides in; during shake it wobbles. */
    private void drawBall(Graphics2D g, int cx, int cy, long elapsed, boolean unused) {
        int size = 34;
        int bx;
        if (elapsed < FLY_MS) {
            double t = elapsed / (double) FLY_MS;
            bx = (int) (t * cx);
        } else {
            double t = (elapsed - FLY_MS) / (double) (SHAKE_END - FLY_MS);
            double wobble = Math.sin(t * Math.PI * 5) * 9 * (1.0 - t);
            bx = cx + (int) wobble;
        }
        drawBallShape(g, bx, cy, size, 1f);
    }

    private void drawBallShape(Graphics2D g, int bx, int cy, int size, float alpha) {
        Composite orig = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g.setColor(new Color(230, 60, 50));   // top red
        g.fillArc(bx - size / 2, cy - size / 2, size, size, 0, 180);
        g.setColor(new Color(240, 240, 240));  // bottom white
        g.fillArc(bx - size / 2, cy - size / 2, size, size, 180, 180);
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(2));
        g.drawOval(bx - size / 2, cy - size / 2, size, size);
        g.drawLine(bx - size / 2, cy, bx + size / 2, cy);
        g.setColor(Color.WHITE);
        g.fillOval(bx - 6, cy - 6, 12, 12);
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(1.5f));
        g.drawOval(bx - 6, cy - 6, 12, 12);
        g.setComposite(orig);
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

    /** Ball halves fly apart, an expanding ring, and a grey puff — a "miss". */
    private void drawExplosion(Graphics2D g, int cx, int cy, double p) {
        int size = 34;
        int spread = (int) (p * 22);
        int alpha = (int) (255 * (1.0 - p));
        // Expanding ring
        g.setColor(new Color(200, 200, 200, Math.max(0, alpha)));
        g.setStroke(new BasicStroke(2f));
        int r = (int) (p * 26);
        g.drawOval(cx - r, cy - r, r * 2, r * 2);
        // Top half flies up, bottom half flies down
        Composite orig = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, 1f - (float) p)));
        g.setColor(new Color(230, 60, 50));
        g.fillArc(cx - size / 2, cy - size / 2 - spread, size, size, 0, 180);
        g.setColor(new Color(240, 240, 240));
        g.fillArc(cx - size / 2, cy - size / 2 + spread, size, size, 180, 180);
        g.setComposite(orig);
        // Puff particles
        g.setColor(new Color(150, 150, 150, Math.max(0, alpha)));
        for (int i = 0; i < 6; i++) {
            double ang = Math.PI * 2 * i / 6;
            int x = cx + (int) (Math.cos(ang) * r);
            int y = cy + (int) (Math.sin(ang) * r);
            g.fillOval(x - 2, y - 2, 4, 4);
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
        // Quality
        int q = c.quality.overallRating();
        Color qcol = q >= 80 ? new Color(90, 220, 90) : q >= 50 ? new Color(220, 220, 90) : new Color(180, 180, 180);
        g.setColor(qcol);
        g.setFont(getFont(Font.BOLD, 13));
        drawCentered(g, "Quality  " + q, tx, 64 + dy);
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
