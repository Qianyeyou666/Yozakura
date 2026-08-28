package gq.yozakura.module.combat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KillAuraAutoBlockControllerTest {
    @Test
    public void startsAndHoldsOneOwnedBlockSessionWithoutRepeatedUsePackets() {
        KillAuraAutoBlockController controller = new KillAuraAutoBlockController();

        assertEquals(KillAuraAutoBlockController.Action.START_BLOCK,
                controller.update(100L, true, true, false, 10));
        controller.onBlockStarted();
        assertTrue(controller.isBlocking());
        assertEquals(KillAuraAutoBlockController.Action.NONE,
                controller.update(120L, true, true, false, 10));
    }

    @Test
    public void opensASeparateAttackWindowThenReblocksOnTheNextUpdate() {
        KillAuraAutoBlockController controller = blockedController();

        assertEquals(KillAuraAutoBlockController.Action.RELEASE_FOR_ATTACK,
                controller.update(200L, true, true, true, 10));
        controller.onBlockStopped();
        assertTrue(controller.isAttackWindowOpen());
        assertEquals(KillAuraAutoBlockController.Action.ATTACK,
                controller.update(201L, true, true, true, 10));
        controller.onAttackResult(true, 201L, 80L);
        assertEquals(KillAuraAutoBlockController.Action.START_BLOCK,
                controller.update(202L, true, true, false, 10));
    }

    @Test
    public void failedAttackClosesTheWindowAndStillRestoresBlocking() {
        KillAuraAutoBlockController controller = blockedController();
        controller.update(200L, true, true, true, 10);
        controller.onBlockStopped();
        controller.update(201L, true, true, true, 10);
        controller.onAttackResult(false, 201L, 80L);

        assertFalse(controller.isAttackWindowOpen());
        assertEquals(KillAuraAutoBlockController.Action.START_BLOCK,
                controller.update(202L, true, true, false, 10));
    }

    @Test
    public void targetCanBlockOutsideAttackRangeWithoutOpeningAnAttackWindow() {
        KillAuraAutoBlockController controller = new KillAuraAutoBlockController();

        assertEquals(KillAuraAutoBlockController.Action.START_BLOCK,
                controller.update(100L, true, false, false, 10));
        controller.onBlockStarted();
        assertEquals(KillAuraAutoBlockController.Action.NONE,
                controller.update(250L, true, false, true, 10));
        assertFalse(controller.isAttackWindowOpen());
    }

    @Test
    public void losingOwnershipAlwaysRequestsOneReleaseAndResets() {
        KillAuraAutoBlockController controller = blockedController();

        assertEquals(KillAuraAutoBlockController.Action.RELEASE,
                controller.update(200L, false, false, false, 10));
        controller.onBlockStopped();
        assertEquals(KillAuraAutoBlockController.Phase.IDLE, controller.getPhase());
        assertEquals(KillAuraAutoBlockController.Action.NONE,
                controller.update(201L, false, false, false, 10));
    }

    @Test
    public void failedAcceptedBlockWriteRollsBackTheOwnedBlockSession() {
        KillAuraAutoBlockController controller = new KillAuraAutoBlockController();
        controller.update(100L, true, true, false, 3);
        controller.onBlockStarted();

        controller.onBlockWriteFailed();

        assertFalse(controller.isBlocking());
        assertEquals(KillAuraAutoBlockController.Phase.REBLOCK_PENDING, controller.getPhase());
    }

    @Test
    public void failedAcceptedReleaseWriteRestoresBlockingBeforeRetrying() {
        KillAuraAutoBlockController controller = blockedController();
        controller.update(433L, true, true, true, 3);
        controller.onBlockStopped();

        controller.onReleaseWriteFailed();

        assertTrue(controller.isBlocking());
        assertFalse(controller.isAttackWindowOpen());
        assertEquals(KillAuraAutoBlockController.Phase.BLOCKING, controller.getPhase());
    }

    @Test
    public void failedOwnershipReleaseRetriesWhenBlockingIsNoLongerWanted() {
        KillAuraAutoBlockController controller = blockedController();
        controller.update(200L, false, false, false, 10);

        controller.onBlockStopFailed();

        assertTrue(controller.isBlocking());
        assertEquals(KillAuraAutoBlockController.Action.RELEASE,
                controller.update(201L, false, false, false, 10));
    }

    private static KillAuraAutoBlockController blockedController() {
        KillAuraAutoBlockController controller = new KillAuraAutoBlockController();
        controller.update(100L, true, true, false, 3);
        controller.onBlockStarted();
        return controller;
    }
}
