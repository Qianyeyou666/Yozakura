package gq.yozakura.module.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class EspBodyGlowContractTest {
    @Test
    public void espHasSeparateBodyAndOutlineGlowModes() throws IOException {
        String esp = source("src/main/java/gq/yozakura/module/render/ESP.java");
        String renderer = source("src/main/java/gq/yozakura/util/render/ScreenSpaceGlowRenderer.java");
        String shader = source("src/main/resources/assets/minecraft/yozakura/shaders/world_glow_composite.frag");

        assertTrue(esp.contains("BODY_GLOW"));
        assertTrue(esp.contains("renderScreenSpaceGlow(event.partialTicks, currentMode == EspBoxMode.BODY_GLOW)"));
        assertTrue(renderer.contains("beginFrame(VisualPalette palette, float strength, boolean fillCore)"));
        assertTrue(shader.contains("fillCore"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
