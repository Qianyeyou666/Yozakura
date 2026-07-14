package gq.yozakura.module.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class TargetEspNightBloomContractTest {
    @Test
    public void targetEspExposesAWorldSpaceNightBloomMode() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/module/render/TargetESP.java")), StandardCharsets.UTF_8);

        assertTrue(source.contains("NIGHT_BLOOM"));
        assertTrue(source.contains("drawNightBloom("));
        assertTrue(source.contains("ClickGUI.currentPalette()"));
        assertTrue("Night Bloom must not inherit the legacy shader's hard-coded tints",
                source.contains("current != EspMode.NIGHT_BLOOM"));
    }
}
