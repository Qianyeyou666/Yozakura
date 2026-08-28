package gq.yozakura.ui.click.qml;

/** Limits expensive raster uploads to active animation windows. */
final class QmlFrameScheduler {
    private static final long FRAME_NANOS = 16_000_000L;
    private static final long INITIAL_ANIMATION_NANOS = 350_000_000L;
    private static final long INTERACTION_ANIMATION_NANOS = 300_000_000L;

    private long dirtyUntilNanos;
    private long lastRenderNanos = Long.MIN_VALUE;

    QmlFrameScheduler(long openedAtNanos) {
        dirtyUntilNanos = openedAtNanos + INITIAL_ANIMATION_NANOS;
    }

    void invalidateForAnimation(long nowNanos) {
        dirtyUntilNanos = Math.max(dirtyUntilNanos, nowNanos + INTERACTION_ANIMATION_NANOS);
    }

    boolean shouldRender(long nowNanos) {
        if (lastRenderNanos == Long.MIN_VALUE) return true;
        return nowNanos <= dirtyUntilNanos && nowNanos - lastRenderNanos >= FRAME_NANOS;
    }

    void didRender(long nowNanos) {
        lastRenderNanos = nowNanos;
    }
}
