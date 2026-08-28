package gq.yozakura.module.render;

/** Time-based form of the source TargetHUD's 500 ms power curves. */
final class NymphTargetHudMotion {
    private static final long DURATION_MILLIS = 500L;
    private static final float SOURCE_HEALTH_FACTOR = 0.05F;
    private static final float SOURCE_FRAMES_PER_SECOND = 60.0F;

    private boolean visible;
    private boolean retained;
    private long transitionStartMillis;
    private float health;

    void reset() {
        visible = false;
        retained = false;
        transitionStartMillis = 0L;
        health = 0.0F;
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

    void snapHealth(float ratio) {
        health = clamp01(ratio);
    }

    void updateHealth(float targetRatio, float deltaSeconds) {
        float delta = Math.max(0.0F, Math.min(0.1F, deltaSeconds));
        float factor = 1.0F - (float) Math.pow(1.0F - SOURCE_HEALTH_FACTOR,
                delta * SOURCE_FRAMES_PER_SECOND);
        health += (clamp01(targetRatio) - health) * factor;
    }

    float getHealth() {
        return health;
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
