package gq.yozakura.ui.click.yozakura;

import gq.yozakura.value.Numbers;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PanelNumberInputPolicyTest {
    @Test
    public void typedIntegerValueIsNotSnappedToSliderIncrement() {
        Numbers<Double> delay = new Numbers<Double>("Delay", "Delay",
                100.0D, 0.0D, 250.0D, 10.0D);

        assertEquals(91.0D, PanelNumberInputPolicy.normalizeTypedValue(delay, 91.0D), 0.0D);
    }

    @Test
    public void typedValueStillClampsToSafeRange() {
        Numbers<Double> delay = new Numbers<Double>("Delay", "Delay",
                100.0D, 0.0D, 250.0D, 10.0D);

        assertEquals(0.0D, PanelNumberInputPolicy.normalizeTypedValue(delay, -50.0D), 0.0D);
        assertEquals(250.0D, PanelNumberInputPolicy.normalizeTypedValue(delay, 999.0D), 0.0D);
    }

    @Test
    public void wholeNumberControlsRejectFractionalTypedValuesWithoutUsingSliderStep() {
        Numbers<Double> delay = new Numbers<Double>("Delay", "Delay",
                100.0D, 0.0D, 250.0D, 10.0D);

        assertEquals(92.0D, PanelNumberInputPolicy.normalizeTypedValue(delay, 91.6D), 0.0D);
    }

    @Test
    public void decimalControlsKeepTypedPrecisionWithinRange() {
        Numbers<Double> cps = new Numbers<Double>("CPS", "CPS",
                8.0D, 1.0D, 20.0D, 0.5D);

        assertEquals(9.63D, PanelNumberInputPolicy.normalizeTypedValue(cps, 9.63D), 0.0D);
    }
}
