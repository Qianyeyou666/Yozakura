package gq.yozakura.module.world;

/** The same 500 ms opacity and scale curves used by Nymphilila TargetHUD. */
final class ScaffoldBlockCounterMotion {
    private static final long DURATION_MILLIS = 500L;

    private boolean visible;
    private boolean retained;
    private long transitionStartMillis;

    void reset() {
        visible = false;
        retained = false;
        transitionStartMillis = 0L;
    }

    void setVisible(boolean nextVisible, long nowMillis) {
        if (visible == nextVisible && (retained || !nextVisible)) {
            return;
        }
        visible = nextVisible;
        transitionStartMillis = nowMillis;
        if (nextVisible) {
            retained = true;
        }
    }

    Snapshot snapshot(long nowMillis) {
        float progress = clamp01((nowMillis - transitionStartMillis) / (float) DURATION_MILLIS);
        float opacityCurve = 1.0F - power(1.0F - progress, 6);
        float scaleCurve = 1.0F - power(1.0F - progress, 8);
        float opacity = visible ? opacityCurve : 1.0F - opacityCurve;
        float scale = visible ? scaleCurve : 1.0F - scaleCurve;
        if (!visible && progress >= 1.0F) {
            retained = false;
        }
        return new Snapshot(opacity, scale, retained);
    }

    private static float power(float value, int exponent) {
        float result = 1.0F;
        for (int index = 0; index < exponent; index++) {
            result *= value;
        }
        return result;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    static final class Snapshot {
        private final float opacity;
        private final float scale;
        private final boolean retained;

        Snapshot(float opacity, float scale, boolean retained) {
            this.opacity = opacity;
            this.scale = scale;
            this.retained = retained;
        }

        float getOpacity() {
            return opacity;
        }

        float getScale() {
            return scale;
        }

        boolean isRetained() {
            return retained;
        }
    }
}
