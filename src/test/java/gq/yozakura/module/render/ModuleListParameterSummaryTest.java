package gq.yozakura.module.render;

import gq.yozakura.value.Mode;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Value;
import gq.yozakura.value.properties.ModeProperty;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ModuleListParameterSummaryTest {
    private enum VisualMode {
        GLOWESP,
        TWO_D_HALF_CORNERS,
        NIGHT_BLOOM
    }

    @Test
    public void summarizesTheClickRateRangeBeforeOtherSettings() {
        List<Value> values = Arrays.<Value>asList(
                new Numbers<Double>("Min CPS", "MinCPS", 8.5D, 1.0D, 20.0D, 0.5D),
                new Numbers<Double>("Max CPS", "MaxCPS", 12.0D, 1.0D, 20.0D, 0.5D),
                new Numbers<Double>("Start delay", "StartDelay", 100.0D, 0.0D, 250.0D, 10.0D));

        assertEquals("8.5-12 CPS", ModuleListParameterSummary.summarize(values));
    }

    @Test
    public void formatsEnumModesForTheArrayListInsteadOfShowingRawConstants() {
        List<Value> glow = Arrays.<Value>asList(
                new Mode<VisualMode>("Mode", "Mode", VisualMode.values(), VisualMode.GLOWESP));
        List<Value> twoD = Arrays.<Value>asList(
                new Mode<VisualMode>("Mode", "Mode", VisualMode.values(), VisualMode.TWO_D_HALF_CORNERS));
        List<Value> nightBloom = Arrays.<Value>asList(
                new ModeProperty("Mode", 2, new String[]{"Outline", "GlowESP", "Night Bloom"}));

        assertEquals("GlowESP", ModuleListParameterSummary.summarize(glow));
        assertEquals("2D Half Corners", ModuleListParameterSummary.summarize(twoD));
        assertEquals("Night Bloom", ModuleListParameterSummary.summarize(nightBloom));
    }

    @Test
    public void showsARelevantDistanceOrDelayWhenNoModeExists() {
        List<Value> range = Arrays.<Value>asList(
                new Numbers<Double>("Range", "Range", 4.5D, 0.0D, 6.0D, 0.1D));
        List<Value> delay = Arrays.<Value>asList(
                new Numbers<Integer>("Click Delay", "ClickDelay", 80, 0, 1000, 10));

        assertEquals("4.5r", ModuleListParameterSummary.summarize(range));
        assertEquals("80ms", ModuleListParameterSummary.summarize(delay));
    }

    @Test
    public void keepsCustomModuleSuffixesCompactWhenTheyProvideMoreUsefulDetail() {
        assertEquals("60%/100%", ModuleListParameterSummary.summarizeExplicit(
                new String[]{"60%", "100%", "ignored"}));
    }
}
