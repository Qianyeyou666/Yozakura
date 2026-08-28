package gq.yozakura.module.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MiningProgressTest {
    @Test
    public void formatsClampedRoundedPercentage() {
        assertEquals("0%", MiningProgressPresentation.formatPercent(-0.2F));
        assertEquals("29%", MiningProgressPresentation.formatPercent(0.286F));
        assertEquals("100%", MiningProgressPresentation.formatPercent(1.4F));
    }

    @Test
    public void interpolatesVisibilityWithoutOvershooting() {
        assertEquals(0.35F, MiningProgressPresentation.approach(0.0F, 1.0F, 0.35F), 0.0001F);
        assertEquals(1.0F, MiningProgressPresentation.approach(0.9F, 1.0F, 0.35F), 0.0001F);
        assertEquals(0.0F, MiningProgressPresentation.approach(0.1F, 0.0F, 0.35F), 0.0001F);
    }

    @Test
    public void placesTheLabelOnTheBlockFaceNearestTheViewer() {
        MiningProgressPresentation.Anchor east = MiningProgressPresentation.anchor(
                10, 20, 30, 15.0D, 20.5D, 30.5D, 0.02D);
        MiningProgressPresentation.Anchor top = MiningProgressPresentation.anchor(
                10, 20, 30, 10.5D, 25.0D, 30.5D, 0.02D);

        assertEquals(11.02D, east.getX(), 0.0001D);
        assertEquals(20.5D, east.getY(), 0.0001D);
        assertEquals(30.5D, east.getZ(), 0.0001D);
        assertEquals(10.5D, top.getX(), 0.0001D);
        assertEquals(21.02D, top.getY(), 0.0001D);
        assertEquals(30.5D, top.getZ(), 0.0001D);
    }

    @Test
    public void diagonalViewUsesATangentPlaneOutsideTheWholeBlock() {
        MiningProgressPresentation.Anchor anchor = MiningProgressPresentation.anchor(
                0, 0, 0, 5.5D, 0.5D, 5.5D, 0.02D);
        double unit = 1.0D / Math.sqrt(2.0D);
        double projectedDistance = (anchor.getX() - 0.5D) * unit
                + (anchor.getZ() - 0.5D) * unit;
        double blockSupport = 0.5D * (unit + unit);

        assertEquals(blockSupport + 0.02D, projectedDistance, 0.0001D);
        assertTrue(anchor.getX() > 1.0D);
        assertTrue(anchor.getZ() > 1.0D);
    }

    @Test
    public void worldScaleGrowsOnlyEnoughToStayReadableAtDistance() {
        float close = MiningProgressPresentation.worldScale(2.0D, 1.0F);
        float far = MiningProgressPresentation.worldScale(30.0D, 1.0F);

        assertEquals(0.018F, close, 0.0001F);
        assertTrue(far > close);
        assertTrue(far <= close * 1.55F);
    }

    @Test
    public void moduleUsesWorldRenderingAndControllerCurrentBlock() throws IOException {
        String module = source("src/main/java/gq/yozakura/module/render/MiningProgress.java");
        String accessor = source("src/main/java/gq/yozakura/bridge/MinecraftAccessor.java");

        assertTrue(module.contains("onRender3D(Render3DEvent event)"));
        assertTrue(module.contains("MinecraftAccessor.getCurrentBlock(mc.playerController)"));
        assertTrue(module.contains("GlStateManager.enableDepth()"));
        assertFalse(module.contains("Render2DEvent"));
        assertFalse(module.contains("RenderGameOverlayEvent"));
        assertFalse(module.contains("Gui.drawRect"));
        assertFalse(module.contains("panelColor"));
        assertTrue(accessor.contains("\"currentBlock\", \"field_178895_c\", \"c\""));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
