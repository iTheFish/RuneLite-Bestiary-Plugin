package net.runelite.client.plugins.bestiary.ui;

import net.runelite.client.plugins.bestiary.BestiaryConfig;
import net.runelite.client.plugins.bestiary.model.CapturedCreature;
import net.runelite.client.plugins.bestiary.model.OverlayPos;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.*;
import java.time.Duration;
import java.time.Instant;

/**
 * In-game overlay for capture notifications.
 *
 * Two modes:
 * 1. Simple (showOverlay only): shows result panel for 4s after a successful capture.
 * 2. Animated (showCaptureAnimation): plays a pok\u00e9ball shake animation then reveals the result.
 *    Can show for both captures and misses depending on config.
 */
@Singleton
public class BestiaryOverlay extends Overlay {

    private int panelW = 200;

    private static final long   ANIM_PHASE_FLY   = 600;  // ms: ball flies in
    private static final long   ANIM_PHASE_SHAKE = 1600; // ms: ball shakes (cumulative)
    private static final long   ANIM_PHASE_OPEN  = 2200; // ms: ball opens (cumulative)
    private static final long   RESULT_DURATION  = 3000; // ms: result shown after anim
    private static final long   MISS_DURATION    = 800;  // ms: "Got away!" shown on miss
    private static final long   SIMPLE_DURATION  = 4000; // ms: simple overlay (no anim)

    private final PanelComponent panelComponent = new PanelComponent();

    // Simple mode state
    private CapturedCreature pendingCapture;
    private Instant shownAt;

    // Animation mode state
    private Instant animStart;
    private CapturedCreature animResult; // null = miss
    private boolean animMissEnabled;

    @Inject
    public BestiaryOverlay(Client client, BestiaryConfig config) {
        setPriority(OverlayPriority.LOW);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        applyConfig(config);
    }

    public void applyConfig(BestiaryConfig config) {
        setPosition(toOverlayPosition(config.overlayPosition()));
        panelW = config.overlayWidth();
        panelComponent.setPreferredSize(new Dimension(panelW, 0));
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

    /** Simple mode: show capture result panel for 4s. */
    public void showCapture(CapturedCreature creature) {
        this.pendingCapture = creature;
        this.shownAt        = Instant.now();
        this.animStart      = null;
    }

    /**
     * Animated mode: play pok\u00e9ball sequence, then reveal result.
     * @param result Captured creature, or null if the attempt failed.
     * @param showMisses Whether to show animation on a miss.
     */
    public void startCaptureSequence(CapturedCreature result, boolean showMisses) {
        if (result == null && !showMisses) return;
        this.animStart      = Instant.now();
        this.animResult     = result;
        this.animMissEnabled = showMisses;
        this.pendingCapture = null;
    }

    // --- Rendering ---

    @Override
    public Dimension render(Graphics2D g) {
        if (animStart != null) {
            return renderAnimation(g);
        }
        if (pendingCapture != null) {
            return renderResult(g, pendingCapture, shownAt, SIMPLE_DURATION);
        }
        return null;
    }

    private Dimension renderAnimation(Graphics2D g) {
        long elapsed = Duration.between(animStart, Instant.now()).toMillis();

        if (elapsed < ANIM_PHASE_OPEN) {
            drawBallAnimation(g, elapsed);
            return new Dimension(panelW, 70);
        }

        // After open phase: show result (or miss text), then expire
        long afterOpen = elapsed - ANIM_PHASE_OPEN;
        if (animResult != null) {
            if (afterOpen > RESULT_DURATION) {
                animStart  = null;
                animResult = null;
                return null;
            }
            return renderResult(g, animResult, animStart.plusMillis(ANIM_PHASE_OPEN), RESULT_DURATION);
        } else {
            // Miss
            if (afterOpen > MISS_DURATION) {
                animStart  = null;
                animResult = null;
                return null;
            }
            panelComponent.getChildren().clear();
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Got away!")
                    .color(new Color(180, 180, 180))
                    .build());
            return panelComponent.render(g);
        }
    }

    private void drawBallAnimation(Graphics2D g, long elapsed) {
        int panelCx = panelW / 2;
        int cy      = 30;
        int size    = 32;

        int bx;
        float alpha = 1.0f;

        if (elapsed < ANIM_PHASE_FLY) {
            // Fly in from the left
            double t = elapsed / (double) ANIM_PHASE_FLY;
            bx = (int) (t * panelCx);
        } else {
            // Shake with decaying amplitude
            double t = (elapsed - ANIM_PHASE_FLY) / (double) (ANIM_PHASE_SHAKE - ANIM_PHASE_FLY);
            double decay  = 1.0 - t;
            double wobble = Math.sin(t * Math.PI * 5) * 9 * decay;
            bx = panelCx + (int) wobble;
        }

        Composite orig = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        // Top half \u2014 orange (pre-reveal)
        g.setColor(new Color(255, 140, 0));
        g.fillArc(bx - size / 2, cy - size / 2, size, size, 0, 180);

        // Bottom half \u2014 dark
        g.setColor(new Color(35, 35, 35));
        g.fillArc(bx - size / 2, cy - size / 2, size, size, 180, 180);

        // Center divider line
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(2));
        g.drawLine(bx - size / 2, cy, bx + size / 2, cy);

        // Center button
        g.setColor(Color.WHITE);
        g.fillOval(bx - 5, cy - 5, 10, 10);
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(1));
        g.drawOval(bx - 5, cy - 5, 10, 10);

        g.setComposite(orig);
    }

    private Dimension renderResult(Graphics2D g, CapturedCreature creature, Instant start, long duration) {
        if (Duration.between(start, Instant.now()).toMillis() > duration) {
            pendingCapture = null;
            animStart      = null;
            return null;
        }

        panelComponent.getChildren().clear();

        panelComponent.getChildren().add(TitleComponent.builder()
                .text(creature.rarity.label + " Captured!")
                .color(creature.rarity.displayColor)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Creature")
                .right(creature.npcName)
                .rightColor(Color.WHITE)
                .build());

        int quality = creature.quality.overallRating();
        Color qualColor = quality >= 80 ? new Color(80, 220, 80)
                        : quality >= 50 ? new Color(220, 220, 80)
                        : new Color(180, 180, 180);
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Quality")
                .right(quality + " / 100")
                .rightColor(qualColor)
                .build());

        return panelComponent.render(g);
    }
}
