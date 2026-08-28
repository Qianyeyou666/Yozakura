package gq.yozakura.ui.engine.text;

import org.junit.Test;

import java.awt.Font;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class FontManagerTest {
    @Test
    public void cachesDerivedFacesAndUsesExplicitFallbackChain() {
        FontManager manager = new FontManager();
        manager.register("Inter", new Font("Dialog", Font.PLAIN, 1), null);
        manager.addFallback("Inter");

        Font first = manager.resolve("MissingFamily", false, 'A', 14.0F);
        Font second = manager.resolve("MissingFamily", false, 'A', 14.0F);

        assertSame(first, second);
        assertEquals(14.0F, first.getSize2D(), 0.001F);
    }

    @Test
    public void reusesDerivedFaceAcrossCodePointsSupportedByTheSameBaseFont() {
        FontManager manager = new FontManager();
        manager.register("Inter", new Font("Dialog", Font.PLAIN, 1), null);

        Font latin = manager.resolve("Inter", false, 'A', 14.0F);
        Font digit = manager.resolve("Inter", false, '7', 14.0F);

        assertSame(latin, digit);
    }

    @Test(expected = IllegalStateException.class)
    public void reportsMissingGlyphWhenNoRegisteredFaceCanDisplayIt() {
        new FontManager().resolve("Missing", false, 'A', 14.0F);
    }

    @Test
    public void explicitSystemCompositeFallbackCoversChineseUiLabels() {
        FontManager manager = new FontManager();
        manager.register("System CJK", new Font("Dialog", Font.PLAIN, 1), null);
        manager.addFallback("System CJK");

        Font resolved = manager.resolve("Inter", false, 0x6218, 14.0F);

        assertEquals(true, resolved.canDisplay(0x6218));
    }
}
