package gq.yozakura.module.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class HotBarVisualContractTest {
    @Test
    public void hotBarUsesRoundedSurfaceSmoothSelectionAndNativeItems() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/module/render/HotBar.java")), StandardCharsets.UTF_8);
        assertTrue(source.contains("Background Alpha"));
        assertTrue(source.contains("HUD.drawNightBloomShadow("));
        assertTrue(source.contains("Smooth Selection"));
        assertTrue(source.contains("Background Blur"));
        assertTrue(source.contains("RenderServices.panels().panel("));
        assertTrue(source.contains("Custom Selection Color"));
        assertTrue(source.contains("Selection Red"));
        assertTrue(source.contains("Selection Green"));
        assertTrue(source.contains("Selection Blue"));
        assertTrue(source.contains("ClickGUI.currentPalette().getAccentPrimary()"));
        assertTrue(source.contains("selectedX += (targetX - selectedX)"));
        assertTrue(source.contains("renderItemAndEffectIntoGUI"));
        assertTrue(source.contains("renderItemOverlayIntoGUI"));
        assertTrue(source.contains("gq.yozakura.bridge.forge.RenderGameOverlayEvent.Text"));
        assertTrue(source.contains("StandaloneGuiIngame.isLunarClient()"));
        assertTrue(source.contains("renderLunarFrame"));
        assertTrue(source.contains("mc.currentScreen instanceof GuiContainer"));
        assertTrue(source.contains("captureLunarBackground"));
        assertTrue(source.contains("restoreLunarBackground"));
        assertTrue(source.contains("glCopyTexSubImage2D"));
        assertTrue(source.contains("glDeleteTextures"));
        assertTrue(source.contains("onContainerPost"));
    }
}
