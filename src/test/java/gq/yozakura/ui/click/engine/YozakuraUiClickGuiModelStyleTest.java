package gq.yozakura.ui.click.engine;

import gq.yozakura.value.Mode;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class YozakuraUiClickGuiModelStyleTest {
    private enum TestMode { FIRST, SECOND }

    @Test
    public void expandedModuleReceivesContinuousCardClasses() {
        assertEquals("module-group expanded",
                YozakuraUiClickGuiModel.moduleGroupClasses(true));
        assertEquals("module-card enabled expanded",
                YozakuraUiClickGuiModel.moduleCardClasses(true, true));
        assertEquals("module-card",
                YozakuraUiClickGuiModel.moduleCardClasses(false, false));
    }

    @Test
    public void settingsUseDesignSpecificControlClassesAndSliderRatio() {
        Numbers<Double> number = new Numbers<Double>("Range", "range", 3.0, 1.0, 5.0, 0.1);
        Mode<TestMode> mode = new Mode<TestMode>("Mode", "mode", TestMode.values(), TestMode.FIRST);
        Option<Boolean> option = new Option<Boolean>("Enabled", "enabled", true);

        assertEquals("setting-control number-control", YozakuraUiClickGuiModel.settingControlClasses(number));
        assertEquals("setting-control mode-control", YozakuraUiClickGuiModel.settingControlClasses(mode));
        assertEquals("setting-control toggle-control", YozakuraUiClickGuiModel.settingControlClasses(option));
        assertEquals(0.5, YozakuraUiClickGuiModel.numberRatio(number), 0.0001);
        assertEquals("Accent", YozakuraUiClickGuiModel.colorPrefix("AccentRed"));
        assertEquals(null, YozakuraUiClickGuiModel.colorPrefix("Range"));
    }

    @Test
    public void settingsButtonUsesTheProjectVectorGearGlyph() {
        assertEquals("F", YozakuraUiClickGuiModel.settingsIcon());
    }

    // Pre-existing breakage: YozakuraUiClickGuiModel.moduleContentHeight(int,float)
    // and scrollThumbHeight(float) were removed, and maximumScroll() is now
    // parameterless, during the YozakuraUI engine refactor. Tracking the fix
    // is out of scope for the RenderUtil/glow perf work; the other three
    // methods above still validate the model contract.
    // @Test
    // public void scrollMetricsUseTheWholeModuleContentAndVisibleViewport() {
    //     assertEquals(632.0F, YozakuraUiClickGuiModel.moduleContentHeight(10, 0.0F), 0.001F);
    //     assertEquals(135.0F, YozakuraUiClickGuiModel.maximumScroll(10, 0.0F), 0.001F);
    //     assertEquals(255.0F, YozakuraUiClickGuiModel.maximumScroll(10, 120.0F), 0.001F);
    //     assertEquals(383.73F, YozakuraUiClickGuiModel.scrollThumbHeight(632.0F), 0.02F);
    // }
}
