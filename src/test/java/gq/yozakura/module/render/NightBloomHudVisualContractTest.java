package gq.yozakura.module.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Keeps the Night Bloom HUD contract separate from the legacy Sakura/Old renderers.
 * This is intentionally source-level: the renderer needs a Minecraft client to draw,
 * while the outline boundary is stable and easy to guard here.
 */
public class NightBloomHudVisualContractTest {
    private static final String HUD_SOURCE = "src/main/java/gq/yozakura/module/render/HUD.java";

    @Test
    public void nightBloomHudDoesNotUseOutlines() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(HUD_SOURCE)), StandardCharsets.UTF_8);
        String nightBloom = between(source, "private void drawNightBloomWatermark()", "private void drawVapeTextChip");
        nightBloom += between(source, "private void drawNightBloomPotionEffects", "private float animateNightBloomPotion");
        nightBloom += between(source, "private void drawNightBloomInventory", "private float animateNightBloomInventorySlot");

        assertFalse("Night Bloom must not draw an outline", nightBloom.contains("roundedBorder"));
    }

    @Test
    public void nightBloomHudUsesOnlyTheBlackOuterShadow() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(HUD_SOURCE)), StandardCharsets.UTF_8);
        String panel = between(source, "private void drawNightBloomPanel(", "private void drawVapeTextChip");

        assertEquals(1, occurrences(panel, "shadowOffset("));
        assertFalse(panel.contains("shapes().shadow("));
        assertFalse(panel.contains("ACCENT_SHADOW"));
        assertFalse(panel.contains("queueNightBloomGlow"));
        assertTrue(panel.contains("NightBloomHudLayout.DEPTH_SHADOW_OFFSET_Y"));
        assertTrue(panel.contains("withNightBloomAlpha(NIGHT_BLOOM_SURFACE, 0.86F * alpha)"));
    }

    @Test
    public void arrayListUsesIndependentRightAlignedRoundedRows() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(HUD_SOURCE)), StandardCharsets.UTF_8);
        String list = between(source, "private void drawNightBloomModuleList(",
                "private void drawNightBloomModuleRow(");
        String row = between(source, "private void drawNightBloomModuleRow(",
                "private void drawNightBloomModuleShadow(");

        assertEquals("only the empty editor preview may draw a list-level panel",
                1, occurrences(list, "drawNightBloomPanel("));
        assertTrue("module names need the reference's large, thick hierarchy",
                list.contains("CFontRenderer nameFont = FontLoaders.TB20"));
        assertTrue("metadata remains subordinate but readable beside the enlarged name",
                list.contains("CFontRenderer metaFont = FontLoaders.C16"));
        assertTrue(list.contains("drawNightBloomModuleRow("));
        assertTrue("Night Bloom rows retain per-module enter, exit, and reorder state",
                list.contains("updateNightBloomModuleRows("));
        assertTrue(list.contains("nightBloomModuleClock.tick(System.nanoTime())"));
        assertTrue(list.contains("long gradientTick = System.currentTimeMillis()"));
        assertFalse("the old one-way scalar cannot drive Night Bloom rows",
                list.contains("animateModule(entry.module, factor)"));
        assertTrue(list.contains("targetY += NightBloomHudLayout.MODULE_ROW_HEIGHT"
                + " + NightBloomHudLayout.MODULE_ROW_GAP"));
        assertTrue("the independent surface pass owns the row radius",
                list.contains("NIGHT_BLOOM_RADIUS"));
        assertTrue(row.contains("contentRight = rowRight - 3.0F"));
        assertTrue(row.contains("NIGHT_BLOOM_PRIMARY"));
        assertTrue(row.contains("NIGHT_BLOOM_SECONDARY"));
        assertTrue(row.contains("drawNightBloomArrayListGradientText("));
        assertTrue(source.contains("NightBloomArrayListGradient.colorAt("));
        assertFalse(source.contains("NIGHT_BLOOM_GRADIENT_END"));

        assertTrue("all row shadows must be completed before the touching backgrounds are filled",
                list.indexOf("drawNightBloomModuleShadows(") < list.indexOf("drawNightBloomModuleSurfaces("));
        assertTrue(list.contains("drawNightBloomModuleSurface("));
        assertTrue("stable adjacent rows need a small bridge instead of two anti-aliased corner gaps",
                list.contains("drawNightBloomModuleConnector("));
        assertFalse(list.contains("RenderServices.stencil()"));
        assertTrue(list.contains("withNightBloomAlpha(NIGHT_BLOOM_SURFACE, 0.68F)"));

        String shadow = between(source, "private void drawNightBloomModuleShadow(",
                "private static void drawNightBloomArrayListGradientText(");
        assertTrue("row shadows must share one framebuffer mask before Gaussian blur",
                list.contains("RenderServices.shadows().beginFrame()"));
        assertTrue(list.contains("RenderServices.shadows().flush()"));
        assertTrue(shadow.contains("RenderServices.shadows().queueRoundedRect("));
        assertTrue(shadow.contains("GlowProfile.SHADOW"));
        assertFalse("per-row immediate shadows create the dark horizontal seams",
                list.contains("RenderServices.shapes().shadowOffset("));

        String gradient = between(source, "private static void drawNightBloomArrayListGradientText(",
                "private void drawNightBloomPanel(");
        assertTrue("gradient glow must use the same moving color as its glyph",
                gradient.contains("int gradientGlow = withNightBloomAlpha(color"));
    }

    @Test
    public void nightBloomGlowBelongsToTextAndIconsOnly() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(HUD_SOURCE)), StandardCharsets.UTF_8);
        String helper = between(source, "public static void drawNightBloomText(",
                "public static boolean isHudFrostedGlassEnabled()");
        String renderer = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/engine/render/glow/GlowRenderer.java")), StandardCharsets.UTF_8);

        assertFalse(source.contains("queueNightBloomGlow("));
        assertTrue(helper.contains("isGlowEnabled()"));
        assertTrue(helper.contains("RenderServices.glow().isFrameOpen()"));
        assertTrue(helper.contains("font.drawStringWithGlow("));
        assertTrue(helper.contains("glowColor"));
        assertTrue(helper.contains("GlowProfile.TEXT"));
        assertTrue(helper.contains("GlowProfile.ACCENT"));
        assertTrue(helper.contains("NIGHT_BLOOM_GLOW_STRENGTH_BOOST"));
        assertTrue(source.contains("drawNightBloomCenteredIcon("));
        assertTrue(occurrences(source, "drawNightBloomText(") >= 8);
        assertTrue(renderer.contains("RenderSnapshot.capture()"));
        assertTrue(renderer.contains("float[] modelView = readMatrix(GL11.GL_MODELVIEW_MATRIX)"));
        assertTrue(renderer.contains("float[] projection = readMatrix(GL11.GL_PROJECTION_MATRIX)"));
        assertTrue(renderer.contains("command.snapshot.apply(targetWidth, targetHeight, displayWidth, displayHeight)"));
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
