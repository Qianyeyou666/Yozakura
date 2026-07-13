package gq.yozakura.module.combat.aim;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AimAssistControllerTest {
    private static final AimAssistController.Settings REGULAR_SETTINGS =
            new AimAssistController.Settings(90.0F, 55.0F, 0.5F, true,
                    AimAssistController.Profile.REGULAR);

    @Test
    public void waitsForReactionDelayBeforeMoving() {
        AimAssistController controller = trackingController(100L, 90.0F, 20.0F);

        AimAssistController.Rotation waiting = controller.step(0.0F, 0.0F, 1.0F / 60.0F,
                99L, REGULAR_SETTINGS);
        AimAssistController.Rotation engaged = controller.step(0.0F, 0.0F, 1.0F / 60.0F,
                100L, REGULAR_SETTINGS);

        assertEquals(0.0F, waiting.getYaw(), 0.0001F);
        assertEquals(0.0F, waiting.getPitch(), 0.0001F);
        assertTrue(engaged.getYaw() > 0.0F);
        assertTrue(engaged.getPitch() > 0.0F);
    }

    @Test
    public void takesShortestYawPathAcrossWrappedBoundary() {
        AimAssistController controller = new AimAssistController();
        controller.acquireTarget(1, 0L, 0L, 179.0F, 0.0F);
        controller.setTargetRotation(-179.0F, 0.0F, 1.0F);

        float yaw = 179.0F;
        AimAssistController.Rotation result = null;
        for (int frame = 0; frame < 6; frame++) {
            result = controller.step(yaw, 0.0F, 1.0F / 60.0F,
                    frame * 16L, REGULAR_SETTINGS);
            yaw = result.getYaw();
        }

        assertTrue(yaw > 179.0F);
        assertTrue(yaw < 181.0F);
        assertTrue(result != null && result.getYawDelta() >= 0.0F);
    }

    @Test
    public void doesNotExceedConfiguredAngularSpeedOrOvershoot() {
        AimAssistController controller = trackingController(0L, 15.0F, 0.0F);
        float yaw = 0.0F;

        for (int frame = 0; frame < 240; frame++) {
            AimAssistController.Rotation result = controller.step(yaw, 0.0F, 1.0F / 120.0F,
                    frame * 8L, REGULAR_SETTINGS);
            assertTrue(Math.abs(result.getYawDelta()) <= 90.0F / 120.0F + 0.0001F);
            assertTrue(result.getYaw() <= 15.0F + 0.0001F);
            assertTrue(result.getYaw() >= yaw - 0.0001F);
            yaw = result.getYaw();
        }

        assertEquals(15.0F, yaw, AimAssistController.mouseQuantum(0.5F));
    }

    @Test
    public void producesSimilarMotionAtDifferentFrameRates() {
        float yawAt60 = simulateForOneSecond(60);
        float yawAt144 = simulateForOneSecond(144);

        assertEquals(yawAt60, yawAt144, 1.0F);
        assertTrue(yawAt60 > 45.0F);
        assertTrue(yawAt60 < 90.0F);
    }

    @Test
    public void accumulatesSubQuantumMotionIntoValidMouseSteps() {
        AimAssistController.Settings slow = new AimAssistController.Settings(8.0F, 8.0F, 0.5F,
                true, AimAssistController.Profile.REGULAR);
        AimAssistController controller = trackingController(0L, 90.0F, 0.0F);
        float yaw = 0.0F;
        float quantum = AimAssistController.mouseQuantum(0.5F);
        boolean observedWaitingFrame = false;
        boolean observedMouseStep = false;

        for (int frame = 0; frame < 240; frame++) {
            AimAssistController.Rotation result = controller.step(yaw, 0.0F, 1.0F / 240.0F,
                    frame * 4L, slow);
            float delta = result.getYawDelta();
            if (Math.abs(delta) < 0.0001F) {
                observedWaitingFrame = true;
            } else {
                observedMouseStep = true;
                assertEquals(Math.rint(delta / quantum), delta / quantum, 0.0001D);
            }
            yaw = result.getYaw();
        }

        assertTrue(observedWaitingFrame);
        assertTrue(observedMouseStep);
    }

    @Test
    public void retainsSubQuantumResidualUntilTheTargetCanAcceptAMouseStep() {
        AimAssistController.Settings slow = new AimAssistController.Settings(8.0F, 8.0F, 0.5F,
                true, AimAssistController.Profile.REGULAR);
        AimAssistController controller = trackingController(0L, 0.10F, 0.0F);

        for (int frame = 0; frame < 30; frame++) {
            AimAssistController.Rotation result = controller.step(0.0F, 0.0F, 1.0F / 120.0F,
                    frame * 8L, slow);
            assertEquals(0.0F, result.getYawDelta(), 0.0001F);
        }

        controller.setTargetRotation(0.20F, 0.0F, 1.0F);
        AimAssistController.Rotation expandedTarget = controller.step(0.0F, 0.0F, 1.0F / 120.0F,
                240L, slow);

        assertEquals(AimAssistController.mouseQuantum(0.5F), expandedTarget.getYawDelta(), 0.0001F);
    }

    @Test
    public void treatsExternalViewRotationAsAuthoritative() {
        AimAssistController controller = trackingController(0L, 90.0F, 0.0F);
        AimAssistController.Rotation first = controller.step(0.0F, 0.0F, 1.0F / 60.0F,
                1L, REGULAR_SETTINGS);
        assertTrue(first.getYaw() > 0.0F);

        AimAssistController.Rotation afterExternalChange = controller.step(-40.0F, 0.0F,
                1.0F / 60.0F, 17L, REGULAR_SETTINGS);

        assertTrue(afterExternalChange.getYaw() > -40.0F);
        assertTrue(afterExternalChange.getYaw() < -38.0F);
    }

    @Test
    public void disablesPitchAssistanceWithoutChangingYawAssistance() {
        AimAssistController.Settings horizontalOnly = new AimAssistController.Settings(90.0F, 55.0F,
                0.5F, false, AimAssistController.Profile.REGULAR);
        AimAssistController controller = trackingController(0L, 90.0F, 45.0F);

        AimAssistController.Rotation result = controller.step(0.0F, 12.0F, 1.0F / 60.0F,
                1L, horizontalOnly);

        assertTrue(result.getYaw() > 0.0F);
        assertEquals(12.0F, result.getPitch(), 0.0001F);
    }

    @Test
    public void releaseClearsTrackingAndMotionState() {
        AimAssistController controller = trackingController(0L, 90.0F, 20.0F);
        controller.step(0.0F, 0.0F, 1.0F / 60.0F, 1L, REGULAR_SETTINGS);

        controller.releaseTarget();

        assertFalse(controller.isTrackingTarget(1));
        AimAssistController.Rotation result = controller.step(25.0F, 10.0F, 1.0F / 60.0F,
                2L, REGULAR_SETTINGS);
        assertEquals(25.0F, result.getYaw(), 0.0001F);
        assertEquals(10.0F, result.getPitch(), 0.0001F);
    }

    @Test
    public void targetSwitchWaitsAgainAndDoesNotCarryOldVelocity() {
        AimAssistController controller = trackingController(0L, 90.0F, 0.0F);
        AimAssistController.Rotation first = controller.step(0.0F, 0.0F, 1.0F / 60.0F,
                1L, REGULAR_SETTINGS);
        assertTrue(first.getYaw() > 0.0F);

        controller.acquireTarget(2, 2L, 80L, first.getYaw(), 0.0F);
        controller.setTargetRotation(-90.0F, 0.0F, 1.0F);

        assertFalse(controller.isReady(81L));
        assertTrue(controller.isReady(82L));
        AimAssistController.Rotation afterSwitch = controller.step(first.getYaw(), 0.0F,
                1.0F / 60.0F, 82L, REGULAR_SETTINGS);
        assertTrue(afterSwitch.getYawDelta() < 0.0F);
    }

    private static AimAssistController trackingController(long reactionDelay, float targetYaw, float targetPitch) {
        AimAssistController controller = new AimAssistController();
        controller.acquireTarget(1, 0L, reactionDelay, 0.0F, 0.0F);
        controller.setTargetRotation(targetYaw, targetPitch, 1.0F);
        return controller;
    }

    private static float simulateForOneSecond(int framesPerSecond) {
        AimAssistController controller = trackingController(0L, 90.0F, 0.0F);
        float yaw = 0.0F;
        float delta = 1.0F / framesPerSecond;
        for (int frame = 0; frame < framesPerSecond; frame++) {
            yaw = controller.step(yaw, 0.0F, delta,
                    Math.round(frame * 1000.0F / framesPerSecond), REGULAR_SETTINGS).getYaw();
        }
        return yaw;
    }
}
