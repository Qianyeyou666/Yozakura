package gq.yozakura.module.combat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JumpResetControllerTest {
    @Test
    public void fakeCheckConsumesExactlyOneHurtConfirmedVelocity() {
        JumpResetController controller = new JumpResetController();

        assertFalse(controller.acceptVelocity(true));

        controller.observePlayerHurt();
        assertTrue(controller.acceptVelocity(true));
        assertFalse(controller.acceptVelocity(true));
    }

    @Test
    public void normalModeAcceptsVelocityWithoutAStatusPacket() {
        JumpResetController controller = new JumpResetController();

        assertTrue(controller.acceptVelocity(false));
    }

    @Test
    public void chanceUsesAStablePercentageAcrossEligibleVelocityPackets() {
        JumpResetController controller = new JumpResetController();

        assertFalse(controller.acceptVelocity(false, 50));
        assertTrue(controller.acceptVelocity(false, 50));
        assertFalse(controller.acceptVelocity(false, 50));
        assertTrue(controller.acceptVelocity(false, 50));
    }

    @Test
    public void failedChanceConsumesTheFakeCheckConfirmation() {
        JumpResetController controller = new JumpResetController();
        controller.observePlayerHurt();

        assertFalse(controller.acceptVelocity(true, 0));
        assertFalse(controller.acceptVelocity(true, 100));
    }

    @Test
    public void resetWindowPressesJumpForTwoTicksForcesForwardForThreeAndReleasesOnce() {
        JumpResetController controller = new JumpResetController();
        controller.acceptVelocity(false);

        assertEquals(JumpResetController.JumpAction.PRESS, controller.advance(true));
        assertTrue(controller.shouldForceForward());

        assertEquals(JumpResetController.JumpAction.PRESS, controller.advance(true));
        assertTrue(controller.shouldForceForward());

        assertEquals(JumpResetController.JumpAction.NONE, controller.advance(true));
        assertTrue(controller.shouldForceForward());

        assertEquals(JumpResetController.JumpAction.RELEASE, controller.advance(true));
        assertFalse(controller.shouldForceForward());
        assertEquals(JumpResetController.JumpAction.NONE, controller.advance(true));
    }

    @Test
    public void airborneResetStillKeepsTheForwardWindowButDoesNotPressJump() {
        JumpResetController controller = new JumpResetController();
        controller.acceptVelocity(false);

        assertEquals(JumpResetController.JumpAction.NONE, controller.advance(false));
        assertTrue(controller.shouldForceForward());
    }

    @Test
    public void aNewVelocityRestartsTheResetWindowBeforeThePreviousOneCanRelease() {
        JumpResetController controller = new JumpResetController();
        controller.acceptVelocity(false);
        controller.advance(true);
        controller.advance(true);
        controller.advance(true);

        controller.acceptVelocity(false);
        assertEquals(JumpResetController.JumpAction.PRESS, controller.advance(true));
        assertTrue(controller.shouldForceForward());
    }

    @Test
    public void cancellingAnActiveWindowRequestsOneKeyRelease() {
        JumpResetController controller = new JumpResetController();
        controller.acceptVelocity(false);
        controller.advance(true);

        assertEquals(JumpResetController.JumpAction.RELEASE, controller.cancel());
        assertFalse(controller.shouldForceForward());
        assertEquals(JumpResetController.JumpAction.NONE, controller.cancel());
    }
}
