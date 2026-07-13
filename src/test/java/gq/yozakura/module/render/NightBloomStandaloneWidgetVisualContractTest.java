package gq.yozakura.module.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
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

        assertEquals(1, occurrences(renderer, "shadowOffset("));
        assertTrue(renderer.contains("NightBloomHudLayout.DEPTH_SHADOW_COLOR"));
        assertTrue(renderer.contains("NightBloomHudLayout.DEPTH_SHADOW_OFFSET_X"));
        assertTrue(renderer.contains("NightBloomHudLayout.DEPTH_SHADOW_OFFSET_Y"));
        assertTrue(renderer.contains("NightBloomHudLayout.DEPTH_SHADOW_BLUR_RADIUS"));
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

        assertEquals(1, occurrences(renderer, "shadowOffset("));
        assertTrue(renderer.contains("NightBloomHudLayout.DEPTH_SHADOW_COLOR"));
        assertTrue(renderer.contains("NightBloomHudLayout.DEPTH_SHADOW_OFFSET_X"));
        assertTrue(renderer.contains("NightBloomHudLayout.DEPTH_SHADOW_OFFSET_Y"));
        assertTrue(renderer.contains("NightBloomHudLayout.DEPTH_SHADOW_BLUR_RADIUS"));
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

    private static int occurrences(String source, String needle) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
