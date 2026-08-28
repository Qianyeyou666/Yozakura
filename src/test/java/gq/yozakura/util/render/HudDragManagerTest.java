package gq.yozakura.util.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class HudDragManagerTest {
    private static final float EPSILON = 0.0001F;

    @Test
    public void namedDraggingObjectsRemainStableAcrossRendererUpdates() {
        HudDragManager manager = new HudDragManager();

        HudDragging first = manager.create("target_hud", 12.0F, 18.0F);
        HudDragging second = manager.create("target_hud", 80.0F, 90.0F);

        assertSame(first, second);
        assertEquals(12.0F, second.getInitialX(), EPSILON);
        assertEquals(18.0F, second.getInitialY(), EPSILON);
        assertEquals(1, manager.size());
    }

    @Test
    public void managerOwnsOneActiveGestureAndKeepsPointerOffset() {
        HudDragManager manager = new HudDragManager();
        HudDragSession.Bounds bounds = new HudDragSession.Bounds(240.0F, 160.0F, 40.0F, 20.0F);

        manager.update("first", null, null, 20.0F, 30.0F, bounds,
                frame(true, 25.0F, 35.0F, true, false));
        manager.update("second", null, null, 20.0F, 30.0F, bounds,
                frame(true, 25.0F, 35.0F, true, false));

        assertTrue(manager.isDragging("first"));
        assertFalse(manager.isDragging("second"));

        HudDragSession.Position moved = manager.update("first", null, null, 20.0F, 30.0F, bounds,
                frame(true, 55.0F, 65.0F, true, false));
        assertEquals(50.0F, moved.getX(), EPSILON);
        assertEquals(60.0F, moved.getY(), EPSILON);

        manager.update("second", null, null, 20.0F, 30.0F, bounds,
                frame(true, 55.0F, 65.0F, false, false));
        assertFalse(manager.isDragging("first"));
        assertEquals("first", manager.getSelectedId());
    }

    @Test
    public void observingTheSameHudThroughFusionCancelsOnlyItsOrdinaryGesture() {
        HudDragManager manager = new HudDragManager();
        HudDragSession.Bounds bounds = new HudDragSession.Bounds(240.0F, 160.0F, 40.0F, 20.0F);

        manager.update("target_hud", null, null, 20.0F, 30.0F, bounds,
                frame(true, 25.0F, 35.0F, true, false));
        manager.observe("watermark", null, null, 10.0F, 10.0F, 10.0F, 10.0F, 60.0F, 20.0F);
        assertTrue(manager.isDragging("target_hud"));

        manager.observe("target_hud", null, null, 20.0F, 30.0F, 40.0F, 50.0F, 40.0F, 20.0F);
        assertFalse(manager.isDragging("target_hud"));
        assertEquals(40.0F, manager.get("target_hud").getX(), EPSILON);
        assertEquals(50.0F, manager.get("target_hud").getY(), EPSILON);
    }

    @Test
    public void hoverFeedbackUsesNymphililaStyleDecelerationAndReversal() {
        HudDragManager manager = new HudDragManager();
        manager.create("target_hud", 0.0F, 0.0F);

        assertEquals(0.0F, manager.updateHoverProgress("target_hud", false, 1000000000L), EPSILON);
        assertEquals(0.36F, manager.updateHoverProgress("target_hud", true, 1100000000L), EPSILON);
        assertEquals(0.64F, manager.updateHoverProgress("target_hud", true, 1200000000L), EPSILON);
        assertEquals(0.36F, manager.updateHoverProgress("target_hud", false, 1300000000L), EPSILON);
    }

    @Test
    public void cancellingRestoresThePointerDownPosition() {
        HudDragManager manager = new HudDragManager();
        HudDragSession.Bounds bounds = new HudDragSession.Bounds(240.0F, 160.0F, 40.0F, 20.0F);

        manager.update("inventory", null, null, 14.0F, 22.0F, bounds,
                frame(true, 18.0F, 26.0F, true, false));
        manager.update("inventory", null, null, 14.0F, 22.0F, bounds,
                frame(true, 80.0F, 70.0F, true, false));
        HudDragSession.Position cancelled = manager.update("inventory", null, null, 14.0F, 22.0F, bounds,
                frame(true, 80.0F, 70.0F, true, true));

        assertEquals(14.0F, cancelled.getX(), EPSILON);
        assertEquals(22.0F, cancelled.getY(), EPSILON);
        assertFalse(manager.isDragging("inventory"));
    }

    private static HudDragManager.Frame frame(boolean editMode, float mouseX, float mouseY,
                                               boolean leftDown, boolean escapeDown) {
        return new HudDragManager.Frame(editMode, mouseX, mouseY, leftDown, escapeDown);
    }
}
