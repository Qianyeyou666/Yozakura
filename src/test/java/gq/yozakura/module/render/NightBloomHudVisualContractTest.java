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
        nightBloom += between(source, "private void drawNightBloomPotionEffects", "private int nightBloomPotionAccent");
        nightBloom += between(source, "private void drawNightBloomInventory", "private float animateNightBloomInventorySlot");

        assertFalse("Night Bloom must not draw an outline", nightBloom.contains("roundedBorder"));
    }

    @Test
    public void nightBloomHudUsesIndependentDraggableWatermarkTilesAndBatchedLiquidShadows() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(HUD_SOURCE)), StandardCharsets.UTF_8);
        String watermark = between(source, "private void drawNightBloomWatermark()",
                "private NightBloomWatermarkLayout.Snapshot updateNightBloomWatermarkLayout(");
        String layout = between(source, "private NightBloomWatermarkLayout.Snapshot updateNightBloomWatermarkLayout(",
                "private List<NightBloomWatermarkLiquid.Bridge> getNightBloomWatermarkBridges(");
        String shadows = between(source, "private void drawNightBloomWatermarkShadows(",
                "private void drawNightBloomWatermarkSurfaces(");
        String surfaces = between(source, "private void drawNightBloomWatermarkSurfaces(",
                "private void drawNightBloomModuleList(");
        String panel = between(source, "private void drawNightBloomPanel(", "private void drawVapeTextChip");
        String shadow = between(source, "public static void drawNightBloomShadow(",
                "public static void drawNightBloomText(");
        String liquid = source("src/main/java/gq/yozakura/module/render/NightBloomWatermarkLiquid.java");

        assertTrue("the watermark must update its independent tile state rather than use one drag rectangle",
                watermark.contains("updateNightBloomWatermarkLayout("));
        assertFalse("the legacy whole-watermark drag path prevents tile-level hit testing",
                watermark.contains("HudDrag.update(\"hud_watermark\""));
        assertFalse("the watermark must not restore a connected parent background",
                watermark.contains("drawNightBloomPanel("));
        assertFalse("the old inset chip stack would visually reconnect the watermark",
                watermark.contains("drawNightBloomChip("));
        assertTrue(layout.contains("new NightBloomWatermarkLayout.Frame("));
        assertTrue(layout.contains("HudDrag.isEditMode()"));
        assertTrue(layout.contains("Mouse.isButtonDown(0)"));
        assertTrue(layout.contains("Mouse.isButtonDown(1)"));
        assertTrue(layout.contains("brandWidth * uiScale"));
        assertTrue("the liquid bridge must share the watermark shadow mask",
                shadows.contains("drawNightBloomShadow(") && shadows.contains("bridge.isVisible()"));
        assertTrue(surfaces.contains("RenderServices.shapes().joinedRounded("));
        assertTrue(surfaces.contains("withNightBloomAlpha(NIGHT_BLOOM_SURFACE, 0.68F * opacity)"));
        assertFalse(surfaces.contains("horizontalGradient("));
        assertTrue(liquid.contains("MIN_NECK_RATIO"));
        assertTrue(liquid.contains("Axis.HORIZONTAL"));
        assertTrue(liquid.contains("Axis.VERTICAL"));
        assertTrue("the brand uses the ArrayList scrolling text treatment",
                source.contains("drawNightBloomArrayListGradientText("));
        assertFalse("legacy immediate shadows disappear at runtime and must not render Night Bloom panels",
                panel.contains("shadowOffset("));
        assertFalse(panel.contains("shapes().shadow("));
        assertFalse(panel.contains("ACCENT_SHADOW"));
        assertFalse(panel.contains("queueNightBloomGlow"));
        assertTrue(panel.contains("drawNightBloomShadow("));
        assertTrue(panel.indexOf("drawNightBloomShadow(") < panel.indexOf("RenderServices.shapes().rounded("));
        assertTrue(shadow.contains("RenderServices.shadows()"));
        assertTrue(shadow.contains("shadows.beginFrame()"));
        assertTrue(shadow.contains("shadows.queueRoundedRect("));
        assertTrue(shadow.contains("shadows.flush()"));
        assertTrue(panel.contains("withNightBloomAlpha(NIGHT_BLOOM_SURFACE, 0.86F * alpha)"));
    }

    @Test
    public void watermarkFusionUsesExactFourSideJoinsAndPromotesVerticalGroupsToOneIsland() throws IOException {
        String source = source(HUD_SOURCE);
        String watermark = between(source, "private void drawNightBloomWatermark()",
                "private NightBloomWatermarkLayout.Snapshot updateNightBloomWatermarkLayout(");
        String surfaces = between(source, "private void drawNightBloomWatermarkSurfaces(",
                "private void drawNightBloomWatermarkBrand(");
        String liquid = source("src/main/java/gq/yozakura/module/render/NightBloomWatermarkLiquid.java");

        assertTrue(watermark.contains("NightBloomWatermarkLiquid.composites("));
        assertTrue(watermark.contains("drawNightBloomWatermarkSurfaces(snapshot, bridges, composites, uiScale)"));
        assertTrue(surfaces.contains("NightBloomWatermarkLiquid.Surface"));
        assertTrue(surfaces.contains("surface.getLeftJoinStart(), surface.getLeftJoinEnd()"));
        assertTrue(surfaces.contains("surface.getRightJoinStart(), surface.getRightJoinEnd()"));
        assertFalse("a bridge cannot use the outside-feather rounded path beside translucent tiles",
                surfaces.contains("RenderServices.shapes().rounded(bridge"));
        assertTrue(liquid.contains("static List<Composite> composites("));
        assertTrue(liquid.contains("ISLAND_EXPANSION_START"));
        assertTrue(liquid.contains("EDGE_EXPANSION_START"));
        assertTrue("an aligned horizontal chain must also finish as one seamless surface and shadow mask",
                liquid.contains("alignedHorizontalCompositeProgress"));
    }

    @Test
    public void watermarkUsesTheSakuraFlowerAndOneSharedOpticalAlignment() throws IOException {
        String source = source(HUD_SOURCE);
        String brand = between(source, "private void drawNightBloomWatermarkBrand(",
                "private void drawNightBloomWatermarkMetadata(");
        String metadata = between(source, "private void drawNightBloomWatermarkMetadata(",
                "private void persistNightBloomWatermarkPositions(");
        String logo = between(source, "private void drawNightBloomSakuraWatermarkLogo(",
                "private void drawSakuraFlower(");

        assertTrue(brand.contains("drawNightBloomSakuraWatermarkLogo("));
        assertFalse(brand.contains("FontLoaders.ICON_SPARK"));
        assertTrue(brand.contains("NightBloomHudLayout.watermarkBrandIconCenterY("));
        assertTrue(brand.contains("NightBloomHudLayout.watermarkBrandTextY("));
        assertTrue(metadata.contains("NightBloomHudLayout.watermarkMetadataTextY("));
        assertTrue(logo.contains("SAKURA_PETAL_POINTS"));
        assertFalse("the Night Bloom Sakura icon stays fill-only", logo.contains("GL_LINE_STRIP"));
    }

    @Test
    public void overlayLifecycleBatchesAllNightBloomShadowsBeforeTextGlow() throws IOException {
        String services = source("src/main/java/gq/yozakura/engine/render/ui/RenderServices.java");
        String forge = source("src/main/java/gq/yozakura/bridge/YozakuraEventBridge.java");
        String standalone = source("src/main/java/gq/yozakura/bridge/StandaloneGuiIngame.java");

        assertTrue(services.contains("beginHudEffectsFrame()"));
        assertTrue(services.contains("SHADOWS.beginFrame()"));
        assertTrue(services.contains("GLOW.beginFrame()"));
        assertTrue(services.contains("flushHudEffectsFrame()"));
        assertTrue(services.indexOf("SHADOWS.flush()") < services.indexOf("GLOW.flush()"));
        assertTrue(forge.contains("RenderServices.beginHudEffectsFrame()"));
        assertTrue(forge.contains("RenderServices.flushHudEffectsFrame()"));
        assertTrue(standalone.contains("RenderServices.beginHudEffectsFrame()"));
        assertTrue(standalone.contains("RenderServices.flushHudEffectsFrame()"));
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
        assertFalse("independent rows must never be joined by a connector rectangle",
                list.contains("drawNightBloomModuleConnector("));
        assertFalse("the connector helper itself must be removed so it cannot draw a horizontal seam",
                source.contains("private void drawNightBloomModuleConnector("));
        assertFalse(list.contains("RenderServices.stencil()"));
        assertTrue(list.contains("withNightBloomAlpha(NIGHT_BLOOM_SURFACE, 0.68F)"));

        String shadow = between(source, "private void drawNightBloomModuleShadow(",
                "private static void drawNightBloomArrayListGradientText(");
        assertTrue("row shadows must join the HUD-wide framebuffer mask before Gaussian blur",
                shadow.contains("drawNightBloomShadow("));
        assertFalse("per-row immediate shadows create the dark horizontal seams",
                list.contains("RenderServices.shapes().shadowOffset("));

        String gradient = between(source, "private static void drawNightBloomArrayListGradientText(",
                "private void drawNightBloomPanel(");
        assertTrue("gradient glow must use the same moving color as its glyph",
                gradient.contains("int gradientGlow = withNightBloomAlpha(color"));
    }

    @Test
    public void everyArrayListEntryIncludingTheLastUsesTheSameRoundedSurfacePath() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(HUD_SOURCE)), StandardCharsets.UTF_8);
        String surfaces = between(source, "private void drawNightBloomModuleSurfaces(",
                "private void drawNightBloomModuleSurface(");
        String surface = between(source, "private void drawNightBloomModuleSurface(",
                "private void drawNightBloomModuleRow(");

        assertTrue("the surface loop must include rows.size(), including the final module",
                surfaces.contains("index < rows.size()"));
        assertEquals("every row is rendered through the one independent rounded-surface call site",
                1, occurrences(surfaces, "drawNightBloomModuleSurface("));
        assertTrue("every row, including the last, keeps the same total background height",
                surfaces.contains("float bottom = NightBloomHudLayout.moduleRowBottom("));
        assertFalse("surface fusion rectangles caused the visible connecting line",
                surfaces.contains("RenderServices.shapes().rect("));
        assertTrue("touching rows must decide their shared right corners from actual animated positions",
                surfaces.contains("NightBloomHudLayout.moduleRowsTouch("));
        assertTrue("the row surface needs the no-outside-feather path so translucent edges are never blended twice",
                surface.contains("RenderServices.shapes().joinedRounded("));
        assertTrue("the joined shader must receive the exact top and bottom overlap intervals",
                surface.contains("topJoinStart, topJoinEnd, bottomJoinStart, bottomJoinEnd"));
        assertTrue("an animated overhanging right edge must keep its own rounded corner",
                surface.contains("NightBloomHudLayout.moduleJoinReachesRight("));
        assertTrue("touching rows must share the exact same mathematical Y edge",
                surfaces.contains("NightBloomHudLayout.moduleRowBottom("));
        assertFalse("the regular rounded shader feathers outside the row and creates the dark two-pixel seam",
                surface.contains("RenderServices.shapes().rounded("));
    }

    @Test
    public void arrayListSortsByItsFinalRenderedRowWidth() throws IOException {
        String source = source(HUD_SOURCE);
        String list = between(source, "private void drawNightBloomModuleList(",
                "private List<NightBloomModuleRenderEntry> updateNightBloomModuleRows(");
        String sorter = between(source, "private List<ModuleListEntry> getSortedNightBloomModuleListEntries(",
                "private List<ModuleListEntry> getSortedModuleListEntries(");

        assertTrue(list.contains("getSortedNightBloomModuleListEntries(modules, nameFont, metaFont)"));
        assertTrue(sorter.contains("NightBloomHudLayout.moduleRowWidth("));
        assertTrue(sorter.contains("metaFont.getStringWidth(entry.sideText)"));
        assertTrue(sorter.contains("nightBloomModuleSortScratch.addAll(getSortedModuleListEntries("));
        assertTrue(sorter.contains("NightBloomHudLayout.compareModuleRowsByRenderedWidth("));
        assertFalse("NightBloom sorting must never reorder the shared legacy cache",
                sorter.contains("moduleEntryCache.sort"));
        assertFalse("the legacy all-TB20 label width is not the NightBloom rendered width",
                sorter.contains("entry.labelWidth"));
    }

    @Test
    public void arrayListShadowMaskFusesTheContinuousRightEdgeWithoutDrawingAVisibleConnector() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(HUD_SOURCE)), StandardCharsets.UTF_8);
        String shadows = between(source, "private void drawNightBloomModuleShadows(",
                "private void drawNightBloomModuleSurfaces(");

        assertTrue("a mask-only spine must close the rounded notches between touching right-aligned rows",
                shadows.contains("drawNightBloomModuleShadowSpine("));
        assertFalse("fusion belongs to the shadow mask and must never add a visible surface rectangle",
                shadows.contains("RenderServices.shapes().rect("));
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

    @Test
    public void potionRowsRetainTheirExitAndPanelHeightUntilTheFadeCompletes() throws IOException {
        String source = source(HUD_SOURCE);
        String potion = between(source, "private void drawNightBloomPotionEffects",
                "private int nightBloomPotionAccent");

        assertTrue(potion.contains("NightBloomPotionMotion.Snapshot"));
        assertTrue(potion.contains("nightBloomPotionClock.tick(System.nanoTime())"));
        assertTrue(potion.contains("NightBloomHudLayout.potionHeight(nightBloomPotionMotion.getLayoutRows())"));
        assertTrue(potion.contains("row.getY() * NightBloomHudLayout.POTION_ROW_HEIGHT"));
        assertTrue(potion.contains("row.getVisibility()"));
        assertTrue(potion.contains("clearNightBloomPotionMotions()"));
        assertFalse(potion.contains("roundedBorder"));
        assertFalse(potion.contains("queueNightBloomGlow"));
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

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
