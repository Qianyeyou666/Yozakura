package gq.yozakura.ui.click.yozakura;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClickGuiThemeRendererTest {
    @Test
    public void onlyTheSakuraPaletteUsesTheSolidGlassPath() {
        assertTrue(ClickGuiThemeRenderer.usesSolidGlass(gq.yozakura.module.render.ClickGUI.Palette.SAKURA));
        assertFalse(ClickGuiThemeRenderer.usesSolidGlass(gq.yozakura.module.render.ClickGUI.Palette.NIGHT_BLOOM));
        assertFalse(ClickGuiThemeRenderer.usesSolidGlass(gq.yozakura.module.render.ClickGUI.Palette.OCEAN));
        assertFalse(ClickGuiThemeRenderer.usesSolidGlass(gq.yozakura.module.render.ClickGUI.Palette.GRAPHITE));
    }
}
