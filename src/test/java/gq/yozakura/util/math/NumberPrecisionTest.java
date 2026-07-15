package gq.yozakura.util.math;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NumberPrecisionTest {
    @Test
    public void refinesFractionalUiStepsWithoutChangingWholeNumberSteps() {
        assertEquals(0.01D, NumberPrecision.uiIncrement(0.1D), 0.0D);
        assertEquals(0.1D, NumberPrecision.uiIncrement(0.5D), 0.0D);
        assertEquals(0.01D, NumberPrecision.uiIncrement(0.05D), 0.0D);
        assertEquals(10.0D, NumberPrecision.uiIncrement(10.0D), 0.0D);
    }

    @Test
    public void formatsValuesWithThePrecisionOfTheirUiStep() {
        assertEquals("3.20", NumberPrecision.format(3.2D, 0.1D));
        assertEquals("8.0", NumberPrecision.format(8.0D, 0.5D));
        assertEquals("0.08", NumberPrecision.format(0.08D, 0.01D));
        assertEquals("80", NumberPrecision.format(80.0D, 10.0D));
    }

    @Test
    public void snapsAndClampsUsingTheRefinedUiStep() {
        assertEquals(3.26D, NumberPrecision.snap(3.256D, 3.0D, 6.0D, 0.1D), 0.0D);
        assertEquals(8.3D, NumberPrecision.snap(8.26D, 1.0D, 20.0D, 0.5D), 0.0D);
        assertEquals(3.0D, NumberPrecision.snap(2.6D, 3.0D, 6.0D, 0.1D), 0.0D);
    }
}
