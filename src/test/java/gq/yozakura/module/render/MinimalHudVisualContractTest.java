package gq.yozakura.module.render;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MinimalHudVisualContractTest {
    @Test
    public void minimalStyleUsesVanillaFontAndOptionalCompactBackgrounds() throws Exception {
        String source = source("src/main/java/gq/yozakura/module/render/HUD.java");

        assertTrue(source.contains("MINIMAL"));
        assertTrue(source.contains("drawMinimalWatermark("));
        assertTrue(source.contains("drawMinimalModuleList("));
        assertTrue(source.contains("drawMinimalPotionEffects("));
        assertTrue(source.contains("drawMinimalInventory("));
        assertTrue(source.contains("mc.fontRendererObj.drawString("));
        assertTrue(source.contains("Boolean.TRUE.equals(backgrounds.getValue())"));
    }

    @Test
    public void minimalArrayListHasNoGradientAndOnlyWeakGlyphGlow() throws Exception {
        String source = source("src/main/java/gq/yozakura/module/render/HUD.java");
        String list = between(source, "private void drawMinimalModuleList(",
                "private void drawMinimalPotionEffects(");
        String text = between(source, "private void drawMinimalText(",
                "private void drawMinimalBackground(");

        assertFalse(list.contains("Gradient"));
        assertFalse(list.contains("NightBloomArrayListGradient"));
        assertTrue(list.contains("MinimalHudLayout.TEXT_COLOR"));
        assertTrue(text.contains("queueVanillaText("));
        assertTrue(text.contains("FontLoaders.C16.drawGlowString("));
        assertTrue(text.contains("MinimalHudLayout.TEXT_GLOW_STRENGTH"));
        assertTrue(text.contains("Math.round("));
        assertTrue(text.contains("FontLoaders.C16.drawStringWithShadow("));
        assertFalse(text.contains("drawMinimalUnicodeCoverage("));
    }

    @Test
    public void glowRendererMasksTheActualMinecraftFontGlyphs() throws Exception {
        String source = source("src/main/java/gq/yozakura/engine/render/glow/GlowRenderer.java");

        assertTrue(source.contains("queueVanillaText("));
        assertTrue(source.contains("VanillaTextCommand"));
        assertTrue(source.contains("font.drawString(text.text"));
    }

    private static String source(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static String between(String source, String start, String end) {
        int begin = source.indexOf(start);
        int finish = source.indexOf(end, begin + start.length());
        return source.substring(begin, finish);
    }
}
