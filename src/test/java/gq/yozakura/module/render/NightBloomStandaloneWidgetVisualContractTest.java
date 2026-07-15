package gq.yozakura.module.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NightBloomStandaloneWidgetVisualContractTest {
    private static final String HEALTH_SOURCE = "src/main/java/gq/yozakura/module/render/Health.java";
    private static final String KEYBOARD_SOURCE = "src/main/java/gq/yozakura/module/render/KeyboardDisplay.java";

    @Test
    public void nightBloomHealthUsesOneBlackShadowAndGlyphGlow() throws IOException {
        String source = source(HEALTH_SOURCE);
        String renderer = between(source, "private void drawNightBloomHealth(", "private void drawVapeHealth(");

        assertTrue(source.contains("NIGHT_BLOOM_RADIUS = 4.0F"));
        assertTrue(source.contains("NIGHT_BLOOM_SURFACE = 0xDC16161A"));
        assertTrue(source.contains("NIGHT_BLOOM_PRIMARY = 0xFFFF4FC7"));
        assertTrue(renderer.contains("RenderServices.shapes().rounded("));
        assertTrue(renderer.contains("NIGHT_BLOOM_SURFACE"));
        assertTrue(renderer.contains("NIGHT_BLOOM_PRIMARY"));
        assertFalse(renderer.contains("HUD.queueNightBloomGlow("));
        assertTrue(renderer.contains("HUD.drawNightBloomText("));
        assertTrue(renderer.contains("HUD.drawNightBloomCenteredIcon("));

        assertFalse(renderer.contains("shadowOffset("));
        assertTrue("the shared dock renderer owns the panel shadow and joined surface",
                renderer.contains("NightBloomHudDockRenderer.drawPanel("));
        assertFalse("Health must not stack a second shadow over its fused surface",
                renderer.contains("HUD.drawNightBloomShadow("));
        assertFalse(renderer.contains("ACCENT_SHADOW"));

        assertFalse(renderer.contains("roundedBorder"));
        assertFalse(renderer.contains(".shadow("));
    }

    @Test
    public void nightBloomKeysUseOneBlackShadowAndTextGlow() throws IOException {
        String source = source(KEYBOARD_SOURCE);
        String renderer = between(source, "private void drawNightBloomKey(", "private void drawVapeKey(");

        assertTrue(source.contains("NIGHT_BLOOM_RADIUS = 4.0F"));
        assertTrue(source.contains("NIGHT_BLOOM_SURFACE = 0xDC16161A"));
        assertTrue(source.contains("NIGHT_BLOOM_PRIMARY = 0xFFFF4FC7"));
        assertTrue(renderer.contains("RenderServices.shapes().rounded("));
        assertTrue(renderer.contains("multiplyAlpha(NIGHT_BLOOM_SURFACE, opacity)"));
        assertTrue(renderer.contains("NIGHT_BLOOM_PRIMARY"));
        assertFalse(renderer.contains("HUD.queueNightBloomGlow("));
        assertTrue(renderer.contains("HUD.drawNightBloomText("));

        assertFalse(renderer.contains("shadowOffset("));
        assertTrue(renderer.contains("HUD.drawNightBloomShadow("));
        assertTrue(source.contains("NightBloomHudDockRenderer.hasLink(\"keyboard_display\")"));
        assertTrue(renderer.contains("if (!NightBloomHudDockRenderer.hasLink(\"keyboard_display\"))"));
        assertFalse(renderer.contains("ACCENT_SHADOW"));

        assertFalse(renderer.contains("roundedBorder"));
        assertFalse(renderer.contains(".shadow("));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        if (startIndex < 0) {
            return "";
        }
        int endIndex = source.indexOf(end, startIndex + start.length());
        return source.substring(startIndex, endIndex < 0 ? source.length() : endIndex);
    }

}
