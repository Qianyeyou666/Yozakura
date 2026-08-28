package gq.yozakura.module.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class WorldMaterialStateContractTest {
    @Test
    public void bedEspRestoresOpaqueItemRenderState() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/render/BedESP.java");

        assertTrue(source.contains("GlStateManager.depthMask(true)"));
        assertTrue(source.contains("GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0)"));
        assertTrue(source.contains("GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F)"));
        assertTrue(source.contains("RenderHelper.disableStandardItemLighting()"));
    }

    @Test
    public void chamsDefaultsToOpaqueAndResynchronizesMinecraftState() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/render/Chams.java");

        assertTrue(source.contains("\"Alpha\", \"Alpha\", 255.0, 35.0, 255.0"));
        assertTrue(source.contains("restoreMinecraftRenderState()"));
        assertTrue(source.contains("GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL)"));
        assertTrue(source.contains("GlStateManager.depthMask(true)"));
        assertTrue(source.contains("GlStateManager.enableTexture2D()"));
        assertTrue(source.contains("GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F)"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
