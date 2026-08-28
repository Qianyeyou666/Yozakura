package gq.yozakura.bridge.modern;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ModernVisibleAimStateTest {
    @Test
    public void axisSpeedsRemainIndependentAndBounded() {
        ModernVisibleAimState state = new ModernVisibleAimState();
        float[] rotation = state.update(0.0F, 0.0F,
                90.0F, 45.0F, 24.0F, 2.0F);

        assertTrue(rotation[0] > 0.0F && rotation[0] <= 24.0F);
        assertTrue(rotation[1] > 0.0F && rotation[1] <= 2.0F);
    }

    @Test
    public void resetStartsFromCurrentVisibleRotation() {
        ModernVisibleAimState state = new ModernVisibleAimState();
        state.update(0.0F, 0.0F, 90.0F, 45.0F, 24.0F, 18.0F);
        state.reset();

        float[] rotation = state.update(-30.0F, 10.0F,
                -30.0F, 10.0F, 24.0F, 18.0F);
        assertEquals(-30.0D, rotation[0], 0.000001D);
        assertEquals(10.0D, rotation[1], 0.000001D);
    }

    @Test
    public void yawUsesShortestWrappedPath() {
        ModernVisibleAimState state = new ModernVisibleAimState();
        float[] rotation = state.update(179.0F, 0.0F,
                -179.0F, 0.0F, 24.0F, 18.0F);

        assertTrue(rotation[0] > 179.0F);
    }
}
