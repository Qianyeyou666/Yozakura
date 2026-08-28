package gq.yozakura.module.combat.aim;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AimAssistVerticalStabilityTest {
    @Test
    public void damagedAirbornePlayerKeepsAnAlreadyHittingPitch() {
        AimAssistVerticalStability stability = new AimAssistVerticalStability();

        stability.update(true, 0);
        stability.update(true, 10);
        stability.update(false, 9);

        assertTrue(stability.shouldHoldPitch(true));
        assertFalse(stability.shouldHoldPitch(false));
    }

    @Test
    public void landingFromKnockbackKeepsPitchStableForThreeTicks() {
        AimAssistVerticalStability stability = new AimAssistVerticalStability();

        stability.update(true, 0);
        stability.update(true, 10);
        stability.update(false, 9);
        stability.update(true, 8);

        assertTrue(stability.shouldHoldPitch(true));
        stability.update(true, 7);
        assertTrue(stability.shouldHoldPitch(true));
        stability.update(true, 6);
        assertTrue(stability.shouldHoldPitch(true));
        stability.update(true, 5);
        assertFalse(stability.shouldHoldPitch(true));
    }

    @Test
    public void normalJumpDoesNotFreezePitch() {
        AimAssistVerticalStability stability = new AimAssistVerticalStability();

        stability.update(true, 0);
        stability.update(false, 0);

        assertFalse(stability.shouldHoldPitch(true));
    }
}
