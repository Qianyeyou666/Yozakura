package gq.yozakura.module.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class InventoryAnimationInteractionContractTest {
    @Test
    public void lunarRendererRestoresVanillaHoveredSlotAndTooltipContracts() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/module/render/LunarInventoryRenderer.java")), StandardCharsets.UTF_8);

        assertTrue(source.contains("setHoveredSlot(container, hoveredSlot)"));
        assertTrue(source.contains("slot.canBeHovered()"));
        assertTrue(source.contains("drawVanillaTooltip"));
        assertTrue(source.contains("mc.thePlayer.inventory.getItemStack() == null"));
        assertTrue(source.contains("itemRender = minecraft.getRenderItem()"));
    }

    @Test
    public void lunarAnimationUsesInverseTransformedPointerForRenderingAndInput() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/module/render/InventoryAnimation.java")), StandardCharsets.UTF_8);

        assertTrue(source.contains("InventoryAnimationPointer.toLogicalCoordinate(mouseX, centerX, scale)"));
        assertTrue(source.contains("InventoryAnimationPointer.toLogicalCoordinate(mouseY, centerY, scale)"));
        assertTrue(source.contains("LunarInventoryRenderer.draw(container, logicalMouseX, logicalMouseY"));
    }
}
