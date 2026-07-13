package gq.yozakura.util.animation;

/**
 * Monotonic UI clock that keeps a stalled frame from producing an oversized animation step.
 */
public final class UiClock {
    public static final float MAX_DELTA_SECONDS = 0.05F;

    private long previousNanos;
    private float deltaSeconds;
    private boolean initialized;

    /**
     * Records a frame time and returns the elapsed UI time, clamped to {@code [0, 0.05]} seconds.
     * The first tick intentionally reports zero elapsed time.
     */
    public float tick(long nowNanos) {
        if (!initialized) {
            previousNanos = nowNanos;
            initialized = true;
            deltaSeconds = 0.0F;
            return deltaSeconds;
        }

        long elapsedNanos = nowNanos - previousNanos;
        previousNanos = nowNanos;
        if (elapsedNanos <= 0L) {
            deltaSeconds = 0.0F;
            return deltaSeconds;
        }

        deltaSeconds = clampDelta(elapsedNanos / 1_000_000_000.0F);
        return deltaSeconds;
    }

    public float getDeltaSeconds() {
        return deltaSeconds;
    }

    public void reset() {
        initialized = false;
        deltaSeconds = 0.0F;
    }

    public static float clampDelta(float deltaSeconds) {
        if (Float.isNaN(deltaSeconds) || Float.isInfinite(deltaSeconds)) {
            throw new IllegalArgumentException("deltaSeconds must be finite");
        }
        return Math.max(0.0F, Math.min(MAX_DELTA_SECONDS, deltaSeconds));
    }
}
