package gq.yozakura.module.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class WorldVisualModeContractTest {
    @Test
    public void entityEspProvidesAnOptInWorldSpaceGlowMode() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/render/ESP.java");

        assertTrue(source.contains("GLOWESP"));
        assertTrue(source.contains("ScreenSpaceGlowRenderer.shared()"));
        assertTrue(source.contains("renderer.collect(entity);"));
        assertTrue(source.contains("renderer.renderMask(partialTicks);"));
        assertTrue(source.contains("renderer.composite();"));
        assertTrue(source.contains("VisualPalette.nightBloom()"));
    }

    @Test
    public void storageEspProvidesAnOptInWorldSpaceGlowMode() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/render/StorageESP.java");

        assertTrue(source.contains("StorageVisualMode"));
        assertTrue(source.contains("GLOWESP"));
        assertTrue(source.contains("ScreenSpaceGlowRenderer.shared()"));
        assertTrue(source.contains("renderer.collect(te.getPos());"));
        assertTrue(source.contains("renderer.renderMask(partialTicks);"));
        assertTrue(source.contains("renderer.composite();"));
        assertTrue(source.contains("VisualPalette.nightBloom()"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
