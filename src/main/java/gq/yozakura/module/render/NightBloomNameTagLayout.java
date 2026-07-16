package gq.yozakura.module.render;

/**
 * Pure geometry for the compact NightBloom world name tag.
 */
final class NightBloomNameTagLayout {
    static final float HEIGHT = 25.0F;
    static final float MIN_WIDTH = 72.0F;
    static final float MAX_WIDTH = 220.0F;
    static final float HORIZONTAL_PADDING = 7.0F;
    static final float CONTENT_GAP = 5.0F;
    static final float BASE_WORLD_SCALE = 0.022F;
    static final float MAX_DISTANCE_SCALE = 1.65F;

    private NightBloomNameTagLayout() {
    }

    static Layout measure(float nameWidth, float healthWidth, float distanceWidth,
                          boolean showHealth, boolean showDistance) {
        float safeNameWidth = Math.max(0.0F, nameWidth);
        float safeHealthWidth = showHealth ? Math.max(0.0F, healthWidth) : 0.0F;
        float safeDistanceWidth = showDistance ? Math.max(0.0F, distanceWidth) : 0.0F;
        float metadataWidth = safeHealthWidth + safeDistanceWidth;
        if (showHealth && showDistance) {
            metadataWidth += CONTENT_GAP;
        }
        float requestedWidth = HORIZONTAL_PADDING * 2.0F + safeNameWidth + metadataWidth;
        if (showHealth || showDistance) {
            requestedWidth += CONTENT_GAP;
        }
        float width = clamp(requestedWidth, MIN_WIDTH, MAX_WIDTH);

        float distanceX = width - HORIZONTAL_PADDING - safeDistanceWidth;
        float healthRight = showDistance ? distanceX - CONTENT_GAP : width - HORIZONTAL_PADDING;
        float healthX = healthRight - safeHealthWidth;
        float metadataX = showHealth ? healthX : distanceX;
        float nameMaxWidth = (showHealth || showDistance)
                ? metadataX - CONTENT_GAP - HORIZONTAL_PADDING
                : width - HORIZONTAL_PADDING * 2.0F;
        return new Layout(width, HEIGHT, Math.max(12.0F, nameMaxWidth),
                healthX, healthRight, distanceX);
    }

    static float healthFraction(float health, float maxHealth) {
        if (maxHealth <= 0.0F) {
            return 0.0F;
        }
        return clamp(health / maxHealth, 0.0F, 1.0F);
    }

    static float worldScale(float distance, float userScale) {
        float distanceFactor = 1.0F + Math.max(0.0F, distance - 12.0F) / 48.0F;
        distanceFactor = clamp(distanceFactor, 1.0F, MAX_DISTANCE_SCALE);
        return BASE_WORLD_SCALE * clamp(userScale, 0.5F, 2.0F) * distanceFactor;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    static final class Layout {
        private final float width;
        private final float height;
        private final float nameMaxWidth;
        private final float healthX;
        private final float healthRight;
        private final float distanceX;

        private Layout(float width, float height, float nameMaxWidth,
                       float healthX, float healthRight, float distanceX) {
            this.width = width;
            this.height = height;
            this.nameMaxWidth = nameMaxWidth;
            this.healthX = healthX;
            this.healthRight = healthRight;
            this.distanceX = distanceX;
        }

        float getWidth() {
            return width;
        }

        float getHeight() {
            return height;
        }

        float getNameMaxWidth() {
            return nameMaxWidth;
        }

        float getHealthX() {
            return healthX;
        }

        float getHealthRight() {
            return healthRight;
        }

        float getDistanceX() {
            return distanceX;
        }
    }
}
