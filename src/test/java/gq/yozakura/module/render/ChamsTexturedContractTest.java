package gq.yozakura.module.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChamsTexturedContractTest {
    @Test
    public void texturedModeKeepsTheEntityTextureOutOfTheColoredChamsPath() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/render/Chams.java");
        String preRender = between(source, "public void onRenderLivingPre", "public void onRenderLivingPost");
        String coloredStyle = between(source, "private void applyColoredStyle", "private boolean isValidTarget");
        String postRender = between(source, "public void onRenderLivingPost", "private void applyColoredStyle");

        assertTrue(preRender.contains("if (!Boolean.TRUE.equals(textured.getValue())) {"));
        assertTrue(preRender.contains("applyColoredStyle(entity);"));
        assertFalse(preRender.contains("getColor(entity)"));
        assertTrue(preRender.contains("GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);"));
        assertTrue(coloredStyle.contains("GL11.glDisable(GL11.GL_TEXTURE_2D);"));
        assertTrue(coloredStyle.contains("GL11.glColor4f("));
        assertFalse(postRender.contains("GL11.glColor4f("));
    }

    @Test
    public void texturedModeHidesColorOnlySettings() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/render/Chams.java");

        assertTrue(source.contains("paletteColors.visibleWhen(() -> !Boolean.TRUE.equals(textured.getValue()))"));
        assertTrue(source.contains("alpha.visibleWhen(() -> !Boolean.TRUE.equals(textured.getValue()))"));
    }

    private static String between(String source, String begin, String end) {
        int start = source.indexOf(begin);
        if (start < 0) {
            return "";
        }
        int finish = source.indexOf(end, start);
        if (finish < 0) {
            return "";
        }
        return source.substring(start, finish);
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
