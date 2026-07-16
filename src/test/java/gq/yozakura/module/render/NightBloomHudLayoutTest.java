package gq.yozakura.module.render;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NightBloomHudLayoutTest {
    private static final float EPSILON = 0.0001F;

    @Test
    public void keepsTheCompactNightBloomRhythm() {
        assertEquals(18.0F, NightBloomHudLayout.WATERMARK_HEIGHT, EPSILON);
        assertEquals(16.0F, NightBloomHudLayout.MODULE_ROW_HEIGHT, EPSILON);
        assertEquals(2.0F, NightBloomHudLayout.MODULE_ROW_GAP, EPSILON);
    }

    @Test
    public void watermarkUsesSeparatedArrayListStyleTiles() {
        assertEquals(2.0F, NightBloomHudLayout.WATERMARK_SEGMENT_GAP, EPSILON);
        assertEquals(68.0F, NightBloomHudLayout.watermarkBrandWidth(48.0F), EPSILON);
        assertEquals(33.0F, NightBloomHudLayout.watermarkMetadataWidth(28.0F), EPSILON);
        assertEquals(29.0F, NightBloomHudLayout.watermarkMetadataWidth(24.0F), EPSILON);
        assertEquals(134.0F, NightBloomHudLayout.watermarkWidth(48.0F, 28.0F, 24.0F), EPSILON);
    }

    @Test
    public void watermarkBrandLogoAndTextShareTheTilesGeometricCenter() {
        assertEquals(9.7F, NightBloomHudLayout.watermarkBrandIconCenterY(0.0F), EPSILON);
        assertEquals(41.7F, NightBloomHudLayout.watermarkBrandIconCenterY(32.0F), EPSILON);
        assertEquals(6.0F, NightBloomHudLayout.watermarkBrandTextY(0.0F, 18.0F, 8.0F), EPSILON);
        assertEquals(8.0F, NightBloomHudLayout.watermarkMetadataTextY(0.0F, 18.0F, 6.0F), EPSILON);
        assertEquals(38.0F, NightBloomHudLayout.watermarkBrandTextY(32.0F, 18.0F, 8.0F), EPSILON);
        assertEquals(40.0F, NightBloomHudLayout.watermarkMetadataTextY(32.0F, 18.0F, 6.0F), EPSILON);
    }

    @Test
    public void moduleRowsShareOneRightAnchorAtEveryVisibility() {
        assertEquals(15.0F, NightBloomHudLayout.moduleRowWidth(10.0F, 0.0F), EPSILON);
        assertEquals(83.0F, NightBloomHudLayout.moduleRowWidth(44.0F, 33.0F), EPSILON);
        assertEquals(139.0F, NightBloomHudLayout.moduleRowWidth(100.0F, 33.0F), EPSILON);
        assertEquals(200.0F, NightBloomHudLayout.moduleRowX(300.0F, 100.0F, 0.0F), EPSILON);
        assertEquals(200.0F, NightBloomHudLayout.moduleRowX(300.0F, 100.0F, 0.5F), EPSILON);
        assertEquals(200.0F, NightBloomHudLayout.moduleRowX(300.0F, 100.0F, 1.0F), EPSILON);
        assertEquals(161.0F, NightBloomHudLayout.moduleRowX(300.0F, 139.0F, 1.0F), EPSILON);
    }

    @Test
    public void moduleOrderingUsesTheFinalNightBloomNameAndMetadataWidths() {
        assertEquals(52.0F, NightBloomHudLayout.moduleRowWidth(38.0F, 8.0F), EPSILON);
        assertEquals(56.0F, NightBloomHudLayout.moduleRowWidth(51.0F, 0.0F), EPSILON);
        assertTrue("JumpReset must sort before the visually narrower BlockHit 6r row",
                NightBloomHudLayout.compareModuleRowsByRenderedWidth(52.0F, 56.0F) > 0);
        assertTrue(NightBloomHudLayout.compareModuleRowsByRenderedWidth(56.0F, 52.0F) < 0);
        assertEquals(0, NightBloomHudLayout.compareModuleRowsByRenderedWidth(56.0F, 56.0F));
    }

    @Test
    public void moduleNameAndMetadataUseCompactOpticalAlignment() {
        assertEquals(1.0F, NightBloomHudLayout.MODULE_TEXT_GAP, EPSILON);
        assertEquals(4.0F, NightBloomHudLayout.moduleNameY(0.0F, 10.0F), EPSILON);
        assertEquals(6.0F, NightBloomHudLayout.moduleMetadataY(0.0F, 8.0F), EPSILON);
        assertEquals(36.0F, NightBloomHudLayout.moduleNameY(32.0F, 10.0F), EPSILON);
        assertEquals(38.0F, NightBloomHudLayout.moduleMetadataY(32.0F, 8.0F), EPSILON);
    }

    @Test
    public void moduleListHeightUsesAStableEmptyPreviewAndCapsVisibleRows() {
        assertEquals(18.0F, NightBloomHudLayout.moduleListHeight(0), EPSILON);
        assertEquals(54.0F, NightBloomHudLayout.moduleListHeight(3), EPSILON);
        assertEquals(324.0F, NightBloomHudLayout.moduleListHeight(99), EPSILON);
    }

    @Test
    public void exposesTheSingleBlackOuterShadow() {
        assertEquals(0xFF000000, NightBloomHudLayout.SHADOW_MASK_COLOR);
    }

    @Test
    public void joinsOnlyModuleRowsWhoseBackgroundExtentsActuallyTouch() throws Exception {
        Method rowsTouch = NightBloomHudLayout.class.getDeclaredMethod(
                "moduleRowsTouch", float.class, float.class);
        rowsTouch.setAccessible(true);

        assertTrue((Boolean) rowsTouch.invoke(null, 10.0F, 28.0F));
        assertTrue((Boolean) rowsTouch.invoke(null, 10.0F, 27.98F));
        assertFalse((Boolean) rowsTouch.invoke(null, 10.0F, 27.5F));
        assertFalse((Boolean) rowsTouch.invoke(null, 10.0F, 29.0F));
        assertFalse((Boolean) rowsTouch.invoke(null, 10.0F, 25.0F));
    }

    @Test
    public void touchingRowsShareOneSymmetricJoinRangeAndExactYEdge() {
        float sharedStart = NightBloomHudLayout.moduleJoinStart(90.0F, 100.0F, 4.0F);
        float reversedStart = NightBloomHudLayout.moduleJoinStart(100.0F, 90.0F, 4.0F);
        float sharedEnd = NightBloomHudLayout.moduleJoinEnd(300.0F, 304.0F);
        float reversedEnd = NightBloomHudLayout.moduleJoinEnd(304.0F, 300.0F);

        assertEquals(100.0F, sharedStart, EPSILON);
        assertEquals(sharedStart, reversedStart, EPSILON);
        assertEquals(300.0F, sharedEnd, EPSILON);
        assertEquals(sharedEnd, reversedEnd, EPSILON);
        assertEquals(sharedStart, 90.0F + (sharedStart - 90.0F), EPSILON);
        assertEquals(sharedStart, 100.0F + (sharedStart - 100.0F), EPSILON);
        assertTrue(NightBloomHudLayout.moduleJoinRangeValid(sharedStart, sharedEnd));
        assertTrue(NightBloomHudLayout.moduleJoinReachesRight(200.0F, 200.0F));
        assertTrue(NightBloomHudLayout.moduleJoinReachesRight(200.0F, 199.98F));
        assertFalse("an animated row that extends farther right must keep its exposed corner rounded",
                NightBloomHudLayout.moduleJoinReachesRight(200.0F, 195.0F));

        assertEquals(27.98F, NightBloomHudLayout.moduleRowBottom(10.0F, 27.98F, true), EPSILON);
        assertEquals(28.0F, NightBloomHudLayout.moduleRowBottom(10.0F, 27.98F, false), EPSILON);
    }

    @Test
    public void fusedModuleStepsFlattenOnlyTheInsetLeftCorners() {
        float radius = NightBloomHudLayout.PANEL_RADIUS;

        assertEquals(0.0F, NightBloomHudLayout.moduleTopLeftRadius(
                110.0F, 90.0F, radius, true), EPSILON);
        assertEquals(radius, NightBloomHudLayout.moduleTopLeftRadius(
                90.0F, 110.0F, radius, true), EPSILON);
        assertEquals(radius, NightBloomHudLayout.moduleTopLeftRadius(
                110.0F, 90.0F, radius, false), EPSILON);

        assertEquals(0.0F, NightBloomHudLayout.moduleBottomLeftRadius(
                110.0F, 90.0F, radius, true), EPSILON);
        assertEquals(radius, NightBloomHudLayout.moduleBottomLeftRadius(
                90.0F, 110.0F, radius, true), EPSILON);
        assertEquals(radius, NightBloomHudLayout.moduleBottomLeftRadius(
                110.0F, 90.0F, radius, false), EPSILON);
    }

    @Test
    public void keepsTheDripLiteSurfaceTokensCompactAndReadable() {
        assertEquals(0xFFFF4FC7, NightBloomHudLayout.PRIMARY_COLOR);
        assertEquals(0xFFEEEEEE, NightBloomHudLayout.SECONDARY_COLOR);
        assertEquals(0xFF16161A, NightBloomHudLayout.SURFACE_COLOR);
        assertEquals(0xFF202025, NightBloomHudLayout.SURFACE_RAISED_COLOR);
        assertEquals(4.0F, NightBloomHudLayout.PANEL_RADIUS, EPSILON);
        assertEquals(0xFF000000, NightBloomHudLayout.SHADOW_MASK_COLOR);
    }

    @Test
    public void potionAndInventoryKeepStableDragBoundsWithCompactContent() {
        assertEquals(166.0F, NightBloomHudLayout.POTION_WIDTH, EPSILON);
        assertEquals(53.0F, NightBloomHudLayout.potionHeight(1), EPSILON);
        assertEquals(64.5F, NightBloomHudLayout.potionHeight(1.5F), EPSILON);
        assertEquals(168.0F, NightBloomHudLayout.inventoryGridRight(8.0F), EPSILON);
    }
}
