package gq.yozakura.ui.engine.text;

import org.junit.Test;

import java.awt.Font;

import static org.junit.Assert.assertTrue;

public class GlyphRasterizerTest {
    @Test
    public void rasterizesAVisibleGlyphAndPreservesAdvanceForSpace() {
        GlyphRasterizer rasterizer = new GlyphRasterizer();
        Font font = new Font("Dialog", Font.PLAIN, 18);

        GlyphBitmap visible = rasterizer.rasterize(font, 'A');
        GlyphBitmap space = rasterizer.rasterize(font, ' ');

        assertTrue(visible.width() > 0);
        assertTrue(visible.height() > 0);
        assertTrue(visible.alpha().length == visible.width() * visible.height());
        assertTrue(visible.advance() > 0.0F);
        assertTrue(space.advance() > 0.0F);
    }
}
