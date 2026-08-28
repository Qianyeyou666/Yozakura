package gq.yozakura.ui.engine.text;

import java.awt.Font;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Multi-page, lazy glyph atlas. All methods are render-thread confined. */
public final class GlyphAtlas {
    private final int pageWidth;
    private final int pageHeight;
    private final int padding;
    private final GlyphTextureBackend backend;
    private final GlyphRasterizer rasterizer;
    private final int maxPages;
    private final Map<GlyphKey, AtlasGlyph> glyphs = new HashMap<GlyphKey, AtlasGlyph>();
    private final List<Page> pages = new ArrayList<Page>();
    private boolean disposed;

    public GlyphAtlas(int pageWidth, int pageHeight, int padding,
                      GlyphTextureBackend backend, GlyphRasterizer rasterizer) {
        this(pageWidth, pageHeight, padding, 8, backend, rasterizer);
    }

    public GlyphAtlas(int pageWidth, int pageHeight, int padding, int maxPages,
                      GlyphTextureBackend backend, GlyphRasterizer rasterizer) {
        if (backend == null || rasterizer == null) {
            throw new IllegalArgumentException("backend and rasterizer must not be null");
        }
        if (maxPages <= 0) throw new IllegalArgumentException("maxPages must be positive");
        // Validate dimensions and padding through the same packer used by every page.
        new GlyphPagePacker(pageWidth, pageHeight, padding);
        this.pageWidth = pageWidth;
        this.pageHeight = pageHeight;
        this.padding = padding;
        this.backend = backend;
        this.rasterizer = rasterizer;
        this.maxPages = maxPages;
    }

    public AtlasGlyph glyph(Font font, int codePoint) {
        ensureOpen();
        if (font == null || !Character.isValidCodePoint(codePoint)) {
            throw new IllegalArgumentException("valid font and codePoint are required");
        }
        GlyphKey key = new GlyphKey(font, codePoint);
        AtlasGlyph cached = glyphs.get(key);
        if (cached != null) {
            return cached;
        }
        GlyphBitmap bitmap = rasterizer.rasterize(font, codePoint);
        AtlasGlyph created;
        if (bitmap.width() == 0 || bitmap.height() == 0) {
            created = new AtlasGlyph(0, 0, 0, 0, 0, 0, 0,
                    bitmap.advance(), bitmap.bearingX(), bitmap.bearingY());
        } else {
            Placement placement = allocate(bitmap.width(), bitmap.height());
            backend.uploadAlpha(placement.page.textureId, placement.slot.x(), placement.slot.y(),
                    bitmap.width(), bitmap.height(), bitmap.alpha());
            float u0 = placement.slot.x() / (float) pageWidth;
            float v0 = placement.slot.y() / (float) pageHeight;
            float u1 = (placement.slot.x() + bitmap.width()) / (float) pageWidth;
            float v1 = (placement.slot.y() + bitmap.height()) / (float) pageHeight;
            created = new AtlasGlyph(placement.page.textureId, u0, v0, u1, v1,
                    bitmap.width(), bitmap.height(), bitmap.advance(),
                    bitmap.bearingX(), bitmap.bearingY());
        }
        glyphs.put(key, created);
        return created;
    }

    public int pageCount() { return pages.size(); }
    public int glyphCount() { return glyphs.size(); }

    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        for (int i = 0; i < pages.size(); i++) {
            backend.deletePage(pages.get(i).textureId);
        }
        pages.clear();
        glyphs.clear();
    }

    private Placement allocate(int width, int height) {
        for (int i = 0; i < pages.size(); i++) {
            Page page = pages.get(i);
            GlyphPagePacker.Slot slot = page.packer.allocate(width, height);
            if (slot != null) {
                return new Placement(page, slot);
            }
        }
        if (pages.size() >= maxPages) {
            throw new IllegalStateException("glyph atlas page limit reached (" + maxPages
                    + ") while allocating " + width + "x" + height);
        }
        Page page = new Page(backend.createPage(pageWidth, pageHeight),
                new GlyphPagePacker(pageWidth, pageHeight, padding));
        pages.add(page);
        GlyphPagePacker.Slot slot = page.packer.allocate(width, height);
        if (slot == null) {
            // Delete the unusable page immediately; keeping it would leak a GPU texture.
            pages.remove(pages.size() - 1);
            backend.deletePage(page.textureId);
            throw new IllegalArgumentException("glyph " + width + "x" + height
                    + " does not fit atlas page " + pageWidth + "x" + pageHeight);
        }
        return new Placement(page, slot);
    }

    private void ensureOpen() {
        if (disposed) {
            throw new IllegalStateException("glyph atlas has been disposed");
        }
    }

    private static final class Page {
        private final int textureId;
        private final GlyphPagePacker packer;

        private Page(int textureId, GlyphPagePacker packer) {
            if (textureId <= 0) {
                throw new IllegalStateException("texture backend returned invalid page id: " + textureId);
            }
            this.textureId = textureId;
            this.packer = packer;
        }
    }

    private static final class Placement {
        private final Page page;
        private final GlyphPagePacker.Slot slot;

        private Placement(Page page, GlyphPagePacker.Slot slot) {
            this.page = page;
            this.slot = slot;
        }
    }

    private static final class GlyphKey {
        private final Font font;
        private final int codePoint;

        private GlyphKey(Font font, int codePoint) {
            this.font = font;
            this.codePoint = codePoint;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof GlyphKey)) return false;
            GlyphKey other = (GlyphKey) value;
            return codePoint == other.codePoint && font.equals(other.font);
        }

        @Override
        public int hashCode() {
            return 31 * font.hashCode() + codePoint;
        }
    }
}
