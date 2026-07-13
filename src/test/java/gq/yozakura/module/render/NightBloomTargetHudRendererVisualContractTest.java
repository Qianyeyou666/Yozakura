package gq.yozakura.module.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NightBloomTargetHudRendererVisualContractTest {
    private static final String SOURCE_PATH =
            "src/main/java/gq/yozakura/module/render/NightBloomTargetHudRenderer.java";

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
    public void targetMotionAvatarHealthTrailAndBoundsRemainIntact() throws IOException {
        String source = source();

        assertTrue(source.contains("static final float WIDTH = 190.0F"));
        assertTrue(source.contains("static final float HEIGHT = 48.0F"));
        assertTrue(source.contains("motion.getPanelYOffset()"));
        assertTrue(source.contains("motion.getPanelScale()"));
        assertTrue(source.contains("drawPortrait("));
        assertTrue(source.contains("motion.getDamageTrail()"));
    }

    private static String source() throws IOException {
        return new String(Files.readAllBytes(Paths.get(SOURCE_PATH)), StandardCharsets.UTF_8);
    }

    private static String between(String source, String start, String end) {
        int begin = source.indexOf(start);
        int finish = source.indexOf(end, begin + start.length());
        return source.substring(begin, finish);
    }

}
