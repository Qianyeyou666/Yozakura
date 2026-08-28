package gq.yozakura.module.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class ItemEspContractTest {
    @Test
    public void itemEspTracksDroppedItemsAndRegistersBothRenderPipelines() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/module/render/ItemESP.java")), StandardCharsets.UTF_8);
        assertTrue(source.contains("class ItemESP extends Module"));
        assertTrue(source.contains("ModuleType.Render"));
        assertTrue(source.contains("EntityItem"));
        assertTrue(source.contains("Render3DEvent"));
        assertTrue(source.contains("RenderWorldLastEvent"));
        assertTrue(source.contains("Items.iron_ingot"));
        assertTrue(source.contains("Items.gold_ingot"));
        assertTrue(source.contains("Items.diamond"));
        assertTrue(source.contains("Items.emerald"));
        assertTrue(source.contains("maxDistance"));
        assertTrue(source.contains("drawOutlinedBoundingBox"));
        assertTrue(source.contains("drawStringWithShadow"));
        assertTrue(source.contains("stackCounts"));
        assertTrue(source.contains("groupKey"));
    }

    @Test
    public void itemEspIsRegisteredAsAUsableModule() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/manager/ModuleManager.java")), StandardCharsets.UTF_8);
        assertTrue(source.contains("addModule(\"ItemESP\""));
    }
}
