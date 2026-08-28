package gq.yozakura.module.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InventoryAnimationScopeContractTest {
    @Test
    public void animationOnlyOwnsThePlayerInventoryScreen() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/module/render/InventoryAnimation.java")), StandardCharsets.UTF_8);

        assertTrue(source.contains("import net.minecraft.client.gui.inventory.GuiInventory;"));
        assertTrue(source.contains("isPlayerInventory(currentScreen)"));
        assertTrue(source.contains("isPlayerInventory(event.gui)"));
        assertFalse(source.contains("event.gui instanceof GuiContainer"));
        assertFalse(source.contains("mc.currentScreen instanceof GuiContainer"));
    }
}
