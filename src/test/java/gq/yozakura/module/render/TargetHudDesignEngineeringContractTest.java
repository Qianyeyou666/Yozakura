package gq.yozakura.module.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TargetHudDesignEngineeringContractTest {
    private static final String TARGET_HUD =
            "src/main/java/gq/yozakura/module/render/TargetHUD.java";
    private static final String STYLE =
            "src/main/java/gq/yozakura/module/render/TargetHudStyle.java";
    private static final String RENDERER =
            "src/main/java/gq/yozakura/module/render/RiseTargetHudRenderer.java";
    private static final String ANIMATION =
            "src/main/java/gq/yozakura/module/render/RiseTargetHudAnimation.java";

    @Test
    public void stylesAreExplicitWithoutAutoOrLegacyAliases() throws IOException {
        String targetHud = source(TARGET_HUD);
        String styles = source(STYLE);

        assertTrue(styles.contains("RISE"));
        assertTrue(styles.contains("NIGHT_BLOOM"));
        assertTrue(styles.contains("APPLE"));
        assertFalse(styles.contains("AUTO"));
        assertFalse(styles.contains("LEGACY"));
        assertTrue(targetHud.contains("SELECTABLE_STYLES, TargetHudStyle.NYMPHILILA"));
        assertTrue(targetHud.contains("selected == TargetHudStyle.APPLE"));
        assertFalse(targetHud.contains("selectedStyle == TargetHudStyle.APPLE"));
        assertFalse(targetHud.contains("HUD.getActiveStyle()"));
    }

    @Test
    public void frequentCombatHudUsesShortInterruptibleEaseOutMotion() throws IOException {
        String targetHud = source(TARGET_HUD);
        String animation = source(ANIMATION);

        assertTrue(targetHud.contains("RISE_ENTER_MS = 180L"));
        assertTrue(targetHud.contains("RISE_EXIT_MS = 140L"));
        assertTrue(targetHud.contains("EASE_OUT_CUBIC"));
        assertFalse(targetHud.contains("EASE_OUT_ELASTIC"));
        assertFalse(targetHud.contains("EASE_IN_BACK"));
        assertTrue(animation.contains("case EASE_OUT_CUBIC"));
    }

    @Test
    public void rendererUsesCompactHierarchyWithoutRedundantNameLabelOrHardBorder() throws IOException {
        String renderer = source(RENDERER);

        assertTrue(renderer.contains("static final float HEIGHT = 46.0F"));
        assertTrue(renderer.contains("static final float RADIUS = 9.0F"));
        assertTrue(renderer.contains("FontLoaders.bricolage"));
        assertTrue(renderer.contains("FontLoaders.inter"));
        assertTrue(renderer.contains("metadataText("));
        assertTrue(renderer.contains("healthText("));
        assertFalse(renderer.contains("String label = \"Name\""));
        assertFalse(renderer.contains("roundedBorder("));
        assertFalse(renderer.contains("EASE_OUT_ELASTIC"));
    }

    @Test
    public void openingNeverScalesFromNothingAndParticlesOnlyExplainDamage() throws IOException {
        String renderer = source(RENDERER);

        assertTrue(renderer.contains("0.96F + 0.04F * opening"));
        assertTrue(renderer.contains("hurt >= lastParticleHurtTime"));
        assertTrue(renderer.contains("PARTICLE_BURST_COUNT = 3"));
        assertFalse(renderer.contains("random.nextFloat() > 0.68F"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
