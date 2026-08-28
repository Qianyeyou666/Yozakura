package gq.yozakura.module.render;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MinimalHudVisualContractTest {
    @Test
    public void minimalStyleUsesTheCrispMinecraftGlyphRenderer() throws Exception {
        String source = source("src/main/java/gq/yozakura/module/render/HUD.java");
        String text = between(source, "private void drawMinimalText(",
                "private void drawMinimalBackground(");
        String width = between(source, "private int minimalTextWidth(",
                "private void drawMinimalBackground(");

        assertTrue(source.contains("MINIMAL"));
        assertTrue(source.contains("drawMinimalWatermark("));
        assertTrue(source.contains("drawMinimalModuleList("));
        assertTrue(source.contains("drawMinimalPotionEffects("));
        assertTrue(source.contains("drawMinimalInventory("));
        assertTrue(source.contains("MinecraftFontLoaders.MINIMAL"));
        assertFalse(text.contains("mc.fontRendererObj.drawString("));
        assertFalse(width.contains("mc.fontRendererObj.getStringWidth("));
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
        assertTrue(list.contains("ModuleListAnchor.isRightSide("));
        assertTrue(list.contains("ModuleListAnchor.textX("));
        assertTrue(text.contains("MinecraftFontLoaders.MINIMAL.drawGlowString("));
        assertFalse(text.contains("queueVanillaText("));
        assertFalse(text.contains("FontLoaders.C16"));
        assertTrue(text.contains("MinimalHudLayout.TEXT_GLOW_STRENGTH"));
        assertTrue(text.contains("Math.round("));
        assertTrue(text.contains("MinecraftFontLoaders.MINIMAL.drawStringWithShadow("));
        assertFalse(text.contains("drawMinimalUnicodeCoverage("));
    }

    @Test
    public void crispRendererKeepsTheActualMinecraftBitmapGlyphSource() throws Exception {
        String renderer = source("src/main/java/gq/yozakura/engine/font/MinecraftFontRenderer.java");
        String loaders = source("src/main/java/gq/yozakura/engine/font/MinecraftFontLoaders.java");
        String glow = source("src/main/java/gq/yozakura/engine/render/glow/GlowRenderer.java");

        assertTrue(renderer.contains("textures/font/ascii.png"));
        assertTrue(renderer.contains("font/glyph_sizes.bin"));
        assertFalse(renderer.contains("java.awt.Font"));
        assertFalse(renderer.contains("FontLoaders.regular("));
        assertTrue(loaders.contains("new MinecraftFontRenderer("));
        assertTrue(glow.contains("queueMinecraftText("));
        assertTrue(glow.contains("MinecraftTextCommand"));
        assertTrue(glow.contains("drawStringForGlowMask"));
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
