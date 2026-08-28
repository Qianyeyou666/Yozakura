package gq.yozakura.ui.click.yozakura;

/**
 * Time-driven category rail width animation matching Epsilon's
 * {@code CategoryRailPanel} expand animation.
 *
 * <p>Epsilon animates the rail between {@code RAIL_COLLAPSED_WIDTH} and
 * {@code RAIL_EXPANDED_WIDTH} with {@code EASE_OUT_CUBIC} over {@code 240ms}.
 * When the target flips mid-animation, it redirects from the current value
 * instead of snapping, so a collapse started during an expand continues
 * smoothly from where it was.</p>
 */
public final class PanelClickGuiRailAnimation {
    public static final float RAIL_COLLAPSED_WIDTH = 42.0f;
    public static final float RAIL_EXPANDED_WIDTH = 120.0f;
    public static final long DURATION_MS = 240L;

    private final float collapsedWidth;
    private final float expandedWidth;
    private final long durationMs;

    private float value;
    private float startValue;
    private float destinationValue;
    private long startTime;
    private boolean finished = true;

    public PanelClickGuiRailAnimation() {
        this(RAIL_COLLAPSED_WIDTH, RAIL_EXPANDED_WIDTH, DURATION_MS);
    }

    public PanelClickGuiRailAnimation(float collapsedWidth) {
        this(collapsedWidth, RAIL_EXPANDED_WIDTH, DURATION_MS);
    }

    public PanelClickGuiRailAnimation(float collapsedWidth, float expandedWidth, long durationMs) {
        this.collapsedWidth = collapsedWidth;
        this.expandedWidth = expandedWidth;
        this.durationMs = Math.max(0L, durationMs);
        this.value = collapsedWidth;
        this.startValue = collapsedWidth;
        this.destinationValue = collapsedWidth;
        this.startTime = 0L;
    }

    /**
     * Returns the animated rail width for {@code expanded} at {@code timeMs}.
     * {@code timeMs} is an absolute monotonic clock in milliseconds; production
     * callers derive it from {@link System#nanoTime()} so the animation is
     * frame-rate independent, high precision, and reversible.
     */
    public float valueAt(boolean expanded, long timeMs) {
        float destination = expanded ? expandedWidth : collapsedWidth;
        if (destination != destinationValue) {
            destinationValue = destination;
            startValue = value;
            startTime = timeMs;
            finished = false;
        } else if (finished) {
            return value;
        }

        if (durationMs <= 0L) {
            value = destinationValue;
            finished = true;
            return value;
        }

        float progress = (float) (timeMs - startTime) / (float) durationMs;
        if (progress >= 1.0f) {
            value = destinationValue;
            finished = true;
            return value;
        }
        if (progress <= 0.0f) {
            value = startValue;
            return value;
        }

        float eased = easeOutCubic(progress);
        value = startValue + (destinationValue - startValue) * eased;
        return value;
    }

    public float currentValue() {
        return value;
    }

    public boolean isFinished() {
        return finished;
    }

    private static float easeOutCubic(float x) {
        float v = 1.0f - x;
        return 1.0f - v * v * v;
    }
}
