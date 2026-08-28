package gq.yozakura.module.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class CoolTargetHudVisualContractTest {
    @Test
    public void coolModeKeepsNymphAvailableAndUsesItsOwnRenderer() throws IOException {
        String targetHud = source("src/main/java/gq/yozakura/module/render/TargetHUD.java");
        String styles = source("src/main/java/gq/yozakura/module/render/TargetHudStyle.java");

        assertTrue(styles.contains("COOL"));
        assertTrue(styles.contains("NYMPHILILA"));
        assertTrue(targetHud.contains("TargetHudStyle.COOL"));
        assertTrue(targetHud.contains("renderCoolOverlay(new ScaledResolution(mc))"));
        assertTrue(targetHud.contains("coolRenderer.draw("));
        assertTrue(targetHud.contains("coolCornerRadius"));
        assertTrue(targetHud.contains("coolHurtMotion.update(displayTarget, now)"));
        assertTrue(targetHud.contains("coolHurtMotion.reset()"));
    }

    @Test
    public void rendererOmitsEquipmentAndBuildsTheLargeGlowingHealthTreatment() throws IOException {
        String renderer = source("src/main/java/gq/yozakura/module/render/CoolTargetHudRenderer.java");

        assertTrue(renderer.contains("CoolTargetHudLayout.HEALTH_BAR_HEIGHT"));
        assertTrue(renderer.contains("queueHealthGlow"));
        assertTrue(renderer.contains("queueNameGlow"));
        assertTrue(renderer.contains("GlowProfile.ACCENT"));
        assertTrue(renderer.contains("GlowProfile.TEXT"));
        assertTrue(renderer.contains("NAME_COLOR"));
        assertTrue(renderer.contains("HEALTH_YELLOW"));
        assertTrue(renderer.contains("HEALTH_RED"));
        assertTrue(renderer.contains("Integer.toString(Math.max(0, Math.round(target.getHealth())))"));
        assertTrue(renderer.contains("y + 10.0F"));
        assertTrue(renderer.contains("float barY = y + 27.0F"));
        assertTrue(renderer.contains("FontLoaders.circularMedium(18)"));
        assertTrue(renderer.contains("FontLoaders.circularMedium(16)"));
        assertTrue(renderer.contains("barY + CoolTargetHudLayout.HEALTH_BAR_HEIGHT * 0.5F"));
        assertTrue(renderer.contains("+ 1.5F"));
        assertTrue(!renderer.contains("drawEquipment"));
        assertTrue(!renderer.contains("renderItemAndEffectIntoGUI"));
        assertTrue(renderer.contains("RenderServices.stencil().initWrite()"));
        assertTrue(renderer.contains("RenderServices.shapes().joinedRounded"));
        assertTrue(renderer.contains("RenderServices.stencil().read(1)"));
        assertTrue(renderer.contains("RenderServices.stencil().end()"));
        assertTrue(renderer.contains("cornerRadius"));
        assertTrue(renderer.contains("hurt.getIntensity()"));
        assertTrue(renderer.contains("1.0F - hurtIntensity * 0.65F"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
