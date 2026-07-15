package gq.yozakura.module.world;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FastPlacePolicyTest {
    @Test
    public void onlyBlocksPreventsFoodAndBlockUseFromAccelerating() {
        assertFalse(FastPlacePolicy.shouldCapCooldown(false, true, true));
        assertFalse(FastPlacePolicy.shouldCapCooldown(true, true, false));
        assertTrue(FastPlacePolicy.shouldCapCooldown(true, true, true));
        assertTrue(FastPlacePolicy.shouldCapCooldown(true, false, false));
    }

    @Test
    public void normalizesPersistedDelayToTheVanillaCooldownRange() {
        assertEquals(0, FastPlacePolicy.normalizeDelayTicks(null));
        assertEquals(0, FastPlacePolicy.normalizeDelayTicks(-1));
        assertEquals(0, FastPlacePolicy.normalizeDelayTicks(Double.NaN));
        assertEquals(0, FastPlacePolicy.normalizeDelayTicks(Double.POSITIVE_INFINITY));
        assertEquals(3, FastPlacePolicy.normalizeDelayTicks(2.6D));
        assertEquals(4, FastPlacePolicy.normalizeDelayTicks(99));
    }
}
