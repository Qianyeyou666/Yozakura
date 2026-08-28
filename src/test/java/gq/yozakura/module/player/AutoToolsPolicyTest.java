package gq.yozakura.module.player;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AutoToolsPolicyTest {
    @Test
    public void efficiencyEnchantContributesOnlyForEffectiveTools() {
        assertEquals(8.0F, AutoToolsPolicy.score(8.0F, 5, true), 0.0F);
        assertEquals(34.0F, AutoToolsPolicy.score(8.0F, 5, false), 0.0F);
        assertEquals(1.0F, AutoToolsPolicy.score(1.0F, 5, false), 0.0F);
    }

    @Test
    public void combatScoreIncludesSharpnessDamageAgainstPlayers() {
        assertEquals(7.0F, AutoToolsPolicy.combatScore(7.0F, 0), 0.0F);
        assertEquals(8.25F, AutoToolsPolicy.combatScore(7.0F, 1), 0.0F);
        assertEquals(13.25F, AutoToolsPolicy.combatScore(7.0F, 5), 0.0F);
    }

    @Test
    public void stableSelectionKeepsCurrentSlotOnTiesAndSkipsLowDurability() {
        float[] scores = {1.0F, 8.0F, 8.0F, 12.0F};
        boolean[] usable = {true, true, true, false};

        assertEquals(2, AutoToolsPolicy.bestSlot(2, scores, usable));
        assertEquals(1, AutoToolsPolicy.bestSlot(0, scores, usable));
    }

    @Test
    public void detectsManualHotbarTakeoverWithoutTreatingTheOwnedSlotAsAnOverride() {
        assertFalse(AutoToolsPolicy.isManualOverride(false, 4, 5));
        assertFalse(AutoToolsPolicy.isManualOverride(true, 5, 5));
        assertTrue(AutoToolsPolicy.isManualOverride(true, 4, 5));
    }

    @Test
    public void restoreOnlyRunsWhileAutoToolsStillOwnsTheSelectedSlot() {
        assertTrue(AutoToolsPolicy.shouldRestore(true, true, 5, 5, 2));
        assertFalse(AutoToolsPolicy.shouldRestore(true, true, 4, 5, 2));
        assertFalse(AutoToolsPolicy.shouldRestore(false, true, 5, 5, 2));
        assertFalse(AutoToolsPolicy.shouldRestore(true, true, 5, 5, 5));
    }
}
