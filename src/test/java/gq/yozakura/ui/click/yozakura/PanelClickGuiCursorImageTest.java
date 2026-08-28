package gq.yozakura.ui.click.yozakura;

import org.junit.Test;

import java.io.InputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PanelClickGuiCursorImageTest {
    @Test
    public void outlinedArrowLoadsTheBreezeXBlackPngAndHotspot() {
        PanelClickGuiCursorImage image = PanelClickGuiCursorImage.outlinedArrow();

        assertEquals(32, image.width());
        assertEquals(32, image.height());
        assertEquals(9, image.hotspotX());
        assertEquals(4, image.hotspotY());
        assertEquals(27, image.lwjglHotspotY());
        assertTrue(isWhite(image.argbAt(9, 4)));
        assertTrue(isBlack(image.argbAt(12, 12)));
        assertEquals(0, image.argbAt(0, 0));
        assertEquals(0, image.argbAt(31, 31));
    }

    @Test
    public void breezeXBlackPngIsBundledAsAClasspathResource() {
        InputStream stream = PanelClickGuiCursorImageTest.class.getResourceAsStream(
                "/assets/yozakura/ui/cursor/breezex-black-left-ptr.png");
        assertNotNull(stream);
    }

    @Test
    public void outlinedArrowPreservesSemiTransparentAntialiasedEdges() {
        PanelClickGuiCursorImage image = PanelClickGuiCursorImage.outlinedArrow();
        int semiTransparentPixels = 0;

        for (int y = 0; y < image.height(); y++) {
            for (int x = 0; x < image.width(); x++) {
                int alpha = image.argbAt(x, y) >>> 24;
                if (alpha > 0 && alpha < 255) {
                    semiTransparentPixels++;
                }
            }
        }

        assertTrue(semiTransparentPixels >= 100);
    }

    @Test
    public void nativeBufferUsesLwjglLowerLeftOrigin() {
        PanelClickGuiCursorImage image = PanelClickGuiCursorImage.outlinedArrow();
        int[] nativePixels = image.nativeArgbPixels();

        assertEquals(image.argbAt(9, 4), nativePixels[(image.height() - 1 - 4) * image.width() + 9]);
        assertEquals(image.argbAt(12, 20), nativePixels[(image.height() - 1 - 20) * image.width() + 12]);
    }

    private static boolean isWhite(int argb) {
        return (argb >>> 24) > 0
                && ((argb >>> 16) & 255) >= 220
                && ((argb >>> 8) & 255) >= 220
                && (argb & 255) >= 220;
    }

    private static boolean isBlack(int argb) {
        return (argb >>> 24) > 0
                && ((argb >>> 16) & 255) <= 24
                && ((argb >>> 8) & 255) <= 24
                && (argb & 255) <= 24;
    }
}
