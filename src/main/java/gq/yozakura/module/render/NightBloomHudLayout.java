package gq.yozakura.module.render;

/**
 * Pure dimensions for the compact Night Bloom HUD components.
 */
final class NightBloomHudLayout {
    static final float WATERMARK_HEIGHT = 22.0F;
    static final float MODULE_ROW_HEIGHT = 16.0F;
    static final float MODULE_ROW_GAP = 2.0F;
    static final float POTION_WIDTH = 166.0F;
    static final float POTION_ROW_HEIGHT = 23.0F;
    static final float INVENTORY_WIDTH = 178.0F;
    static final float INVENTORY_HEIGHT = 88.0F;
    static final float INVENTORY_SLOT_SIZE = 16.0F;
    static final float INVENTORY_SLOT_STRIDE = 18.0F;

    static final int PRIMARY_COLOR = 0xFFFF4FC7;
    static final int SECONDARY_COLOR = 0xFFEEEEEE;
    static final int SURFACE_COLOR = 0xFF16161A;
    static final int SURFACE_RAISED_COLOR = 0xFF202025;
    static final float PANEL_RADIUS = 4.0F;

    static final int SHADOW_MASK_COLOR = 0xFF000000;
    private static final float MODULE_ROW_JOIN_EPSILON = 0.05F;

    static final int MAX_VISIBLE_MODULE_ROWS = 18;
    static final int MAX_VISIBLE_POTION_ROWS = 6;
    static final int INVENTORY_COLUMNS = 9;
    private static final float MIN_WATERMARK_WIDTH = 120.0F;
    static final float MIN_MODULE_ROW_WIDTH = 48.0F;
    private static final float BRAND_ICON_WIDTH = 12.0F;
    private static final float POTION_VERTICAL_CHROME = 30.0F;

    private NightBloomHudLayout() {
    }

    static float watermarkWidth(float brandWidth, float versionWidth, float fpsWidth) {
        float width = 7.0F + BRAND_ICON_WIDTH + 5.0F + nonNegative(brandWidth)
                + 7.0F + chipWidth(versionWidth) + 4.0F + chipWidth(fpsWidth) + 7.0F;
        return Math.max(MIN_WATERMARK_WIDTH, width);
    }

    static float moduleRowWidth(float nameWidth, float metaWidth) {
        float width = 2.0F + nonNegative(nameWidth) + 3.0F;
        if (metaWidth > 0.0F) {
            width += 3.0F + metaWidth;
        }
        return width;
    }

    static int compareModuleRowsByRenderedWidth(float firstWidth, float secondWidth) {
        return Float.compare(secondWidth, firstWidth);
    }

    static float moduleRowX(float right, float rowWidth, float visibility) {
        // Visibility controls opacity only; every row keeps the same right anchor.
        return right - nonNegative(rowWidth);
    }

    static float moduleListHeight(int visibleRows) {
        int rows = Math.max(1, Math.min(MAX_VISIBLE_MODULE_ROWS, visibleRows));
        return rows * (MODULE_ROW_HEIGHT + MODULE_ROW_GAP);
    }

    static boolean moduleRowsTouch(float upperY, float lowerY) {
        float expectedLowerY = upperY + MODULE_ROW_HEIGHT + MODULE_ROW_GAP;
        return Math.abs(lowerY - expectedLowerY) <= MODULE_ROW_JOIN_EPSILON;
    }

    static float moduleJoinStart(float firstLeft, float secondLeft, float radius) {
        return Math.max(firstLeft, secondLeft) + nonNegative(radius);
    }

    static float moduleJoinEnd(float firstRight, float secondRight) {
        return Math.min(firstRight, secondRight);
    }

    static boolean moduleJoinRangeValid(float start, float end) {
        return end > start;
    }

    static boolean moduleJoinReachesRight(float rowWidth, float joinEnd) {
        return joinEnd >= nonNegative(rowWidth) - MODULE_ROW_JOIN_EPSILON;
    }

    static float moduleRowBottom(float rowY, float nextRowY, boolean joinsBelow) {
        return joinsBelow ? nextRowY : rowY + MODULE_ROW_HEIGHT + MODULE_ROW_GAP;
    }

    static float potionHeight(int effectCount) {
        return POTION_VERTICAL_CHROME + visiblePotionRows(effectCount) * POTION_ROW_HEIGHT;
    }

    static float inventoryGridRight(float gridLeft) {
        return gridLeft + INVENTORY_COLUMNS * INVENTORY_SLOT_STRIDE
                - (INVENTORY_SLOT_STRIDE - INVENTORY_SLOT_SIZE);
    }

    private static int visiblePotionRows(int effectCount) {
        return Math.max(1, Math.min(MAX_VISIBLE_POTION_ROWS, effectCount));
    }

    private static float chipWidth(float textWidth) {
        return nonNegative(textWidth) + 12.0F;
    }

    private static float nonNegative(float value) {
        return Math.max(0.0F, value);
    }
}
