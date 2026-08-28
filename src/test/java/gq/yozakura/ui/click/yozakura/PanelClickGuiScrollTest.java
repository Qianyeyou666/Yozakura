package gq.yozakura.ui.click.yozakura;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PanelClickGuiScrollTest {
    @Test
    public void computesThumbAndMapsItsEndBackToMaximumScroll() {
        PanelClickGuiLayout.Rect viewport = new PanelClickGuiLayout.Rect(10.0f, 20.0f, 100.0f, 200.0f);
        PanelClickGuiScroll.Geometry geometry = PanelClickGuiScroll.geometry(viewport, 50.0f, 100.0f, 400.0f);

        assertNotNull(geometry);
        assertEquals(107.5f, geometry.x(), 0.001f);
        assertEquals(2.0f, geometry.width(), 0.001f);
        assertEquals(70.0f, geometry.y(), 0.001f);
        assertEquals(100.0f, geometry.height(), 0.001f);
        assertEquals(100.0f, geometry.trackX(), 0.001f);
        assertEquals(10.0f, geometry.trackWidth(), 0.001f);
        assertTrue(geometry.thumbContains(101.0f, 80.0f));
        assertTrue(geometry.trackContains(101.0f, 210.0f));
        assertFalse(geometry.trackContains(99.0f, 80.0f));
        assertEquals(100.0f, PanelClickGuiScroll.scrollFromThumbTop(120.0f, viewport, 100.0f, 400.0f), 0.001f);
    }

    @Test
    public void enforcesEpsilonMinimumThumbHeight() {
        PanelClickGuiLayout.Rect viewport = new PanelClickGuiLayout.Rect(0.0f, 0.0f, 100.0f, 40.0f);
        PanelClickGuiScroll.Geometry geometry = PanelClickGuiScroll.geometry(viewport, 0.0f, 960.0f, 1000.0f);

        assertNotNull(geometry);
        assertEquals(10.0f, geometry.height(), 0.001f);
    }

    @Test
    public void omitsTheScrollbarWhenContentFits() {
        PanelClickGuiLayout.Rect viewport = new PanelClickGuiLayout.Rect(0.0f, 0.0f, 100.0f, 200.0f);
        assertNull(PanelClickGuiScroll.geometry(viewport, 0.0f, 0.0f, 180.0f));
    }

    @Test
    public void hoverWidthExpandsAroundTheThumbCenter() {
        PanelClickGuiLayout.Rect viewport = new PanelClickGuiLayout.Rect(10.0f, 20.0f, 100.0f, 200.0f);
        PanelClickGuiScroll.Geometry geometry = PanelClickGuiScroll.geometry(viewport, 50.0f, 100.0f, 400.0f);

        assertNotNull(geometry);
        assertEquals(2.0f, PanelClickGuiScroll.visualWidth(geometry, 0.0f), 0.001f);
        assertEquals(2.5f, PanelClickGuiScroll.visualWidth(geometry, 1.0f), 0.001f);
        assertEquals(107.5f, PanelClickGuiScroll.visualX(geometry, 0.0f), 0.001f);
        assertEquals(107.25f, PanelClickGuiScroll.visualX(geometry, 1.0f), 0.001f);
    }
}
