package gq.yozakura.module.combat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class KillAuraRotationControllerTest {
    @Test
    public void choosesTheShortestWrappedYawStep() {
        KillAuraRotationController controller = new KillAuraRotationController();

        KillAuraRotationController.Rotation next = controller.step(
                179.0F, 0.0F, -179.0F, 0.0F, 70.0F, 25.0F);

        assertTrue(next.getYaw() > 179.0F);
        assertTrue(next.getYaw() < 190.0F);
    }

    @Test
    public void capsLargeTurnsWhileAllowingSmallErrorsToSettle() {
        KillAuraRotationController controller = new KillAuraRotationController();

        KillAuraRotationController.Rotation first = controller.step(
                0.0F, 0.0F, 160.0F, 40.0F, 70.0F, 25.0F);
        KillAuraRotationController.Rotation settled = controller.step(
                10.0F, 5.0F, 10.1F, 5.1F, 70.0F, 25.0F);

        assertTrue(first.getYaw() > 0.0F && first.getYaw() < 70.0F);
        assertTrue(first.getPitch() > 0.0F && first.getPitch() < 40.0F);
        assertEquals(10.1F, settled.getYaw(), 0.2F);
        assertEquals(5.1F, settled.getPitch(), 0.2F);
    }

    @Test
    public void closestAimPointUsesTheReachableFaceInsteadOfTheBoxCenter() {
        KillAuraAimPoint.Point point = KillAuraAimPoint.closest(
                0.0D, 1.62D, 0.0D,
                2.0D, 0.0D, -1.0D,
                4.0D, 2.0D, 1.0D);

        assertEquals(2.0D, point.getX(), 0.000001D);
        assertEquals(1.62D, point.getY(), 0.000001D);
        assertEquals(0.0D, point.getZ(), 0.000001D);
    }
}
