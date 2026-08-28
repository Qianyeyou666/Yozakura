package gq.yozakura.util.notification;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ModernNotificationVisualContractTest {
    private static final String SOURCE_PATH =
            "src/main/java/gq/yozakura/util/notification/Notification.java";

    @Test
    public void defaultRendererUsesOneQuietCardWithSemanticAccentHierarchy() throws IOException {
        String source = source();
        String modern = between(source, "private void drawModern(", "private void drawVape(");

        assertTrue(source.contains("this.modernLayout = ModernNotificationLayout.create("));
        assertTrue(modern.contains("ModernNotificationLayout.Layout layout = modernLayout"));
        assertFalse("the render hot path must reuse its immutable layout",
                modern.contains("ModernNotificationLayout.create("));
        assertTrue(modern.contains("drawModernPanelBackground("));
        assertTrue(modern.contains("RenderServices.shapes().rounded("));
        assertTrue(modern.contains("drawCenteredIconWithOptionalGlow("));
        assertTrue(modern.contains("FontLoaders.C16"));
        assertTrue(modern.contains("FontLoaders.C12"));
        assertTrue(modern.contains("RenderServices.shapes().progressBar("));
        assertFalse("the modern card must not restore the noisy top gradient strip",
                modern.contains("horizontalGradient("));
        assertFalse("the entire card must not receive an accent-colored glow",
                modern.contains("queueRoundedRect("));
        assertFalse("the icon well must not stack a second immediate shadow",
                modern.contains("shapes().shadow("));
    }

    @Test
    public void notificationLifecycleUsesOneMonotonicClock() throws IOException {
        String source = source();

        assertTrue(source.contains("private static long monotonicMillis()"));
        assertTrue(source.contains("System.nanoTime() / 1000000L"));
        assertTrue(source.contains("this.createdAt = monotonicMillis()"));
        assertTrue(source.contains("long now = monotonicMillis()"));
        assertTrue(source.contains("now - createdAt >= stayTime"));
        assertFalse(source.contains("System.currentTimeMillis()"));
        assertFalse(source.contains("TimerUtil"));
    }

    @Test
    public void alternateSakuraAndNightBloomRenderersRemainIndependent() throws IOException {
        String source = source();
        String draw = between(source, "public void draw(", "private void drawModern(");

        assertTrue(draw.contains("drawVape("));
        assertTrue(draw.contains("drawSakura("));
        assertTrue(draw.contains("drawNightBloom("));
        assertTrue(draw.contains("drawModern("));
        assertTrue(source.contains("private boolean useNightBloomRenderer()"));
        assertTrue(source.contains("NightBloomNotificationLayout.createLiquidPair("));
    }

    private static String source() throws IOException {
        return new String(Files.readAllBytes(Paths.get(SOURCE_PATH)), StandardCharsets.UTF_8);
    }

    private static String between(String source, String start, String end) {
        int begin = source.indexOf(start);
        int finish = source.indexOf(end, begin + start.length());
        assertTrue("missing modern notification renderer", begin >= 0);
        assertTrue("missing renderer boundary after modern notification", finish > begin);
        return source.substring(begin, finish);
    }
}
