package gq.yozakura.ui.engine.text;

import org.junit.Test;

import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class GlyphAtlasTest {
    @Test
    public void uploadsMissingGlyphOnceAndReusesIt() {
        FakeBackend backend = new FakeBackend();
        GlyphAtlas atlas = new GlyphAtlas(64, 64, 1, backend, new GlyphRasterizer());
        Font font = new Font("Dialog", Font.PLAIN, 18);

        AtlasGlyph first = atlas.glyph(font, 'A');
        AtlasGlyph second = atlas.glyph(font, 'A');

        assertSame(first, second);
        assertEquals(1, backend.uploads);
        assertEquals(1, backend.created.size());
        assertTrue(first.textureId() > 0);
    }

    @Test
    public void disposalDeletesPagesExactlyOnce() {
        FakeBackend backend = new FakeBackend();
        GlyphAtlas atlas = new GlyphAtlas(64, 64, 1, backend, new GlyphRasterizer());
        atlas.glyph(new Font("Dialog", Font.PLAIN, 18), 'A');

        atlas.dispose();
        atlas.dispose();

        assertEquals(1, backend.deleted.size());
    }

    private static final class FakeBackend implements GlyphTextureBackend {
        private int nextId = 1;
        private int uploads;
        private final List<Integer> created = new ArrayList<Integer>();
        private final List<Integer> deleted = new ArrayList<Integer>();

        @Override
        public int createPage(int width, int height) {
            int id = nextId++;
            created.add(id);
            return id;
        }

        @Override
        public void uploadAlpha(int textureId, int x, int y, int width, int height, byte[] alpha) {
            uploads++;
            assertEquals(width * height, alpha.length);
        }

        @Override
        public void deletePage(int textureId) {
            deleted.add(textureId);
        }
    }
}
