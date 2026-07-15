package gq.yozakura.module.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EspHealthBarModeContractTest {
    @Test
    public void healthBarIsAvailableAndRenderedForEveryEspMode() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/render/ESP.java");

        assertTrue(source.contains("new Option<Boolean>(\"Health Bar\", \"HealthBar\", true)"));
        assertTrue(source.contains("new Option<Boolean>(\"Health Bar Glow\", \"HealthBarGlow\", false)"));
        assertFalse(source.contains("healthBar.visibleWhen"));
        assertTrue(source.contains("healthBarGlow.visibleWhen(() -> Boolean.TRUE.equals(healthBar.getValue()));"));
        assertTrue(source.contains("if (is2DMode() || Boolean.TRUE.equals(healthBar.getValue())) {"));
        assertTrue(source.contains("boolean shouldDrawHealthBar = Boolean.TRUE.equals(healthBar.getValue());"));
        assertTrue(source.contains("boolean shouldGlowHealthBar = shouldDrawHealthBar"));
        assertTrue(source.contains("if (shouldDrawHealthBar) {\n                    drawHealthBar(entry, glowRenderer);"));
        assertTrue(source.contains("glowRenderer.queueRoundedRect("));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
