package gq.yozakura.engine.render.glow;

/**
 * Describes visual intent without exposing FBO or shader implementation details.
 */
public enum GlowProfile {
    TEXT(3.0f),
    ACCENT(4.5f),
    SHADOW(6.0f),
    PANEL(8.0f);

    private final float logicalRadius;

    GlowProfile(float logicalRadius) {
        this.logicalRadius = logicalRadius;
    }

    public float getLogicalRadius() {
        return logicalRadius;
    }

    public int resolveKernelRadius(float guiScale, Quality quality) {
        if (!isFinite(guiScale) || guiScale <= 0.0f) {
            throw new IllegalArgumentException("guiScale must be finite and greater than zero");
        }
        if (quality == null) {
            throw new IllegalArgumentException("quality must not be null");
        }
        int radius = Math.round(logicalRadius * guiScale * quality.getDownsample());
        return Math.max(1, Math.min(GaussianKernel.MAX_RADIUS, radius));
    }

    public static float clampStrength(float strength) {
        if (!isFinite(strength)) {
            throw new IllegalArgumentException("strength must be finite");
        }
        return Math.max(0.0f, Math.min(1.0f, strength));
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    public enum Quality {
        LOW(0.40f),
        MEDIUM(0.50f),
        HIGH(0.75f);

        private final float downsample;

        Quality(float downsample) {
            this.downsample = downsample;
        }

        public float getDownsample() {
            return downsample;
        }
    }
}
