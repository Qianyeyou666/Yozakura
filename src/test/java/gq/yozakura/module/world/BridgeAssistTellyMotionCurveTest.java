package gq.yozakura.module.world;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BridgeAssistTellyMotionCurveTest {
    private static final float DELTA = 0.0001F;

    @Test
    public void boundsAxisReversalAndConvergesWithoutOvershoot() {
        TellyBridgeMotionCurve curve = new TellyBridgeMotionCurve();
        curve.reset(-1.0F, -1.0F);

        TellyBridgeMotionCurve.Sample first = curve.sample(1.0F, 0.0F, false, true, 10);
        assertEquals(-0.45F, first.forward, DELTA);
        assertEquals(-0.55F, first.strafe, DELTA);

        TellyBridgeMotionCurve.Sample second = curve.sample(1.0F, 0.0F, false, true, 11);
        assertEquals(0.10F, second.forward, DELTA);
        assertEquals(-0.3025F, second.strafe, DELTA);

        TellyBridgeMotionCurve.Sample current = second;
        for (int tick = 12; tick < 32; tick++) {
            current = curve.sample(1.0F, 0.0F, false, true, tick);
            assertTrue(current.forward <= 1.0F);
            assertTrue(current.strafe <= 0.0F);
        }
        assertEquals(1.0F, current.forward, DELTA);
        assertEquals(0.0F, current.strafe, DELTA);
    }

    @Test
    public void emitsJumpOnlyAtWindowEntryAndLandingEdges() {
        TellyBridgeMotionCurve curve = new TellyBridgeMotionCurve();
        curve.reset(-1.0F, -1.0F);

        assertTrue(curve.sample(-1.0F, -1.0F, true, true, 20).jump);
        assertFalse(curve.sample(-1.0F, -1.0F, true, true, 21).jump);
        assertFalse(curve.sample(-1.0F, -1.0F, true, false, 22).jump);
        assertTrue(curve.sample(-1.0F, -1.0F, true, true, 23).jump);
        assertFalse(curve.sample(-1.0F, -1.0F, true, true, 24).jump);
        assertFalse(curve.sample(-1.0F, -1.0F, false, true, 25).jump);
        assertTrue(curve.sample(-1.0F, -1.0F, true, true, 26).jump);
    }

    @Test
    public void returnsTheSameResolvedSampleWithinOneTick() {
        TellyBridgeMotionCurve curve = new TellyBridgeMotionCurve();
        curve.reset(-1.0F, -1.0F);

        TellyBridgeMotionCurve.Sample first = curve.sample(1.0F, 0.0F, true, true, 30);
        TellyBridgeMotionCurve.Sample repeated = curve.sample(-1.0F, -1.0F, false, false, 30);
        assertEquals(first.forward, repeated.forward, DELTA);
        assertEquals(first.strafe, repeated.strafe, DELTA);
        assertEquals(first.jump, repeated.jump);
    }
}
