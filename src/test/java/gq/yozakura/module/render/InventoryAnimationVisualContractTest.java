package gq.yozakura.module.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class InventoryAnimationVisualContractTest {
    @Test
    public void inventoryAnimationUsesTargetHudMotionForOpenAndClose() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/module/render/InventoryAnimation.java")), StandardCharsets.UTF_8);
        assertTrue(source.contains("InventoryAnimation"));
        assertTrue(source.contains("GuiOpenEvent"));
        assertTrue(source.contains("GuiScreenEvent.DrawScreenEvent.Pre"));
        assertTrue(source.contains("GuiScreenEvent.DrawScreenEvent.Post"));
        assertTrue(source.contains("motion.run(1.0D)"));
        assertTrue(source.contains("motion.run(0.0D)"));
        assertTrue(source.contains("GlStateManager.scale(scale, scale, 1.0F)"));
        assertTrue(source.contains("prepareLunarScreen"));
        assertTrue(source.contains("renderLunarScreen"));
        assertTrue(source.contains("renderContainer"));
        assertTrue(source.contains("Start Scale"));
        assertTrue(source.contains("Fade"));
        String renderer = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/module/render/LunarInventoryRenderer.java")), StandardCharsets.UTF_8);
        assertTrue(renderer.contains("container instanceof GuiInventory"));
        assertTrue(renderer.contains("drawPlayerModel"));
        assertTrue(renderer.contains("drawSlots"));
        assertTrue(renderer.contains("itemVisibility(animationAlpha)"));
        assertTrue(renderer.contains("GlStateManager.scale(visibility, visibility, 1.0F)"));
    }
}
