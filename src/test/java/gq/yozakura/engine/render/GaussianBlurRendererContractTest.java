package gq.yozakura.engine.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class GaussianBlurRendererContractTest {
    private static final String SOURCE =
            "src/main/java/gq/yozakura/engine/render/GaussianBlurRenderer.java";

    @Test
    public void blurUsesOwnSeparableGaussianShader() throws IOException {
        String source = source();

        assertTrue(source.contains("BLUR_FRAGMENT"));
        assertTrue(source.contains("#version 120"));
        assertTrue(source.contains("uniform sampler2D originalTexture"));
        assertTrue(source.contains("float gauss(float x, float sigma)"));
        assertTrue(source.contains("for(float i = -radius; i <= radius; i++)"));
        assertTrue(source.contains(".4 * exp(-.5 * x * x / (sigma * sigma)) / sigma"));
        assertTrue(source.contains("runBlurPass("));
        assertTrue(source.contains("horizontalFramebuffer"));
        assertTrue(source.contains("verticalFramebuffer"));
    }

    @Test
    public void compositeMasksBlurBehindRoundedCard() throws IOException {
        String source = source();

        assertTrue(source.contains("COMPOSITE_FRAGMENT"));
        assertTrue(source.contains("roundSDF("));
        assertTrue(source.contains("regionOrigin"));
        assertTrue(source.contains("regionSize"));
        assertTrue(source.contains("gl_FragCoord.xy - regionOrigin"));
    }

    @Test
    public void captureIsBoundedToThePanelRegion() throws IOException {
        String source = source();

        assertTrue(source.contains("glCopyTexSubImage2D"));
        assertTrue(source.contains("regionWidth"));
        assertTrue(source.contains("regionHeight"));
        assertTrue(source.contains("logicalPadding"));
    }

    private static String source() throws IOException {
        return new String(Files.readAllBytes(Paths.get(SOURCE)), StandardCharsets.UTF_8);
    }
}
