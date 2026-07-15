package gq.yozakura.module.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

/** Ensures every requested Night Bloom HUD endpoint participates in the one docking graph. */
public class NightBloomHudDockingCoverageContractTest {
    @Test
    public void requestedHudWidgetsUseSharedDockingInsteadOfIndependentDragSessions() throws IOException {
        String hud = source("src/main/java/gq/yozakura/module/render/HUD.java");
        String health = source("src/main/java/gq/yozakura/module/render/Health.java");
        String keyboard = source("src/main/java/gq/yozakura/module/render/KeyboardDisplay.java");
        String target = source("src/main/java/gq/yozakura/module/render/TargetHUD.java");
        String notifications = source("src/main/java/gq/yozakura/manager/NotificationManager.java");

        assertTrue(hud.contains("updateDocked(\"hud_module_list\""));
        assertTrue(hud.contains("updateDocked(\"hud_potions\""));
        assertTrue(hud.contains("updateDocked(\"hud_inventory\""));
        assertTrue(hud.contains("registerDockedPassive(\"hud_watermark\""));
        assertTrue(hud.contains("float[] docked = HudDrag.registerDockedPassive(\"hud_watermark\""));
        assertTrue(hud.contains("nightBloomWatermarkLayout.translateAll(deltaX, deltaY)"));
        assertTrue(health.contains("updateDocked(\"health_display\""));
        assertTrue(keyboard.contains("updateDocked(\"keyboard_display\""));
        assertTrue(target.contains("updateDocked(\"target_hud\""));
        assertTrue(notifications.contains("updateDocked(\"hud_notifications\""));
    }

    @Test
    public void requestedDockedPanelsUseTheSharedBackgroundPass() throws IOException {
        String hud = source("src/main/java/gq/yozakura/module/render/HUD.java");
        String health = source("src/main/java/gq/yozakura/module/render/Health.java");
        String keyboard = source("src/main/java/gq/yozakura/module/render/KeyboardDisplay.java");
        String target = source("src/main/java/gq/yozakura/module/render/NightBloomTargetHudRenderer.java");
        String notifications = source("src/main/java/gq/yozakura/manager/NotificationManager.java");

        assertTrue(hud.contains("NightBloomHudDockRenderer.drawPanel(\"hud_potions\""));
        assertTrue(hud.contains("NightBloomHudDockRenderer.drawPanel(\"hud_inventory\""));
        assertTrue(health.contains("NightBloomHudDockRenderer.drawPanel(\"health_display\""));
        assertTrue(keyboard.contains("NightBloomHudDockRenderer.drawPanel(\"keyboard_display\""));
        assertTrue(target.contains("NightBloomHudDockRenderer.drawPanel(\"target_hud\""));
        assertTrue(notifications.contains("NightBloomHudDockRenderer.drawPanel(\"hud_notifications\""));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
