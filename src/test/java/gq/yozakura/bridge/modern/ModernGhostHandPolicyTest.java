package gq.yozakura.bridge.modern;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ModernGhostHandPolicyTest {
    @Test
    public void skipsEligibleNonBotPlayer() {
        assertTrue(ModernGhostHandPolicy.shouldSkip(true, false,
                false, false, false, false));
    }

    @Test
    public void teamOnlyRequiresSameTeam() {
        assertFalse(ModernGhostHandPolicy.shouldSkip(true, false,
                true, false, false, false));
        assertTrue(ModernGhostHandPolicy.shouldSkip(true, false,
                true, true, false, false));
    }

    @Test
    public void ignoreWeaponsPreservesProtectedTargets() {
        assertFalse(ModernGhostHandPolicy.shouldSkip(true, false,
                false, false, true, true));
        assertTrue(ModernGhostHandPolicy.shouldSkip(true, false,
                false, false, true, false));
    }

    @Test
    public void neverSkipsBotsOrNonPlayers() {
        assertFalse(ModernGhostHandPolicy.shouldSkip(true, true,
                false, false, false, false));
        assertFalse(ModernGhostHandPolicy.shouldSkip(false, false,
                false, false, false, false));
    }
}
