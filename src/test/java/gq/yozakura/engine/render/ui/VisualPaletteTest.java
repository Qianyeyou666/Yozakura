package gq.yozakura.engine.render.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class VisualPaletteTest {
    @Test
    public void customPaletteOverridesUiAndEspSemanticColors() {
        VisualPalette palette = VisualPalette.custom(VisualPalette.nightBloom(),
                0xFF010203, 0xFF040506, 0xFF070809, 0xFF0A0B0C, 0xFF0D0E0F,
                0xFF101112, 0xFF131415, 0xFF161718, 0xFF191A1B, 0xFF1C1D1E);

        assertEquals(0xFF010203, palette.getCanvas());
        assertEquals(0xFF040506, palette.getSurface());
        assertEquals(0xFF070809, palette.getAccentPrimary());
        assertEquals(0xFF0A0B0C, palette.getAccentAlt());
        assertEquals(0xFF0D0E0F, palette.getDanger());
        assertEquals(0xFF101112, palette.getEntityPlayer());
        assertEquals(0xFF131415, palette.getEntityMob());
        assertEquals(0xFF161718, palette.getEntityAnimal());
        assertEquals(0xFF191A1B, palette.getStorageChest());
        assertEquals(0xFF1C1D1E, palette.getStorageEnderChest());
    }
}
