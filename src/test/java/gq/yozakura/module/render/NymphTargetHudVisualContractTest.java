package gq.yozakura.module.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class NymphTargetHudVisualContractTest {
    @Test
    public void targetHudExposesTheNymphililaStrifeMode() throws IOException {
        String targetHud = source("src/main/java/gq/yozakura/module/render/TargetHUD.java");
        String styles = source("src/main/java/gq/yozakura/module/render/TargetHudStyle.java");

        assertTrue(styles.contains("NYMPHILILA"));
        assertTrue(targetHud.contains("renderNymphOverlay(new ScaledResolution(mc))"));
        assertTrue(targetHud.contains("nymphBackgroundAlpha.visibleWhen"));
        assertTrue(targetHud.contains("nymphMotion.setVisible(true, now)"));
        assertTrue(targetHud.contains("nymphMotion.setVisible(false, now)"));
        assertTrue(targetHud.contains("nymphRenderer.draw("));
    }

    @Test
    public void rendererKeepsSourceFontsPortraitEquipmentAndHealthSemantics() throws IOException {
        String renderer = source("src/main/java/gq/yozakura/module/render/NymphTargetHudRenderer.java");

        assertTrue(renderer.contains("FontLoaders.productSans(15)"));
        assertTrue(renderer.contains("FontLoaders.productSans(18)"));
        assertTrue(renderer.contains("drawScaledCustomSizeModalRect"));
        assertTrue(renderer.contains("target.getCurrentArmor(3 - sourceSlot)"));
        assertTrue(renderer.contains("target.getHeldItem()"));
        assertTrue(renderer.contains("renderItemAndEffectIntoGUI"));
        assertTrue(renderer.contains("GlStateManager.enableTexture2D()"));
        assertTrue(renderer.contains("GlStateManager.enableAlpha()"));
        assertTrue(renderer.contains("GlStateManager.enableDepth()"));
        assertTrue(renderer.contains("GlStateManager.depthMask(true)"));
        assertTrue(renderer.contains("GlStateManager.enableRescaleNormal()"));
        assertTrue(renderer.contains("GlStateManager.disableRescaleNormal()"));
        assertTrue(renderer.contains("NymphTargetHudLayout.healthColor(actualRatio)"));
        assertTrue(renderer.contains("NymphTargetHudLayout.healthBarWidth"));
    }

    @Test
    public void rendererReplaysTheSourceBloomEventMaskAtTheAnimatedPanelBounds() throws IOException {
        String renderer = source("src/main/java/gq/yozakura/module/render/NymphTargetHudRenderer.java");

        assertTrue(renderer.contains("DoAFuckingBloomEvent mask pass"));
        assertTrue(renderer.contains("queueBloomMask"));
        assertTrue(renderer.contains("RenderServices.shadows()"));
        assertTrue(renderer.contains("GlowProfile.SHADOW"));
        assertTrue(renderer.contains("motion.getScale() * scale"));
        assertTrue(renderer.contains("motion.getOpacity()"));
    }

    @Test
    public void motionPortsTheSourceCurvesWithoutFrameCountTiming() throws IOException {
        String motion = source("src/main/java/gq/yozakura/module/render/NymphTargetHudMotion.java");

        assertTrue(motion.contains("DURATION_MILLIS = 500L"));
        assertTrue(motion.contains("power(1.0F - progress, 6)"));
        assertTrue(motion.contains("power(1.0F - progress, 8)"));
        assertTrue(motion.contains("delta * SOURCE_FRAMES_PER_SECOND"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
