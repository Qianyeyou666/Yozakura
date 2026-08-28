package gq.yozakura.module.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class NymphArrayListStyleTest {
    private static final float EPSILON = 0.0001F;

    @Test
    public void staticModePreservesTheConfiguredColor() {
        assertEquals(0xFFD13232, NymphArrayListStyle.colorAt(
                NymphArrayListStyle.ColorMode.STATIC, 0xFFD13232, 0xFF1DCDC8, 0, 1250L));
    }

    @Test
    public void everyAnimatedSourceModeVariesByRowOrTime() {
        for (NymphArrayListStyle.ColorMode mode : NymphArrayListStyle.ColorMode.values()) {
            if (mode == NymphArrayListStyle.ColorMode.STATIC) {
                continue;
            }
            int first = NymphArrayListStyle.colorAt(mode, 0xFFD13232, 0xFF1DCDC8, 0, 250L);
            int laterRow = NymphArrayListStyle.colorAt(mode, 0xFFD13232, 0xFF1DCDC8, 200, 1250L);
            assertNotEquals(mode.name(), first, laterRow);
        }
    }

    @Test
    public void switchModeTravelsBetweenBothConfiguredColors() {
        int first = 0xFFD13232;
        int second = 0xFF1DCDC8;
        assertEquals(first, NymphArrayListStyle.colorAt(
                NymphArrayListStyle.ColorMode.SWITCH, first, second, 0, 0L));
        assertEquals(second, NymphArrayListStyle.colorAt(
                NymphArrayListStyle.ColorMode.SWITCH, first, second, 0, 1000L));
        assertEquals(first, NymphArrayListStyle.colorAt(
                NymphArrayListStyle.ColorMode.SWITCH, first, second, 0, 2000L));
    }

    @Test
    public void moveInComesFromTheNearestScreenEdge() {
        assertEquals(-80.0F, NymphArrayListStyle.animatedTextX(
                20.0F, 80.0F, 320.0F, 0.0F, false), EPSILON);
        assertEquals(20.0F, NymphArrayListStyle.animatedTextX(
                20.0F, 80.0F, 320.0F, 1.0F, false), EPSILON);
        assertEquals(320.0F, NymphArrayListStyle.animatedTextX(
                220.0F, 80.0F, 320.0F, 0.0F, true), EPSILON);
        assertEquals(220.0F, NymphArrayListStyle.animatedTextX(
                220.0F, 80.0F, 320.0F, 1.0F, true), EPSILON);
    }

    @Test
    public void sourceGeometryUsesOneFixedBaselineAndLeavesSizingToHudScale() {
        assertEquals(19, NymphArrayListStyle.FONT_SIZE);
        assertEquals(11.0F, NymphArrayListStyle.ROW_HEIGHT, EPSILON);
    }
}
