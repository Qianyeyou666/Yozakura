package gq.yozakura.module.combat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class KillAuraRotationQuantizerTest {
    @Test
    public void usesTheVanillaMouseQuantumForTheConfiguredSensitivity() {
        float quantum = KillAuraRotationQuantizer.quantum(0.5F);
        float yaw = KillAuraRotationQuantizer.quantizeYaw(12.0F, 13.07F, 0.5F);

        assertEquals(0.15F, quantum, 0.000001F);
        assertEquals(Math.round((yaw - 12.0F) / quantum), (yaw - 12.0F) / quantum, 0.00001F);
    }

    @Test
    public void followsTheShortestYawPathAcrossTheWrappedBoundary() {
        float yaw = KillAuraRotationQuantizer.quantizeYaw(179.0F, -179.0F, 0.5F);

        assertTrue(yaw > 179.0F);
        assertTrue(yaw < 182.0F);
    }

    @Test
    public void clampsPitchAfterQuantization() {
        assertEquals(90.0F,
                KillAuraRotationQuantizer.quantizePitch(89.9F, 95.0F, 1.0F), 0.00001F);
        assertEquals(-90.0F,
                KillAuraRotationQuantizer.quantizePitch(-89.9F, -95.0F, 1.0F), 0.00001F);
    }
}
