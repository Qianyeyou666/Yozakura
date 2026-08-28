package gq.yozakura.module.render;

/** Retains a Nymph ArrayList row through the source client's 500 ms decelerate transition. */
final class NymphArrayListRowMotion {
    private static final long DURATION_MILLIS = 500L;

    private boolean visible;
    private boolean retained;
    private long transitionStartMillis;
    private float transitionStartProgress;

    void setVisible(boolean nextVisible, long nowMillis) {
        if (visible == nextVisible && (retained || !nextVisible)) {
            return;
        }
        transitionStartProgress = progressAt(nowMillis);
        visible = nextVisible;
        retained = retained || nextVisible || transitionStartProgress > 0.0F;
        transitionStartMillis = nowMillis;
    }

    Snapshot snapshot(long nowMillis) {
        float progress = progressAt(nowMillis);
        if (!visible && progress <= 0.001F
                && nowMillis - transitionStartMillis >= DURATION_MILLIS) {
            retained = false;
            progress = 0.0F;
        }
        return new Snapshot(progress, retained);
    }

    private float progressAt(long nowMillis) {
        float elapsed = Math.max(0.0F, nowMillis - transitionStartMillis);
        float time = Math.min(1.0F, elapsed / DURATION_MILLIS);
        float decelerate = 1.0F - (time - 1.0F) * (time - 1.0F);
        float target = visible ? 1.0F : 0.0F;
        return clamp01(transitionStartProgress + (target - transitionStartProgress) * decelerate);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    static final class Snapshot {
        private final float progress;
        private final boolean retained;

        Snapshot(float progress, boolean retained) {
            this.progress = progress;
            this.retained = retained;
        }

        float getProgress() {
            return progress;
        }

        boolean isRetained() {
            return retained;
        }
    }
}
