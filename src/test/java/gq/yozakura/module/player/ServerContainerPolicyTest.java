package gq.yozakura.module.player;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ServerContainerPolicyTest {
    @Test
    public void rejectsCommonServerSelectorsAndShopsEvenWhenCustomChestsAreAllowed() {
        assertFalse(ServerContainerPolicy.canStealFrom("Game Menu", true));
        assertFalse(ServerContainerPolicy.canStealFrom("Bed Wars - Select a Mode", true));
        assertFalse(ServerContainerPolicy.canStealFrom("Shop & My Cosmetics", true));
        assertFalse(ServerContainerPolicy.canStealFrom("Lobby Selector", true));
        assertFalse(ServerContainerPolicy.canStealFrom("Kit Selector", true));
    }

    @Test
    public void standardChestIsAllowedByDefaultAndCustomLootNeedsOptIn() {
        assertTrue(ServerContainerPolicy.canStealFrom("Chest", false));
        assertTrue(ServerContainerPolicy.canStealFrom("Large Chest", false));
        assertFalse(ServerContainerPolicy.canStealFrom("Supply Crate", false));
        assertTrue(ServerContainerPolicy.canStealFrom("Supply Crate", true));
    }

    @Test
    public void inventoryManagerRequiresThePlayersOwnContainerContext() {
        assertTrue(ServerContainerPolicy.isPlayerInventoryContext(
                true, true, false, true, true, true));
        assertTrue(ServerContainerPolicy.isPlayerInventoryContext(
                true, true, false, false, false, true));
        assertFalse(ServerContainerPolicy.isPlayerInventoryContext(
                true, true, false, true, false, false));
        assertFalse(ServerContainerPolicy.isPlayerInventoryContext(
                true, true, false, true, false, true));
    }
}
