package gq.yozakura.module.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NymphArrayListEffectsContractTest {
    @Test
    public void nymphBlurUsesTheSourceKawasePipelineAndOneConnectedMask() throws IOException {
        String hud = read("src/main/java/gq/yozakura/module/render/HUD.java");
        String nymph = read("src/main/java/gq/yozakura/module/render/NymphArrayListRenderer.java");
        String renderer = read("src/main/java/gq/yozakura/module/render/NymphArrayListEffectsRenderer.java");

        assertTrue(hud.contains("NymphArrayListRenderer"));
        assertTrue(hud.contains("nymphRenderer.drawBackgrounds"));
        assertTrue(hud.contains("nymphGlow.getValue()"));
        assertTrue(hud.contains("nymphGlowSize.getValue()"));
        assertTrue(nymph.contains("drawUnionMask"));
        assertTrue(nymph.contains("effects.drawBlurredSurface"));
        assertTrue(nymph.contains("DoAFuckingBloomEvent mask pass"));
        assertTrue(nymph.contains("queueBloomMask"));
        assertTrue(nymph.contains("GlowProfile.SHADOW"));
        assertTrue(hud.contains("nymphTextGlowProfile"));
        assertTrue(hud.contains("queueVanillaText"));
        assertTrue(nymph.contains("RenderServices.shadows()"));
        assertTrue(nymph.contains("rendering background failed"));
        assertTrue(nymph.contains("GL11.glColorMask(true, true, true, true)"));
        assertTrue(nymph.contains("GL11.glDisable(GL11.GL_STENCIL_TEST)"));
        assertFalse(renderer.contains("OpenGlHelper.isFramebufferEnabled()"));
        assertTrue(nymph.contains("effects.prepareBlur(KAWASE_ITERATIONS, KAWASE_OFFSET)"));
        assertFalse(nymph.contains("backgrounds.getValue()"));
        assertFalse(nymph.contains("drawHudFrostedGlass"));
        assertTrue(renderer.contains("kawase_down.frag"));
        assertTrue(renderer.contains("kawase_up.frag"));
        assertTrue(renderer.contains("glCopyTexSubImage2D"));
        assertTrue(renderer.contains("sourceTexture"));
        assertTrue(renderer.contains("set2f(\"offset\""));
        assertTrue(renderer.contains("set2f(\"halfpixel\""));
        assertTrue(renderer.contains("for (int index = count; index > 0; index--)"));
        assertTrue(renderer.contains("dispose()"));
        assertTrue(renderer.contains("Nymph ArrayList Kawase blur unavailable"));

        assertResource("src/main/resources/assets/yozakura/ui/shaders/nymph/kawase_down.frag");
        assertResource("src/main/resources/assets/yozakura/ui/shaders/nymph/kawase_up.frag");
        assertResource("src/main/resources/assets/yozakura/ui/shaders/nymph/rounded_composite.frag");
    }

    private static void assertResource(String file) {
        Path path = Paths.get(file);
        assertTrue(file, Files.isRegularFile(path));
    }

    private static String read(String file) throws IOException {
        return new String(Files.readAllBytes(Paths.get(file)), StandardCharsets.UTF_8);
    }
}
