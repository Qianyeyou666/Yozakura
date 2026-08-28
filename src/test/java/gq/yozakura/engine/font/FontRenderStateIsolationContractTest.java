package gq.yozakura.engine.font;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FontRenderStateIsolationContractTest {
    @Test
    public void customFontKeepsPerStringStateRestorationInsideRawGlBoundary() throws IOException {
        String source = source("src/main/java/gq/yozakura/engine/font/CFontRenderer.java");
        String draw = method(source,
                "    private float drawStringInternal(String text, double x, double y, int color, boolean shadow,\n"
                        + "                                     boolean allowScaleCompensation, boolean maskPass,\n"
                        + "                                     CodePointColorProvider colors) {",
                "    private double snapToTextGrid(double value) {");

        assertTrue(draw.contains("GL11.glPushAttrib(FONT_ATTRIB_MASK);"));
        assertTrue(draw.contains("GL11.glPushMatrix();"));
        assertTrue(draw.contains("GL11.glPopMatrix();"));
        assertTrue(draw.contains("GL11.glPopAttrib();"));
        assertTrue(draw.contains("GL13.glActiveTexture(GL13.GL_TEXTURE0);"));
        assertTrue(draw.contains("GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);"));
        assertFalse("Per-string rendering must not force full driver state readback",
                draw.contains("syncToCurrent()"));
        assertFalse("Raw GL restoration must not mutate Minecraft's cached state",
                draw.contains("GlStateManager."));
        assertFalse("Attribute restoration already restores active texture and binding",
                draw.contains("glGetInteger("));
    }

    @Test
    public void minecraftFontKeepsPerStringStateRestorationInsideRawGlBoundary() throws IOException {
        String source = source("src/main/java/gq/yozakura/engine/font/MinecraftFontRenderer.java");
        String draw = method(source,
                "    private float drawStringInternal(String text, double x, double y,\n"
                        + "                                     int color, boolean maskPass) {",
                "    private void drawGlyph(char character, float x, float y, boolean italic) {");

        assertTrue(draw.contains("GL11.glPushAttrib(FONT_ATTRIB_MASK);"));
        assertTrue(draw.contains("GL11.glPopAttrib();"));
        assertTrue(draw.contains("GL13.glActiveTexture(GL13.GL_TEXTURE0);"));
        assertTrue(draw.contains("GL11.glBindTexture(GL11.GL_TEXTURE_2D"));
        assertFalse("Per-string rendering must not force full driver state readback",
                draw.contains("syncToCurrent()"));
        assertFalse("Raw GL restoration must not mutate Minecraft's cached state",
                draw.contains("GlStateManager."));
        assertFalse("Attribute restoration already restores active texture and binding",
                draw.contains("glGetInteger("));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }

    private static String method(String source, String beginMarker, String endMarker) {
        int begin = source.indexOf(beginMarker);
        int end = source.indexOf(endMarker, begin);
        assertTrue("Expected source method boundary was not found", begin >= 0 && end > begin);
        return source.substring(begin, end);
    }
}
