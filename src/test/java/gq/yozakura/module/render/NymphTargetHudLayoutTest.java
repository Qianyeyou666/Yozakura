package gq.yozakura.module.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NymphTargetHudLayoutTest {
    private static final float EPSILON = 0.0001F;

    @Test
    public void keepsTheSourceStrifeDimensions() {
        assertEquals(130.0F, NymphTargetHudLayout.width(20.0F), EPSILON);
        assertEquals(156.0F, NymphTargetHudLayout.width(156.0F), EPSILON);
        assertEquals(37.0F, NymphTargetHudLayout.HEIGHT, EPSILON);
        assertEquals(33.0F, NymphTargetHudLayout.AVATAR_SIZE, EPSILON);
        assertEquals(93.0F, NymphTargetHudLayout.healthBarWidth(130.0F), EPSILON);
    }

    @Test
    public void sourceHealthThresholdsProgressFromGreenToRed() {
        assertEquals(0xFF00FF00, NymphTargetHudLayout.healthColor(0.80F));
        assertEquals(0xFFF0FF00, NymphTargetHudLayout.healthColor(0.40F));
        assertEquals(0xFFFFC800, NymphTargetHudLayout.healthColor(0.30F));
        assertEquals(0xFFFF0000, NymphTargetHudLayout.healthColor(0.20F));
    }

    @Test
    public void itemSlotsKeepTheOriginalSixteenPixelStride() {
        assertEquals(39.0F, NymphTargetHudLayout.itemX(34.0F, 0, false), EPSILON);
        assertEquals(36.5F, NymphTargetHudLayout.itemX(34.0F, 0, true), EPSILON);
        assertEquals(55.0F, NymphTargetHudLayout.itemX(34.0F, 1, false), EPSILON);
    }
}
