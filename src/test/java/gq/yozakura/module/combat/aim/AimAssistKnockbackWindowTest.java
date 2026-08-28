package gq.yozakura.module.combat.aim;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AimAssistKnockbackWindowTest {
    @Test
    public void eitherDamagedAirborneEntityOpensTheFreeZoneWindow() {
        AimAssistKnockbackWindow window = new AimAssistKnockbackWindow();

        window.update(true, 0, true, 0);
        window.update(true, 10, true, 0);
        window.update(false, 9, true, 0);
        assertTrue(window.isActive());

        window.reset();
        window.update(true, 0, true, 0);
        window.update(true, 0, false, 10);
        assertTrue(window.isActive());
    }

    @Test
    public void targetJumpOpensTheFreeZoneWindow() {
        AimAssistKnockbackWindow window = new AimAssistKnockbackWindow();

        window.update(true, 0, 0.0D, false, 0, 0.12D);

        assertTrue(window.isActive());
    }

    @Test
    public void verticalKnockbackOpensImmediatelyEvenBeforeGroundFlagClears() {
        AimAssistKnockbackWindow window = new AimAssistKnockbackWindow();

        window.update(true, 10, 0.18D, true, 0, 0.0D);

        assertTrue(window.isActive());
    }

    @Test
    public void landingKeepsThreeTicksOfRecovery() {
        AimAssistKnockbackWindow window = new AimAssistKnockbackWindow();
        window.update(true, 10, true, 0);
        window.update(false, 9, true, 0);
        window.update(true, 8, true, 0);

        assertTrue(window.isActive());
        window.update(true, 7, true, 0);
        assertTrue(window.isActive());
        window.update(true, 6, true, 0);
        assertTrue(window.isActive());
        window.update(true, 5, true, 0);
        assertFalse(window.isActive());
    }
}
