package gq.yozakura.module.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TargetHudRiseTenacityContractTest {
    private static final String TARGET_HUD =
            "src/main/java/gq/yozakura/module/render/TargetHUD.java";
    private static final String RENDERER =
            "src/main/java/gq/yozakura/module/render/RiseTargetHudRenderer.java";
    private static final String ANIMATION =
            "src/main/java/gq/yozakura/module/render/RiseTargetHudAnimation.java";
    private static final String FONT_LOADERS =
            "src/main/java/gq/yozakura/engine/font/FontLoaders.java";

    @Test
    public void portsRiseModernTargetLifecycleAndOpeningAnimation() throws IOException {
        String targetHud = source(TARGET_HUD);
        String animation = source(ANIMATION);

        assertTrue(targetHud.contains("rememberRiseAttackTarget(event.getTarget())"));
        assertTrue(targetHud.contains("EntityLivingBase attacked = asTarget(entity)"));
        assertTrue(targetHud.contains("now - riseTargetSeenAt >= RISE_TARGET_HOLD_MS"));
        assertTrue(targetHud.contains("editMode ? mc.thePlayer : resolveTarget()"));
        assertTrue(targetHud.contains("out ? RISE_EXIT_MS : RISE_ENTER_MS"));
        assertTrue(targetHud.contains("EASE_OUT_CUBIC"));
        assertFalse(targetHud.contains("EASE_IN_BACK"));
        assertFalse(targetHud.contains("EASE_OUT_ELASTIC"));
        assertTrue(animation.contains("case EASE_OUT_CUBIC"));
    }

    @Test
    public void keepsCompactInterAndBricolageHierarchyWithDynamicGeometry() throws IOException {
        String renderer = source(RENDERER);
        String fonts = source(FONT_LOADERS);

        assertTrue(fonts.contains("inter("));
        assertTrue(fonts.contains("bricolage("));
        assertTrue(renderer.contains("static final float EDGE_OFFSET = 7.0F"));
        assertTrue(renderer.contains("static final float FACE_SCALE = 30.0F"));
        assertTrue(renderer.contains("Math.max(nameWidth + 26.0F - healthTextWidth, MINIMUM_HEALTH_BAR_WIDTH)"));
        assertTrue(renderer.contains("float width = avatarWidth + Math.max(healthBarWidth, metadataWidth)"));
        assertFalse(renderer.contains("String label = \"Name\""));
    }

    @Test
    public void keepsGlassTintHealthFaceAndDamageOnlyParticles() throws IOException {
        String targetHud = source(TARGET_HUD);
        String renderer = source(RENDERER);

        assertTrue(targetHud.contains("RiseTargetHudBackground.GLASS"));
        assertTrue(targetHud.contains("RiseParticles"));
        assertTrue(targetHud.contains("EASE_OUT_QUINT"));
        assertTrue(targetHud.contains("setDuration(250L)"));
        assertTrue(renderer.contains("RenderServices.liquidGlass().rounded"));
        assertTrue(renderer.contains("darkTint(accent1, 128)"));
        assertTrue(renderer.contains("drawRoundedHead"));
        assertTrue(renderer.contains("Gui.drawScaledCustomSizeModalRect"));
        assertTrue(renderer.contains("hurtTime(target, partialTicks)"));
        assertTrue(renderer.contains("spawnHurtParticles"));
        assertTrue(renderer.contains("PARTICLE_LIFETIME_MS = 650L"));
        assertTrue(renderer.contains("0.96F + 0.04F * opening"));
    }

    @Test
    public void keepsCurrentRuntimeAndNightBloomCompatibility() throws IOException {
        String targetHud = source(TARGET_HUD);

        assertTrue(targetHud.contains("public void onRender(Render2DEvent event)"));
        assertTrue(targetHud.contains("public void onRender(RenderGameOverlayEvent.Text event)"));
        assertTrue(targetHud.contains("RenderServices.beginHudEffectsFrame()"));
        assertTrue(targetHud.contains("renderNightBloomOverlay()"));
        assertTrue(targetHud.contains("HudDrag.updateDocked(\"target_hud\""));
        assertTrue(targetHud.contains("HudDrag.update(\"target_hud\""));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
