package gq.yozakura.util.render;

import gq.yozakura.engine.render.ui.VisualPalette;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ScreenSpaceGlowColorsTest {
    @Test
    public void nightBloomPaletteDrivesCyanCoreAndMagentaOuterGlow() {
        VisualPalette palette = VisualPalette.nightBloom();
        ScreenSpaceGlowColors colors = ScreenSpaceGlowColors.from(palette);

        assertEquals(palette.getAccentAlt(), colors.getCoreColor());
        assertEquals(palette.getGlowPrimary(), colors.getOuterColor());
    }

    @Test
    public void colorsCannotBeConstructedFromRawArgbValues() {
        for (Constructor<?> constructor : ScreenSpaceGlowColors.class.getDeclaredConstructors()) {
            Class<?>[] parameters = constructor.getParameterTypes();
            assertFalse("palette colors must not expose a raw int constructor",
                    parameters.length == 2 && parameters[0] == int.class && parameters[1] == int.class);
        }
    }

    @Test
    public void rendererPublicApiDoesNotAcceptArgbColors() throws IOException {
        String source = rendererSource();

        assertFalse(source.contains("public void beginFrame(int"));
        assertFalse(source.contains("public void collect(int"));
        assertFalse(source.contains("public void setColor(int"));
    }

    @Test
    public void modulesCanReuseOneWorldGlowFboOwner() throws IOException {
        String source = rendererSource();

        assertTrue(source.contains("private static final ScreenSpaceGlowRenderer SHARED"));
        assertTrue(source.contains("public static ScreenSpaceGlowRenderer shared()"));
        assertTrue(source.contains("public boolean isFrameOpen()"));
    }

    private static String rendererSource() throws IOException {
        return new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/util/render/ScreenSpaceGlowRenderer.java")), StandardCharsets.UTF_8);
    }
}
