package gq.yozakura.module.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NightBloomTargetHudRendererVisualContractTest {
    private static final String SOURCE_PATH =
            "src/main/java/gq/yozakura/module/render/NightBloomTargetHudRenderer.java";
    private static final String TARGET_HUD_SOURCE =
            "src/main/java/gq/yozakura/module/render/TargetHUD.java";
    private static final String TARGET_HUD_STYLE_SOURCE =
            "src/main/java/gq/yozakura/module/render/TargetHudStyle.java";

    @Test
    public void targetHudUsesTheBorderlessDripLiteSurfaceAndTypography() throws IOException {
        String source = source();

        assertFalse(source.contains("roundedBorder"));
        assertFalse("Night Bloom must not use stroked highlight lines", source.contains("shapes().line("));
        assertTrue(source.contains("NightBloomHudLayout.SURFACE_COLOR"));
        assertTrue(source.contains("0.86F * alpha"));
        assertTrue(source.contains("NightBloomHudLayout.PRIMARY_COLOR"));
        assertTrue(source.contains("NightBloomHudLayout.SECONDARY_COLOR"));
        assertTrue(source.contains("NightBloomHudLayout.PANEL_RADIUS * uiScale"));
        assertFalse(source.contains("HUD.queueNightBloomGlow("));
        assertTrue(source.contains("HUD.drawNightBloomText("));
        assertTrue(source.contains("HUD.drawNightBloomCenteredIcon("));
    }

    @Test
    public void targetHudDrawsItsBlackOuterShadowThroughTheBatchedRenderer() throws IOException {
        String panel = between(source(), "private void drawPanel(", "private void drawContent(");

        assertFalse(panel.contains("shadowOffset("));
        assertTrue(panel.contains("HUD.drawNightBloomShadow("));
        assertFalse(panel.contains("ACCENT_SHADOW"));
        assertFalse(panel.contains("queueNightBloomGlow"));
    }

    @Test
    public void targetHudMergesAvatarAndInformationThroughTheLiquidSurfacePath() throws IOException {
        String source = source();
        assertTrue(source.contains("drawFusedPanel("));
        String fusion = between(source, "private void drawFusedPanel(", "private void drawContent(");
        assertTrue(fusion.contains("NightBloomWatermarkLiquid.bridge("));
        assertTrue(fusion.contains("NightBloomWatermarkLiquid.composites("));
        assertTrue(fusion.contains("RenderServices.shapes().joinedRounded("));
        assertTrue(fusion.contains("HUD.drawNightBloomShadow("));
        assertFalse("the fused TargetHUD background must not reintroduce a panel glow",
                fusion.contains("queueNightBloomGlow"));
    }

    @Test
    public void targetMotionAvatarHealthTrailAndBoundsRemainIntact() throws IOException {
        String source = source();

        assertTrue(source.contains("static final float WIDTH = 190.0F"));
        assertTrue(source.contains("static final float HEIGHT = 48.0F"));
        assertTrue(source.contains("motion.getPanelYOffset()"));
        assertTrue(source.contains("motion.getPanelScale()"));
        assertTrue(source.contains("drawPortrait("));
        assertTrue(source.contains("motion.getDamageTrail()"));
    }

    @Test
    public void targetHudKeepsDockedAndDetachingPanelsOnRegisteredBounds() throws IOException {
        String renderer = source();
        String draw = between(renderer, "void draw(", "private void drawDockedAvatarWell(");
        String targetHud = source(TARGET_HUD_SOURCE);
        String targetDraw = between(targetHud, "private void drawNightBloomHud(",
                "private NightBloomTargetHudRenderer.Content nightBloomContent(");

        assertTrue(draw.contains("hasDockingLink(\"target_hud\")"));
        assertTrue(draw.contains("float drawY = dockGeometryLocked || editMode ? y : y + motion.getPanelYOffset() * uiScale;"));
        assertTrue(draw.contains("float panelScale = dockGeometryLocked || editMode ? 1.0F : motion.getPanelScale();"));
        assertTrue(draw.contains("NightBloomHudDockRenderer.drawPanel(\"target_hud\", x, drawY, width, height"));
        assertTrue("A detaching link still locks the panel's render geometry", renderer.contains("snapshot.hasLink(id)"));
        assertFalse(draw.contains("NightBloomHudDockRenderer.isDocked(\"target_hud\")"));
        assertTrue(targetDraw.contains("HudDrag.updateDocked(\"target_hud\""));
        assertTrue(targetDraw.contains("HudDrag.drawDockHint(\"target_hud\", position[0], position[1], width, height"));
        assertTrue(targetDraw.contains("HudDrag.handleScroll(\"target_hud\", scale, position[0], position[1], width, height"));
    }

    @Test
    public void targetHudUsesOneEffectsFrameForTheForgeFallback() throws IOException {
        String fallback = between(source(TARGET_HUD_SOURCE), "public void onRender(RenderGameOverlayEvent.Text event)",
                "private void renderOverlay()");

        assertTrue(fallback.contains("RenderServices.beginHudEffectsFrame()"));
        assertTrue(fallback.contains("RenderServices.flushHudEffectsFrame()"));
        assertTrue(fallback.contains("finally"));
        assertTrue(fallback.contains("!RenderServices.shadows().isFrameOpen()"));
        assertTrue(fallback.contains("!RenderServices.glow().isFrameOpen()"));
    }

    @Test
    public void targetCompositeAlphaCompensatesForThePanelEnteringOrLeaving() {
        float panelAlpha = 0.5F;
        float compositeProgress = 0.5F;
        float baseOpacity = 0.86F;
        float individual = baseOpacity * panelAlpha * (1.0F - compositeProgress);
        float composite = baseOpacity * panelAlpha
                * NightBloomTargetHudRenderer.fusedCompositeSurfaceOpacity(panelAlpha, compositeProgress);
        float resolved = individual + (1.0F - individual) * composite;

        assertEquals(baseOpacity * panelAlpha, resolved, 0.0001F);
    }

    @Test
    public void targetHudAutoStyleInheritsThePrimaryNightBloomSelection() throws IOException {
        String targetHud = source(TARGET_HUD_SOURCE);
        String styles = source(TARGET_HUD_STYLE_SOURCE);

        assertTrue(styles.contains("AUTO"));
        assertTrue(targetHud.contains("TargetHudStyle.AUTO"));
        assertTrue(targetHud.contains("HUD.getActiveStyle() == HUD.HudStyle.NIGHT_BLOOM"));
        assertTrue(targetHud.contains("TargetHudStyle.NIGHT_BLOOM"));
        assertTrue(targetHud.contains("TargetHudStyle.LEGACY"));
    }

    private static String source() throws IOException {
        return source(SOURCE_PATH);
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static String between(String source, String start, String end) {
        int begin = source.indexOf(start);
        int finish = source.indexOf(end, begin + start.length());
        return source.substring(begin, finish);
    }

}
