package gq.yozakura.module.combat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BlockHitControllerTest {
    @Test
    public void attackArmsUseOnlyAfterTheFollowingConfirmedMovementBoundary() {
        BlockHitController controller = new BlockHitController();

        controller.observe(BlockHitController.PacketKind.ATTACK);
        assertTrue(controller.armUseAfterMovementBoundary());

        assertFalse(controller.consumeUseRequest());
        controller.observe(BlockHitController.PacketKind.MOVEMENT);
        assertFalse(controller.consumeUseRequest());
        controller.confirmMovementBoundary();

        assertTrue(controller.consumeUseRequest());
        assertTrue(controller.snapshot().isRequestedUseActive());
    }

    @Test
    public void automaticUseReleasesOnlyAfterItsOwnMovementBoundary() {
        BlockHitController controller = new BlockHitController();

        controller.observe(BlockHitController.PacketKind.ATTACK);
        assertTrue(controller.armUseAfterMovementBoundary());
        controller.confirmMovementBoundary();
        assertTrue(controller.consumeUseRequest());

        assertFalse(controller.consumeReleaseRequest());
        controller.confirmUseWritten();
        controller.confirmMovementBoundary();
        assertTrue(controller.consumeReleaseRequest());
        assertFalse(controller.snapshot().isRequestedUseActive());
    }

    @Test
    public void activeOrPendingCycleCannotBeArmedAgain() {
        BlockHitController controller = new BlockHitController();

        controller.observe(BlockHitController.PacketKind.ATTACK);
        assertTrue(controller.armUseAfterMovementBoundary());
        assertFalse(controller.armUseAfterMovementBoundary());
        controller.confirmMovementBoundary();
        assertTrue(controller.consumeUseRequest());
        assertFalse(controller.armUseAfterMovementBoundary());
    }

    @Test
    public void packetObservationRemainsSeparateFromSyntheticCycleState() {
        BlockHitController controller = new BlockHitController();

        controller.observe(BlockHitController.PacketKind.USE_ITEM);
        assertTrue(controller.snapshot().isObservedUseActive());
        assertFalse(controller.snapshot().isRequestedUseActive());
        controller.observe(BlockHitController.PacketKind.RELEASE_USE_ITEM);

        assertFalse(controller.snapshot().isObservedUseActive());
        assertFalse(controller.snapshot().isRequestedUseActive());
    }

    @Test
    public void resetClearsAllObservedState() {
        BlockHitController controller = new BlockHitController();
        controller.observe(BlockHitController.PacketKind.MOVEMENT);
        controller.observe(BlockHitController.PacketKind.ATTACK);
        controller.observe(BlockHitController.PacketKind.USE_ITEM);

        controller.reset();
        BlockHitController.Snapshot snapshot = controller.snapshot();

        assertEquals(0L, snapshot.getMovementEpoch());
        assertEquals(-1L, snapshot.getLastAttackEpoch());
        assertFalse(snapshot.isObservedUseActive());
        assertFalse(snapshot.isRequestedUseActive());
    }

    @Test
    public void cancelingAnActiveCycleDoesNotCreateALaterReleaseRequest() {
        BlockHitController controller = new BlockHitController();

        controller.observe(BlockHitController.PacketKind.ATTACK);
        assertTrue(controller.armUseAfterMovementBoundary());
        controller.confirmMovementBoundary();
        assertTrue(controller.consumeUseRequest());
        controller.cancelCycle();
        controller.confirmMovementBoundary();

        assertFalse(controller.consumeReleaseRequest());
        assertFalse(controller.snapshot().isRequestedUseActive());
    }

    @Test
    public void unconfirmedMovementPacketsCannotAdvanceTheCycle() {
        BlockHitController controller = new BlockHitController();

        controller.observe(BlockHitController.PacketKind.ATTACK);
        assertTrue(controller.armUseAfterMovementBoundary());
        controller.observe(BlockHitController.PacketKind.MOVEMENT);

        assertFalse(controller.consumeUseRequest());
        assertEquals(0L, controller.snapshot().getMovementEpoch());
    }

    @Test
    public void useCannotReleaseUntilItsOwnVanillaUsePacketWasWritten() {
        BlockHitController controller = new BlockHitController();

        controller.observe(BlockHitController.PacketKind.ATTACK);
        assertTrue(controller.armUseAfterMovementBoundary());
        controller.confirmMovementBoundary();
        assertTrue(controller.consumeUseRequest());

        controller.confirmMovementBoundary();
        assertFalse(controller.consumeReleaseRequest());

        controller.confirmUseWritten();
        controller.confirmMovementBoundary();
        assertTrue(controller.consumeReleaseRequest());
    }

    @Test
    public void staleUseWriteCannotConfirmANewerBlockHitCycle() {
        BlockHitController controller = new BlockHitController();

        controller.observe(BlockHitController.PacketKind.ATTACK);
        assertTrue(controller.armUseAfterMovementBoundary());
        controller.confirmMovementBoundary();
        assertTrue(controller.consumeUseRequest());
        long firstCycle = controller.getRequestedUseCycleId();

        controller.cancelCycle();
        controller.observe(BlockHitController.PacketKind.ATTACK);
        assertTrue(controller.armUseAfterMovementBoundary());
        controller.confirmMovementBoundary();
        assertTrue(controller.consumeUseRequest());
        long secondCycle = controller.getRequestedUseCycleId();

        assertFalse(firstCycle == secondCycle);
        assertFalse(controller.confirmUseWritten(firstCycle));
        controller.confirmMovementBoundary();
        assertFalse(controller.consumeReleaseRequest());

        assertTrue(controller.confirmUseWritten(secondCycle));
        controller.confirmMovementBoundary();
        assertTrue(controller.consumeReleaseRequest());
    }

    @Test
    public void attackAndReleaseRequireDifferentCanonicalMovementWindows() {
        BlockHitController controller = new BlockHitController();

        controller.observe(BlockHitController.PacketKind.ATTACK);
        long attackWindow = controller.snapshot().getMovementEpoch();
        assertTrue(controller.armUseAfterMovementBoundary());

        assertFalse(controller.consumeReleaseRequest());
        controller.confirmMovementBoundary();
        assertTrue(controller.consumeUseRequest());
        assertTrue(controller.confirmUseWritten(controller.getRequestedUseCycleId()));
        assertFalse(controller.consumeReleaseRequest());

        controller.confirmMovementBoundary();
        assertTrue(controller.consumeReleaseRequest());
        assertTrue(controller.snapshot().getMovementEpoch() > attackWindow);
    }

    @Test
    public void pendingReleaseCanBeDeferredWithoutDiscardingTheConfirmedUseCycle() {
        BlockHitController controller = new BlockHitController();

        controller.observe(BlockHitController.PacketKind.ATTACK);
        assertTrue(controller.armUseAfterMovementBoundary());
        controller.confirmMovementBoundary();
        assertTrue(controller.consumeUseRequest());
        assertTrue(controller.confirmUseWritten(controller.getRequestedUseCycleId()));
        controller.confirmMovementBoundary();

        assertTrue(controller.hasReleaseRequest());
        assertTrue(controller.hasReleaseRequest());
        assertTrue(controller.consumeReleaseRequest());
    }
}
