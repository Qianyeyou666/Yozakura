package gq.yozakura.module.combat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VelocityControllerTest {
    @Test
    public void attackWindowAcceptsOnlyOneEligibleRealAttack() {
        VelocityController controller = new VelocityController();
        controller.armAttackWindow(6);

        assertFalse(controller.acceptRealAttack(false, true, true));
        assertFalse(controller.acceptRealAttack(true, false, true));
        assertTrue(controller.acceptRealAttack(true, true, true));
        assertFalse(controller.isAttackWindowActive());
        assertTrue(controller.hasPendingAttackSlowdown());
        assertFalse(controller.acceptRealAttack(true, true, true));
        assertTrue(controller.consumePendingAttackSlowdown());
        assertFalse(controller.hasPendingAttackSlowdown());
    }

    @Test
    public void attackWindowExpiresInsteadOfFiringOnAStaleTarget() {
        VelocityController controller = new VelocityController();
        controller.armAttackWindow(2);

        controller.tick();
        assertTrue(controller.isAttackWindowActive());
        controller.tick();
        assertFalse(controller.isAttackWindowActive());
        assertFalse(controller.acceptRealAttack(true, true, false));
    }

    @Test
    public void nonSprintingAttackCanConsumeTheWindowWhenAllowed() {
        VelocityController controller = new VelocityController();
        controller.armAttackWindow(2);

        assertTrue(controller.acceptRealAttack(true, false, false));
        assertTrue(controller.consumePendingAttackSlowdown());
    }

    @Test
    public void retainedPercentScalesMotionAndClampsInvalidValues() {
        assertEquals(0.6D, VelocityController.scale(1.0D, 60), 0.00001D);
        assertEquals(0.0D, VelocityController.scale(1.0D, -20), 0.00001D);
        assertEquals(1.0D, VelocityController.scale(1.0D, 140), 0.00001D);
        assertEquals(-600, VelocityController.scalePacketMotion(-1000, 60));
    }

    @Test
    public void deterministicChanceDoesNotProduceLongRandomStreaks() {
        VelocityController controller = new VelocityController();

        assertFalse(controller.shouldApplyReduction(50));
        assertTrue(controller.shouldApplyReduction(50));
        assertFalse(controller.shouldApplyReduction(50));
        assertTrue(controller.shouldApplyReduction(50));
    }
}
