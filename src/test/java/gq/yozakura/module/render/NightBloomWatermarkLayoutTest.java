package gq.yozakura.module.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NightBloomWatermarkLayoutTest {
    private static final float EPSILON = 0.0001F;

    @Test
    public void snapsAndMergesTilesAlongBothAxes() {
        NightBloomWatermarkLayout layout = new NightBloomWatermarkLayout();
        frame(layout, false, 0.0F, 0.0F, false, false, 0.0F);

        frame(layout, true, 110.0F, 19.0F, true, false, 0.0F);
        frame(layout, true, 84.0F, 19.0F, true, false, 0.0F);
        NightBloomWatermarkLayout.Snapshot horizontal = frame(layout, true, 84.0F, 19.0F,
                false, false, 0.0F);

        assertTrue(horizontal.isGrouped(NightBloomWatermarkLayout.Tile.BRAND,
                NightBloomWatermarkLayout.Tile.VERSION));
        assertEquals(70.0F, horizontal.tile(NightBloomWatermarkLayout.Tile.VERSION).getTargetX(), EPSILON);

        settle(layout);
        frame(layout, true, 155.0F, 89.0F, true, false, 0.0F);
        frame(layout, true, 15.0F, 37.0F, true, false, 0.0F);
        NightBloomWatermarkLayout.Snapshot vertical = frame(layout, true, 15.0F, 37.0F,
                false, false, 0.0F);

        assertTrue(vertical.isGrouped(NightBloomWatermarkLayout.Tile.BRAND,
                NightBloomWatermarkLayout.Tile.STATUS));
        assertEquals(10.0F, vertical.tile(NightBloomWatermarkLayout.Tile.STATUS).getTargetX(), EPSILON);
        assertEquals(28.0F, vertical.tile(NightBloomWatermarkLayout.Tile.STATUS).getTargetY(), EPSILON);
    }

    @Test
    public void mergedTilesMoveAsOneAndRightClickSplitsThemWithAnAnimatedGap() {
        NightBloomWatermarkLayout layout = new NightBloomWatermarkLayout();
        frame(layout, false, 0.0F, 0.0F, false, false, 0.0F);
        mergeBrandAndVersion(layout);
        settle(layout);

        frame(layout, true, 20.0F, 19.0F, true, false, 0.0F);
        NightBloomWatermarkLayout.Snapshot moved = frame(layout, true, 40.0F, 29.0F,
                true, false, 0.0F);

        assertEquals(30.0F, moved.tile(NightBloomWatermarkLayout.Tile.BRAND).getTargetX(), EPSILON);
        assertEquals(20.0F, moved.tile(NightBloomWatermarkLayout.Tile.BRAND).getTargetY(), EPSILON);
        assertEquals(90.0F, moved.tile(NightBloomWatermarkLayout.Tile.VERSION).getTargetX(), EPSILON);
        assertEquals(20.0F, moved.tile(NightBloomWatermarkLayout.Tile.VERSION).getTargetY(), EPSILON);

        frame(layout, true, 40.0F, 29.0F, false, false, 0.0F);
        NightBloomWatermarkLayout.Snapshot split = frame(layout, true, 40.0F, 29.0F,
                false, true, 0.0F);

        assertFalse(split.isGrouped(NightBloomWatermarkLayout.Tile.BRAND,
                NightBloomWatermarkLayout.Tile.VERSION));
        assertTrue(split.tile(NightBloomWatermarkLayout.Tile.VERSION).getTargetX()
                > split.tile(NightBloomWatermarkLayout.Tile.BRAND).getTargetX() + 60.0F);
        assertTrue(split.hasLiquidTransition());
    }

    @Test
    public void snapsTargetsImmediatelyButAnimatesTheRenderedMergePosition() {
        NightBloomWatermarkLayout layout = new NightBloomWatermarkLayout();
        frame(layout, false, 0.0F, 0.0F, false, false, 0.0F);

        frame(layout, true, 110.0F, 19.0F, true, false, 0.0F);
        frame(layout, true, 88.0F, 19.0F, true, false, 0.0F);
        NightBloomWatermarkLayout.Snapshot released = frame(layout, true, 88.0F, 19.0F,
                false, false, 0.0F);

        NightBloomWatermarkLayout.TileView version = released.tile(NightBloomWatermarkLayout.Tile.VERSION);
        assertEquals(70.0F, version.getTargetX(), EPSILON);
        assertTrue("the tile should travel into its snapped edge instead of teleporting", version.getX() > 70.0F);

        NightBloomWatermarkLayout.Snapshot advanced = frame(layout, true, 88.0F, 19.0F,
                false, false, 0.05F);
        assertTrue(advanced.tile(NightBloomWatermarkLayout.Tile.VERSION).getX() < version.getX());
    }

    @Test
    public void rightClickDetachesOnlyTheHitTileFromAComposite() {
        NightBloomWatermarkLayout layout = new NightBloomWatermarkLayout();
        frame(layout, false, 0.0F, 0.0F, false, false, 0.0F);
        mergeBrandAndVersion(layout);
        settle(layout);
        frame(layout, true, 155.0F, 89.0F, true, false, 0.0F);
        frame(layout, true, 15.0F, 37.0F, true, false, 0.0F);
        frame(layout, true, 15.0F, 37.0F, false, false, 0.0F);
        settle(layout);

        NightBloomWatermarkLayout.Snapshot split = frame(layout, true, 85.0F, 19.0F,
                false, true, 0.0F);

        assertFalse(split.isGrouped(NightBloomWatermarkLayout.Tile.BRAND,
                NightBloomWatermarkLayout.Tile.VERSION));
        assertTrue("right-clicking Version must leave the Brand/Status attachment intact",
                split.isGrouped(NightBloomWatermarkLayout.Tile.BRAND,
                        NightBloomWatermarkLayout.Tile.STATUS));
    }

    @Test
    public void doesNotAllowTwoTilesToFuseIntoTheSameSideOfOneTile() {
        NightBloomWatermarkLayout layout = new NightBloomWatermarkLayout();
        frame(layout, false, 0.0F, 0.0F, false, false, 0.0F);
        mergeBrandAndVersion(layout);
        settle(layout);

        frame(layout, true, 155.0F, 89.0F, true, false, 0.0F);
        frame(layout, true, 75.0F, 19.0F, true, false, 0.0F);
        NightBloomWatermarkLayout.Snapshot released = frame(layout, true, 75.0F, 19.0F,
                false, false, 0.0F);

        assertTrue(released.isGrouped(NightBloomWatermarkLayout.Tile.BRAND,
                NightBloomWatermarkLayout.Tile.VERSION));
        assertFalse("Brand's right edge is already occupied by Version and must reject Status",
                released.isGrouped(NightBloomWatermarkLayout.Tile.BRAND,
                        NightBloomWatermarkLayout.Tile.STATUS));
    }

    @Test
    public void defaultTilesFollowTheirScaledInputsWhileDraggedTilesKeepTheirAnchors() {
        NightBloomWatermarkLayout defaults = new NightBloomWatermarkLayout();
        scalableFrame(defaults, 1.0F, true);
        NightBloomWatermarkLayout.Snapshot scaledDefaults = scalableFrame(defaults, 1.5F, true);

        assertEquals(103.0F, scaledDefaults.tile(NightBloomWatermarkLayout.Tile.VERSION).getTargetX(), EPSILON);
        assertEquals(148.0F, scaledDefaults.tile(NightBloomWatermarkLayout.Tile.STATUS).getTargetX(), EPSILON);
        assertEquals(3.0F, scaledDefaults.tile(NightBloomWatermarkLayout.Tile.VERSION).getTargetX()
                - scaledDefaults.tile(NightBloomWatermarkLayout.Tile.BRAND).getTargetX()
                - scaledDefaults.tile(NightBloomWatermarkLayout.Tile.BRAND).getWidth(), EPSILON);
        assertEquals(3.0F, scaledDefaults.tile(NightBloomWatermarkLayout.Tile.STATUS).getTargetX()
                - scaledDefaults.tile(NightBloomWatermarkLayout.Tile.VERSION).getTargetX()
                - scaledDefaults.tile(NightBloomWatermarkLayout.Tile.VERSION).getWidth(), EPSILON);

        NightBloomWatermarkLayout anchored = new NightBloomWatermarkLayout();
        scalableFrame(anchored, 1.0F, false);
        NightBloomWatermarkLayout.Snapshot scaledAnchored = scalableFrame(anchored, 1.5F, false);

        assertEquals(72.0F, scaledAnchored.tile(NightBloomWatermarkLayout.Tile.VERSION).getTargetX(), EPSILON);
        assertEquals(102.0F, scaledAnchored.tile(NightBloomWatermarkLayout.Tile.STATUS).getTargetX(), EPSILON);
    }

    @Test
    public void draggingBrandDoesNotClaimPersistenceForUntouchedDefaultTiles() {
        NightBloomWatermarkLayout layout = new NightBloomWatermarkLayout();
        scalableFrame(layout, 1.0F, true);

        frame(layout, true, 20.0F, 19.0F, true, false, 0.0F);
        frame(layout, true, 40.0F, 19.0F, true, false, 0.0F);
        NightBloomWatermarkLayout.Snapshot released = frame(layout, true, 40.0F, 19.0F,
                false, false, 0.0F);

        assertTrue(released.shouldPersist(NightBloomWatermarkLayout.Tile.BRAND));
        assertFalse(released.shouldPersist(NightBloomWatermarkLayout.Tile.VERSION));
        assertFalse(released.shouldPersist(NightBloomWatermarkLayout.Tile.STATUS));
    }

    @Test
    public void fusingWithADefaultTileClaimsBothEndsWithoutClaimingTheNextDefaultTile() {
        NightBloomWatermarkLayout layout = new NightBloomWatermarkLayout();
        scalableFrame(layout, 1.0F, true);

        frame(layout, true, 20.0F, 19.0F, true, false, 0.0F);
        frame(layout, true, 22.0F, 19.0F, true, false, 0.0F);
        NightBloomWatermarkLayout.Snapshot released = frame(layout, true, 22.0F, 19.0F,
                false, false, 0.0F);

        assertTrue(released.isGrouped(NightBloomWatermarkLayout.Tile.BRAND,
                NightBloomWatermarkLayout.Tile.VERSION));
        assertTrue(released.shouldPersist(NightBloomWatermarkLayout.Tile.BRAND));
        assertTrue(released.shouldPersist(NightBloomWatermarkLayout.Tile.VERSION));
        assertFalse(released.shouldPersist(NightBloomWatermarkLayout.Tile.STATUS));
    }

    @Test
    public void fusedDefaultTileDoesNotDriftWhenItsFallbackMovesWithTheDraggedBrand() {
        NightBloomWatermarkLayout layout = new NightBloomWatermarkLayout();
        scalableFrame(layout, 1.0F, true);
        frame(layout, true, 20.0F, 19.0F, true, false, 0.0F);
        frame(layout, true, 22.0F, 19.0F, true, false, 0.0F);
        NightBloomWatermarkLayout.Snapshot merged = frame(layout, true, 22.0F, 19.0F,
                false, false, 0.0F);

        for (int index = 0; index < 4; index++) {
            merged = fallbackFrameAfterBrandDrag(layout);
        }

        assertEquals(12.0F, merged.tile(NightBloomWatermarkLayout.Tile.BRAND).getTargetX(), EPSILON);
        assertEquals(72.0F, merged.tile(NightBloomWatermarkLayout.Tile.VERSION).getTargetX(), EPSILON);
    }

    @Test
    public void clickingADefaultTileWithoutMovingItDoesNotFreezeItsScaleAwarePosition() {
        NightBloomWatermarkLayout layout = new NightBloomWatermarkLayout();
        scalableFrame(layout, 1.0F, true);

        frame(layout, true, 82.0F, 19.0F, true, false, 0.0F);
        NightBloomWatermarkLayout.Snapshot released = frame(layout, true, 82.0F, 19.0F,
                false, false, 0.0F);

        assertFalse(released.isDirty());
        assertFalse(released.shouldPersist(NightBloomWatermarkLayout.Tile.VERSION));
        NightBloomWatermarkLayout.Snapshot scaled = scalableFrame(layout, 1.5F, true);
        assertEquals(103.0F, scaled.tile(NightBloomWatermarkLayout.Tile.VERSION).getTargetX(), EPSILON);
    }

    @Test
    public void persistedFusionRestoresItsAttachmentAfterTheLayoutIsRecreated() {
        NightBloomWatermarkLayout restored = new NightBloomWatermarkLayout();
        NightBloomWatermarkLayout.Snapshot snapshot = restored.update(new NightBloomWatermarkLayout.Frame(
                320.0F, 180.0F, 1.0F, 0.0F,
                false, 0.0F, 0.0F, false, false,
                new NightBloomWatermarkLayout.TileInput(NightBloomWatermarkLayout.Tile.BRAND,
                        12.0F, 10.0F, 60.0F, 18.0F),
                new NightBloomWatermarkLayout.TileInput(NightBloomWatermarkLayout.Tile.VERSION,
                        72.0F, 10.0F, 28.0F, 18.0F),
                new NightBloomWatermarkLayout.TileInput(NightBloomWatermarkLayout.Tile.STATUS,
                        102.0F, 10.0F, 52.0F, 18.0F, true)));

        assertTrue(snapshot.isGrouped(NightBloomWatermarkLayout.Tile.BRAND,
                NightBloomWatermarkLayout.Tile.VERSION));
    }

    @Test
    public void externalDockProxyMovesEveryTileAndPersistsTheSharedOffset() {
        NightBloomWatermarkLayout layout = new NightBloomWatermarkLayout();
        frame(layout, false, 0.0F, 0.0F, false, false, 0.0F);

        NightBloomWatermarkLayout.Snapshot shifted = layout.translateAll(16.0F, -5.0F);

        assertEquals(26.0F, shifted.tile(NightBloomWatermarkLayout.Tile.BRAND).getX(), EPSILON);
        assertEquals(116.0F, shifted.tile(NightBloomWatermarkLayout.Tile.VERSION).getX(), EPSILON);
        assertEquals(75.0F, shifted.tile(NightBloomWatermarkLayout.Tile.STATUS).getY(), EPSILON);
        assertTrue(shifted.shouldPersist(NightBloomWatermarkLayout.Tile.BRAND));
        assertTrue(shifted.shouldPersist(NightBloomWatermarkLayout.Tile.VERSION));
        assertTrue(shifted.shouldPersist(NightBloomWatermarkLayout.Tile.STATUS));
    }

    private static void mergeBrandAndVersion(NightBloomWatermarkLayout layout) {
        frame(layout, true, 110.0F, 19.0F, true, false, 0.0F);
        frame(layout, true, 84.0F, 19.0F, true, false, 0.0F);
        frame(layout, true, 84.0F, 19.0F, false, false, 0.0F);
    }

    private static void settle(NightBloomWatermarkLayout layout) {
        for (int index = 0; index < 6; index++) {
            frame(layout, true, 0.0F, 0.0F, false, false, 0.05F);
        }
    }

    private static NightBloomWatermarkLayout.Snapshot scalableFrame(NightBloomWatermarkLayout layout,
                                                                      float scale, boolean followDefaults) {
        float gap = 2.0F * scale;
        float brandWidth = 60.0F * scale;
        float versionWidth = 28.0F * scale;
        float statusWidth = 52.0F * scale;
        float height = 18.0F * scale;
        float versionX = 10.0F + brandWidth + gap;
        float statusX = versionX + versionWidth + gap;
        return layout.update(new NightBloomWatermarkLayout.Frame(
                320.0F, 180.0F, scale, 0.0F,
                false, 0.0F, 0.0F, false, false,
                new NightBloomWatermarkLayout.TileInput(NightBloomWatermarkLayout.Tile.BRAND,
                        10.0F, 10.0F, brandWidth, height),
                new NightBloomWatermarkLayout.TileInput(NightBloomWatermarkLayout.Tile.VERSION,
                        versionX, 10.0F, versionWidth, height, followDefaults),
                new NightBloomWatermarkLayout.TileInput(NightBloomWatermarkLayout.Tile.STATUS,
                        statusX, 10.0F, statusWidth, height, followDefaults)));
    }

    private static NightBloomWatermarkLayout.Snapshot fallbackFrameAfterBrandDrag(
            NightBloomWatermarkLayout layout) {
        return layout.update(new NightBloomWatermarkLayout.Frame(
                320.0F, 180.0F, 1.0F, 0.05F,
                false, 0.0F, 0.0F, false, false,
                new NightBloomWatermarkLayout.TileInput(NightBloomWatermarkLayout.Tile.BRAND,
                        12.0F, 10.0F, 60.0F, 18.0F),
                new NightBloomWatermarkLayout.TileInput(NightBloomWatermarkLayout.Tile.VERSION,
                        74.0F, 10.0F, 28.0F, 18.0F, true),
                new NightBloomWatermarkLayout.TileInput(NightBloomWatermarkLayout.Tile.STATUS,
                        102.0F, 10.0F, 52.0F, 18.0F, true)));
    }

    private static NightBloomWatermarkLayout.Snapshot frame(NightBloomWatermarkLayout layout,
                                                              boolean editMode, float mouseX, float mouseY,
                                                              boolean leftDown, boolean rightDown,
                                                              float deltaSeconds) {
        return layout.update(new NightBloomWatermarkLayout.Frame(
                320.0F, 180.0F, 1.0F, deltaSeconds,
                editMode, mouseX, mouseY, leftDown, rightDown,
                new NightBloomWatermarkLayout.TileInput(NightBloomWatermarkLayout.Tile.BRAND,
                        10.0F, 10.0F, 60.0F, 18.0F),
                new NightBloomWatermarkLayout.TileInput(NightBloomWatermarkLayout.Tile.VERSION,
                        100.0F, 10.0F, 28.0F, 18.0F),
                new NightBloomWatermarkLayout.TileInput(NightBloomWatermarkLayout.Tile.STATUS,
                        150.0F, 80.0F, 52.0F, 18.0F)));
    }
}
