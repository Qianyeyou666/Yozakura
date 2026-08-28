package gq.yozakura.module.player;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChestStealerTest {
    @Test
    public void stealingLoopUsesTheSharedTickBridgeInForgeAndStandalone() throws IOException {
        String source = source();

        assertTrue(source.contains("import gq.yozakura.event.bridge.TickEvent;"));
        assertTrue(source.contains("@EventTarget\n    public void onTick(TickEvent event)"));
        assertTrue(source.contains("event.getType() != EventType.PRE"));
        assertFalse(source.contains("net.minecraftforge.fml.common.gameevent.TickEvent"));
        assertFalse(source.contains("@SubscribeEvent\n    public void onTick("));
    }

    @Test
    public void anyRealChestContainerCanEnterTheStealingLoopWithoutInteractionAuthorization() throws IOException {
        String source = source();

        assertTrue(source.contains("mc.currentScreen instanceof GuiChest"));
        assertTrue(source.contains("chest.inventorySlots instanceof ContainerChest"));
        assertFalse(source.contains("chestAccess.authorizeWindow("));
        assertFalse(source.contains("ChestStealerPolicy.canUseChestWindow("));
    }

    @Test
    public void inventoryNameRemainsAnAdditionalOptionalCheck() throws IOException {
        String source = source();

        assertTrue(source.contains("new Option<Boolean>(\"Name Check\", \"NameCheck\", false)"));
        assertTrue(source.contains("if (!Boolean.TRUE.equals(nameCheck.getValue()))"));
        assertTrue(source.contains("ChestStealerPolicy.isStandardChestTitle(title)"));
    }

    @Test
    public void improvedStealerResetsPerWindowAndStopsWhenInventoryCannotAcceptItems() throws IOException {
        String source = source();

        assertTrue(source.contains("activeWindowId"));
        assertTrue(source.contains("resetWindowState("));
        assertTrue(source.contains("canTransfer(stack)"));
        assertTrue(source.contains("delayJitter"));
        assertTrue(source.contains("randomOrder"));
        assertFalse(source.contains("for (int slot = 0; slot < inventory.getSizeInventory(); slot++)"));
    }

    @Test
    public void instantModeStealsAWholeWindowOnlyOnce() throws IOException {
        String source = source();

        assertTrue(source.contains("ChestMode"));
        assertTrue(source.contains("ChestMode.INSTANT"));
        assertTrue(source.contains("instantExecutedWindowId"));
        assertTrue(source.contains("stealInstant("));
        assertTrue(source.contains("MAX_INSTANT_CLICKS"));
        assertTrue("Instant must keep the same smart transfer filter",
                source.contains("canTransfer(stack) && isValid(stack)"));
    }

    private static String source() throws IOException {
        return new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/module/player/ChestStealer.java")), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }
}
