package gq.yozakura.module.world;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BridgeAssistTellyRotationTest {
    private static final float DELTA = 0.0001F;

    @Test
    public void interpolatesOverRequestedFiftyMilliseconds() {
        TellyBridgeRotation rotation = new TellyBridgeRotation(0.5F);
        assertTrue(rotation.setTarget(0.0F, 60.0F, 20.0F, 80.0F, 1000L, 50L));

        TellyBridgeRotation.Sample halfway = rotation.sample(1025L);
        assertEquals(10.0F, halfway.yaw, DELTA);
        assertEquals(70.0F, halfway.pitch, DELTA);
        assertTrue(rotation.isActive());

        TellyBridgeRotation.Sample complete = rotation.sample(1050L);
        assertEquals(20.0F, complete.yaw, DELTA);
        assertEquals(80.0F, complete.pitch, DELTA);
        assertFalse(rotation.isActive());
    }

    @Test
    public void usesShortestYawPathAcrossWrapBoundary() {
        TellyBridgeRotation rotation = new TellyBridgeRotation(0.1F);
        rotation.setTarget(170.0F, 0.0F, -170.0F, 0.0F, 0L, 50L);

        TellyBridgeRotation.Sample halfway = rotation.sample(25L);
        TellyBridgeRotation.Sample complete = rotation.sample(50L);
        assertEquals(179.9F, halfway.yaw, DELTA);
        assertEquals(189.9F, complete.yaw, DELTA);
    }

    @Test
    public void quantizesFromSegmentOriginAndClampsPitch() {
        TellyBridgeRotation rotation = new TellyBridgeRotation(0.5F);
        rotation.setTarget(1.2F, 10.2F, 3.1F, 100.0F, 0L, 50L);

        TellyBridgeRotation.Sample complete = rotation.sample(50L);
        assertEquals(2.7F, complete.yaw, DELTA);
        assertEquals(89.7F, complete.pitch, DELTA);
    }

    @Test
    public void easesVelocityAtBothEndsInsteadOfMovingLinearly() {
        TellyBridgeRotation rotation = new TellyBridgeRotation(0.1F);
        rotation.setTarget(0.0F, 0.0F, 20.0F, 20.0F, 0L, 100L);

        TellyBridgeRotation.Sample quarter = rotation.sample(25L);
        TellyBridgeRotation.Sample halfway = rotation.sample(50L);
        TellyBridgeRotation.Sample threeQuarters = rotation.sample(75L);
        assertEquals(3.1F, quarter.yaw, DELTA);
        assertEquals(10.0F, halfway.yaw, DELTA);
        assertEquals(16.8F, threeQuarters.yaw, DELTA);
        assertTrue(quarter.yaw < 5.0F);
        assertTrue(threeQuarters.yaw > 15.0F);
    }

    @Test
    public void retargetsFromCurrentInterpolatedSample() {
        TellyBridgeRotation rotation = new TellyBridgeRotation(0.1F);
        rotation.setTarget(0.0F, 0.0F, 20.0F, 20.0F, 0L, 50L);
        assertTrue(rotation.setTarget(0.0F, 0.0F, 30.0F, 30.0F, 25L, 50L));

        TellyBridgeRotation.Sample retargetStart = rotation.sample(25L);
        TellyBridgeRotation.Sample retargetEnd = rotation.sample(75L);
        assertEquals(9.9F, retargetStart.yaw, DELTA);
        assertEquals(9.9F, retargetStart.pitch, DELTA);
        assertEquals(29.9F, retargetEnd.yaw, DELTA);
        assertEquals(29.9F, retargetEnd.pitch, DELTA);
        assertFalse(rotation.setTarget(30.0F, 30.0F, 30.0F, 30.0F, 80L, 50L));
    }
}
