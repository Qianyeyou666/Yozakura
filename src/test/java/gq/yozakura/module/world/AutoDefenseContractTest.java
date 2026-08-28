package gq.yozakura.module.world;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class AutoDefenseContractTest {
    @Test
    public void moduleRechecksReplaceabilityAndUsesVanillaPlacementController() throws Exception {
        String source = read("src/main/java/gq/yozakura/module/world/AutoDefense.java");

        assertTrue(source.contains("BlockUtil.isReplaceable(target)"));
        assertTrue(source.contains("intersectsBlockingEntity(target)"));
        assertTrue(source.contains("itemBlock.canPlaceBlockOnSide"));
        assertTrue(source.contains("MinecraftAccessor.syncCurrentPlayItem(mc.playerController)"));
        assertTrue(source.contains("mc.playerController.onPlayerRightClick"));
    }

    @Test
    public void blockSlotIsSelectedBeforeAnyPlacementSearch() throws Exception {
        String source = read("src/main/java/gq/yozakura/module/world/AutoDefense.java");

        int switchSlot = source.indexOf("switchSlot(slot);");
        int findTarget = source.indexOf("BlockPos target = findNextPlaceableTarget(selectedStack);");
        assertTrue(switchSlot >= 0);
        assertTrue(findTarget >= 0);
        assertTrue(switchSlot < findTarget);
        assertTrue(source.contains("mc.thePlayer.inventory.getStackInSlot(slot)"));
    }

    @Test
    public void placementAndHeldItemChangeKeepOriginalPreOrder() throws Exception {
        String source = read("src/main/java/gq/yozakura/module/world/AutoDefense.java");

        assertTrue(source.contains("event.trySetRotation"));
        assertTrue(source.contains("VisualRotationState.publish(\"AutoDefense\""));
        assertTrue(source.contains("event.requestOriginalPacketOrder()"));
        assertTrue(source.contains("event.getPacket() instanceof C09PacketHeldItemChange"));
        assertTrue(source.contains("event.getPacket() instanceof C08PacketPlayerBlockPlacement"));
        assertTrue(!source.contains("event.requestAfterCurrentRotation()"));
    }

    @Test
    public void optionalRoofIsAttemptedOnlyWhenVanillaPlacementIsLegalAndNeverBlocksCompletion() throws Exception {
        String source = read("src/main/java/gq/yozakura/module/world/AutoDefense.java");

        assertTrue(source.contains("AutoDefensePlan.optionalRoofOffset()"));
        assertTrue(source.contains("findOptionalRoofPlacement(selectedStack)"));
        assertTrue(source.contains("optionalRoofAttempted"));
        assertTrue(source.contains("setState(false, false)"));
    }

    @Test
    public void moduleIsRegistered() throws Exception {
        String source = read("src/main/java/gq/yozakura/manager/ModuleManager.java");
        assertTrue(source.contains("addModule(\"AutoDefense\""));
    }

    private static String read(String relativePath) throws Exception {
        return new String(Files.readAllBytes(Paths.get(relativePath)), StandardCharsets.UTF_8);
    }
}
