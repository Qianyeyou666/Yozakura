package gq.yozakura.util.render;

import org.junit.Test;

import java.io.InputStream;
import java.util.Scanner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ScreenSpaceGlowShaderResourceContractTest {
    private static final String ROOT = "assets/minecraft/yozakura/shaders/";

    @Test
    public void isolatedWorldGlowShadersExistAndAreNotHudGlowResources() {
        assertShader(ROOT + "world_glow_outline.frag");
        assertShader(ROOT + "world_glow_blur.frag");
        assertShader(ROOT + "world_glow_composite.frag");
    }

    @Test
    public void compositeTakesBothPaletteLayersAndExcludesTheSolidMaskCore() {
        String shader = compact(read(ROOT + "world_glow_composite.frag"));

        assertTrue(shader.contains("uniformsampler2DmaskTexture;"));
        assertTrue(shader.contains("uniformsampler2DblurTexture;"));
        assertTrue(shader.contains("uniformvec4coreColor;"));
        assertTrue(shader.contains("uniformvec4outerColor;"));
        assertTrue(shader.contains("1.0-maskAlpha"));
        assertTrue("the premultiplied composite path must premultiply RGB by its final alpha",
                shader.contains("tint.rgb*finalAlpha"));
    }

    @Test
    public void shadersDoNotHardCodeTheCyanOrMagentaPaletteValues() {
        String composite = compact(read(ROOT + "world_glow_composite.frag"));

        assertFalse(composite.contains("0.447"));
        assertFalse(composite.contains("0.925"));
        assertFalse(composite.contains("0.557"));
    }

    private static void assertShader(String resource) {
        String shader = read(resource);
        assertFalse(resource + " must not be empty", shader.trim().isEmpty());
        assertTrue(resource + " must declare main", shader.contains("void main"));
    }

    private static String read(String resource) {
        InputStream stream = ScreenSpaceGlowShaderResourceContractTest.class.getClassLoader()
                .getResourceAsStream(resource);
        assertNotNull("missing resource " + resource, stream);
        try (Scanner scanner = new Scanner(stream, "UTF-8").useDelimiter("\\A")) {
            return scanner.hasNext() ? scanner.next() : "";
        }
    }

    private static String compact(String source) {
        return source.replaceAll("/\\*.*?\\*/", "").replaceAll("//.*?(\\R|$)", "")
                .replaceAll("\\s+", "");
    }
}
