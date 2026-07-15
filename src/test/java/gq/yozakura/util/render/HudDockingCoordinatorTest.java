package gq.yozakura.util.render;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Pure geometry contracts for the shared Night Bloom HUD docking graph.
 */
public class HudDockingCoordinatorTest {
    private static final float EPSILON = 0.01F;

    @Test
    public void horizontallyDockedWidgetsShareFacingJoinsAndMoveAsOneGroup() {
        HudDockingCoordinator coordinator = new HudDockingCoordinator();
        HudDockingCoordinator.Frame frame = frame(false, 0.0F, 0.0F, false, false,
                node("left", 10.0F, 30.0F, 40.0F, 20.0F),
                node("right", 80.0F, 30.0F, 50.0F, 20.0F));
        coordinator.update(frame);

        coordinator.update(frame(true, 20.0F, 40.0F, true, false,
                node("left", 10.0F, 30.0F, 40.0F, 20.0F),
                node("right", 80.0F, 30.0F, 50.0F, 20.0F)));
        HudDockingCoordinator.Snapshot preview = coordinator.update(frame(true, 50.0F, 40.0F, true, false,
                node("left", 10.0F, 30.0F, 40.0F, 20.0F),
                node("right", 80.0F, 30.0F, 50.0F, 20.0F)));

        assertNotNull("moving into the magnetic range must expose a liquid preview", preview.getPreview());
        HudDockingCoordinator.Snapshot attached = coordinator.update(frame(true, 50.0F, 40.0F, false, false,
                node("left", 10.0F, 30.0F, 40.0F, 20.0F),
                node("right", 80.0F, 30.0F, 50.0F, 20.0F)));
        attached = settle(coordinator, attached, node("left", 40.0F, 30.0F, 40.0F, 20.0F),
                node("right", 80.0F, 30.0F, 50.0F, 20.0F));

        assertEquals(1, attached.getLinks().size());
        assertEquals(40.0F, attached.getNode("left").getTargetX(), EPSILON);
        HudDockingCoordinator.Surface left = attached.getSurface("left");
        HudDockingCoordinator.Surface right = attached.getSurface("right");
        assertTrue("the left widget's facing edge must flatten", left.getRightJoinEnd() > left.getRightJoinStart());
        assertTrue("the right widget's facing edge must flatten", right.getLeftJoinEnd() > right.getLeftJoinStart());

        coordinator.update(frame(true, 50.0F, 40.0F, true, false,
                node("left", 40.0F, 30.0F, 40.0F, 20.0F),
                node("right", 80.0F, 30.0F, 50.0F, 20.0F)));
        HudDockingCoordinator.Snapshot moved = coordinator.update(frame(true, 70.0F, 55.0F, true, false,
                node("left", 40.0F, 30.0F, 40.0F, 20.0F),
                node("right", 80.0F, 30.0F, 50.0F, 20.0F)));
        assertEquals("a linked partner must keep its relative X offset", 40.0F,
                moved.getNode("right").getTargetX() - moved.getNode("left").getTargetX(), EPSILON);
        assertEquals("a linked partner must keep its relative Y offset", 0.0F,
                moved.getNode("right").getTargetY() - moved.getNode("left").getTargetY(), EPSILON);
    }

    @Test
    public void verticalDockingPromotesUnequalWidgetsIntoOneIslandAndReflowsOnResize() {
        HudDockingCoordinator coordinator = new HudDockingCoordinator();
        coordinator.update(frame(false, 0.0F, 0.0F, false, false,
                node("top", 40.0F, 10.0F, 72.0F, 22.0F),
                node("bottom", 40.0F, 68.0F, 120.0F, 32.0F)));

        coordinator.update(frame(true, 50.0F, 18.0F, true, false,
                node("top", 40.0F, 10.0F, 72.0F, 22.0F),
                node("bottom", 40.0F, 68.0F, 120.0F, 32.0F)));
        coordinator.update(frame(true, 50.0F, 54.0F, true, false,
                node("top", 40.0F, 10.0F, 72.0F, 22.0F),
                node("bottom", 40.0F, 68.0F, 120.0F, 32.0F)));
        HudDockingCoordinator.Snapshot attached = coordinator.update(frame(true, 50.0F, 54.0F, false, false,
                node("top", 40.0F, 10.0F, 72.0F, 22.0F),
                node("bottom", 40.0F, 68.0F, 120.0F, 32.0F)));
        attached = settle(coordinator, attached, node("top", 40.0F, 46.0F, 72.0F, 22.0F),
                node("bottom", 40.0F, 68.0F, 120.0F, 32.0F));

        assertFalse(attached.getComposites().isEmpty());
        HudDockingCoordinator.Composite island = attached.getComposites().get(0);
        assertEquals(40.0F, island.getX(), EPSILON);
        assertEquals(160.0F, island.getRight(), EPSILON);
        assertTrue("vertical fusion should become a coherent island instead of a stepped seam",
                island.getProgress() > 0.99F);

        HudDockingCoordinator.Snapshot resized = coordinator.update(frame(false, 0.0F, 0.0F, false, false,
                node("top", 40.0F, 46.0F, 72.0F, 44.0F),
                node("bottom", 40.0F, 68.0F, 120.0F, 32.0F)));
        assertEquals("the lower linked component follows a changed height", 90.0F,
                resized.getNode("bottom").getTargetY(), EPSILON);
    }

    @Test
    public void oneSideCannotAcceptTwoWidgetsAndRightClickSeparatesTheTouchedLink() {
        HudDockingCoordinator coordinator = new HudDockingCoordinator();
        coordinator.update(frame(false, 0.0F, 0.0F, false, false,
                node("a", 20.0F, 30.0F, 40.0F, 20.0F),
                node("center", 80.0F, 30.0F, 40.0F, 20.0F),
                node("c", 140.0F, 30.0F, 40.0F, 20.0F)));

        coordinator.update(frame(true, 30.0F, 40.0F, true, false,
                node("a", 20.0F, 30.0F, 40.0F, 20.0F), node("center", 80.0F, 30.0F, 40.0F, 20.0F),
                node("c", 140.0F, 30.0F, 40.0F, 20.0F)));
        coordinator.update(frame(true, 50.0F, 40.0F, true, false,
                node("a", 20.0F, 30.0F, 40.0F, 20.0F), node("center", 80.0F, 30.0F, 40.0F, 20.0F),
                node("c", 140.0F, 30.0F, 40.0F, 20.0F)));
        HudDockingCoordinator.Snapshot attached = coordinator.update(frame(true, 50.0F, 40.0F, false, false,
                node("a", 20.0F, 30.0F, 40.0F, 20.0F), node("center", 80.0F, 30.0F, 40.0F, 20.0F),
                node("c", 140.0F, 30.0F, 40.0F, 20.0F)));
        attached = settle(coordinator, attached, node("a", 40.0F, 30.0F, 40.0F, 20.0F),
                node("center", 80.0F, 30.0F, 40.0F, 20.0F), node("c", 140.0F, 30.0F, 40.0F, 20.0F));
        assertEquals(1, attached.getLinks().size());

        coordinator.update(frame(true, 150.0F, 40.0F, true, false,
                node("a", 40.0F, 30.0F, 40.0F, 20.0F), node("center", 80.0F, 30.0F, 40.0F, 20.0F),
                node("c", 140.0F, 30.0F, 40.0F, 20.0F)));
        coordinator.update(frame(true, 50.0F, 40.0F, true, false,
                node("a", 40.0F, 30.0F, 40.0F, 20.0F), node("center", 80.0F, 30.0F, 40.0F, 20.0F),
                node("c", 140.0F, 30.0F, 40.0F, 20.0F)));
        HudDockingCoordinator.Snapshot rejected = coordinator.update(frame(true, 50.0F, 40.0F, false, false,
                node("a", 40.0F, 30.0F, 40.0F, 20.0F), node("center", 80.0F, 30.0F, 40.0F, 20.0F),
                node("c", 140.0F, 30.0F, 40.0F, 20.0F)));
        assertEquals("the occupied left edge of center cannot form a second bridge", 1, rejected.getLinks().size());

        HudDockingCoordinator.Snapshot detached = coordinator.update(frame(true, 100.0F, 40.0F, false, true,
                node("a", 40.0F, 30.0F, 40.0F, 20.0F), node("center", 80.0F, 30.0F, 40.0F, 20.0F),
                node("c", 140.0F, 30.0F, 40.0F, 20.0F)));
        assertTrue("right click starts the liquid separation", detached.getLinks().get(0).isDetaching());
        for (int index = 0; index < 8; index++) {
            detached = coordinator.update(frame(true, 50.0F, 40.0F, false, false,
                    node("a", detached.getNode("a").getTargetX(), detached.getNode("a").getTargetY(), 40.0F, 20.0F),
                    node("center", detached.getNode("center").getTargetX(), detached.getNode("center").getTargetY(), 40.0F, 20.0F),
                    node("c", 140.0F, 30.0F, 40.0F, 20.0F)));
        }
        assertTrue("the detached widgets gain a visible separation gap", detached.getNode("center").getTargetX()
                - detached.getNode("a").getTargetX() > 40.0F + EPSILON);
    }

    @Test
    public void irregularWidgetsCanExposeAStableEdgeWithoutBeingPromotedToARectangleIsland() {
        HudDockingCoordinator coordinator = new HudDockingCoordinator();
        HudDockingCoordinator.NodeInput rows = new HudDockingCoordinator.NodeInput("rows", 10.0F, 30.0F,
                40.0F, 20.0F, 4.0F,
                HudDockingCoordinator.Side.all(), true, false);
        HudDockingCoordinator.NodeInput panel = node("panel", 80.0F, 30.0F, 50.0F, 20.0F);
        coordinator.update(frame(false, 0.0F, 0.0F, false, false, rows, panel));
        coordinator.update(frame(true, 20.0F, 40.0F, true, false, rows, panel));
        coordinator.update(frame(true, 50.0F, 40.0F, true, false, rows, panel));
        HudDockingCoordinator.Snapshot attached = coordinator.update(frame(true, 50.0F, 40.0F, false, false,
                rows, panel));
        attached = settle(coordinator, attached,
                new HudDockingCoordinator.NodeInput("rows", 40.0F, 30.0F, 40.0F, 20.0F, 4.0F,
                        HudDockingCoordinator.Side.all(), true, false),
                panel);

        assertEquals(1, attached.getLinks().size());
        assertTrue("the liquid bridge remains available", !attached.getBridges().isEmpty());
        assertTrue("an irregular ArrayList-like node must retain its own row silhouette",
                attached.getComposites().isEmpty());
    }

    @Test
    public void dormantLinkedWidgetKeepsItsLinkWithoutLeavingAGhostSurface() {
        HudDockingCoordinator coordinator = new HudDockingCoordinator();
        coordinator.update(frame(false, 0.0F, 0.0F, false, false,
                node("left", 10.0F, 30.0F, 40.0F, 20.0F),
                node("right", 80.0F, 30.0F, 50.0F, 20.0F)));
        coordinator.update(frame(true, 20.0F, 40.0F, true, false,
                node("left", 10.0F, 30.0F, 40.0F, 20.0F),
                node("right", 80.0F, 30.0F, 50.0F, 20.0F)));
        coordinator.update(frame(true, 50.0F, 40.0F, true, false,
                node("left", 10.0F, 30.0F, 40.0F, 20.0F),
                node("right", 80.0F, 30.0F, 50.0F, 20.0F)));
        HudDockingCoordinator.Snapshot attached = coordinator.update(frame(true, 50.0F, 40.0F, false, false,
                node("left", 10.0F, 30.0F, 40.0F, 20.0F),
                node("right", 80.0F, 30.0F, 50.0F, 20.0F)));
        attached = settle(coordinator, attached, node("left", 40.0F, 30.0F, 40.0F, 20.0F),
                node("right", 80.0F, 30.0F, 50.0F, 20.0F));

        HudDockingCoordinator.NodeInput hiddenLeft = new HudDockingCoordinator.NodeInput("left",
                40.0F, 30.0F, 40.0F, 20.0F, 4.0F, HudDockingCoordinator.Side.all(), true, true, false);
        HudDockingCoordinator.Snapshot dormant = coordinator.update(frame(false, 0.0F, 0.0F, false, false,
                hiddenLeft, node("right", 80.0F, 30.0F, 50.0F, 20.0F)));

        assertEquals("a temporarily hidden panel must retain its docking relationship", 1,
                dormant.getLinks().size());
        assertFalse("a dormant panel cannot leave a bridge or composite visible", dormant.getBridges().size() > 0);
        assertFalse("a dormant panel must not expose a clickable hit box", dormant.getNode("left").isVisible());

        HudDockingCoordinator.Snapshot restored = coordinator.update(frame(false, 0.0F, 0.0F, false, false,
                node("left", 40.0F, 30.0F, 40.0F, 20.0F),
                node("right", 80.0F, 30.0F, 50.0F, 20.0F)));
        assertEquals("the original docking relationship resumes when the panel returns", 1,
                restored.getLinks().size());
        assertEquals(40.0F, restored.getNode("left").getTargetX(), EPSILON);
    }

    @Test
    public void linkedPassiveAnchorFollowsTheMovablePartnerAsOneGroup() {
        HudDockingCoordinator coordinator = new HudDockingCoordinator();
        HudDockingCoordinator.NodeInput watermark = new HudDockingCoordinator.NodeInput("watermark",
                10.0F, 30.0F, 60.0F, 20.0F, 4.0F, HudDockingCoordinator.Side.all(), false, false);
        HudDockingCoordinator.NodeInput panel = node("panel", 110.0F, 30.0F, 40.0F, 20.0F);
        coordinator.update(frame(false, 0.0F, 0.0F, false, false, watermark, panel));
        coordinator.update(frame(true, 120.0F, 40.0F, true, false, watermark, panel));
        coordinator.update(frame(true, 80.0F, 40.0F, true, false, watermark, panel));
        HudDockingCoordinator.Snapshot attached = coordinator.update(frame(true, 80.0F, 40.0F, false, false,
                watermark, panel));
        attached = settle(coordinator, attached, watermark, node("panel", 70.0F, 30.0F, 40.0F, 20.0F));

        coordinator.update(frame(true, 80.0F, 40.0F, true, false, watermark,
                node("panel", 70.0F, 30.0F, 40.0F, 20.0F)));
        HudDockingCoordinator.Snapshot moved = coordinator.update(frame(true, 100.0F, 50.0F, true, false,
                watermark, node("panel", 70.0F, 30.0F, 40.0F, 20.0F)));

        assertEquals(30.0F, moved.getNode("watermark").getTargetX(), EPSILON);
        assertEquals(40.0F, moved.getNode("watermark").getTargetY(), EPSILON);
        assertEquals(90.0F, moved.getNode("panel").getTargetX(), EPSILON);
        assertEquals(40.0F, moved.getNode("panel").getTargetY(), EPSILON);
    }

    @Test
    public void passiveWatermarkProxyCanSnapAfterLocalDragAndDetachBeforeTheNextLocalMove() {
        HudDockingCoordinator coordinator = new HudDockingCoordinator();
        HudDockingCoordinator.NodeInput watermark = new HudDockingCoordinator.NodeInput("watermark",
                10.0F, 30.0F, 60.0F, 20.0F, 4.0F, HudDockingCoordinator.Side.all(), false, false);
        HudDockingCoordinator.NodeInput panel = node("panel", 74.0F, 30.0F, 40.0F, 20.0F);
        coordinator.update(frame(false, 0.0F, 0.0F, false, false, watermark, panel));

        HudDockingCoordinator.Snapshot attached = coordinator.attachNearest("watermark");
        assertEquals(1, attached.getLinks().size());
        assertEquals(14.0F, attached.getNode("watermark").getTargetX(), EPSILON);

        HudDockingCoordinator.Snapshot detached = coordinator.detach("watermark");
        assertTrue(detached.getLinks().get(0).isDetaching());
    }

    private static HudDockingCoordinator.Snapshot settle(HudDockingCoordinator coordinator,
                                                           HudDockingCoordinator.Snapshot snapshot,
                                                           HudDockingCoordinator.NodeInput... nodes) {
        HudDockingCoordinator.Snapshot current = snapshot;
        for (int index = 0; index < 8; index++) {
            current = coordinator.update(frame(false, 0.0F, 0.0F, false, false, nodes));
        }
        return current;
    }

    private static HudDockingCoordinator.Frame frame(boolean editMode, float mouseX, float mouseY,
                                                      boolean leftDown, boolean rightDown,
                                                      HudDockingCoordinator.NodeInput... nodes) {
        return new HudDockingCoordinator.Frame(240.0F, 160.0F, 0.08F, editMode,
                mouseX, mouseY, leftDown, rightDown, false, Arrays.asList(nodes));
    }

    private static HudDockingCoordinator.NodeInput node(String id, float x, float y, float width, float height) {
        return new HudDockingCoordinator.NodeInput(id, x, y, width, height,
                HudDockingCoordinator.Side.all());
    }
}
