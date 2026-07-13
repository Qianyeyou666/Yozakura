package gq.yozakura.util.render;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HudDragPaletteContractTest {
    @Test
    public void dragHintsUseNightBloomPaletteTokensInsteadOfAdHocColors() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/util/render/HudDrag.java")), StandardCharsets.UTF_8);

        assertTrue(source.contains("VisualPalette.nightBloom()"));
        assertTrue(source.contains("getBorderFocus()"));
        assertTrue(source.contains("getAccentPrimary()"));
        assertTrue(source.contains("getAccentAlt()"));
        assertFalse(source.contains("0xE8FFC1EB"));
        assertFalse(source.contains("0xC870C1DC"));
        assertFalse(source.contains("0xB88B7CFF"));
        assertFalse(source.contains("0x8870C1DC"));
        assertFalse(source.contains("0x9CF7D6FF"));
    }
}
