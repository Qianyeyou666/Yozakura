package gq.yozakura.engine.font;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class CFontAtlasSamplingContractTest {
    @Test
    public void atlasUvsStayInsideGlyphCellsUnderLinearFiltering() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/engine/font/CFont.java")), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");

        assertTrue(source.contains("private static final float TEXEL_INSET = 0.5f;"));
        assertTrue(source.contains("data.storedX + TEXEL_INSET"));
        assertTrue(source.contains("data.storedY + TEXEL_INSET"));
        assertTrue(source.contains("data.storedX + data.srcWidth - TEXEL_INSET"));
        assertTrue(source.contains("data.storedY + data.srcHeight - TEXEL_INSET"));
    }

    @Test
    public void glyphCellsHavePhysicalGuttersAndAreClearedBeforeRasterization() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/engine/font/CFont.java")), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");

        assertTrue(source.contains("private static final int CELL_GUTTER"));
        assertTrue(source.contains("positionX += data.srcWidth + CELL_GUTTER"));
        assertTrue(source.contains("clearGlyphCell(bufferedImage, data)"));
        assertTrue(source.contains("graphics.setClip("));
        assertTrue(source.contains("graphics.setClip(null)"));
    }
}
