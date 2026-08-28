package gq.yozakura.module.combat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JumpResetVapeVelocityTest {
    @Test
    public void acceptsOnlyUsableHorizontalVelocityWithNonNegativeVerticalMotion() {
        JumpResetController controller = new JumpResetController();

        assertFalse(controller.acceptVelocity(false, 100, 0, 120, 0));
        assertFalse(controller.acceptVelocity(false, 100, 240, -1, 0));
        assertTrue(controller.acceptVelocity(false, 100, 240, 120, 0));
    }

    @Test
    public void usesOneTickJumpPulseAfterAnEligibleVelocity() {
        JumpResetController controller = new JumpResetController();
        controller.acceptVelocity(false, 100, 240, 120, 0);

        assertEquals(JumpResetController.JumpAction.PRESS, controller.advance(true));
        assertEquals(JumpResetController.JumpAction.RELEASE, controller.advance(true));
    }
}
