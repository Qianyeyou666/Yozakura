package gq.yozakura.ui.click.engine;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HsvColorTest {
    @Test
    public void rgbAndHsvRoundTripPrimaryAndThemeColors() {
        assertRoundTrip(255, 0, 0);
        assertRoundTrip(0, 255, 0);
        assertRoundTrip(0, 0, 255);
        assertRoundTrip(240, 139, 176);
        assertRoundTrip(153, 110, 245);
    }

    @Test
    public void clampsPickerCoordinatesAndFormatsHex() {
        HsvColor color = HsvColor.fromPicker(1.4F, -0.2F, 0.0F);
        assertEquals(1.0F, color.saturation(), 0.0001F);
        assertEquals(1.0F, color.value(), 0.0001F);
        assertEquals("#FF0000", color.toHex());
    }

    private static void assertRoundTrip(int red, int green, int blue) {
        HsvColor color = HsvColor.fromRgb(red, green, blue);
        assertEquals(red, color.red());
        assertEquals(green, color.green());
        assertEquals(blue, color.blue());
    }
}
