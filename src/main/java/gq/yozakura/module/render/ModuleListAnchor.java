package gq.yozakura.module.render;

/**
 * Shared horizontal anchoring for the HUD module list. The list keeps its stored top-left drag
 * bounds, while rows and text switch their visual anchor when the list center crosses the screen
 * midpoint.
 */
final class ModuleListAnchor {
    private static final float EXIT_OFFSET = 8.0F;

    private ModuleListAnchor() {
    }

    static boolean isRightSide(float listX, float listWidth, float screenWidth) {
        return listX + Math.max(0.0F, listWidth) * 0.5F >= Math.max(0.0F, screenWidth) * 0.5F;
    }

    static float rowX(float listX, float listWidth, float rowWidth, float visibility, boolean rightSide) {
        float width = Math.max(0.0F, rowWidth);
        float hidden = 1.0F - clamp01(visibility);
        if (rightSide) {
            return listX + Math.max(0.0F, listWidth) - width + hidden * EXIT_OFFSET;
        }
        return listX - hidden * EXIT_OFFSET;
    }

    static float textX(float listX, float listWidth, float textWidth, float padding, boolean rightSide) {
        float inset = Math.max(0.0F, padding);
        return rightSide
                ? listX + Math.max(0.0F, listWidth) - inset - Math.max(0.0F, textWidth)
                : listX + inset;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
