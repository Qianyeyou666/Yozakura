package gq.yozakura.module.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ModuleListAnchorTest {
    private static final float EPSILON = 0.0001F;

    @Test
    public void switchesAtTheScreenMidpointUsingTheListCenter() {
        assertFalse(ModuleListAnchor.isRightSide(10.0F, 80.0F, 200.0F));
        assertTrue(ModuleListAnchor.isRightSide(120.0F, 80.0F, 200.0F));
        assertTrue(ModuleListAnchor.isRightSide(60.0F, 80.0F, 200.0F));
    }

    @Test
    public void anchorsRowsAndExitMotionToTheNearestOuterEdge() {
        assertEquals(10.0F, ModuleListAnchor.rowX(10.0F, 100.0F, 40.0F, 1.0F, false), EPSILON);
        assertEquals(70.0F, ModuleListAnchor.rowX(10.0F, 100.0F, 40.0F, 1.0F, true), EPSILON);
        assertEquals(2.0F, ModuleListAnchor.rowX(10.0F, 100.0F, 40.0F, 0.0F, false), EPSILON);
        assertEquals(78.0F, ModuleListAnchor.rowX(10.0F, 100.0F, 40.0F, 0.0F, true), EPSILON);
    }

    @Test
    public void resolvesTextFromTheSameSideAsTheRows() {
        assertEquals(14.0F, ModuleListAnchor.textX(10.0F, 100.0F, 30.0F, 4.0F, false), EPSILON);
        assertEquals(76.0F, ModuleListAnchor.textX(10.0F, 100.0F, 30.0F, 4.0F, true), EPSILON);
    }
}
