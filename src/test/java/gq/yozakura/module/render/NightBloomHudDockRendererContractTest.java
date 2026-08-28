package gq.yozakura.module.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Guards the shared background path that prevents fused HUD widgets from stacking dark seams. */
public class NightBloomHudDockRendererContractTest {
    @Test
    public void sharedDockRendererOwnsLiquidBridgesIslandsAndNormalBlackShadows() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/module/render/NightBloomHudDockRenderer.java")),
                StandardCharsets.UTF_8);

        assertTrue(source.contains("drawSharedSurfaces"));
        assertTrue(source.contains("snapshot.getBridges()"));
        assertTrue(source.contains("snapshot.getComposites()"));
        assertTrue(source.contains("HUD.drawNightBloomShadow"));
        assertTrue(source.contains("RenderServices.shapes().joinedRounded"));
        assertTrue(source.contains("surface.getIndividualOpacity()"));
        assertFalse("outer dock surfaces stay shadow-only, never panel glow", source.contains("GlowProfile.PANEL"));
        assertFalse("a dock seam must not be repaired with an outline", source.contains("roundedBorder"));
    }

    @Test
    public void compositeSurfaceKeepsTheBaseOpacityWhileIndividualPanelsFadeOut() {
        float base = 220.0F / 255.0F;
        float progress = 0.5F;
        float individual = base * (1.0F - progress);
        float composite = base * NightBloomHudDockRenderer.fusedCompositeSurfaceOpacity(base, progress);
        float resolved = individual + (1.0F - individual) * composite;

        assertEquals(base, resolved, 0.0001F);
    }

    @Test
    public void drawPanelWrapsShadowAndSurfaceAndRaisedBandInOneShapeBatch() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/module/render/NightBloomHudDockRenderer.java")),
                StandardCharsets.UTF_8);
        String drawPanel = between(source,
                "public static void drawPanel(String id, float x, float y, float width, float height, float radius,\n" +
                        "                                 float alpha, int fill, int raisedFill)",
                "public static void drawPanel(String id, float x, float y, float width, float height, float radius,\n" +
                        "                                 float alpha, int fill)");

        assertTrue("drawPanel must query isBatchActive before opening its own batch",
                drawPanel.contains("ShaderRenderer.isBatchActive()"));
        assertTrue("drawPanel must own its batch via ownsBatch guard",
                drawPanel.contains("boolean ownsBatch"));
        assertTrue("drawPanel must open a shape batch when no batch is active",
                drawPanel.contains("ShaderRenderer.beginShapeBatch();"));
        assertTrue("drawPanel must close the shape batch in a finally",
                drawPanel.contains("ShaderRenderer.endShapeBatch();"));
        assertTrue("endShapeBatch must be guarded by ownsBatch to prevent nesting",
                drawPanel.contains("if (ownsBatch)"));
        assertTrue("drawPanel must keep early-out guards before opening a batch",
                drawPanel.contains("width <= 0.0F || height <= 0.0F || alpha <= 0.0F"));
    }

    private static String between(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        if (start < 0) {
            return "";
        }
        int end = source.indexOf(endMarker, start + startMarker.length());
        if (end < 0) {
            return source.substring(start);
        }
        return source.substring(start, end);
    }
}
