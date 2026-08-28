package gq.yozakura.bridge.modern;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ModernHitSelectStateTest {
    @Test
    public void newTargetAndHurtRiseWaitForConfiguredDelay() {
        ModernHitSelectState state = new ModernHitSelectState();

        assertFalse(state.shouldAttack(1_000L, 7, 0, 0,
                "Vulnerable", 2, 7, 0L, true, 50L));
        assertTrue(state.shouldAttack(1_050L, 7, 0, 0,
                "Vulnerable", 2, 7, 0L, true, 50L));
        assertFalse(state.shouldAttack(1_060L, 7, 4, 0,
                "Vulnerable", 4, 7, 0L, true, 50L));
        assertTrue(state.shouldAttack(1_110L, 7, 4, 0,
                "Vulnerable", 4, 7, 0L, true, 50L));
    }

    @Test
    public void smartTradeAllowsSlightlyHigherTargetHurtTime() {
        ModernHitSelectState smart = new ModernHitSelectState();
        ModernHitSelectState vulnerable = new ModernHitSelectState();

        assertTrue(smart.shouldAttack(2_000L, 9, 4, 3,
                "Smart", 2, 7, 0L, true, 0L));
        assertFalse(vulnerable.shouldAttack(2_000L, 9, 4, 3,
                "Vulnerable", 2, 7, 0L, true, 0L));
    }

    @Test
    public void postAttackDelayAndChanceGateAttacks() {
        ModernHitSelectState state = new ModernHitSelectState();
        state.onAttack(3_000L, 11, 0, 0L);

        assertFalse(state.shouldAttack(3_079L, 11, 0, 0,
                "Vulnerable", 2, 7, 80L, true, 0L));
        assertFalse(state.shouldAttack(3_080L, 11, 0, 0,
                "Vulnerable", 2, 7, 80L, false, 0L));
        assertTrue(state.shouldAttack(3_080L, 11, 0, 0,
                "Vulnerable", 2, 7, 80L, true, 0L));
    }

    @Test
    public void resetRemovesPreviousAttackDelay() {
        ModernHitSelectState state = new ModernHitSelectState();
        state.onAttack(4_000L, 13, 0, 100L);
        state.reset();

        assertTrue(state.shouldAttack(0L, 13, 0, 0,
                "Vulnerable", 2, 7, 0L, true, 0L));
    }
}
