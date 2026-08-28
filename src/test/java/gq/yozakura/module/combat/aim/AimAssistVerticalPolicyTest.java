package gq.yozakura.module.combat.aim;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AimAssistVerticalPolicyTest {
    @Test
    public void movingTargetKeepsPlayerPitchWhenTrackedYawAlreadyHitsTheBox() {
        assertTrue(AimAssistVerticalPolicy.shouldHoldPitch(false, true, true));
        assertFalse(AimAssistVerticalPolicy.shouldHoldPitch(false, true, false));
    }

    @Test
    public void existingGroundedPitchHoldBehaviorIsPreserved() {
        assertTrue(AimAssistVerticalPolicy.shouldHoldPitch(true, false, true));
        assertFalse(AimAssistVerticalPolicy.shouldHoldPitch(false, false, true));
    }

    @Test
    public void detectsMeaningfulTargetMotionWithoutTreatingTinyNoiseAsMovement() {
        assertTrue(AimAssistVerticalPolicy.isMoving(0.02D, 0.0D, 0.0D));
        assertFalse(AimAssistVerticalPolicy.isMoving(0.00001D, 0.0D, 0.0D));
    }
}
