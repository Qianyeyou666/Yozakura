package gq.yozakura.util.notification;

/**
 * Layout and timing math for the Night Bloom notification theme.
 */
final class NightBloomNotificationLayout {
    static final float PANEL_RADIUS = 4.0F;
    private static final float SIDE_INSET = 9.0F;
    private static final float ICON_SIZE = 22.0F;
    private static final float TEXT_GAP = 8.0F;
    private static final float MIN_PANEL_WIDTH = 190.0F;
    private static final float MAX_PANEL_WIDTH = 248.0F;
    private static final float HORIZONTAL_CHROME = 58.0F;
    private static final float ICON_TILE_WIDTH = 32.0F;
    private static final float CONTENT_INSET = 7.0F;
    private static final float LIQUID_GAP = 6.0F;
    private static final float MIN_NECK_RATIO = 0.22F;
    private static final float NECK_FADE_END = 0.24F;
    private static final float EDGE_EXPANSION_START = 0.16F;
    private static final float COMPOSITE_START = 0.32F;
    private static final float EPSILON = 0.01F;

    private NightBloomNotificationLayout() {
    }

    static float alphaForSlide(float slide) {
        float clamped = clamp(slide, 0.0F, 1.0F);
        float smooth = clamped * clamped * (3.0F - 2.0F * clamped);
        return 1.0F - smooth;
    }

    static float progressForLifetime(long elapsedMillis, long stayMillis) {
        if (stayMillis <= 0L) {
            return 0.0F;
        }
        return 1.0F - clamp(elapsedMillis / (float) stayMillis, 0.0F, 1.0F);
    }

    static float panelWidth(float titleWidth, float messageWidth) {
        float contentWidth = Math.max(Math.max(0.0F, titleWidth), Math.max(0.0F, messageWidth));
        return clamp(contentWidth + HORIZONTAL_CHROME, MIN_PANEL_WIDTH, MAX_PANEL_WIDTH);
    }

    static float panelHeight(boolean hasMessage) {
        return hasMessage ? 46.0F : 38.0F;
    }

    static Layout create(float left, float top, float right, float bottom) {
        float iconLeft = left + SIDE_INSET;
        float iconTop = top + SIDE_INSET;
        float textX = iconLeft + ICON_SIZE + TEXT_GAP;
        float progressLeft = left + SIDE_INSET;
        float progressRight = right - SIDE_INSET;
        return new Layout(iconLeft, iconTop, ICON_SIZE, textX, top + 7.0F, top + 22.0F,
                progressLeft, bottom - 3.8F, progressRight, bottom - 2.0F);
    }

    /**
     * Produces the two local surfaces that make up one notification. The pair never knows about
     * another notification: its bridge and composite are bounded by the supplied rectangle.
     *
     * <p>The geometry mirrors the Watermark liquid sequence: a narrow neck appears first,
     * facing edges flatten as it widens, then one composite surface replaces both tiles.</p>
     */
    static LiquidPair createLiquidPair(float left, float top, float right, float bottom, float fusionProgress) {
        float resolvedLeft = Math.min(left, right);
        float resolvedRight = Math.max(left, right);
        float resolvedTop = Math.min(top, bottom);
        float resolvedBottom = Math.max(top, bottom);
        float width = Math.max(0.0F, resolvedRight - resolvedLeft);
        float height = Math.max(0.0F, resolvedBottom - resolvedTop);
        float progress = clamp(fusionProgress, 0.0F, 1.0F);

        float iconWidth = Math.min(ICON_TILE_WIDTH, Math.max(0.0F, width - CONTENT_INSET));
        float iconRight = resolvedLeft + iconWidth;
        float bodyLeft = Math.min(resolvedRight, iconRight + LIQUID_GAP * (1.0F - progress));
        float bridgeOpacity = smoothStep(progress / NECK_FADE_END);
        float edgeProgress = smoothStep((progress - EDGE_EXPANSION_START)
                / (1.0F - EDGE_EXPANSION_START));
        float neckHeight = height * bridgeOpacity
                * (MIN_NECK_RATIO + (1.0F - MIN_NECK_RATIO) * edgeProgress);
        float bridgeTop = (resolvedTop + resolvedBottom - neckHeight) * 0.5F;
        float compositeProgress = smoothStep((progress - COMPOSITE_START) / (1.0F - COMPOSITE_START));

        return new LiquidPair(resolvedLeft, resolvedTop, iconRight, resolvedBottom,
                bodyLeft, resolvedRight, iconRight, bridgeTop, bodyLeft, bridgeTop + neckHeight,
                resolvedLeft, resolvedTop, resolvedRight, resolvedBottom,
                bridgeOpacity, edgeProgress, compositeProgress);
    }

    /**
     * Returns the source-opacity factor relative to the base surface alpha needed to replace
     * fading individual tiles without deepening the shared area during enter or exit.
     */
    static float fusedCompositeSurfaceOpacity(float baseOpacity, float panelAlpha, float compositeProgress) {
        float base = clamp(baseOpacity, 0.0F, 1.0F);
        float desired = base * clamp(panelAlpha, 0.0F, 1.0F);
        if (base <= EPSILON || desired <= EPSILON) {
            return 0.0F;
        }
        float individual = desired * (1.0F - clamp(compositeProgress, 0.0F, 1.0F));
        float composite = (desired - individual) / Math.max(EPSILON, 1.0F - individual);
        return composite / base;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float smoothStep(float value) {
        float clamped = clamp(value, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    static final class Layout {
        private final float iconLeft;
        private final float iconTop;
        private final float iconSize;
        private final float titleX;
        private final float titleY;
        private final float messageY;
        private final float progressLeft;
        private final float progressTop;
        private final float progressRight;
        private final float progressBottom;

        private Layout(float iconLeft, float iconTop, float iconSize, float titleX, float titleY, float messageY,
                       float progressLeft, float progressTop, float progressRight, float progressBottom) {
            this.iconLeft = iconLeft;
            this.iconTop = iconTop;
            this.iconSize = iconSize;
            this.titleX = titleX;
            this.titleY = titleY;
            this.messageY = messageY;
            this.progressLeft = progressLeft;
            this.progressTop = progressTop;
            this.progressRight = progressRight;
            this.progressBottom = progressBottom;
        }

        float getIconLeft() {
            return iconLeft;
        }

        float getIconTop() {
            return iconTop;
        }

        float getIconSize() {
            return iconSize;
        }

        float getTitleX() {
            return titleX;
        }

        float getTitleY() {
            return titleY;
        }

        float getMessageY() {
            return messageY;
        }

        float getProgressLeft() {
            return progressLeft;
        }

        float getProgressTop() {
            return progressTop;
        }

        float getProgressRight() {
            return progressRight;
        }

        float getProgressBottom() {
            return progressBottom;
        }
    }

    static final class LiquidPair {
        private final float iconLeft;
        private final float iconTop;
        private final float iconRight;
        private final float iconBottom;
        private final float bodyLeft;
        private final float bodyRight;
        private final float bridgeLeft;
        private final float bridgeTop;
        private final float bridgeRight;
        private final float bridgeBottom;
        private final float compositeLeft;
        private final float compositeTop;
        private final float compositeRight;
        private final float compositeBottom;
        private final float bridgeOpacity;
        private final float edgeProgress;
        private final float compositeProgress;

        private LiquidPair(float iconLeft, float iconTop, float iconRight, float iconBottom,
                           float bodyLeft, float bodyRight,
                           float bridgeLeft, float bridgeTop, float bridgeRight, float bridgeBottom,
                           float compositeLeft, float compositeTop, float compositeRight, float compositeBottom,
                           float bridgeOpacity, float edgeProgress, float compositeProgress) {
            this.iconLeft = iconLeft;
            this.iconTop = iconTop;
            this.iconRight = iconRight;
            this.iconBottom = iconBottom;
            this.bodyLeft = bodyLeft;
            this.bodyRight = bodyRight;
            this.bridgeLeft = bridgeLeft;
            this.bridgeTop = bridgeTop;
            this.bridgeRight = bridgeRight;
            this.bridgeBottom = bridgeBottom;
            this.compositeLeft = compositeLeft;
            this.compositeTop = compositeTop;
            this.compositeRight = compositeRight;
            this.compositeBottom = compositeBottom;
            this.bridgeOpacity = bridgeOpacity;
            this.edgeProgress = edgeProgress;
            this.compositeProgress = compositeProgress;
        }

        boolean isRenderable() {
            return iconRight - iconLeft > EPSILON && bodyRight - bodyLeft > EPSILON
                    && iconBottom - iconTop > EPSILON;
        }

        boolean hasVisibleBridge() {
            return bridgeOpacity > EPSILON && bridgeRight - bridgeLeft > EPSILON
                    && bridgeBottom - bridgeTop > EPSILON;
        }

        float getIconLeft() {
            return iconLeft;
        }

        float getIconTop() {
            return iconTop;
        }

        float getIconRight() {
            return iconRight;
        }

        float getIconBottom() {
            return iconBottom;
        }

        float getIconCenterX() {
            return (iconLeft + iconRight) * 0.5F;
        }

        float getIconCenterY() {
            return (iconTop + iconBottom) * 0.5F;
        }

        float getIconSize() {
            return Math.min(ICON_SIZE, Math.min(iconRight - iconLeft, iconBottom - iconTop));
        }

        float getBodyLeft() {
            return bodyLeft;
        }

        float getBodyRight() {
            return bodyRight;
        }

        float getBridgeLeft() {
            return bridgeLeft;
        }

        float getBridgeTop() {
            return bridgeTop;
        }

        float getBridgeRight() {
            return bridgeRight;
        }

        float getBridgeBottom() {
            return bridgeBottom;
        }

        float getBridgeOpacity() {
            return bridgeOpacity;
        }

        float getBridgeRadius() {
            float size = Math.min(bridgeRight - bridgeLeft, bridgeBottom - bridgeTop);
            return Math.min(2.0F, Math.max(0.0F, size) * 0.5F) * (1.0F - edgeProgress);
        }

        float getEdgeProgress() {
            return edgeProgress;
        }

        float getCompositeLeft() {
            return compositeLeft;
        }

        float getCompositeTop() {
            return compositeTop;
        }

        float getCompositeRight() {
            return compositeRight;
        }

        float getCompositeBottom() {
            return compositeBottom;
        }

        float getCompositeProgress() {
            return compositeProgress;
        }

        float getTitleX() {
            return bodyLeft + CONTENT_INSET;
        }

        float getProgressLeft() {
            return getTitleX();
        }

        float getProgressRight() {
            return Math.max(getProgressLeft(), bodyRight - SIDE_INSET);
        }

        float getRightJoinStart() {
            return hasVisibleBridge() ? bridgeTop - iconTop : 1.0F;
        }

        float getRightJoinEnd() {
            return hasVisibleBridge() ? bridgeBottom - iconTop : 0.0F;
        }

        float getLeftJoinStart() {
            return hasVisibleBridge() ? bridgeTop - iconTop : 1.0F;
        }

        float getLeftJoinEnd() {
            return hasVisibleBridge() ? bridgeBottom - iconTop : 0.0F;
        }
    }
}
