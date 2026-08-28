package gq.yozakura.ui.click.yozakura;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class PanelPaletteColorControlTest {
    @Test
    public void eachRgbTripletCollapsesToItsRedLeader() {
        assertSame(PanelPaletteColorControl.Group.CANVAS,
                PanelPaletteColorControl.groupForName("CanvasGreen"));
        assertTrue(PanelPaletteColorControl.isLeaderName("CanvasRed"));
        assertFalse(PanelPaletteColorControl.isLeaderName("CanvasGreen"));
        assertFalse(PanelPaletteColorControl.isLeaderName("CanvasBlue"));
        assertSame(PanelPaletteColorControl.Group.HOTBAR_SELECTION,
                PanelPaletteColorControl.groupForName("SelectionGreen"));
        assertTrue(PanelPaletteColorControl.isLeaderName("SelectionRed"));
    }

    @Test
    public void swatchUsesCompactTrailingGeometry() {
        PanelClickGuiLayout.Rect row = new PanelClickGuiLayout.Rect(10.0f, 20.0f, 180.0f, 28.0f);
        PanelClickGuiLayout.Rect swatch = PanelPaletteColorControl.swatchBounds(row);
        assertEquals(157.0f, swatch.x(), 0.001f);
        assertEquals(26.0f, swatch.y(), 0.001f);
        assertEquals(28.0f, swatch.width(), 0.001f);
        assertEquals(16.0f, swatch.height(), 0.001f);
    }
}
