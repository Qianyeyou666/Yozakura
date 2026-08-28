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
    private final float framebufferScale;

    private ScreenSpaceGlowPlan(int collectedTargetCount, int outlineRadius,
                                int outerBlurRadius, int coreBlurRadius, float framebufferScale) {
        this.collectedTargetCount = collectedTargetCount;
        this.outlineRadius = outlineRadius;
        this.outerBlurRadius = outerBlurRadius;
        this.coreBlurRadius = coreBlurRadius;
        this.framebufferScale = framebufferScale;
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
                clampBlurRadius(quality.coreBlurRadius), quality.framebufferScale);
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

    public float getFramebufferScale() {
        return framebufferScale;
    }

    public int scaleDimension(int dimension) {
        if (dimension <= 0) {
            throw new IllegalArgumentException("framebuffer dimension must be positive");
        }
        return Math.max(1, Math.round(dimension * framebufferScale));
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
        // Radii are measured in the scaled target's pixels. They preserve the
        // previous approximate on-screen glow width while cutting off-screen
        // fill rate and texture memory.
        LOW(1, 4, 2, 0.5f),
        MEDIUM(2, 6, 3, 0.5f),
        HIGH(3, 12, 6, 0.75f);

        private final int outlineRadius;
        private final int outerBlurRadius;
        private final int coreBlurRadius;
        private final float framebufferScale;

        Quality(int outlineRadius, int outerBlurRadius, int coreBlurRadius, float framebufferScale) {
            this.outlineRadius = outlineRadius;
            this.outerBlurRadius = outerBlurRadius;
            this.coreBlurRadius = coreBlurRadius;
            this.framebufferScale = framebufferScale;
        }
    }
}
