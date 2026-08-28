package gq.yozakura.ui.click.timewarp;

/** Reversible fixed-duration transition driven exclusively by a monotonic clock. */
public final class TimewarpClickGuiAnimation {
    public static final long OPEN_DURATION_MS = 230L;
    public static final long CLOSE_DURATION_MS = 170L;

    private final long openDurationNanos;
    private final long closeDurationNanos;
    private float progress;
    private float startProgress;
    private float targetProgress;
    private long startNanos;
    private long durationNanos;
    private boolean initialized;

    public TimewarpClickGuiAnimation() {
        this(OPEN_DURATION_MS, CLOSE_DURATION_MS);
    }

    TimewarpClickGuiAnimation(long openDurationMs, long closeDurationMs) {
        openDurationNanos = Math.max(0L, openDurationMs) * 1_000_000L;
        closeDurationNanos = Math.max(0L, closeDurationMs) * 1_000_000L;
    }

    public void reset(boolean open, long nowNanos) {
        progress = open ? 1.0f : 0.0f;
        startProgress = progress;
        targetProgress = progress;
        startNanos = nowNanos;
        durationNanos = 0L;
        initialized = true;
    }

    public float progressAt(boolean open, long nowNanos) {
        if (!initialized) {
            reset(!open, nowNanos);
        }
        sample(nowNanos);
        float requested = open ? 1.0f : 0.0f;
        if (Float.compare(requested, targetProgress) != 0) {
            startProgress = progress;
            targetProgress = requested;
            startNanos = nowNanos;
            long fullDuration = open ? openDurationNanos : closeDurationNanos;
            durationNanos = Math.round(fullDuration * Math.abs(targetProgress - startProgress));
        }
        return sample(nowNanos);
    }

    public boolean isClosed() {
        return progress <= 0.0f;
    }

    private float sample(long nowNanos) {
        if (durationNanos <= 0L || Float.compare(startProgress, targetProgress) == 0) {
            progress = targetProgress;
            return progress;
        }
        long elapsed = Math.max(0L, nowNanos - startNanos);
        if (elapsed >= durationNanos) {
            progress = targetProgress;
            startProgress = targetProgress;
            durationNanos = 0L;
            return progress;
        }
        float time = elapsed / (float) durationNanos;
        progress = startProgress + (targetProgress - startProgress) * time;
        return progress;
    }

    public static float easeOutCubic(float value) {
        float t = clamp(value);
        float inverse = 1.0f - t;
        return 1.0f - inverse * inverse * inverse;
    }

    public static float stagger(float progress, int index, int count) {
        if (count <= 1) {
            return easeOutCubic(progress);
        }
        float delay = Math.min(0.42f, Math.max(0, index) * 0.055f);
        float available = Math.max(0.01f, 1.0f - delay);
        return easeOutCubic((progress - delay) / available);
    }

    private static float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
