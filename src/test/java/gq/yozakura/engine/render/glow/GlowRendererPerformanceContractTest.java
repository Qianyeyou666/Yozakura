package gq.yozakura.engine.render.glow;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class GlowRendererPerformanceContractTest {
    private static final String GLOW_RENDERER_PATH =
            "src/main/java/gq/yozakura/engine/render/glow/GlowRenderer.java";
    private static final String RENDER_SERVICES_PATH =
            "src/main/java/gq/yozakura/engine/render/ui/RenderServices.java";
    private static final String HUD_PATH =
            "src/main/java/gq/yozakura/module/render/HUD.java";

    @Test
    public void hudEffectCommandsReuseOneSnapshotUntilTheGlTransformChanges() throws IOException {
        String glowRenderer = source(GLOW_RENDERER_PATH);
        String renderServices = source(RENDER_SERVICES_PATH);
        String hud = source(HUD_PATH);

        assertTrue("a frame-local command snapshot must avoid GL matrix/viewport reads per glyph",
                glowRenderer.contains("private RenderSnapshot commandSnapshot;"));
        assertTrue("commands must use the cached snapshot while the render state is unchanged",
                glowRenderer.contains("return commandSnapshot;"));
        assertTrue("callers need an explicit transform invalidation boundary",
                glowRenderer.contains("public void markRenderStateChanged()"));
        assertTrue("the HUD effect service must invalidate both effect renderers together",
                renderServices.contains("public static void markHudEffectsStateChanged()"));
        assertTrue("scaled HUD blocks must refresh their command snapshot after push/scale",
                hud.contains("RenderServices.markHudEffectsStateChanged();"));
    }

    @Test
    public void shadowPipelineUsesTheLowCostDownsampleTarget() throws IOException {
        String renderServices = source(RENDER_SERVICES_PATH);

        assertTrue("HUD shadows must use the 0.4x target because the separable blur preserves logical radius after upsampling",
                renderServices.contains("renderer.setQuality(GlowProfile.Quality.LOW);"));
    }

    @Test
    public void nightBloomArrayListReusesWidthsAndQueuesOneGlowMaskPerLabel() throws IOException {
        String hud = source(HUD_PATH);
        String row = between(hud, "private void drawNightBloomModuleRow(",
                "private void drawNightBloomModuleShadow(");
        String gradient = between(hud, "private static void drawNightBloomArrayListGradientText(",
                "private void drawNightBloomPanel(");

        assertTrue("row rendering must reuse the width measured during the frame's sorting pass",
                row.contains("nightBloomCachedMetaWidth(entry, metaFont)"));
        assertTrue("one whole-label glow mask replaces one queued glow command per character",
                gradient.contains("queueNightBloomTextGlow(font, text"));
        assertTrue("the visible gradient must be rendered in one font-state batch instead of one draw call per character",
                gradient.contains("font.drawColoredString(text"));
        assertTrue("one-character strings create avoidable allocation and font-cache churn every frame",
                !gradient.contains("String.valueOf(text.charAt(index))"));
    }

    private static String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        if (startIndex < 0) {
            return "";
        }
        int endIndex = source.indexOf(end, startIndex + start.length());
        return source.substring(startIndex, endIndex < 0 ? source.length() : endIndex);
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
