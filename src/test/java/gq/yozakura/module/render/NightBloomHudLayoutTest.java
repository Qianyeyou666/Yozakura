package gq.yozakura.module.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NightBloomHudLayoutTest {
    private static final float EPSILON = 0.0001F;

    @Test
    public void keepsTheCompactNightBloomRhythm() {
        assertEquals(22.0F, NightBloomHudLayout.WATERMARK_HEIGHT, EPSILON);
        assertEquals(16.0F, NightBloomHudLayout.MODULE_ROW_HEIGHT, EPSILON);
        assertEquals(2.0F, NightBloomHudLayout.MODULE_ROW_GAP, EPSILON);
    }

    @Test
    public void watermarkWidthReservesSeparateVersionAndFpsChips() {
        assertEquals(166.0F, NightBloomHudLayout.watermarkWidth(48.0F, 28.0F, 24.0F), EPSILON);
        assertEquals(120.0F, NightBloomHudLayout.watermarkWidth(12.0F, 10.0F, 10.0F), EPSILON);
    }

    @Test
    public void moduleRowsShareOneRightAnchorAtEveryVisibility() {
        assertEquals(48.0F, NightBloomHudLayout.moduleRowWidth(10.0F, 0.0F), EPSILON);
        assertEquals(85.0F, NightBloomHudLayout.moduleRowWidth(44.0F, 33.0F), EPSILON);
        assertEquals(141.0F, NightBloomHudLayout.moduleRowWidth(100.0F, 33.0F), EPSILON);
        assertEquals(200.0F, NightBloomHudLayout.moduleRowX(300.0F, 100.0F, 0.0F), EPSILON);
        assertEquals(200.0F, NightBloomHudLayout.moduleRowX(300.0F, 100.0F, 0.5F), EPSILON);
        assertEquals(200.0F, NightBloomHudLayout.moduleRowX(300.0F, 100.0F, 1.0F), EPSILON);
        assertEquals(159.0F, NightBloomHudLayout.moduleRowX(300.0F, 141.0F, 1.0F), EPSILON);
    }

    @Test
    public void moduleListHeightUsesAStableEmptyPreviewAndCapsVisibleRows() {
        assertEquals(16.0F, NightBloomHudLayout.moduleListHeight(0), EPSILON);
        assertEquals(52.0F, NightBloomHudLayout.moduleListHeight(3), EPSILON);
        assertEquals(322.0F, NightBloomHudLayout.moduleListHeight(99), EPSILON);
    }

    @Test
    public void exposesTheSingleBlackOuterShadow() {
        assertEquals(0x73000000, NightBloomHudLayout.DEPTH_SHADOW_COLOR);
        assertEquals(0.0F, NightBloomHudLayout.DEPTH_SHADOW_OFFSET_X, EPSILON);
        assertEquals(0.0F, NightBloomHudLayout.DEPTH_SHADOW_OFFSET_Y, EPSILON);
        assertEquals(9.0F, NightBloomHudLayout.DEPTH_SHADOW_BLUR_RADIUS, EPSILON);
    }

    @Test
    public void keepsTheDripLiteSurfaceTokensCompactAndReadable() {
        assertEquals(0xFFFF4FC7, NightBloomHudLayout.PRIMARY_COLOR);
        assertEquals(0xFFEEEEEE, NightBloomHudLayout.SECONDARY_COLOR);
        assertEquals(0xFF16161A, NightBloomHudLayout.SURFACE_COLOR);
        assertEquals(0xFF202025, NightBloomHudLayout.SURFACE_RAISED_COLOR);
        assertEquals(4.0F, NightBloomHudLayout.PANEL_RADIUS, EPSILON);
        assertEquals(0x50000000, NightBloomHudLayout.MODULE_SHADOW_COLOR);
    }

    @Test
    public void potionAndInventoryKeepStableDragBoundsWithCompactContent() {
        assertEquals(166.0F, NightBloomHudLayout.POTION_WIDTH, EPSILON);
        assertEquals(53.0F, NightBloomHudLayout.potionHeight(1), EPSILON);
        assertEquals(168.0F, NightBloomHudLayout.inventoryGridRight(8.0F), EPSILON);
    }
}
