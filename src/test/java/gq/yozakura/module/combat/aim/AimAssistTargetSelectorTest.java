package gq.yozakura.module.combat.aim;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AimAssistTargetSelectorTest {
    @Test
    public void keepsCurrentTargetDuringMinimumLockWindow() {
        assertFalse(AimAssistTargetLock.shouldSwitch(20.0D, 2.0D, 2.0D, 149L, 150L));
    }

    @Test
    public void keepsCurrentTargetWhenChallengerDoesNotBeatHysteresisMargin() {
        assertFalse(AimAssistTargetLock.shouldSwitch(10.0D, 8.5D, 2.0D, 300L, 150L));
    }

    @Test
    public void switchesWhenChallengerClearlyBeatsCurrentTarget() {
        assertTrue(AimAssistTargetLock.shouldSwitch(10.0D, 7.5D, 2.0D, 300L, 150L));
    }
}
