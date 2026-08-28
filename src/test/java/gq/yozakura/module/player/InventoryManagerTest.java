package gq.yozakura.module.player;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InventoryManagerTest {
    @Test
    public void equalItemsKeepTheirAssignedHotbarSlot() {
        assertTrue(InventorySelection.isBetterCandidate(
                7.0F, 120, 36,
                7.0F, 120, 9,
                36));
        assertFalse(InventorySelection.isBetterCandidate(
                7.0F, 120, 9,
                7.0F, 120, 36,
                36));
    }

    @Test
    public void durabilityBreaksOtherwiseEqualItemScores() {
        assertTrue(InventorySelection.isBetterCandidate(
                7.0F, 121, 20,
                7.0F, 120, 36,
                36));
    }

    @Test
    public void armorActionOnlyUnequipsWhenInventoryCanAcceptTheCurrentPiece() {
        assertEquals(InventorySelection.ArmorAction.UNEQUIP_CURRENT,
                InventorySelection.chooseArmorAction(12, 5, true, true));
        assertEquals(InventorySelection.ArmorAction.NONE,
                InventorySelection.chooseArmorAction(12, 5, true, false));
        assertEquals(InventorySelection.ArmorAction.EQUIP_BEST,
                InventorySelection.chooseArmorAction(12, 5, false, false));
        assertEquals(InventorySelection.ArmorAction.NONE,
                InventorySelection.chooseArmorAction(5, 5, true, true));
    }

    @Test
    public void toolScoreUsesMaterialEfficiencyAndEfficiencyEnchantBeforeDurabilityTieBreak() {
        assertEquals(8.0F, InventorySelection.toolScore(8.0F, 0), 0.0F);
        assertEquals(13.0F, InventorySelection.toolScore(8.0F, 2), 0.0F);
        assertEquals(1.0F, InventorySelection.toolScore(1.0F, 5), 0.0F);
    }

    @Test
    public void cleanKeepsAllPlaceableBlocksInsteadOfOnlyTheLargestStack() {
        assertTrue(InventorySelection.shouldKeepBlock(true));
        assertFalse(InventorySelection.shouldKeepBlock(false));
    }
}
