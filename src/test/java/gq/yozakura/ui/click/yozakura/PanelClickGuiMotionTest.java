package gq.yozakura.ui.click.yozakura;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PanelClickGuiMotionTest {
    @Test
    public void dampedApproachIsMonotonicAndTimeBased() {
        float first = PanelClickGuiMotion.approach(0.0f, 100.0f, 16.0f, 80.0f);
        float second = PanelClickGuiMotion.approach(first, 100.0f, 16.0f, 80.0f);

        assertTrue(first > 0.0f && first < 100.0f);
        assertTrue(second > first && second < 100.0f);
        assertEquals(100.0f, PanelClickGuiMotion.approach(100.0f, 100.0f, 16.0f, 80.0f), 0.001f);
    }

    @Test
    public void epsilonWheelImpulseAndFrameDecayArePreserved() {
        assertEquals(-24.0f, PanelClickGuiMotion.addWheelImpulse(0.0f, 1.0f), 0.001f);
        assertEquals(24.0f, PanelClickGuiMotion.addWheelImpulse(0.0f, -1.0f), 0.001f);

        PanelClickGuiMotion.ScrollFrame first = PanelClickGuiMotion.advanceScroll(50.0f, 24.0f, 1.0f, 0.0f, 200.0f);
        assertEquals(74.0f, first.scroll(), 0.001f);
        assertEquals(20.64f, first.velocity(), 0.001f);

        PanelClickGuiMotion.ScrollFrame stopped = PanelClickGuiMotion.advanceScroll(50.0f, 0.31f, 1.0f, 0.0f, 200.0f);
        assertEquals(50.31f, stopped.scroll(), 0.001f);
        assertEquals(0.0f, stopped.velocity(), 0.001f);
    }

    @Test
    public void scrollIntegrationUsesFrameDeltaInsteadOfMinecraftTickInterpolationPhase() {
        PanelClickGuiMotion.ScrollFrame full = PanelClickGuiMotion.advanceScroll(
                50.0f, 24.0f, 1.0f, 0.0f, 200.0f);
        PanelClickGuiMotion.ScrollFrame halfA = PanelClickGuiMotion.advanceScroll(
                50.0f, 24.0f, 0.5f, 0.0f, 200.0f);
        PanelClickGuiMotion.ScrollFrame halfB = PanelClickGuiMotion.advanceScroll(
                halfA.scroll(), halfA.velocity(), 0.5f, 0.0f, 200.0f);

        assertEquals(full.velocity(), halfB.velocity(), 0.001f);
        assertTrue(Math.abs(full.scroll() - halfB.scroll()) < 2.0f);
    }

    @Test
    public void scrollIsClampedWhileVelocityContinuesDecayingAtBounds() {
        PanelClickGuiMotion.ScrollFrame top = PanelClickGuiMotion.advanceScroll(2.0f, -24.0f, 1.0f, 0.0f, 200.0f);
        PanelClickGuiMotion.ScrollFrame bottom = PanelClickGuiMotion.advanceScroll(195.0f, 24.0f, 1.0f, 0.0f, 200.0f);

        assertEquals(0.0f, top.scroll(), 0.001f);
        assertEquals(-20.64f, top.velocity(), 0.001f);
        assertEquals(200.0f, bottom.scroll(), 0.001f);
        assertEquals(20.64f, bottom.velocity(), 0.001f);
    }

    @Test
    public void cubicEaseHasStableEndpoints() {
        assertEquals(0.0f, PanelClickGuiMotion.easeOutCubic(0.0f), 0.001f);
        assertEquals(1.0f, PanelClickGuiMotion.easeOutCubic(1.0f), 0.001f);
        assertTrue(PanelClickGuiMotion.easeOutCubic(0.5f) > 0.5f);
    }
}
