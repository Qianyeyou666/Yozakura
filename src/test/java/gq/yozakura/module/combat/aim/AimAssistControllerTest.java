package gq.yozakura.module.combat.aim;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AimAssistControllerTest {
    private static final AimAssistController.Settings SETTINGS = new AimAssistController.Settings(
            180.0F, 90.0F, 0.5F, true, AimAssistController.Profile.REGULAR);

    @Test
    public void reactionDelayBlocksAdaptiveOutputUntilReady() {
        AimAssistController controller = new AimAssistController();
        controller.acquireTarget(4, 1000L, 50L, 0.0F, 0.0F);
        controller.setTargetRotation(45.0F, 10.0F, 1.0F);

        assertEquals(0.0F, controller.step(0.0F, 0.0F, 1.0F / 60.0F, 1049L, SETTINGS)
                .getYawDelta(), 0.0001F);
        assertTrue(controller.step(0.0F, 0.0F, 1.0F / 60.0F, 1050L, SETTINGS)
                .getYawDelta() > 0.0F);
    }

    @Test
    public void yawUsesTheShortestWrappedDirection() {
        AimAssistController controller = readyController(179.0F, 0.0F, -179.0F, 0.0F);

        float yaw = 179.0F;
        float firstNonZeroDelta = 0.0F;
        for (int frame = 0; frame < 12 && firstNonZeroDelta == 0.0F; frame++) {
            AimAssistController.Rotation rotation = controller.step(
                    yaw, 0.0F, 1.0F / 60.0F, frame + 1L, SETTINGS);
            firstNonZeroDelta = rotation.getYawDelta();
            yaw = rotation.getYaw();
        }
        assertTrue(firstNonZeroDelta > 0.0F);
    }

    @Test
    public void verticalToggleLeavesPitchUntouched() {
        AimAssistController controller = readyController(0.0F, 12.0F, 30.0F, -20.0F);
        AimAssistController.Settings horizontalOnly = new AimAssistController.Settings(
                180.0F, 90.0F, 0.5F, false, AimAssistController.Profile.REGULAR);

        assertEquals(0.0F, controller.step(0.0F, 12.0F, 1.0F / 60.0F, 1L, horizontalOnly)
                .getPitchDelta(), 0.0001F);
    }

    @Test
    public void targetSwitchAndReleaseResetControllerState() {
        AimAssistController controller = readyController(0.0F, 0.0F, 45.0F, 5.0F);
        controller.step(0.0F, 0.0F, 1.0F / 60.0F, 1L, SETTINGS);

        assertTrue(controller.acquireTarget(9, 2L, 0L, 10.0F, 2.0F));
        assertFalse(controller.isReady(2L));
        controller.releaseTarget();
        assertEquals(-1, controller.getTargetId());
        assertEquals(0.0F, controller.step(10.0F, 2.0F, 1.0F / 60.0F, 3L, SETTINGS)
                .getYawDelta(), 0.0001F);
    }

    @Test
    public void outputUsesMinecraftSensitivityQuantum() {
        AimAssistController controller = readyController(0.0F, 0.0F, 90.0F, 0.0F);
        AimAssistController.Rotation rotation = controller.step(
                0.0F, 0.0F, 1.0F / 60.0F, 1L, SETTINGS);
        float quantum = AimAssistController.mouseQuantum(0.5F);

        assertEquals(Math.round(rotation.getYawDelta() / quantum) * quantum,
                rotation.getYawDelta(), 0.0001F);
    }

    private static AimAssistController readyController(float viewYaw, float viewPitch,
                                                        float targetYaw, float targetPitch) {
        AimAssistController controller = new AimAssistController();
        controller.acquireTarget(4, 0L, 0L, viewYaw, viewPitch);
        controller.setTargetRotation(targetYaw, targetPitch, 1.0F);
        return controller;
    }
}
