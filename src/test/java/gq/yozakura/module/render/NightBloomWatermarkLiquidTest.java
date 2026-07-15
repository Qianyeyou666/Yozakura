package gq.yozakura.module.render;

import gq.yozakura.util.render.HudDockingCoordinator;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NightBloomWatermarkLiquidTest {
    private static final float EPSILON = 0.0001F;

    @Test
    public void horizontalFusionGrowsFromANarrowNeckToTheFullSharedEdge() {
        NightBloomWatermarkLayout.TileView left = tile(NightBloomWatermarkLayout.Tile.BRAND,
                10.0F, 10.0F, 60.0F, 18.0F);
        NightBloomWatermarkLayout.TileView right = tile(NightBloomWatermarkLayout.Tile.VERSION,
                76.0F, 10.0F, 28.0F, 18.0F);

        NightBloomWatermarkLiquid.Bridge early = bridge(left, right,
                NightBloomWatermarkLayout.Placement.RIGHT_OF, 0.25F);
        NightBloomWatermarkLiquid.Bridge merged = bridge(left, right,
                NightBloomWatermarkLayout.Placement.RIGHT_OF, 1.0F);

        assertEquals(NightBloomWatermarkLiquid.Axis.HORIZONTAL, early.getAxis());
        assertEquals(70.0F, early.getX(), EPSILON);
        assertEquals(76.0F, early.getRight(), EPSILON);
        assertTrue("fusion must begin with a visibly smaller liquid neck", early.getHeight() < merged.getHeight());
        assertEquals(18.0F, merged.getHeight(), EPSILON);

        NightBloomWatermarkLiquid.Surface surface = NightBloomWatermarkLiquid.surfaceFor(
                right, Collections.singletonList(merged), 4.0F);
        assertEquals(0.0F, surface.getTopLeft(), EPSILON);
        assertEquals(0.0F, surface.getBottomLeft(), EPSILON);
        assertEquals(4.0F, surface.getTopRight(), EPSILON);
        assertEquals(4.0F, surface.getBottomRight(), EPSILON);
        assertEquals(0.0F, surface.getLeftJoinStart(), EPSILON);
        assertEquals(18.0F, surface.getLeftJoinEnd(), EPSILON);
    }

    @Test
    public void verticalFusionGrowsFromANarrowNeckToTheFullSharedEdge() {
        NightBloomWatermarkLayout.TileView top = tile(NightBloomWatermarkLayout.Tile.BRAND,
                40.0F, 10.0F, 60.0F, 18.0F);
        NightBloomWatermarkLayout.TileView bottom = tile(NightBloomWatermarkLayout.Tile.STATUS,
                52.0F, 34.0F, 36.0F, 18.0F);

        NightBloomWatermarkLiquid.Bridge early = bridge(bottom, top,
                NightBloomWatermarkLayout.Placement.BELOW, 0.25F);
        NightBloomWatermarkLiquid.Bridge merged = bridge(bottom, top,
                NightBloomWatermarkLayout.Placement.BELOW, 1.0F);

        assertEquals(NightBloomWatermarkLiquid.Axis.VERTICAL, early.getAxis());
        assertEquals(28.0F, early.getY(), EPSILON);
        assertEquals(34.0F, early.getBottom(), EPSILON);
        assertTrue("vertical fusion must begin with a visibly smaller liquid neck", early.getWidth() < merged.getWidth());
        assertEquals(36.0F, merged.getWidth(), EPSILON);

        NightBloomWatermarkLiquid.Surface surface = NightBloomWatermarkLiquid.surfaceFor(
                top, Collections.singletonList(merged), 4.0F);
        assertEquals(4.0F, surface.getTopLeft(), EPSILON);
        assertEquals(4.0F, surface.getTopRight(), EPSILON);
        assertEquals(4.0F, surface.getBottomRight(), EPSILON);
        assertEquals(4.0F, surface.getBottomLeft(), EPSILON);
        assertEquals(12.0F, surface.getBottomJoinStart(), EPSILON);
        assertEquals(48.0F, surface.getBottomJoinEnd(), EPSILON);
    }

    @Test
    public void partialHorizontalFusionOnlyFlattensTheCoveredCornerAndDelaysTheEdgeExpansion() {
        NightBloomWatermarkLayout.TileView left = tile(NightBloomWatermarkLayout.Tile.BRAND,
                10.0F, 10.0F, 60.0F, 18.0F);
        NightBloomWatermarkLayout.TileView right = tile(NightBloomWatermarkLayout.Tile.VERSION,
                76.0F, 14.0F, 28.0F, 14.0F);

        NightBloomWatermarkLiquid.Bridge early = bridge(left, right,
                NightBloomWatermarkLayout.Placement.RIGHT_OF, 0.12F);
        NightBloomWatermarkLiquid.Bridge merged = bridge(left, right,
                NightBloomWatermarkLayout.Placement.RIGHT_OF, 1.0F);
        NightBloomWatermarkLiquid.Surface surface = NightBloomWatermarkLiquid.surfaceFor(
                left, Collections.singletonList(merged), 4.0F);

        assertTrue("the first phase must fade in a narrow neck before the edge expands",
                early.getOpacity() > 0.0F && early.getEdgeProgress() < merged.getEdgeProgress());
        assertEquals(4.0F, surface.getTopRight(), EPSILON);
        assertEquals(0.0F, surface.getBottomRight(), EPSILON);
        assertEquals(4.0F, surface.getRightJoinStart(), EPSILON);
        assertEquals(18.0F, surface.getRightJoinEnd(), EPSILON);
    }

    @Test
    public void aDetachingNeckShrinksContinuouslyBeforeItsLinkCanDisappear() {
        NightBloomWatermarkLayout.TileView left = tile(NightBloomWatermarkLayout.Tile.BRAND,
                10.0F, 10.0F, 60.0F, 18.0F);
        NightBloomWatermarkLayout.TileView right = tile(NightBloomWatermarkLayout.Tile.VERSION,
                76.0F, 10.0F, 28.0F, 18.0F);

        NightBloomWatermarkLiquid.Bridge nearlyGone = bridge(left, right,
                NightBloomWatermarkLayout.Placement.RIGHT_OF, 0.011F);
        NightBloomWatermarkLiquid.Bridge visible = bridge(left, right,
                NightBloomWatermarkLayout.Placement.RIGHT_OF, 0.12F);

        assertTrue(nearlyGone.getOpacity() < visible.getOpacity());
        assertTrue("a split bridge must taper toward zero instead of popping off at cleanup",
                nearlyGone.getHeight() < visible.getHeight());
    }

    @Test
    public void aVerticalLinkPromotesItsConnectedTilesIntoOneDynamicIslandEnvelope() {
        NightBloomWatermarkLayout.TileView brand = tile(NightBloomWatermarkLayout.Tile.BRAND,
                10.0F, 10.0F, 60.0F, 18.0F);
        NightBloomWatermarkLayout.TileView version = tile(NightBloomWatermarkLayout.Tile.VERSION,
                70.0F, 10.0F, 30.0F, 18.0F);
        NightBloomWatermarkLayout.TileView status = tile(NightBloomWatermarkLayout.Tile.STATUS,
                10.0F, 28.0F, 48.0F, 18.0F);
        NightBloomWatermarkLiquid.Bridge horizontal = bridge(brand, version,
                NightBloomWatermarkLayout.Placement.RIGHT_OF, 1.0F);
        NightBloomWatermarkLiquid.Bridge vertical = bridge(status, brand,
                NightBloomWatermarkLayout.Placement.BELOW, 1.0F);

        List<NightBloomWatermarkLiquid.Composite> composites = NightBloomWatermarkLiquid.composites(
                Arrays.asList(brand, version, status), Arrays.asList(horizontal, vertical));

        assertEquals(1, composites.size());
        NightBloomWatermarkLiquid.Composite island = composites.get(0);
        assertEquals(10.0F, island.getX(), EPSILON);
        assertEquals(10.0F, island.getY(), EPSILON);
        assertEquals(100.0F, island.getRight(), EPSILON);
        assertEquals(46.0F, island.getBottom(), EPSILON);
        assertEquals(1.0F, island.getProgress(), EPSILON);
    }

    @Test
    public void addingAHorizontalTileToASettledIslandKeepsTheLiquidNeckUntilThatLinkSettles() {
        NightBloomWatermarkLayout.TileView brand = tile(NightBloomWatermarkLayout.Tile.BRAND,
                10.0F, 10.0F, 60.0F, 18.0F);
        NightBloomWatermarkLayout.TileView version = tile(NightBloomWatermarkLayout.Tile.VERSION,
                70.0F, 10.0F, 30.0F, 18.0F);
        NightBloomWatermarkLayout.TileView status = tile(NightBloomWatermarkLayout.Tile.STATUS,
                10.0F, 28.0F, 48.0F, 18.0F);
        NightBloomWatermarkLiquid.Bridge vertical = bridge(status, brand,
                NightBloomWatermarkLayout.Placement.BELOW, 1.0F);
        NightBloomWatermarkLiquid.Bridge incomingHorizontal = bridge(brand, version,
                NightBloomWatermarkLayout.Placement.RIGHT_OF, 0.25F);

        List<NightBloomWatermarkLiquid.Composite> composites = NightBloomWatermarkLiquid.composites(
                Arrays.asList(brand, version, status), Arrays.asList(vertical, incomingHorizontal));

        assertEquals(1, composites.size());
        NightBloomWatermarkLiquid.Composite island = composites.get(0);
        assertTrue(island.contains(NightBloomWatermarkLayout.Tile.BRAND));
        assertTrue(island.contains(NightBloomWatermarkLayout.Tile.STATUS));
        assertFalse("the new tile must not jump into a completed island before its liquid edge expands",
                island.contains(NightBloomWatermarkLayout.Tile.VERSION));
        assertEquals(70.0F, island.getRight(), EPSILON);
        assertEquals(1.0F, island.getProgress(), EPSILON);
    }

    @Test
    public void addingAVerticalTileToASettledIslandKeepsTheLiquidNeckUntilThatLinkSettles() {
        NightBloomWatermarkLayout.TileView brand = tile(NightBloomWatermarkLayout.Tile.BRAND,
                10.0F, 10.0F, 60.0F, 18.0F);
        NightBloomWatermarkLayout.TileView status = tile(NightBloomWatermarkLayout.Tile.STATUS,
                10.0F, 28.0F, 48.0F, 18.0F);
        NightBloomWatermarkLayout.TileView version = tile(NightBloomWatermarkLayout.Tile.VERSION,
                10.0F, 46.0F, 42.0F, 18.0F);
        NightBloomWatermarkLiquid.Bridge settled = bridge(status, brand,
                NightBloomWatermarkLayout.Placement.BELOW, 1.0F);
        NightBloomWatermarkLiquid.Bridge incoming = bridge(version, status,
                NightBloomWatermarkLayout.Placement.BELOW, 0.25F);

        List<NightBloomWatermarkLiquid.Composite> composites = NightBloomWatermarkLiquid.composites(
                Arrays.asList(brand, version, status), Arrays.asList(settled, incoming));

        assertEquals(1, composites.size());
        NightBloomWatermarkLiquid.Composite island = composites.get(0);
        assertFalse("a new vertical tile must not inherit the old island's finished envelope progress",
                island.contains(NightBloomWatermarkLayout.Tile.VERSION));
        assertEquals(46.0F, island.getBottom(), EPSILON);
        assertEquals(1.0F, island.getProgress(), EPSILON);
    }

    @Test
    public void alignedHorizontalLinksPromoteTilesIntoOneSeamlessEnvelope() {
        NightBloomWatermarkLayout.TileView brand = tile(NightBloomWatermarkLayout.Tile.BRAND,
                10.0F, 10.0F, 60.0F, 18.0F);
        NightBloomWatermarkLayout.TileView version = tile(NightBloomWatermarkLayout.Tile.VERSION,
                70.0F, 10.0F, 30.0F, 18.0F);
        NightBloomWatermarkLayout.TileView status = tile(NightBloomWatermarkLayout.Tile.STATUS,
                100.0F, 10.0F, 48.0F, 18.0F);
        NightBloomWatermarkLiquid.Bridge first = bridge(brand, version,
                NightBloomWatermarkLayout.Placement.RIGHT_OF, 1.0F);
        NightBloomWatermarkLiquid.Bridge second = bridge(version, status,
                NightBloomWatermarkLayout.Placement.RIGHT_OF, 1.0F);

        List<NightBloomWatermarkLiquid.Composite> earlyComposites = NightBloomWatermarkLiquid.composites(
                Arrays.asList(brand, version, status), Arrays.asList(
                        bridge(brand, version, NightBloomWatermarkLayout.Placement.RIGHT_OF, 0.25F),
                        bridge(version, status, NightBloomWatermarkLayout.Placement.RIGHT_OF, 0.25F)));
        assertTrue("the liquid neck must remain visible before the row becomes one envelope",
                earlyComposites.isEmpty());

        List<NightBloomWatermarkLiquid.Composite> composites = NightBloomWatermarkLiquid.composites(
                Arrays.asList(brand, version, status), Arrays.asList(first, second));

        assertEquals("fully merged horizontal tiles need one surface and one shadow mask", 1, composites.size());
        NightBloomWatermarkLiquid.Composite envelope = composites.get(0);
        assertEquals(10.0F, envelope.getX(), EPSILON);
        assertEquals(10.0F, envelope.getY(), EPSILON);
        assertEquals(148.0F, envelope.getRight(), EPSILON);
        assertEquals(28.0F, envelope.getBottom(), EPSILON);
        assertEquals(1.0F, envelope.getProgress(), EPSILON);
    }

    @Test
    public void globalDockBridgeFlattensOnlyTheTouchedOuterWatermarkTile() {
        HudDockingCoordinator coordinator = new HudDockingCoordinator();
        HudDockingCoordinator.NodeInput watermark = new HudDockingCoordinator.NodeInput("hud_watermark",
                10.0F, 10.0F, 94.0F, 18.0F, 4.0F, HudDockingCoordinator.Side.all(), false, false);
        HudDockingCoordinator.NodeInput panel = new HudDockingCoordinator.NodeInput("panel",
                140.0F, 10.0F, 40.0F, 18.0F, 4.0F, HudDockingCoordinator.Side.all());
        coordinator.update(dockFrame(false, 0.0F, false, watermark, panel));
        coordinator.update(dockFrame(true, 150.0F, true, watermark, panel));
        coordinator.update(dockFrame(true, 114.0F, true, watermark, panel));
        HudDockingCoordinator.Snapshot snapshot = coordinator.update(dockFrame(true, 114.0F, false,
                watermark, panel));
        for (int index = 0; index < 8; index++) {
            snapshot = coordinator.update(dockFrame(false, 0.0F, false, watermark,
                    new HudDockingCoordinator.NodeInput("panel", 104.0F, 10.0F, 40.0F, 18.0F,
                            4.0F, HudDockingCoordinator.Side.all())));
        }

        NightBloomWatermarkLayout.TileView status = tile(NightBloomWatermarkLayout.Tile.STATUS,
                70.0F, 10.0F, 34.0F, 18.0F);
        NightBloomWatermarkLiquid.Surface merged = NightBloomWatermarkLiquid.mergeDockingSurface(
                NightBloomWatermarkLiquid.surfaceFor(status, Collections.<NightBloomWatermarkLiquid.Bridge>emptyList(),
                        4.0F), status, snapshot, "hud_watermark");

        assertEquals(0.0F, merged.getTopRight(), EPSILON);
        assertEquals(0.0F, merged.getBottomRight(), EPSILON);
        assertEquals(0.0F, merged.getRightJoinStart(), EPSILON);
        assertEquals(18.0F, merged.getRightJoinEnd(), EPSILON);
    }

    private static HudDockingCoordinator.Frame dockFrame(boolean editMode, float mouseX, boolean leftDown,
                                                          HudDockingCoordinator.NodeInput... nodes) {
        return new HudDockingCoordinator.Frame(260.0F, 160.0F, 0.10F, editMode, mouseX, 19.0F,
                leftDown, false, false, Arrays.asList(nodes));
    }

    private static NightBloomWatermarkLayout.TileView tile(NightBloomWatermarkLayout.Tile tile,
                                                             float x, float y, float width, float height) {
        return new NightBloomWatermarkLayout.TileView(tile, x, y, x, y, width, height);
    }

    private static NightBloomWatermarkLiquid.Bridge bridge(NightBloomWatermarkLayout.TileView child,
                                                             NightBloomWatermarkLayout.TileView parent,
                                                             NightBloomWatermarkLayout.Placement placement,
                                                             float progress) {
        NightBloomWatermarkLayout.LinkView link = new NightBloomWatermarkLayout.LinkView(
                child.getTile(), parent.getTile(), placement,
                NightBloomWatermarkLayout.CrossAlignment.START, progress, false);
        return NightBloomWatermarkLiquid.bridge(child, parent, link);
    }
}
