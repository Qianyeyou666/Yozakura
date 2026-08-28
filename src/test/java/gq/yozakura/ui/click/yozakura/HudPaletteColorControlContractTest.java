package gq.yozakura.ui.click.yozakura;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class HudPaletteColorControlContractTest {
    @Test
    public void nymphHudColorsAreExposedAsDedicatedPaletteSwatches() {
        PanelPaletteColorControl.Group primary =
                PanelPaletteColorControl.groupForName("NymphPrimaryRed");
        PanelPaletteColorControl.Group secondary =
                PanelPaletteColorControl.groupForName("NymphSecondaryBlue");

        assertNotNull(primary);
        assertNotNull(secondary);
        assertEquals("HUD Primary", primary.label());
        assertEquals("HUD Secondary", secondary.label());
        assertTrue(primary.isHudPalette());
        assertTrue(secondary.isHudPalette());
    }

    @Test
    public void hudPaletteDoesNotMutateTheClickGuiAlphaControl() throws Exception {
        String picker = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/ui/click/yozakura/PanelPaletteColorPicker.java")),
                StandardCharsets.UTF_8);
        assertTrue(picker.contains("if (!group.isHudPalette())"));
    }
}
