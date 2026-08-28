package gq.yozakura.module.player;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InventoryManagerContractTest {
    @Test
    public void usesUnifiedTickAndRejectsUnsafeInventoryContexts() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/player/InventoryManager.java");

        assertTrue(source.contains("import gq.yozakura.event.bridge.TickEvent;"));
        assertTrue(source.contains("event.getType() != EventType.PRE"));
        assertTrue(source.contains("mc.thePlayer.inventory.getItemStack() != null"));
        assertTrue(source.contains("mc.currentScreen instanceof GuiContainer"));
        assertTrue(source.contains("hasInventorySpace()"));
        assertTrue(source.contains("InventorySelection.toolScore"));
        assertTrue(source.contains("InventorySelection.shouldKeepBlock"));
        assertFalse(source.contains("net.minecraftforge"));
        assertFalse(source.contains("int[] slots = new int[4]"));
        assertFalse(source.contains("float[] scores = new float[4]"));
    }

    @Test
    public void instantModeCompletesAllInventoryActionsInOnePreTick() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/player/InventoryManager.java");

        assertTrue(source.contains("INSTANT"));
        assertTrue(source.contains("current == InventoryMode.INSTANT"));
        assertTrue(source.contains("runInstantActions()"));
        assertTrue(source.contains("MAX_INSTANT_ACTIONS"));
        assertTrue("Instant must require the real inventory screen",
                source.contains("mc.currentScreen instanceof GuiInventory"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
