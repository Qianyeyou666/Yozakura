package gq.yozakura.util.notification;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NotificationNightBloomVisualContractTest {
    private static final String SOURCE_PATH =
            "src/main/java/gq/yozakura/util/notification/Notification.java";

    @Test
    public void nightBloomUsesTheBorderlessDripLiteSurfaceAndTypography() throws IOException {
        String source = source();
        String nightBloom = between(source, "private void drawNightBloom(", "private void drawSakura(");

        assertTrue(source.contains("NIGHT_BLOOM_PANEL_FILL = 0xFF16161A"));
        assertTrue(source.contains("NIGHT_BLOOM_PRIMARY = 0xFFFF4FC7"));
        assertTrue(source.contains("NIGHT_BLOOM_SECONDARY = 0xFFEEEEEE"));
        assertTrue(source.contains("NIGHT_BLOOM_PANEL_RADIUS = 4.0F"));
        assertTrue(nightBloom.contains("RenderServices.shapes().rounded("));
        assertTrue("Night Bloom keeps the reference's circular information icon",
                nightBloom.contains("RenderServices.shapes().circle("));
        assertTrue(nightBloom.contains("getIcon()"));
        assertTrue(nightBloom.contains("NIGHT_BLOOM_PRIMARY"));
        assertTrue(nightBloom.contains("NIGHT_BLOOM_SECONDARY"));
        assertTrue(nightBloom.contains("Math.round(220.0F * alpha)"));
        assertFalse(nightBloom.contains("HUD.queueNightBloomGlow("));
        assertTrue(nightBloom.contains("HUD.drawNightBloomCenteredIcon("));
        assertTrue(nightBloom.contains("HUD.drawNightBloomText("));
        assertTrue(source.contains("float drawWidth = renderWidth()"));
        assertTrue(source.contains("float drawHeight = renderHeight()"));
        assertTrue(source.contains("NightBloomNotificationLayout.panelWidth("));
        assertTrue(source.contains("NightBloomNotificationLayout.panelHeight("));
        assertTrue(source.contains("return renderHeight()"));
        assertTrue(source.contains("private boolean useNightBloomRenderer()"));
        assertTrue(source.contains("!HUD.useVapeSimpleStyle()"));
        assertTrue(source.contains("if (useNightBloomRenderer())"));
        assertFalse("Night Bloom notification must not draw a border", nightBloom.contains("roundedBorder"));
    }

    @Test
    public void nightBloomDrawsOnlyTheBlackOuterShadow() throws IOException {
        String source = source();
        String nightBloom = between(source, "private void drawNightBloom(", "private void drawSakura(");

        assertFalse(source.contains("NIGHT_BLOOM_ACCENT_SHADOW"));
        assertTrue(source.contains("NIGHT_BLOOM_DEPTH_SHADOW_COLOR = 0x73000000"));
        assertTrue(source.contains("NIGHT_BLOOM_DEPTH_SHADOW_OFFSET_X = 0.0F"));
        assertTrue(source.contains("NIGHT_BLOOM_DEPTH_SHADOW_OFFSET_Y = 0.0F"));
        assertTrue(source.contains("NIGHT_BLOOM_DEPTH_SHADOW_BLUR_RADIUS = 9.0F"));
        assertTrue("Night Bloom outer depth must come from exactly one black shadow",
                occurrences(nightBloom, "shadowOffset") == 1);
        assertFalse("Night Bloom must not add an extra non-offset shadow",
                nightBloom.contains("shapes().shadow("));
        assertTrue(source.contains("HUD.isGlowEnabled() && isGlowFrameOpen()"));
        assertTrue(source.contains("GlowProfile.TEXT"));
        assertTrue(source.contains("GlowProfile.ACCENT"));
    }

    private static String source() throws IOException {
        return new String(Files.readAllBytes(Paths.get(SOURCE_PATH)), StandardCharsets.UTF_8);
    }

    private static String between(String source, String start, String end) {
        int begin = source.indexOf(start);
        int finish = source.indexOf(end, begin + start.length());
        assertTrue("missing Night Bloom notification renderer", begin >= 0);
        assertTrue("missing renderer boundary after Night Bloom notification", finish > begin);
        return source.substring(begin, finish);
    }

    private static int occurrences(String source, String token) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }
}
