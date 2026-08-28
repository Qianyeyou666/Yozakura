package gq.yozakura.ui.click.timewarp;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TimewarpClickGuiGeometryTest {
    @Test
    public void centersAStableTwoColumnWindow() {
        TimewarpClickGuiGeometry.Layout layout = TimewarpClickGuiGeometry.compute(1280.0f, 720.0f);

        assertEquals(500.0f, layout.window().width(), 0.001f);
        assertEquals(382.0f, layout.window().height(), 0.001f);
        assertEquals(390.0f, layout.window().x(), 0.001f);
        assertEquals(169.0f, layout.window().y(), 0.001f);
        assertEquals(122.0f, layout.sidebar().width(), 0.001f);
        assertEquals(layout.sidebar().right(), layout.content().x(), 0.001f);
        assertEquals(layout.window().right(), layout.content().right(), 0.001f);
    }

    @Test
    public void clampsToSmallScreensWithoutOverlappingColumns() {
        TimewarpClickGuiGeometry.Layout layout = TimewarpClickGuiGeometry.compute(460.0f, 300.0f);

        assertTrue(layout.window().x() >= TimewarpClickGuiGeometry.VIEWPORT_MARGIN);
        assertTrue(layout.window().y() >= TimewarpClickGuiGeometry.VIEWPORT_MARGIN);
        assertTrue(layout.window().right() <= 460.0f - TimewarpClickGuiGeometry.VIEWPORT_MARGIN + 0.001f);
        assertTrue(layout.window().bottom() <= 300.0f - TimewarpClickGuiGeometry.VIEWPORT_MARGIN + 0.001f);
        assertTrue(layout.sidebar().width() >= TimewarpClickGuiGeometry.MIN_SIDEBAR_WIDTH);
        assertTrue(layout.content().width() > layout.sidebar().width());
        assertFalse(layout.sidebar().contains(layout.content().x() + 1.0f, layout.content().y() + 1.0f));
    }

    @Test
    public void exposesReferenceSizedNavigationAndModuleRows() {
        TimewarpClickGuiGeometry.Layout layout = TimewarpClickGuiGeometry.compute(1280.0f, 720.0f);
        TimewarpClickGuiGeometry.Rect combat = TimewarpClickGuiGeometry.navigationItem(layout, 0);
        TimewarpClickGuiGeometry.Rect firstModule = TimewarpClickGuiGeometry.moduleRow(layout, 0, 0.0f);

        assertEquals(29.0f, combat.height(), 0.001f);
        assertEquals(42.0f, firstModule.height(), 0.001f);
        assertTrue(firstModule.x() > layout.content().x());
        assertTrue(firstModule.right() < layout.content().right());
    }
}
