package gq.yozakura.module.world;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BridgeAssistTellyProgramTest {
    private static final float DELTA = 0.0001F;

    @Test
    public void preservesCompleteTwentyOnePhaseProgram() {
        float[] expectedYaw = new float[]{
                91.68F, 98.88F, 78.94F, 37.45F, 1.61F, -21.69F, -33.98F,
                -35.8F, -34.64F, -33.85F, -33.06F, -31.55F, -29.26F,
                -26.65F, -24.19F, -21.07F, -18.84F, -17.06F, -8.87F,
                2.61F, 41.94F
        };
        float[] expectedPitch = new float[]{
                64.31F, 59.95F, 60.57F, 61.46F, 60.64F, 58.89F, 56.91F,
                56.63F, 58.65F, 61.63F, 64.2F, 66.74F, 68.69F, 70.64F,
                73.01F, 75.37F, 77.46F, 78.56F, 78.9F, 77.22F, 72.25F
        };
        float[] expectedForward = new float[]{
                1.0F, 1.0F, 0.0F, 0.0F, -1.0F, -1.0F, -1.0F, -1.0F,
                -1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F,
                -1.0F, -1.0F, -1.0F, -1.0F, -1.0F, 1.0F
        };
        float[] expectedStrafe = new float[]{
                -1.0F, -1.0F, -1.0F, -1.0F, 0.0F, 0.0F, 0.0F, 0.0F,
                0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F,
                0.0F, -1.0F, -1.0F, -1.0F, -1.0F
        };

        float[] actualYaw = new float[TellyBridgeProgram.length()];
        float[] actualPitch = new float[TellyBridgeProgram.length()];
        float[] actualForward = new float[TellyBridgeProgram.length()];
        float[] actualStrafe = new float[TellyBridgeProgram.length()];
        for (int phase = 0; phase < TellyBridgeProgram.length(); phase++) {
            actualYaw[phase] = TellyBridgeProgram.yaw(phase);
            actualPitch[phase] = TellyBridgeProgram.pitch(phase);
            actualForward[phase] = TellyBridgeProgram.forward(phase);
            actualStrafe[phase] = TellyBridgeProgram.strafe(phase);
        }

        assertEquals(21, TellyBridgeProgram.length());
        assertArrayEquals(expectedYaw, actualYaw, DELTA);
        assertArrayEquals(expectedPitch, actualPitch, DELTA);
        assertArrayEquals(expectedForward, actualForward, DELTA);
        assertArrayEquals(expectedStrafe, actualStrafe, DELTA);
    }

    @Test
    public void preservesSprintJumpAndUseWindows() {
        for (int phase = 0; phase < TellyBridgeProgram.length(); phase++) {
            assertEquals(phase == 0 || phase == 1, TellyBridgeProgram.sprinting(phase));
            assertEquals(phase >= 1 && phase <= 19, TellyBridgeProgram.jumping(phase));
            assertEquals(phase >= 7, TellyBridgeProgram.using(phase));
        }
    }

    @Test
    public void wrapsPhasesAndAcceptsOnlyDiagonalActivationYaw() {
        assertEquals(TellyBridgeProgram.yaw(20), TellyBridgeProgram.yaw(-1), DELTA);
        assertEquals(TellyBridgeProgram.pitch(0), TellyBridgeProgram.pitch(21), DELTA);

        assertTrue(TellyBridgeProgram.isActivationYawAligned(45.0F));
        assertTrue(TellyBridgeProgram.isActivationYawAligned(136.9F));
        assertTrue(TellyBridgeProgram.isActivationYawAligned(-135.0F));
        assertFalse(TellyBridgeProgram.isActivationYawAligned(137.1F));
        assertFalse(TellyBridgeProgram.isActivationYawAligned(90.0F));
    }

    @Test
    public void resolvesCardinalTravelFromDiagonalActivationYaw() {
        assertEquals(0, TellyBridgeProgram.travelX(45.0F));
        assertEquals(-1, TellyBridgeProgram.travelZ(45.0F));
        assertEquals(1, TellyBridgeProgram.travelX(135.0F));
        assertEquals(0, TellyBridgeProgram.travelZ(135.0F));
        assertEquals(0, TellyBridgeProgram.travelX(-135.0F));
        assertEquals(1, TellyBridgeProgram.travelZ(-135.0F));
        assertEquals(-1, TellyBridgeProgram.travelX(-45.0F));
        assertEquals(0, TellyBridgeProgram.travelZ(-45.0F));
    }
}
