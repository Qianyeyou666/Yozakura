package gq.yozakura.ui.click.yozakura;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ClickGuiThemeTest {
    @Test
    public void parsesShortAndLongHexColors() {
        assertEquals(0xFF996EF5, ClickGuiTheme.fromHex("#996EF5"));
        assertEquals(0xFFAABBCC, ClickGuiTheme.fromHex("#abc"));
    }

    @Test
    public void formatsColorsAsUppercaseSixDigitHex() {
        assertEquals("#996EF5", ClickGuiTheme.toHex(0xFF996EF5));
        assertEquals("#00A10F", ClickGuiTheme.toHex(0xFF00A10F));
    }

    @Test
    public void usesTheAccentAuthoredByTheDesignSource() {
        assertEquals(0xFF8B5CF6, ClickGuiTheme.DESIGN_ACCENT);
        assertEquals("Nether", ClickGuiTheme.BRAND_NAME);
        assertEquals("v2.1", ClickGuiTheme.BRAND_VERSION);
    }
}
