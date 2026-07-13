package gq.yozakura.module.combat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BlockHitControllerTest {
    @Test
    public void autoWaitsForTheMovementBoundaryAfterTheObservedAttack() {
        BlockHitController controller = new BlockHitController();

        controller.armAuto();

        assertFalse(controller.isAutoReadyAfterMovement());
        controller.advanceMovementEpoch();
        assertTrue(controller.isAutoReadyAfterMovement());
        controller.consumeAutoArm();
        assertFalse(controller.isAutoReadyAfterMovement());
    }

    @Test
    public void activeUseWindowExpiresAtItsRecordedDeadline() {
        BlockHitController controller = new BlockHitController();

        assertTrue(controller.beginUse(BlockHitController.UseOwner.AUTO, 100L, 50L));
        assertTrue(controller.isUseActive(149L));
        assertFalse(controller.isUseActive(150L));
        assertEquals(BlockHitController.UseOwner.NONE, controller.activeOwner(150L));
    }

    @Test
    public void manualUseCannotBePreemptedByPredict() {
        BlockHitController controller = new BlockHitController();

        assertTrue(controller.beginUse(BlockHitController.UseOwner.MANUAL, 100L, 50L));
        assertFalse(controller.beginUse(BlockHitController.UseOwner.PREDICT, 110L, 50L));
        assertEquals(BlockHitController.UseOwner.MANUAL, controller.activeOwner(110L));
    }

    @Test
    public void autoCanReplacePredictWithoutShorteningTheExistingWindow() {
        BlockHitController controller = new BlockHitController();

        assertTrue(controller.beginUse(BlockHitController.UseOwner.PREDICT, 100L, 100L));
        assertTrue(controller.beginUse(BlockHitController.UseOwner.AUTO, 120L, 50L));
        assertEquals(BlockHitController.UseOwner.AUTO, controller.activeOwner(150L));
        assertTrue(controller.isUseActive(199L));
        assertFalse(controller.isUseActive(200L));
    }
}
