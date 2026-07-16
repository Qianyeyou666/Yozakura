package gq.yozakura.module.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MinimalHudLayoutTest {
    @Test
    public void keepsVanillaTypographyOnWholePixels() {
        assertEquals(0xFFFFFFFF, MinimalHudLayout.TEXT_COLOR);
        assertEquals(0xFFD0D0D0, MinimalHudLayout.MUTED_COLOR);
        assertEquals(10.0F, MinimalHudLayout.ROW_HEIGHT, 0.0F);
        assertEquals(42.0F, MinimalHudLayout.pixel(41.6F), 0.0F);
        assertEquals(41.0F, MinimalHudLayout.pixel(41.4F), 0.0F);
    }

    @Test
    public void usesCompactBoundsWithoutOversizedPadding() {
        assertEquals(86.0F, MinimalHudLayout.contentWidth(80), 0.0F);
        assertEquals(32.0F, MinimalHudLayout.listHeight(3), 0.0F);
        assertEquals(12.0F, MinimalHudLayout.listHeight(0), 0.0F);
    }

    @Test
    public void detectsGlyphsThatNeedTheHighResolutionUnicodeRenderer() {
        assertEquals(false, MinimalHudLayout.usesUnicodeFallback("AimAssist"));
        assertEquals(true, MinimalHudLayout.usesUnicodeFallback("辅助瞄准"));
        assertEquals(true, MinimalHudLayout.usesUnicodeFallback("ESP 实体透视"));
    }
}
