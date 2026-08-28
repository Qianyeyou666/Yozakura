package gq.yozakura.module.world;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SpeedMinePolicyTest {
    @Test
    public void appliesConfiguredMultiplierOnlyWhileMining() {
        assertEquals(0.0F, SpeedMinePolicy.extraDamage(false, 0.25F, 2.0D), 0.0F);
        assertEquals(0.0F, SpeedMinePolicy.extraDamage(true, 0.0F, 2.0D), 0.0F);
        assertEquals(0.25F, SpeedMinePolicy.extraDamage(true, 0.25F, 2.0D), 0.0F);
        assertEquals(0.75F, SpeedMinePolicy.extraDamage(true, 0.25F, 4.0D), 0.0F);
    }

    @Test
    public void normalizesPersistedSpeedAndFinishThreshold() {
        assertEquals(1.0D, SpeedMinePolicy.normalizeSpeed(null), 0.0D);
        assertEquals(1.0D, SpeedMinePolicy.normalizeSpeed(Double.NaN), 0.0D);
        assertEquals(1.0D, SpeedMinePolicy.normalizeSpeed(0.5D), 0.0D);
        assertEquals(5.0D, SpeedMinePolicy.normalizeSpeed(99.0D), 0.0D);

        assertEquals(0.70F, SpeedMinePolicy.normalizeFinishThreshold(null), 0.0F);
        assertEquals(0.50F, SpeedMinePolicy.normalizeFinishThreshold(0.1D), 0.0F);
        assertEquals(0.95F, SpeedMinePolicy.normalizeFinishThreshold(1.0D), 0.0F);
    }

    @Test
    public void finishOnlyTriggersForAnActiveNonInstantBlock() {
        assertFalse(SpeedMinePolicy.shouldFinish(false, 0.80F, 0.70F, 0.25F));
        assertFalse(SpeedMinePolicy.shouldFinish(true, 0.80F, 0.70F, 1.0F));
        assertFalse(SpeedMinePolicy.shouldFinish(true, 0.60F, 0.70F, 0.25F));
        assertTrue(SpeedMinePolicy.shouldFinish(true, 0.70F, 0.70F, 0.25F));
    }
}
