package gq.yozakura.module.render;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NymphArrayListBackgroundPlanTest {
    @Test
    public void everyBackgroundModeKeepsItsOwnVisualContract() {
        NymphArrayListBackgroundPlan none = NymphArrayListBackgroundPlan.forMode(HUD.NymphBackground.NONE);
        assertFalse(none.hasSurface());
        assertFalse(none.hasBlur());

        NymphArrayListBackgroundPlan blur = NymphArrayListBackgroundPlan.forMode(HUD.NymphBackground.BLUR);
        assertTrue(blur.hasSurface());
        assertTrue(blur.hasBlur());
        assertFalse(blur.hasOutline());

        NymphArrayListBackgroundPlan outline = NymphArrayListBackgroundPlan.forMode(HUD.NymphBackground.OUTLINE);
        assertTrue(outline.hasSurface());
        assertTrue(outline.hasOutline());
        assertFalse(outline.hasBar());

        assertTrue(NymphArrayListBackgroundPlan.forMode(HUD.NymphBackground.BARLEFT).isLeftBar());
        assertTrue(NymphArrayListBackgroundPlan.forMode(HUD.NymphBackground.BARRIGHT).isRightBar());
    }

    @Test
    public void sourceRowBoundsKeepTheOriginalPaddingAndHeight() {
        NymphArrayListBackgroundPlan.Bounds bounds = NymphArrayListBackgroundPlan.bounds(40.0F, 12.0F, 70.0F);
        assertTrue(bounds.left == 38.0F);
        assertTrue(bounds.top == 9.0F);
        assertTrue(bounds.right == 113.0F);
        assertTrue(bounds.bottom == 20.0F);
    }

    @Test
    public void touchingRowsProduceOneConnectorForTheUnionMask() {
        NymphArrayListBackgroundPlan.Bounds first =
                new NymphArrayListBackgroundPlan.Bounds(30.0F, 5.0F, 100.0F, 16.0F);
        NymphArrayListBackgroundPlan.Bounds second =
                new NymphArrayListBackgroundPlan.Bounds(45.0F, 16.0F, 100.0F, 27.0F);

        NymphArrayListBackgroundPlan.Bounds connector =
                NymphArrayListBackgroundPlan.connector(first, second, 4.0F);

        assertTrue(connector.left == 45.0F);
        assertTrue(connector.right == 100.0F);
        assertTrue(connector.top < 16.0F);
        assertTrue(connector.bottom > 16.0F);
    }
}
