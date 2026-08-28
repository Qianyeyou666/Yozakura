package gq.yozakura.bridge.modern;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ModernInventoryPolicyTest {
    @Test
    public void bestSlotKeepsTheCurrentSlotOnEqualScore() {
        float[] scores = new float[]{5.0F, 8.0F, 8.0F};
        boolean[] usable = new boolean[]{true, true, true};

        assertEquals(2, ModernInventoryPolicy.bestHotbarSlot(2, scores, usable));
        assertEquals(1, ModernInventoryPolicy.bestHotbarSlot(0, scores, usable));
    }

    @Test
    public void autoToolsOnlyRestoresAStillOwnedSlot() {
        assertTrue(ModernInventoryPolicy.shouldRestoreSlot(true, true, 4, 4, 1));
        assertFalse(ModernInventoryPolicy.shouldRestoreSlot(false, true, 4, 4, 1));
        assertFalse(ModernInventoryPolicy.shouldRestoreSlot(true, true, 7, 4, 1));
        assertFalse(ModernInventoryPolicy.shouldRestoreSlot(true, false, 4, 4, 1));
    }

    @Test
    public void inventoryCandidateUsesScoreDurabilityThenPreferredSlot() {
        assertTrue(ModernInventoryPolicy.isBetterCandidate(
                8.0F, 100, 36, 8.0F, 100, 9, 36));
        assertTrue(ModernInventoryPolicy.isBetterCandidate(
                8.0F, 101, 20, 8.0F, 100, 36, 36));
        assertFalse(ModernInventoryPolicy.isBetterCandidate(
                7.0F, 500, 20, 8.0F, 1, 36, 36));
    }

    @Test
    public void chestDelayAndTransferRulesMatchLegacyBehavior() {
        assertEquals(60L, ModernInventoryPolicy.nextDelay(80L, 20L, 0.0D));
        assertEquals(100L, ModernInventoryPolicy.nextDelay(80L, 20L, 0.9999D));
        assertTrue(ModernInventoryPolicy.canTransfer(false, true));
        assertTrue(ModernInventoryPolicy.canTransfer(true, false));
        assertFalse(ModernInventoryPolicy.canTransfer(false, false));
    }

    @Test
    public void smartEquipmentRequiresAStrictUpgrade() {
        assertTrue(ModernInventoryPolicy.shouldTakeEquipment(8.0F, 7.0F, true));
        assertFalse(ModernInventoryPolicy.shouldTakeEquipment(7.0F, 7.0F, true));
        assertTrue(ModernInventoryPolicy.shouldTakeEquipment(2.0F, 9.0F, false));
    }
}
