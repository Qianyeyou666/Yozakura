package gq.yozakura.bridge;

import gq.yozakura.event.bridge.SneakInputEvent;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SneakInputCoordinatorTest {
    @Test
    public void appliesVanillaSneakScalingExactlyOnceWhenBridgeAssistForcesSneak() {
        SneakInputCoordinator.ResolvedInput resolved = SneakInputCoordinator.resolve(
                false, 1.0F, -1.0F, false, SneakInputEvent.SneakIntent.FORCE_ON);

        assertTrue(resolved.isSneaking());
        assertEquals(0.3F, resolved.getForward(), 0.00001F);
        assertEquals(-0.3F, resolved.getStrafe(), 0.00001F);
    }

    @Test
    public void restoresRawAxesWhenBridgeAssistForcesRelease() {
        SneakInputCoordinator.ResolvedInput resolved = SneakInputCoordinator.resolve(
                true, 1.0F, -1.0F, false, SneakInputEvent.SneakIntent.FORCE_OFF);

        assertEquals(false, resolved.isSneaking());
        assertEquals(1.0F, resolved.getForward(), 0.00001F);
        assertEquals(-1.0F, resolved.getStrafe(), 0.00001F);
    }

    @Test
    public void safeWalkWinsOverARequestedBridgeAssistRelease() {
        SneakInputCoordinator.ResolvedInput resolved = SneakInputCoordinator.resolve(
                true, 1.0F, 0.0F, true, SneakInputEvent.SneakIntent.FORCE_OFF);

        assertTrue(resolved.isSneaking());
        assertEquals(0.3F, resolved.getForward(), 0.00001F);
    }

    @Test
    public void keepsTheVanillaInputWhenNoModuleRequestsAnOverride() {
        SneakInputCoordinator.ResolvedInput resolved = SneakInputCoordinator.resolve(
                false, 0.5F, 0.0F, false, SneakInputEvent.SneakIntent.KEEP);

        assertEquals(false, resolved.isSneaking());
        assertEquals(0.5F, resolved.getForward(), 0.00001F);
    }
}
