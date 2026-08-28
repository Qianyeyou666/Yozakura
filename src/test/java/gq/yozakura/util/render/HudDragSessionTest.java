package gq.yozakura.util.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HudDragSessionTest {
    private static final float EPSILON = 0.0001F;
    private static final HudDragSession.Bounds BOUNDS = new HudDragSession.Bounds(200.0F, 120.0F, 40.0F, 20.0F);

    @Test
    public void staysArmedUntilPointerMovesThreeLogicalPixels() {
        HudDragSession session = new HudDragSession();
        session.arm("watermark", new HudDragSession.Position(20.0F, 30.0F), 30.0F, 40.0F);

        HudDragSession.Preview beforeThreshold = session.drag(32.9F, 40.0F, BOUNDS);

        assertEquals(HudDragSession.DragState.ARMED, session.getState());
        assertPosition(beforeThreshold.getPosition(), 20.0F, 30.0F);

        HudDragSession.Preview atThreshold = session.drag(33.0F, 40.0F, BOUNDS);

        assertEquals(HudDragSession.DragState.DRAGGING, session.getState());
        assertPosition(atThreshold.getPosition(), 23.0F, 30.0F);
    }

    @Test
    public void previewsScreenCenterSnapWithoutPersistingBeforeRelease() {
        HudDragSession session = new HudDragSession();
        session.arm("inventory", new HudDragSession.Position(10.0F, 20.0F), 20.0F, 30.0F);

        HudDragSession.Preview preview = session.drag(84.0F, 60.0F, BOUNDS);

        assertEquals(HudDragSession.DragState.SNAP_PREVIEW, session.getState());
        assertEquals(HudDragSession.SnapTarget.CENTER, preview.getHorizontalSnap());
        assertEquals(HudDragSession.SnapTarget.CENTER, preview.getVerticalSnap());
        assertPosition(preview.getPosition(), 80.0F, 50.0F);

        HudDragSession.Completion release = session.release();

        assertEquals(HudDragSession.DragState.RELEASE, session.getState());
        assertTrue(release.shouldPersist());
        assertFalse(release.isCancelled());
        assertPosition(release.getPosition(), 80.0F, 50.0F);
        session.acknowledgeRelease();
        assertEquals(HudDragSession.DragState.IDLE, session.getState());
    }

    @Test
    public void snapsToSafeEdgesWithinSixLogicalPixels() {
        HudDragSession session = new HudDragSession();
        session.arm("effects", new HudDragSession.Position(30.0F, 30.0F), 40.0F, 40.0F);

        HudDragSession.Preview preview = session.drag(16.0F, 16.0F, BOUNDS);

        assertEquals(HudDragSession.DragState.SNAP_PREVIEW, session.getState());
        assertEquals(HudDragSession.SnapTarget.START, preview.getHorizontalSnap());
        assertEquals(HudDragSession.SnapTarget.START, preview.getVerticalSnap());
        assertPosition(preview.getPosition(), HudDragSession.SAFE_MARGIN, HudDragSession.SAFE_MARGIN);
    }

    @Test
    public void escapeReturnsThePointerDownSnapshot() {
        HudDragSession session = new HudDragSession();
        session.arm("target_hud", new HudDragSession.Position(14.0F, 18.0F), 20.0F, 20.0F);
        session.drag(70.0F, 50.0F, BOUNDS);

        HudDragSession.Completion cancellation = session.cancel();

        assertEquals(HudDragSession.DragState.RELEASE, session.getState());
        assertTrue(cancellation.isCancelled());
        assertFalse(cancellation.shouldPersist());
        assertPosition(cancellation.getPosition(), 14.0F, 18.0F);
    }

    @Test
    public void releaseBlocksAnotherHudUntilThePhysicalPointerIsUp() {
        HudDragSession session = new HudDragSession();
        session.arm("first", new HudDragSession.Position(20.0F, 30.0F), 30.0F, 40.0F);

        session.release();
        session.acknowledgeRelease();

        assertTrue(session.isPointerBlockedUntilRelease());
        session.releasePointerBlock();
        assertFalse(session.isPointerBlockedUntilRelease());
    }

    @Test
    public void convertsMouseCoordinatesWithoutDriftAcrossGuiScalesOneThroughFour() {
        for (int scale = 1; scale <= 4; scale++) {
            float x = HudDragSession.toLogicalCoordinate(673.0F * scale, 1280 * scale, 1280);
            float y = HudDragSession.toLogicalYFromBottom(317.0F * scale, 720 * scale, 720);

            assertEquals(673.0F, x, EPSILON);
            assertEquals(402.0F, y, EPSILON);
        }
    }

    private static void assertPosition(HudDragSession.Position position, float expectedX, float expectedY) {
        assertEquals(expectedX, position.getX(), EPSILON);
        assertEquals(expectedY, position.getY(), EPSILON);
    }
}
