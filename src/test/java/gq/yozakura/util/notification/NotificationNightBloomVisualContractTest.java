package gq.yozakura.util.notification;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
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
    public void nightBloomFusesOnlyEachNotificationsIconAndContentTiles() throws IOException {
        String source = source();
        String nightBloom = between(source, "private void drawNightBloom(", "private void drawSakura(");

        assertTrue(nightBloom.contains("NightBloomNotificationLayout.createLiquidPair("));
        assertTrue(nightBloom.contains("drawNightBloomFusedShadows("));
        assertTrue(nightBloom.contains("drawNightBloomFusedSurfaces("));
        assertTrue(source.contains("RenderServices.shapes().joinedRounded("));
        assertTrue("the icon and body receive their own local bridge geometry",
                source.contains("LiquidPair pair"));
        assertFalse("notifications must not be collected into a shared cross-notification surface",
                nightBloom.contains("List<Notification>"));
        assertTrue("a globally docked notification stack must yield its outer surface to the shared docking pass",
                nightBloom.contains("NightBloomHudDockRenderer.hasLink(\"hud_notifications\")"));
        assertTrue(nightBloom.contains("if (!stackDocked)"));
    }

    @Test
    public void nightBloomDrawsOnlyTheBlackOuterShadow() throws IOException {
        String source = source();
        String nightBloom = between(source, "private void drawNightBloom(", "private void drawSakura(");

        assertFalse(source.contains("NIGHT_BLOOM_ACCENT_SHADOW"));
        assertFalse("legacy immediate shadows disappear at runtime and must not render notifications",
                nightBloom.contains("shadowOffset("));
        assertFalse("Night Bloom must not add an extra non-offset shadow",
                nightBloom.contains("shapes().shadow("));
        assertTrue(nightBloom.contains("HUD.drawNightBloomShadow("));
        assertTrue(source.contains("HUD.isGlowEnabled() && isGlowFrameOpen()"));
        assertTrue(source.contains("GlowProfile.TEXT"));
        assertTrue(source.contains("GlowProfile.ACCENT"));
    }

    @Test
    public void compositeAlphaStaysContinuousWhileTheNotificationEntersOrLeaves() {
        float panelAlpha = 0.5F;
        float compositeProgress = 0.5F;
        float baseOpacity = 220.0F / 255.0F;
        float individual = baseOpacity * panelAlpha * (1.0F - compositeProgress);
        float composite = baseOpacity
                * NightBloomNotificationLayout.fusedCompositeSurfaceOpacity(baseOpacity, panelAlpha,
                        compositeProgress);
        float resolved = individual + (1.0F - individual) * composite;

        assertEquals(baseOpacity * panelAlpha, resolved, 0.0001F);
    }

    @Test
    public void notificationAutoThemeInheritsThePrimaryNightBloomSelection() throws IOException {
        String hud = source("src/main/java/gq/yozakura/module/render/HUD.java");

        assertTrue(hud.contains("AUTO,"));
        assertTrue(hud.contains("NotificationTheme.AUTO"));
        assertTrue(hud.contains("selected == NotificationTheme.NIGHT_BLOOM"));
        assertTrue(hud.contains("selected == NotificationTheme.AUTO"));
        assertTrue(hud.contains("getActiveStyle() == HudStyle.NIGHT_BLOOM"));
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
        assertTrue("missing Night Bloom notification renderer", begin >= 0);
        assertTrue("missing renderer boundary after Night Bloom notification", finish > begin);
        return source.substring(begin, finish);
    }

}
