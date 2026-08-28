package gq.yozakura.module.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TargetEspRenderQualityTest {
    @Test
    public void highFrameRateRetainsFullGeometryAndLowFrameRateUsesTheConfiguredFloor() {
        assertEquals(96, TargetEspRenderQuality.segments(96, 40, 240));
        assertEquals(96, TargetEspRenderQuality.segments(96, 40, 200));
        assertEquals(40, TargetEspRenderQuality.segments(96, 40, 60));
        assertEquals(40, TargetEspRenderQuality.segments(96, 40, 20));
    }

    @Test
    public void riseSigmaRingKeepsItsRiseAndTrailWithinTheTargetBody() {
        assertEquals(32, TargetEspRenderQuality.riseSigmaRingSegments(240));
        assertEquals(16, TargetEspRenderQuality.riseSigmaRingSegments(20));
        float top = TargetEspRenderQuality.riseSigmaRingHeight(2.0f, (float) (Math.PI / 10.0D));
        float trail = TargetEspRenderQuality.riseSigmaRingTrailOffset(2.0f, (float) (Math.PI / 10.0D));
        assertTrue(top > 1.9f && top <= 2.0f);
        assertTrue(trail < 0.1f);
    }

    @Test
    public void detailFallsSmoothlyInFourVertexStepsBeforeTheFrameRateCollapses() {
        assertEquals(76, TargetEspRenderQuality.segments(96, 40, 150));
        assertEquals(56, TargetEspRenderQuality.segments(96, 40, 100));
        assertEquals(28, TargetEspRenderQuality.segments(64, 28, 60));

        int resolved = TargetEspRenderQuality.segments(92, 36, 125);
        assertTrue(resolved >= 36 && resolved <= 92);
        assertEquals("segment counts remain quad-aligned for stable GL strips", 0, resolved % 4);
    }
}
