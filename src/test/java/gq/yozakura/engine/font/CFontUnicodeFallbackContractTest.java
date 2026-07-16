package gq.yozakura.engine.font;

import org.junit.Test;

import java.awt.Font;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class CFontUnicodeFallbackContractTest {
    @Test
    public void selectsTheFirstFontThatCanRenderTheWholeCodePoint() {
        Font primary = new DisplayFont("primary", false);
        Font firstFallback = new DisplayFont("first", false);
        Font chineseFallback = new DisplayFont("chinese", true);

        Font selected = UnicodeGlyphCache.selectDisplayFont(
                0x754C,
                primary,
                new Font[]{firstFallback, chineseFallback});

        assertSame(chineseFallback, selected);
    }

    @Test
    public void keepsThePrimaryFontWhenItContainsTheGlyph() {
        Font primary = new DisplayFont("primary", true);

        Font selected = UnicodeGlyphCache.selectDisplayFont(
                0x754C,
                primary,
                new Font[]{new DisplayFont("fallback", true)});

        assertSame(primary, selected);
    }

    @Test
    public void buildsARealSupersampledUnicodeGlyphForTheMixedTextPath() {
        UnicodeGlyphCache cache = new UnicodeGlyphCache(
                new Font("Dialog", Font.PLAIN, 16),
                new Font[]{new Font("Dialog", Font.PLAIN, 16)}, true, true);

        UnicodeGlyphCache.Glyph glyph = cache.glyph(0x754C, Font.PLAIN);

        assertTrue(glyph.drawable);
        assertTrue(glyph.advance > 0);
        assertTrue(UnicodeGlyphCache.GLYPH_SCALE >= 2);
    }

    @Test
    public void customRendererOwnsUnicodeRenderingAndGlowReplay() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/engine/font/CFontRenderer.java")), StandardCharsets.UTF_8);

        assertFalse("constructor fallbacks must not be discarded", source.contains("ignoredFallbacks"));
        assertFalse("CJK must not fall back to Minecraft's low-resolution unicode pages",
                source.contains("Minecraft.getMinecraft().fontRendererObj"));
        assertFalse("unicode strings must use the same mixed-glyph render loop",
                source.contains("drawUnicodeFallback"));
        assertFalse("glow queuing must not skip CJK text",
                source.contains("if (requiresUnicodeFallback(text))"));
        assertTrue(source.contains("UnicodeGlyphCache"));
        assertTrue(source.contains("unicodeGlyphCache.glyph"));
    }

    private static final class DisplayFont extends Font {
        private final boolean display;

        private DisplayFont(String name, boolean display) {
            super(name, Font.PLAIN, 16);
            this.display = display;
        }

        @Override
        public boolean canDisplay(int codePoint) {
            return display;
        }
    }
}
