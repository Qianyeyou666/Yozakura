package gq.yozakura.module.combat;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BlockHitHelperControllerTest {
    @Test
    public void predictedThreatCannotAcquireBlockBeforeThePlayerAttacks() {
        BlockHitHelperController controller = new BlockHitHelperController();

        BlockHitHelperController.Action action = controller.tick(false, true, false, 2);

        assertFalse(action.shouldHoldUse());
        assertFalse(action.shouldSuppressUse());
        assertFalse(action.shouldPressAttack());
        assertFalse(action.shouldForceBlockPose());
        assertFalse(controller.isActive());
    }

    @Test
    public void noThreatStopsAutomaticBlockAndPose() {
        BlockHitHelperController controller = new BlockHitHelperController();

        controller.tick(true, true, false, 5);
        BlockHitHelperController.Action release = controller.tick(false, false, true, 5);

        assertFalse(release.shouldHoldUse());
        assertFalse(release.shouldSuppressUse());
        assertFalse(release.shouldPressAttack());
        assertFalse(release.shouldForceBlockPose());
        assertFalse(controller.isActive());
    }

    @Test
    public void localAttackCanActivateTheSameStateMachineWithoutPrediction() {
        BlockHitHelperController controller = new BlockHitHelperController();

        BlockHitHelperController.Action acquire = controller.tick(true, true, false, 2);
        BlockHitHelperController.Action release = controller.tick(false, false, true, 2);

        assertTrue(acquire.shouldHoldUse());
        assertTrue(acquire.shouldForceBlockPose());
        assertFalse(release.shouldHoldUse());
        assertFalse(release.shouldForceBlockPose());
        assertFalse(controller.isActive());
    }

    @Test
    public void firstAttackTickDefersAutomaticBlockUntilTheNextTick() {
        BlockHitHelperController controller = new BlockHitHelperController();
        controller.armFirstAttackWarmUp();

        BlockHitHelperController.Action first = controller.tick(true, true, false, 2);
        BlockHitHelperController.Action second = controller.tick(true, true, false, 2);

        assertFalse(first.shouldHoldUse());
        assertFalse(first.shouldSuppressUse());
        assertFalse(first.shouldPressAttack());
        assertTrue(first.shouldForceBlockPose());
        assertTrue(second.shouldHoldUse());
        assertFalse(second.shouldSuppressUse());
        assertFalse(second.shouldPressAttack());
        assertTrue(second.shouldForceBlockPose());
    }

    @Test
    public void acquiredBlockStartsTheHelperReleaseWindowOnlyWhileAttacking() {
        BlockHitHelperController controller = new BlockHitHelperController();

        BlockHitHelperController.Action acquire = controller.tick(true, true, false, 2);
        BlockHitHelperController.Action first = controller.tick(true, true, true, 2);
        BlockHitHelperController.Action second = controller.tick(true, true, false, 2);

        assertTrue(acquire.shouldHoldUse());
        assertFalse(acquire.shouldSuppressUse());
        assertTrue(first.shouldSuppressUse());
        assertFalse(first.shouldPressAttack());
        assertTrue(second.shouldSuppressUse());
        assertTrue(second.shouldPressAttack());
    }

    @Test
    public void oneStopTickStillRepeatsAttackBeforeHoldingUseAgain() {
        BlockHitHelperController controller = new BlockHitHelperController();

        controller.tick(true, true, true, 1);
        BlockHitHelperController.Action restore = controller.tick(true, true, false, 1);

        assertTrue(restore.shouldPressAttack());
        assertTrue(restore.shouldHoldUse());
        assertFalse(restore.shouldSuppressUse());
        assertTrue(restore.shouldForceBlockPose());
    }

    @Test
    public void configuredWindowReturnsToPredictedBlock() {
        BlockHitHelperController controller = new BlockHitHelperController();

        controller.tick(true, true, true, 2);
        controller.tick(true, true, false, 2);
        BlockHitHelperController.Action restore = controller.tick(true, true, false, 2);

        assertTrue(restore.shouldHoldUse());
        assertFalse(restore.shouldSuppressUse());
        assertTrue(restore.shouldForceBlockPose());
        assertTrue(controller.isActive());
        assertFalse(controller.isHelping());
    }
}
