package gq.yozakura.module.combat;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BlockHitHelperThreatPredictorTest {
    @Test
    public void blocksAnInRangeOpponentSwingingTowardThePlayer() {
        assertTrue(BlockHitHelperThreatPredictor.isThreat(
                true, true, true, true, 2.8D, 3.6D, 18.0D, 65.0D, 0.0D));
    }

    @Test
    public void ignoresSwingsFacingAwayOrOutsideMeleeRange() {
        assertFalse(BlockHitHelperThreatPredictor.isThreat(
                true, true, true, true, 2.8D, 3.6D, 120.0D, 65.0D, 0.0D));
        assertFalse(BlockHitHelperThreatPredictor.isThreat(
                true, true, true, true, 4.2D, 3.6D, 10.0D, 65.0D, 0.0D));
    }

    @Test
    public void aVisibleMeleeSwingIsAThreatEvenWithoutWeaponMetadata() {
        assertTrue(BlockHitHelperThreatPredictor.isThreat(
                true, true, false, true, 2.8D, 3.6D, 18.0D, 65.0D, 0.0D));
    }

    @Test
    public void predictsAnArmedOpponentClosingWhileFacingThePlayer() {
        assertTrue(BlockHitHelperThreatPredictor.isThreat(
                true, true, true, false, 3.2D, 3.6D, 22.0D, 65.0D, 0.18D));
    }

    @Test
    public void doesNotPredictAnUnarmedOrRetreatingOpponent() {
        assertFalse(BlockHitHelperThreatPredictor.isThreat(
                true, true, false, false, 3.2D, 3.6D, 22.0D, 65.0D, 0.18D));
        assertFalse(BlockHitHelperThreatPredictor.isThreat(
                true, true, true, false, 3.2D, 3.6D, 22.0D, 65.0D, -0.05D));
    }

    @Test
    public void requiresVisibilityAndAValidOpponent() {
        assertFalse(BlockHitHelperThreatPredictor.isThreat(
                false, true, true, true, 2.8D, 3.6D, 18.0D, 65.0D, 0.0D));
        assertFalse(BlockHitHelperThreatPredictor.isThreat(
                true, false, true, true, 2.8D, 3.6D, 18.0D, 65.0D, 0.0D));
    }
}
