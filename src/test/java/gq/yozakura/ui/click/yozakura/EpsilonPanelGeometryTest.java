package gq.yozakura.ui.click.yozakura;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EpsilonPanelGeometryTest {
    @Test
    public void detailHeaderUsesThePinnedEpsilonControlGeometry() {
        PanelClickGuiLayout.Rect panel = new PanelClickGuiLayout.Rect(100.0f, 20.0f, 260.0f, 314.0f);
        EpsilonPanelGeometry.DetailHeader header = EpsilonPanelGeometry.detailHeader(panel);

        assertRect(header.background(), 103.0f, 54.0f, 254.0f, 36.0f);
        assertRect(header.keybind(), 111.0f, 63.0f, 18.0f, 18.0f);
        assertRect(header.bindMode(), 135.0f, 63.0f, 72.0f, 18.0f);
        assertRect(header.hidden(), 213.0f, 63.0f, 72.0f, 18.0f);
        assertEquals(171.0f, header.bindMode().x() + header.bindMode().width() * 0.5f, 0.001f);
        assertEquals(249.0f, header.hidden().x() + header.hidden().width() * 0.5f, 0.001f);
    }

    @Test
    public void moduleSwitchAndNumberControlsShareDrawAndHitBounds() {
        PanelClickGuiLayout.Rect row = new PanelClickGuiLayout.Rect(20.0f, 40.0f, 150.0f, 34.0f);
        PanelClickGuiLayout.Rect moduleSwitch = EpsilonPanelGeometry.moduleSwitch(row);
        PanelClickGuiLayout.Rect settingsButton = EpsilonPanelGeometry.moduleSettingsButton(row);
        assertRect(moduleSwitch, 134.0f, 49.0f, 26.0f, 16.0f);
        assertRect(settingsButton, 106.0f, 47.0f, 20.0f, 20.0f);
        assertEquals(moduleSwitch.y() + moduleSwitch.height() * 0.5f,
                settingsButton.y() + settingsButton.height() * 0.5f, 0.001f);

        PanelClickGuiLayout.Rect setting = new PanelClickGuiLayout.Rect(10.0f, 30.0f, 220.0f, 28.0f);
        assertRect(EpsilonPanelGeometry.numberTrack(setting), 109.0f, 42.0f, 72.0f, 6.0f);
        assertRect(EpsilonPanelGeometry.numberField(setting), 185.0f, 34.0f, 40.0f, 18.0f);
        assertRect(EpsilonPanelGeometry.numberInteractive(setting), 109.0f, 36.0f, 72.0f, 18.0f);
        assertTrue(EpsilonPanelGeometry.numberInteractive(setting).contains(145.0f, 36.0f));
        assertRect(EpsilonPanelGeometry.optionSwitch(setting), 199.0f, 36.0f, 26.0f, 16.0f);
    }

    @Test
    public void railDrawAndHitBoundsUseTheSamePinnedGeometry() {
        PanelClickGuiLayout.Rect rail = new PanelClickGuiLayout.Rect(10.0f, 20.0f, 120.0f, 314.0f);
        assertRect(EpsilonPanelGeometry.railMenuButton(rail), 16.0f, 24.0f, 28.0f, 28.0f);
        assertRect(EpsilonPanelGeometry.railCategoryItem(rail, 2), 15.0f, 136.0f, 110.0f, 34.0f);
        assertRect(EpsilonPanelGeometry.railConfigManagerItem(rail), 15.0f, 257.0f, 110.0f, 34.0f);
        assertRect(EpsilonPanelGeometry.railSettingsItem(rail), 15.0f, 295.0f, 110.0f, 34.0f);
        assertEquals(EpsilonPanelMetrics.CATEGORY_ITEM_STEP,
                EpsilonPanelGeometry.railSettingsItem(rail).y()
                        - EpsilonPanelGeometry.railConfigManagerItem(rail).y(), 0.001f);
    }

    @Test
    public void railMetricsMatchEpsilon() {
        assertEquals(34.0f, EpsilonPanelMetrics.CATEGORY_ITEM_HEIGHT, 0.001f);
        assertEquals(38.0f, EpsilonPanelMetrics.CATEGORY_ITEM_STEP, 0.001f);
        assertEquals(40.0f, EpsilonPanelMetrics.CATEGORY_START_Y, 0.001f);
        assertEquals(1.02f, EpsilonPanelMetrics.CATEGORY_ICON_SCALE, 0.001f);
        assertEquals(0.62f, EpsilonPanelMetrics.CATEGORY_LABEL_SCALE, 0.001f);
        assertEquals(0.58f, EpsilonPanelMetrics.CATEGORY_COUNT_SCALE, 0.001f);
        assertEquals(12.0f, EpsilonPanelMetrics.MENU_LINE_WIDTH, 0.001f);
        assertEquals(1.6f, EpsilonPanelMetrics.MENU_LINE_HEIGHT, 0.001f);
    }

    private static void assertRect(PanelClickGuiLayout.Rect rect, float x, float y, float width, float height) {
        assertEquals(x, rect.x(), 0.001f);
        assertEquals(y, rect.y(), 0.001f);
        assertEquals(width, rect.width(), 0.001f);
        assertEquals(height, rect.height(), 0.001f);
    }
}
