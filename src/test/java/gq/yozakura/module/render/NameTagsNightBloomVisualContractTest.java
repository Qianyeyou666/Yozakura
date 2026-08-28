package gq.yozakura.module.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NameTagsNightBloomVisualContractTest {
    private static final String SOURCE = "src/main/java/gq/yozakura/module/render/NameTags.java";
    private static final String MANAGER = "src/main/java/gq/yozakura/manager/ModuleManager.java";

    @Test
    public void nameTagsUseTheBorderlessNightBloomSurfaceAndGlyphGlow() throws IOException {
        String source = source(SOURCE);

        assertTrue(source.contains("0xDC16161A"));
        assertTrue(source.contains("Background Alpha"));
        assertTrue(source.contains("backgroundAlpha.getValue()"));
        assertTrue(source.contains("HUD.drawNightBloomShadow("));
        assertTrue(source.contains("RenderServices.shapes().rounded("));
        assertTrue(source.contains("HUD.drawNightBloomText("));
        assertTrue(source.contains("ClickGUI.currentPalette()"));
        assertTrue(source.contains("NightBloomHudLayout.PRIMARY_COLOR"));
        assertTrue(source.contains("NightBloomHudLayout.SECONDARY_COLOR"));
        assertFalse(source.contains("roundedBorder"));
        assertFalse(source.contains("GlowProfile.PANEL"));
    }

    @Test
    public void nameTagsBatchEffectsAndSupportBothRuntimeBridges() throws IOException {
        String source = source(SOURCE);

        assertTrue(source.contains("onRender3D(Render3DEvent event)"));
        assertTrue(source.contains("@SubscribeEvent"));
        assertTrue(source.contains("net.minecraftforge.client.event.RenderLivingEvent.Specials.Pre"));
        assertTrue(source.contains("@EventTarget"));
        assertTrue(source.contains("gq.yozakura.bridge.forge.RenderLivingEvent.Specials.Pre"));
        assertTrue(source.contains("RenderServices.shadows().beginFrame()"));
        assertTrue(source.contains("RenderServices.glow().beginFrame()"));
        assertTrue(source.contains("finally"));
    }

    @Test
    public void moduleIsRegisteredAndEspDoesNotDrawASecondName() throws IOException {
        assertTrue(source(MANAGER).contains(
                "addModule(\"NameTags\", new ModuleFactory() { public Module create() { return new NameTags(); } });"));
        assertTrue(source("src/main/java/gq/yozakura/module/render/ESP.java")
                .contains("isStandaloneNameTagsEnabled()"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
