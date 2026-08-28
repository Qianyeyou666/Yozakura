package gq.yozakura.module.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TargetHudRiseRefactorContractTest {
    private static final String TARGET_HUD =
            "src/main/java/gq/yozakura/module/render/TargetHUD.java";
    private static final String STYLE =
            "src/main/java/gq/yozakura/module/render/TargetHudStyle.java";
    private static final String RENDERER =
            "src/main/java/gq/yozakura/module/render/RiseTargetHudRenderer.java";

    @Test
    public void routesSelectableStylesAndKeepsAppleOnlyAsAConfigAlias() throws IOException {
        String targetHud = source(TARGET_HUD);
        String style = source(STYLE);

        assertFalse(style.contains("AUTO"));
        assertTrue(style.contains("APPLE"));
        assertTrue(style.contains("RISE"));
        assertTrue(style.contains("NIGHT_BLOOM"));
        assertFalse(style.contains("LEGACY"));
        assertTrue(targetHud.contains("TargetHudStyle.APPLE"));
        assertTrue(targetHud.contains("TargetHudStyle.RISE"));
        assertFalse(targetHud.contains("TargetHudStyle.LEGACY"));
        assertTrue(targetHud.contains("selected == TargetHudStyle.APPLE"));
        assertFalse(targetHud.contains("selectedStyle == TargetHudStyle.APPLE"));
        assertTrue(targetHud.contains("renderRiseOverlay(new ScaledResolution(mc), partialTicks)"));
        assertTrue(targetHud.contains("renderNightBloomOverlay()"));
        assertFalse(targetHud.contains("selectedStyle == TargetHudStyle.LEGACY ? render"));
    }

    @Test
    public void keepsTargetLifecycleInControllerAndVisualDetailsInRenderer() throws IOException {
        String targetHud = source(TARGET_HUD);
        String renderer = source(RENDERER);

        assertTrue(targetHud.contains("EntityLivingBase resolved = editMode ? mc.thePlayer : resolveTarget()"));
        assertTrue(targetHud.contains("riseRenderer.draw("));
        assertTrue(renderer.contains("drawHealth("));
        assertTrue(renderer.contains("drawFace("));
        assertTrue(renderer.contains("spawnHurtParticles("));
        assertFalse(targetHud.contains("private void drawRiseAvatar("));
        assertFalse(targetHud.contains("private void drawRiseContent("));
    }

    @Test
    public void passesHealthInRendererUnitsAndUsesGenericResolvedTargets() throws IOException {
        String targetHud = source(TARGET_HUD);
        String renderer = source(RENDERER);

        assertTrue(targetHud.contains("riseHealthAnimation.run(health * layout.healthBarWidth)"));
        assertTrue(targetHud.contains("resolveTarget()"));
        assertFalse(targetHud.contains("private void drawRiseAvatar("));
        assertTrue(targetHud.contains("EntityLivingBase attacked = asTarget(entity)"));
        assertTrue(renderer.contains("healthRemainingWidth * uiScale"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
