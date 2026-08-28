package gq.yozakura.ui.click.yozakura;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PanelClickGuiLayoutTest {
    @Test
    public void matchesTimewarpTwoColumnGeometryAtReferenceViewport() {
        PanelClickGuiLayout.Layout layout = PanelClickGuiLayout.compute(1280.0f, 720.0f, 120.0f);

        assertEquals(584.0f, layout.panel().width(), 0.001f);
        assertEquals(420.0f, layout.panel().height(), 0.001f);
        assertEquals(348.0f, layout.panel().x(), 0.001f);
        assertEquals(150.0f, layout.panel().y(), 0.001f);
        assertEquals(132.0f, layout.rail().width(), 0.001f);
        assertEquals(439.0f, layout.modules().width(), 0.001f);
        assertEquals(layout.modules().x(), layout.detail().x(), 0.001f);
        assertEquals(layout.modules().right(), layout.detail().right(), 0.001f);
    }

    @Test
    public void preservesPanelMinimumGeometryOnSmallViewports() {
        PanelClickGuiLayout.Layout layout = PanelClickGuiLayout.compute(540.0f, 320.0f, 120.0f);

        assertEquals(528.0f, layout.panel().width(), 0.001f);
        assertEquals(310.0f, layout.panel().height(), 0.001f);
        assertEquals(6.0f, layout.panel().x(), 0.001f);
        assertEquals(5.0f, layout.panel().y(), 0.001f);
        assertEquals(534.0f, layout.panel().right(), 0.001f);
        assertEquals(315.0f, layout.panel().bottom(), 0.001f);
    }

    @Test
    public void requestedSizeIsCenteredAndClampedToViewport() {
        PanelClickGuiLayout.Layout layout = PanelClickGuiLayout.compute(
                1000.0f, 700.0f, 120.0f, 760.0f, 480.0f);
        assertEquals(760.0f, layout.panel().width(), 0.001f);
        assertEquals(480.0f, layout.panel().height(), 0.001f);
        assertEquals(120.0f, layout.panel().x(), 0.001f);
        assertEquals(110.0f, layout.panel().y(), 0.001f);

        PanelClickGuiLayout.Layout clamped = PanelClickGuiLayout.compute(
                600.0f, 340.0f, 120.0f, 900.0f, 900.0f);
        assertEquals(590.0f, clamped.panel().width(), 0.001f);
        assertEquals(330.0f, clamped.panel().height(), 0.001f);
        assertEquals(5.0f, clamped.panel().x(), 0.001f);
        assertEquals(5.0f, clamped.panel().y(), 0.001f);
    }

    @Test
    public void requestedPositionIsClampedWithoutRecentering() {
        PanelClickGuiLayout.Layout positioned = PanelClickGuiLayout.compute(
                1000.0f, 700.0f, 120.0f, 600.0f, 360.0f, 250.0f, 180.0f);
        assertEquals(250.0f, positioned.panel().x(), 0.001f);
        assertEquals(180.0f, positioned.panel().y(), 0.001f);

        PanelClickGuiLayout.Layout clamped = PanelClickGuiLayout.compute(
                1000.0f, 700.0f, 120.0f, 600.0f, 360.0f, 900.0f, -20.0f);
        assertEquals(395.0f, clamped.panel().x(), 0.001f);
        assertEquals(5.0f, clamped.panel().y(), 0.001f);
    }

    @Test
    public void resizeKeepsTopLeftFixedAndRebuildsSharedContentGeometry() {
        PanelClickGuiLayout.Layout source = PanelClickGuiLayout.compute(
                1000.0f, 700.0f, 120.0f, 600.0f, 360.0f, 80.0f, 70.0f);

        PanelClickGuiLayout.Layout resized = PanelClickGuiLayout.resized(source, 720.0f, 440.0f);

        assertEquals(80.0f, resized.panel().x(), 0.001f);
        assertEquals(70.0f, resized.panel().y(), 0.001f);
        assertEquals(720.0f, resized.panel().width(), 0.001f);
        assertEquals(440.0f, resized.panel().height(), 0.001f);
        assertEquals(resized.panel().bottom() - PanelClickGuiLayout.OUTER_PADDING,
                resized.rail().bottom(), 0.001f);
        assertEquals(resized.panel().bottom() - PanelClickGuiLayout.OUTER_PADDING,
                resized.modules().bottom(), 0.001f);
        assertEquals(resized.modules().x(), resized.detail().x(), 0.001f);
        assertEquals(resized.modules().y(), resized.detail().y(), 0.001f);
        assertEquals(resized.modules().width(), resized.detail().width(), 0.001f);
        assertEquals(resized.modules().height(), resized.detail().height(), 0.001f);
    }

    @Test
    public void detailTitleBarIsTheSharedDragHandle() {
        PanelClickGuiLayout.Layout layout = PanelClickGuiLayout.compute(
                1000.0f, 700.0f, 120.0f, 600.0f, 360.0f, 40.0f, 90.0f);
        PanelClickGuiLayout.Rect handle = PanelClickGuiLayout.dragHandle(layout);
        assertEquals(layout.panel().x(), handle.x(), 0.001f);
        assertEquals(layout.panel().y(), handle.y(), 0.001f);
        assertEquals(layout.panel().width(), handle.width(), 0.001f);
        assertEquals(34.0f, handle.height(), 0.001f);
    }

    @Test
    public void resizeHandleUsesSharedBottomRightGeometry() {
        PanelClickGuiLayout.Rect panel = new PanelClickGuiLayout.Rect(100.0f, 80.0f, 600.0f, 360.0f);
        PanelClickGuiLayout.Rect handle = PanelClickGuiLayout.resizeHandle(panel);
        assertEquals(684.0f, handle.x(), 0.001f);
        assertEquals(424.0f, handle.y(), 0.001f);
        assertEquals(16.0f, handle.width(), 0.001f);
        assertEquals(16.0f, handle.height(), 0.001f);
    }
}
