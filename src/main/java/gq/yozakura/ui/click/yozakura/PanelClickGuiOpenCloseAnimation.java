package gq.yozakura.ui.click.yozakura;

/**
 * Fixed-duration, monotonic open/close animation for the Panel ClickGUI.
 *
 * <p>The raw progress is linear in time so it is independent of frame rate.
 * A direction change starts from the exact sampled progress and scales the
 * remaining duration by the distance left to travel, so it stays position-
 * continuous instead of restarting a full close or open animation.</p>
 */
public final class PanelClickGuiOpenCloseAnimation {
    public static final long OPEN_DURATION_MS = 220L;
    public static final long CLOSE_DURATION_MS = 180L;

    private final long openDurationNanos;
    private final long closeDurationNanos;

    private float progress;
    private float startProgress;
    private float targetProgress;
    private long transitionStartNanos;
    private long transitionDurationNanos;
    private boolean initialized;

    public PanelClickGuiOpenCloseAnimation() {
        this(OPEN_DURATION_MS, CLOSE_DURATION_MS);
    }

    PanelClickGuiOpenCloseAnimation(long openDurationMs, long closeDurationMs) {
        openDurationNanos = millisToNanos(openDurationMs);
        closeDurationNanos = millisToNanos(closeDurationMs);
    }

    public void reset(boolean open, long nowNanos) {
        progress = open ? 1.0f : 0.0f;
        startProgress = progress;
        targetProgress = progress;
        transitionStartNanos = nowNanos;
        transitionDurationNanos = 0L;
        initialized = true;
    }

    public float progressAt(boolean open, long nowNanos) {
        if (!initialized) {
            reset(!open, nowNanos);
        }
        sample(nowNanos);
        float requestedTarget = open ? 1.0f : 0.0f;
        if (Float.compare(requestedTarget, targetProgress) != 0) {
            startProgress = progress;
            targetProgress = requestedTarget;
            transitionStartNanos = nowNanos;
            long fullDuration = open ? openDurationNanos : closeDurationNanos;
            transitionDurationNanos = Math.round(fullDuration
                    * Math.abs(targetProgress - startProgress));
            if (transitionDurationNanos <= 0L) {
                progress = targetProgress;
            }
        }
        return sample(nowNanos);
    }

    public float visualProgress(float rawProgress) {
        float value = clamp(rawProgress);
        if (value < 0.5f) {
            return 4.0f * value * value * value;
        }
        float inverse = -2.0f * value + 2.0f;
        return 1.0f - inverse * inverse * inverse * 0.5f;
    }

    public boolean isOpen() {
        return progress >= 1.0f;
    }

    public boolean isClosed() {
        return progress <= 0.0f;
    }

    private float sample(long nowNanos) {
        if (transitionDurationNanos <= 0L
                || Float.compare(startProgress, targetProgress) == 0) {
            progress = targetProgress;
            return progress;
        }
        long elapsed = Math.max(0L, nowNanos - transitionStartNanos);
        if (elapsed >= transitionDurationNanos) {
            progress = targetProgress;
            startProgress = targetProgress;
            transitionDurationNanos = 0L;
            return progress;
        }
        float time = elapsed / (float) transitionDurationNanos;
        progress = startProgress + (targetProgress - startProgress) * time;
        return progress;
    }

    private static long millisToNanos(long durationMs) {
        return Math.max(0L, durationMs) * 1_000_000L;
    }

    private static float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
