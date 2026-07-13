package gq.yozakura.util.render;

/**
 * Fixed-cost post-processing plan for a collected world-glow batch.
 */
public final class ScreenSpaceGlowPlan {
    public static final int MAX_OUTLINE_RADIUS = 6;
    public static final int MAX_BLUR_RADIUS = 16;
    public static final int FIXED_POST_PROCESS_PASS_COUNT = 8;

    private final int collectedTargetCount;
    private final int outlineRadius;
    private final int outerBlurRadius;
    private final int coreBlurRadius;

    private ScreenSpaceGlowPlan(int collectedTargetCount, int outlineRadius,
                                int outerBlurRadius, int coreBlurRadius) {
        this.collectedTargetCount = collectedTargetCount;
        this.outlineRadius = outlineRadius;
        this.outerBlurRadius = outerBlurRadius;
        this.coreBlurRadius = coreBlurRadius;
    }

    public static ScreenSpaceGlowPlan forBatch(int entityCount, int blockCount, Quality quality) {
        if (entityCount < 0 || blockCount < 0) {
            throw new IllegalArgumentException("Collected target counts must not be negative");
        }
        if (quality == null) {
            throw new IllegalArgumentException("quality must not be null");
        }
        return new ScreenSpaceGlowPlan(entityCount + blockCount,
                clampOutlineRadius(quality.outlineRadius), clampBlurRadius(quality.outerBlurRadius),
                clampBlurRadius(quality.coreBlurRadius));
    }

    public int getCollectedTargetCount() {
        return collectedTargetCount;
    }

    public int getMaskPassCount() {
        return collectedTargetCount == 0 ? 0 : 1;
    }

    public int getPostProcessPassCount() {
        return collectedTargetCount == 0 ? 0 : FIXED_POST_PROCESS_PASS_COUNT;
    }

    public int getOutlineRadius() {
        return outlineRadius;
    }

    public int getOuterBlurRadius() {
        return outerBlurRadius;
    }

    public int getCoreBlurRadius() {
        return coreBlurRadius;
    }

    static int clampOutlineRadius(int radius) {
        return Math.max(1, Math.min(MAX_OUTLINE_RADIUS, radius));
    }

    static int clampBlurRadius(int radius) {
        return Math.max(1, Math.min(MAX_BLUR_RADIUS, radius));
    }

    static float clampStrength(float strength) {
        if (Float.isNaN(strength) || Float.isInfinite(strength)) {
            throw new IllegalArgumentException("strength must be finite");
        }
        return Math.max(0.0f, Math.min(1.0f, strength));
    }

    public enum Quality {
        LOW(2, 8, 4),
        MEDIUM(3, 12, 6),
        HIGH(4, 16, 8);

        private final int outlineRadius;
        private final int outerBlurRadius;
        private final int coreBlurRadius;

        Quality(int outlineRadius, int outerBlurRadius, int coreBlurRadius) {
            this.outlineRadius = outlineRadius;
            this.outerBlurRadius = outerBlurRadius;
            this.coreBlurRadius = coreBlurRadius;
        }
    }
}
