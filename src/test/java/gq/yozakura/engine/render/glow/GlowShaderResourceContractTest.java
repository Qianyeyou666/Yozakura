package gq.yozakura.engine.render.glow;

import org.junit.Test;

import java.io.InputStream;
import java.util.Scanner;
import java.util.regex.Pattern;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class GlowShaderResourceContractTest {
    private static final String SHADER_ROOT = "assets/minecraft/yozakura/shaders/";
    private static final String MASK_SHADER = SHADER_ROOT + "glow_mask.frag";
    private static final String BLUR_SHADER = SHADER_ROOT + "glow_blur.frag";
    private static final String COMPOSITE_SHADER = SHADER_ROOT + "glow_composite.frag";
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT = Pattern.compile("//.*?(?:\\R|$)");
    private static final Pattern FIXED_OPAQUE_OUTPUT = Pattern.compile(
            "gl_FragColor=vec4\\([^;]*,1(?:\\.0+)?\\);");

    @Test
    public void maskShaderResourceExists() {
        assertShaderResource(MASK_SHADER);
    }

    @Test
    public void blurShaderResourceExists() {
        assertShaderResource(BLUR_SHADER);
    }

    @Test
    public void compositeShaderResourceExists() {
        assertShaderResource(COMPOSITE_SHADER);
    }

    @Test
    public void blurConvolvesRgbaWithoutForcingOpaqueAlpha() {
        String shader = compact(readResource(BLUR_SHADER));

        assertTrue("blur shader must sample a texture", shader.contains("texture2D("));
        assertTrue("blur shader must accumulate RGBA values", shader.contains("vec4"));
        assertFalse("blur shader must not discard sampled alpha through an rgb swizzle",
                shader.contains(").rgb"));
        assertFalse("blur shader must not force alpha to one",
                FIXED_OPAQUE_OUTPUT.matcher(shader).find());
    }

    @Test
    public void compositeSamplesMaskAndBlurAndSuppressesTheSourceCore() {
        String shader = compact(readResource(COMPOSITE_SHADER));

        assertTrue(shader.contains("uniformsampler2DmaskTexture;"));
        assertTrue(shader.contains("uniformsampler2DblurTexture;"));
        assertTrue(shader.contains("uniformfloatstrength;"));
        assertTrue(shader.contains("texture2D(maskTexture"));
        assertTrue(shader.contains("texture2D(blurTexture"));
        assertTrue("composite shader must suppress the sharp source core",
                shader.contains("1.0-mask.a"));
        assertFalse("composite shader must preserve the blurred alpha",
                FIXED_OPAQUE_OUTPUT.matcher(shader).find());
    }

    @Test
    public void shadowModeSeparatesOpaqueGeometryCoverageFromFinalBlackStrength() {
        String mask = compact(readResource(MASK_SHADER));
        String composite = compact(readResource(COMPOSITE_SHADER));

        assertTrue(mask.contains("uniformintshadowMode;"));
        assertTrue("shadow mask alpha must remain the full rounded geometry coverage",
                mask.contains("vec4(maskColor.a*sourceAlpha,0.0,0.0,sourceAlpha)"));
        assertTrue(composite.contains("uniformintshadowMode;"));
        assertTrue("shadow opacity must come from the separately blurred strength channel",
                composite.contains("blur.r"));
        assertTrue("the shadow must continue underneath antialiased edge coverage instead of exposing a bright ring",
                composite.contains("floatoutsideMask=1.0-mask.a;"));
        assertTrue("edge coverage must attenuate the blurred black shadow continuously",
                composite.contains("blur.r*outsideMask"));
        assertFalse("discarding every non-zero mask sample punches a visible one-pixel halo around translucent panels",
                composite.contains("mask.a>0.001"));
        assertTrue(composite.contains("vec4(0.0,0.0,0.0,shadowAlpha)"));
    }

    private static void assertShaderResource(String resource) {
        String shader = readResource(resource);
        assertFalse(resource + " must not be empty", shader.trim().isEmpty());
        assertTrue(resource + " must declare a fragment entry point",
                Pattern.compile("void\\s+main\\s*\\(").matcher(shader).find());
    }

    private static String readResource(String resource) {
        InputStream stream = GlowShaderResourceContractTest.class.getClassLoader()
                .getResourceAsStream(resource);
        assertNotNull("missing shader resource: " + resource, stream);

        try (Scanner scanner = new Scanner(stream, "UTF-8").useDelimiter("\\A")) {
            return scanner.hasNext() ? scanner.next() : "";
        }
    }

    private static String compact(String shader) {
        String withoutBlockComments = BLOCK_COMMENT.matcher(shader).replaceAll("");
        String withoutComments = LINE_COMMENT.matcher(withoutBlockComments).replaceAll("");
        return withoutComments.replaceAll("\\s+", "");
    }
}
