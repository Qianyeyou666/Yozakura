package gq.yozakura.ui.click.yozakura;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class PanelClubGeometryTest {
    @Test
    public void allHallControlsStayInsideMinimumDetailColumnWithoutOverlapping() {
        PanelClickGuiLayout.Layout layout = PanelClickGuiLayout.compute(
                1280.0f, 720.0f, 120.0f,
                PanelClickGuiLayout.PANEL_MIN_WIDTH,
                PanelClickGuiLayout.PANEL_MIN_HEIGHT);
        PanelClickGuiLayout.Rect bounds = layout.detail();
        PanelClickGuiLayout.Rect[] controls = {
                PanelClubGeometry.localTab(bounds),
                PanelClubGeometry.cloudTab(bounds),
                PanelClubGeometry.identity(bounds),
                PanelClubGeometry.searchField(bounds),
                PanelClubGeometry.cloudList(bounds),
                PanelClubGeometry.uploadButton(bounds),
                PanelClubGeometry.downloadButton(bounds),
                PanelClubGeometry.useButton(bounds),
                PanelClubGeometry.deleteButton(bounds),
                PanelClubGeometry.refreshButton(bounds),
                PanelClubGeometry.status(bounds)
        };
        for (PanelClickGuiLayout.Rect control : controls) {
            assertTrue(control.x() >= bounds.x() + 14.0f - 0.001f);
            assertTrue(control.right() <= bounds.right() - 14.0f + 0.001f);
            assertTrue(control.y() >= bounds.y());
            assertTrue(control.bottom() <= bounds.bottom());
        }
        assertTrue(PanelClubGeometry.localTab(bounds).bottom()
                <= PanelClubGeometry.identity(bounds).y());
        assertTrue(PanelClubGeometry.identity(bounds).bottom()
                <= PanelClubGeometry.searchField(bounds).y());
        assertTrue(PanelClubGeometry.searchField(bounds).bottom()
                <= PanelClubGeometry.cloudList(bounds).y());
        assertTrue(PanelClubGeometry.cloudList(bounds).bottom()
                <= PanelClubGeometry.uploadButton(bounds).y());
        assertTrue(PanelClubGeometry.uploadButton(bounds).bottom()
                <= PanelClubGeometry.status(bounds).y());
    }
}
