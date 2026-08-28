package gq.yozakura.module.world;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BedNukerProgressHudContractTest {
    private static final String BED_NUKER =
            "src/main/java/gq/yozakura/module/world/BedNuker.java";

    @Test
    public void keepsOneContinuousTaskProgressAcrossProtectionAndBedStages() throws IOException {
        String source = source();

        assertTrue(source.contains("bedTaskHasProtection"));
        assertTrue(source.contains("bedTaskProgressBase"));
        assertTrue(source.contains("phase * 0.42F"));
        assertTrue(source.contains("start + phase * (1.0F - start)"));
        assertTrue(source.contains("this.bedTaskProgressBase = 1.0F"));
        assertTrue(source.contains("BedNukerTargetPolicy.completionThreshold"));
        assertTrue(source.contains("private float getBedBreakHudProgress()"));
        assertFalse(source.contains("this.targetIsBed && this.breaking"));
        assertFalse(source.contains("MinecraftAccessor.getCurrentBlockDamage"));
    }

    @Test
    public void usesMonotonicRetainedAnimationAndCompletesExitAfterDisable() throws IOException {
        String source = source();

        assertTrue(source.contains("bedProgressClock.tick(System.nanoTime())"));
        assertTrue(source.contains("bedProgressVisibility.updateSpring"));
        assertTrue(source.contains("bedProgressFill.updateSpring"));
        assertTrue(source.contains("BedProgressExitRenderer"));
        assertTrue(source.contains("EventManager.register(this.bedProgressExitRenderer)"));
        assertTrue(source.contains("EventManager.unregister(this.bedProgressExitRenderer)"));
        assertTrue(source.contains("this.bedProgressVisibility.setTarget(0.0F)"));
        assertFalse(source.contains("bedProgressVisibility.snapTo(0.0F);\n        resetBreaking()"));
    }

    @Test
    public void drawsCompactRiseTenacityStyleProgressBar() throws IOException {
        String source = source();

        assertTrue(source.contains("Breaking bed"));
        assertTrue(source.contains("BED_PROGRESS_WIDTH"));
        assertTrue(source.contains("RenderUtil.drawRoundedBorderedRect"));
        assertTrue(source.contains("RenderUtil.drawRoundedRect"));
        assertTrue(source.contains("Math.round(this.displayedBedProgress * 100.0F)"));
        assertTrue(source.contains("FontLoaders.productSans(14)"));
        assertTrue(source.contains("GlStateManager.depthMask(true)"));
        assertFalse(source.contains("drawSoftShadowOffset"));
        assertFalse(source.contains("renderItemAndEffectIntoGUI"));
    }

    private static String source() throws IOException {
        return new String(Files.readAllBytes(Paths.get(BED_NUKER)), StandardCharsets.UTF_8);
    }
}
