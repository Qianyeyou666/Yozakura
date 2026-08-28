package gq.yozakura.module.combat.aim;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AimAssistPitchHoldHysteresisTest {
    @Test
    public void requiresTheInnerBoxToBeginHoldingPitch() {
        AimAssistPitchHoldHysteresis hysteresis = new AimAssistPitchHoldHysteresis();

        assertFalse(hysteresis.update(true, false, true));
        assertTrue(hysteresis.update(true, true, true));
    }

    @Test
    public void remainsHeldUntilPitchLeavesTheOuterBox() {
        AimAssistPitchHoldHysteresis hysteresis = new AimAssistPitchHoldHysteresis();

        assertTrue(hysteresis.update(true, true, true));
        assertTrue(hysteresis.update(true, false, true));
        assertFalse(hysteresis.update(true, false, false));
    }

    @Test
    public void ineligibleFrameReleasesThePitchHoldImmediately() {
        AimAssistPitchHoldHysteresis hysteresis = new AimAssistPitchHoldHysteresis();

        assertTrue(hysteresis.update(true, true, true));
        assertFalse(hysteresis.update(false, true, true));
    }
}
