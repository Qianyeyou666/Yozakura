package gq.yozakura.module.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AppleTargetHudDesignContractTest {
    private static final String TARGET_HUD =
            "src/main/java/gq/yozakura/module/render/TargetHUD.java";
    private static final String STYLE =
            "src/main/java/gq/yozakura/module/render/TargetHudStyle.java";
    private static final String RENDERER =
            "src/main/java/gq/yozakura/module/render/AppleTargetHudRenderer.java";
    private static final String MOTION =
            "src/main/java/gq/yozakura/module/render/AppleTargetHudMotion.java";

    @Test
    public void appleStyleIsRetainedOnlyForLegacyConfigMigration() throws IOException {
        String targetHud = source(TARGET_HUD);
        String styles = source(STYLE);

        assertTrue(styles.contains("APPLE"));
        assertTrue(styles.contains("RISE"));
        assertTrue(styles.contains("NIGHT_BLOOM"));
        assertFalse(styles.contains("AUTO"));
        assertFalse(styles.contains("LEGACY"));
        assertTrue(targetHud.contains("SELECTABLE_STYLES, TargetHudStyle.NYMPHILILA"));
        assertTrue(targetHud.contains("selected == TargetHudStyle.APPLE"));
        assertFalse(targetHud.contains("selectedStyle == TargetHudStyle.APPLE"));
        assertFalse(targetHud.contains("HUD.getActiveStyle()"));
    }

    @Test
    public void appleRendererUsesBoldHierarchyAndGaussianBlurCard() throws IOException {
        String renderer = source(RENDERER);

        assertTrue(renderer.contains("static final float HEIGHT = 54.0F"));
        assertTrue(renderer.contains("static final float RADIUS = 9.0F"));
        assertTrue(renderer.contains("static final float AVATAR_SIZE = 34.0F"));
        assertTrue(renderer.contains("FontLoaders.tenacityBold("));
        assertTrue(renderer.contains("FontLoaders.jetBrainsMono("));
        assertTrue(renderer.contains("panelBlur.prepareBlur"));
        assertTrue(renderer.contains("panelBlur.prepareBlur(10.0F)"));
        assertTrue(renderer.contains("panelBlur.drawBlurredSurface"));
        assertTrue(renderer.contains("RenderServices.shapes().roundedGradient"));
        assertTrue(renderer.contains("SURFACE_COLOR"));
        assertFalse(renderer.contains("RenderServices.blur()"));
        assertFalse(renderer.contains("GaussianBlurRenderer"));
        assertFalse(renderer.contains("drawEquipmentRow("));
        assertFalse(renderer.contains("ItemStack"));
        assertFalse(renderer.contains("LiquidGlassSettings"));
        assertFalse(renderer.contains("RenderServices.liquidGlass"));
        assertFalse(renderer.contains("random.nextFloat"));
        assertFalse(renderer.contains("EASE_OUT_ELASTIC"));
    }

    @Test
    public void appleMotionUsesInterruptibleCriticallyDampedSprings() throws IOException {
        String motion = source(MOTION);

        assertTrue(motion.contains("MotionValue"));
        assertTrue(motion.contains("updateSpring("));
        assertTrue(motion.contains("ENTER_SETTLE_SECONDS = 0.18F"));
        assertTrue(motion.contains("EXIT_SETTLE_SECONDS = 0.14F"));
        assertTrue(motion.contains("HURT_SETTLE_SECONDS = 0.14F"));
        assertFalse(motion.contains("EASE_OUT_ELASTIC"));
        assertFalse(motion.contains("Random"));
    }

    @Test
    public void disabledAppleRendererIsNotReachableFromTheController() throws IOException {
        String targetHud = source(TARGET_HUD);

        assertTrue(targetHud.contains("renderRiseOverlay("));
        assertTrue(targetHud.contains("renderNightBloomOverlay()"));
        assertFalse(targetHud.contains("selectedStyle == TargetHudStyle.APPLE"));
    }

    @Test
    public void appleFeedbackIsLimitedToDamageTrailAndHurtTint() throws IOException {
        String renderer = source(RENDERER);

        assertTrue(renderer.contains("getDamageTrail()"));
        assertTrue(renderer.contains("DAMAGE_TRAIL_COLOR"));
        assertTrue(renderer.contains("getHurt()"));
        assertTrue(renderer.contains("HEALTH_LOW"));
        assertFalse(renderer.contains("spawnHurtParticles"));
        assertFalse(renderer.contains("PARTICLE_BURST_COUNT"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
