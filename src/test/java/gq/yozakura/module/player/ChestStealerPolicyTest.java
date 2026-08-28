package gq.yozakura.module.player;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChestStealerPolicyTest {
    @Test
    public void randomStartStillVisitsEverySlotExactlyOnce() {
        assertEquals(4, ChestStealerPolicy.slotAt(0, 4, 6));
        assertEquals(5, ChestStealerPolicy.slotAt(1, 4, 6));
        assertEquals(0, ChestStealerPolicy.slotAt(2, 4, 6));
        assertEquals(3, ChestStealerPolicy.slotAt(5, 4, 6));
    }

    @Test
    public void jitteredDelayStaysInsideConfiguredBounds() {
        assertEquals(80L, ChestStealerPolicy.nextDelay(80L, 0L, 0.9D));
        assertEquals(60L, ChestStealerPolicy.nextDelay(80L, 20L, 0.0D));
        assertEquals(100L, ChestStealerPolicy.nextDelay(80L, 20L, 0.9999D));
        assertEquals(0L, ChestStealerPolicy.nextDelay(0L, 50L, 0.0D));
    }

    @Test
    public void recognizesStandardSkyWarsChestTitlesWithoutAuthorizingServerMenus() {
        assertTrue(ChestStealerPolicy.isStandardChestTitle("Chest"));
        assertTrue(ChestStealerPolicy.isStandardChestTitle("Large Chest"));
        assertTrue(ChestStealerPolicy.isStandardChestTitle("container.chest"));
        assertTrue(ChestStealerPolicy.isStandardChestTitle("箱子"));
        assertTrue(ChestStealerPolicy.isStandardChestTitle("大型箱子"));
        assertFalse(ChestStealerPolicy.isStandardChestTitle("战利品箱"));
        assertFalse(ChestStealerPolicy.isStandardChestTitle("SkyWars Item Shop"));
        assertFalse(ChestStealerPolicy.isStandardChestTitle("Play SkyWars"));
        assertFalse(ChestStealerPolicy.isStandardChestTitle(null));
    }

    @Test
    public void fullInventoryCanStillAcceptACompatiblePartialStack() {
        assertTrue(ChestStealerPolicy.canTransfer(false, true));
        assertTrue(ChestStealerPolicy.canTransfer(true, false));
        assertFalse(ChestStealerPolicy.canTransfer(false, false));
    }

    @Test
    public void smartEquipmentOnlyTakesStrictUpgrades() {
        assertTrue(ChestStealerPolicy.shouldTakeEquipment(8.0F, 7.0F, true));
        assertFalse(ChestStealerPolicy.shouldTakeEquipment(7.0F, 7.0F, true));
        assertTrue(ChestStealerPolicy.shouldTakeEquipment(2.0F, 9.0F, false));
    }

    @Test
    public void smartPotionFilterRequiresUsefulEffectsWithoutHarmfulEffects() {
        assertTrue(ChestStealerPolicy.shouldTakePotion(true, false));
        assertFalse(ChestStealerPolicy.shouldTakePotion(false, false));
        assertFalse(ChestStealerPolicy.shouldTakePotion(true, true));
        assertFalse(ChestStealerPolicy.shouldTakePotion(false, true));
    }
}
