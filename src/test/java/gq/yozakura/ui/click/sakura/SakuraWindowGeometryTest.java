package gq.yozakura.ui.click.sakura;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SakuraWindowGeometryTest {
    private static final float EPSILON = 0.0001F;

    @Test
    public void resizeHandleHitTestUsesTheScreenCoordinatesUsedForRendering() {
        assertTrue(SakuraWindowGeometry.containsScreen(500.0F, 300.0F, 532.0F, 332.0F, 516.0F, 316.0F));
        assertFalse(SakuraWindowGeometry.containsScreen(500.0F, 300.0F, 532.0F, 332.0F, 499.0F, 316.0F));
    }

    @Test
    public void diagonalResizeTracksTheFixedAspectRatioWithoutAxisJumps() {
        assertEquals(1.10F, SakuraWindowGeometry.resizeScale(1.0F, 0.0F, 0.0F,
                76.0F, 43.0F, 1.0F, 760.0F, 430.0F, 0.70F, 1.35F), EPSILON);
        assertEquals(0.90F, SakuraWindowGeometry.resizeScale(1.0F, 0.0F, 0.0F,
                -76.0F, -43.0F, 1.0F, 760.0F, 430.0F, 0.70F, 1.35F), EPSILON);
    }

    @Test
    public void windowDragKeepsThePointerAnchoredToTheVisibleHeader() {
        assertEquals(180.0F, SakuraWindowGeometry.windowYFromHeader(214.0F, 20.0F, 14.0F), EPSILON);
    }
}
