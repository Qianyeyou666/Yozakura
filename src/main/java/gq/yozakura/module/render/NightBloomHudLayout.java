package gq.yozakura.module.render;

/**
 * Pure dimensions for the compact Night Bloom HUD components.
 */
final class NightBloomHudLayout {
    static final float WATERMARK_HEIGHT = 18.0F;
    static final float WATERMARK_SEGMENT_GAP = 2.0F;
    static final float MODULE_ROW_HEIGHT = 18.0F;
    static final float MODULE_ROW_GAP = 2.0F;
    static final float MODULE_TEXT_GAP = 3.0F;
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
    static final float MIN_MODULE_ROW_WIDTH = 48.0F;
    private static final float BRAND_ICON_WIDTH = 12.0F;
    private static final float BRAND_ICON_CENTER_OFFSET_Y = 0.70F;
    private static final float MODULE_NAME_OPTICAL_OFFSET_Y = 1.0F;
    private static final float MODULE_METADATA_OPTICAL_OFFSET_Y = 2.0F;
    private static final float POTION_VERTICAL_CHROME = 30.0F;

    private NightBloomHudLayout() {
    }

    static float watermarkWidth(float brandWidth, float versionWidth, float fpsWidth) {
        return watermarkBrandWidth(brandWidth) + WATERMARK_SEGMENT_GAP
                + watermarkMetadataWidth(versionWidth) + WATERMARK_SEGMENT_GAP
                + watermarkMetadataWidth(fpsWidth);
    }

    static float watermarkBrandWidth(float brandWidth) {
        return 2.0F + BRAND_ICON_WIDTH + 3.0F + nonNegative(brandWidth) + 3.0F;
    }

    static float watermarkMetadataWidth(float textWidth) {
        return 2.0F + nonNegative(textWidth) + 3.0F;
    }

    static float watermarkBrandIconCenterY(float watermarkY) {
        return watermarkY + WATERMARK_HEIGHT * 0.5F + BRAND_ICON_CENTER_OFFSET_Y;
    }

    static float watermarkTextY(float watermarkY, float watermarkHeight, float fontHeight) {
        return watermarkY + (nonNegative(watermarkHeight) - nonNegative(fontHeight)) * 0.5F;
    }

    static float watermarkBrandTextY(float watermarkY, float watermarkHeight, float fontHeight) {
        return watermarkTextY(watermarkY, watermarkHeight, fontHeight) + 1.0F;
    }

    static float watermarkMetadataTextY(float watermarkY, float watermarkHeight, float fontHeight) {
        return watermarkTextY(watermarkY, watermarkHeight, fontHeight) + 2.0F;
    }

    static float moduleRowWidth(float nameWidth, float metaWidth) {
        float width = 3.0F + nonNegative(nameWidth) + 4.0F;
        if (metaWidth > 0.0F) {
            width += MODULE_TEXT_GAP + metaWidth;
        }
        return width;
    }

    static float moduleNameY(float rowY, float fontHeight) {
        return centeredModuleTextY(rowY, fontHeight, MODULE_NAME_OPTICAL_OFFSET_Y);
    }

    static float moduleMetadataY(float rowY, float fontHeight) {
        return centeredModuleTextY(rowY, fontHeight, MODULE_METADATA_OPTICAL_OFFSET_Y);
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
        return Math.max(firstLeft, secondLeft);
    }

    static float moduleJoinEnd(float firstRight, float secondRight) {
        return Math.min(firstRight, secondRight);
    }

    static boolean moduleJoinRangeValid(float start, float end) {
        return end > start;
    }

    static boolean moduleRowsShareBounds(float firstLeft, float firstRight,
                                         float secondLeft, float secondRight) {
        return Math.abs(firstLeft - secondLeft) <= MODULE_ROW_JOIN_EPSILON
                && Math.abs(firstRight - secondRight) <= MODULE_ROW_JOIN_EPSILON;
    }

    static boolean moduleRowsSharePhysicalBounds(float firstLeft, float firstRight,
                                                 float secondLeft, float secondRight,
                                                 float uiScale) {
        float scale = Math.max(0.01F, uiScale);
        float halfPhysicalPixel = 0.5F;
        return Math.abs(firstLeft - secondLeft) * scale <= halfPhysicalPixel
                && Math.abs(firstRight - secondRight) * scale <= halfPhysicalPixel;
    }

    static boolean moduleJoinReachesRight(float rowWidth, float joinEnd) {
        return joinEnd >= nonNegative(rowWidth) - MODULE_ROW_JOIN_EPSILON;
    }

    static float moduleTopLeftRadius(float rowLeft, float aboveLeft, float radius, boolean joinsAbove) {
        return joinsAbove && rowLeft >= aboveLeft - MODULE_ROW_JOIN_EPSILON
                ? 0.0F : nonNegative(radius);
    }

    static float moduleBottomLeftRadius(float rowLeft, float belowLeft, float radius, boolean joinsBelow) {
        return joinsBelow && rowLeft >= belowLeft - MODULE_ROW_JOIN_EPSILON
                ? 0.0F : nonNegative(radius);
    }

    static float moduleFusionShadowRadius(float radius) {
        return nonNegative(radius) * 0.35F;
    }

    static float moduleRowBottom(float rowY, float nextRowY, boolean joinsBelow) {
        return joinsBelow ? nextRowY : rowY + MODULE_ROW_HEIGHT + MODULE_ROW_GAP;
    }

    static float potionHeight(int effectCount) {
        return potionHeight((float) visiblePotionRows(effectCount));
    }

    static float potionHeight(float visibleRows) {
        float rows = Math.max(1.0F, Math.min(MAX_VISIBLE_POTION_ROWS, visibleRows));
        return POTION_VERTICAL_CHROME + rows * POTION_ROW_HEIGHT;
    }

    static float inventoryGridRight(float gridLeft) {
        return gridLeft + INVENTORY_COLUMNS * INVENTORY_SLOT_STRIDE
                - (INVENTORY_SLOT_STRIDE - INVENTORY_SLOT_SIZE);
    }

    private static int visiblePotionRows(int effectCount) {
        return Math.max(1, Math.min(MAX_VISIBLE_POTION_ROWS, effectCount));
    }

    private static float centeredModuleTextY(float rowY, float fontHeight, float opticalOffsetY) {
        return rowY + (MODULE_ROW_HEIGHT - nonNegative(fontHeight)) * 0.5F + opticalOffsetY;
    }

    private static float nonNegative(float value) {
        return Math.max(0.0F, value);
    }
}
