package gq.yozakura.ui.engine.text;

/**
 * Deterministic row packer used by a single glyph atlas page.
 * Padding is reserved around every allocated glyph to prevent texture bleed.
 */
public final class GlyphPagePacker {
    private final int pageWidth;
    private final int pageHeight;
    private final int padding;
    private int cursorX;
    private int cursorY;
    private int rowHeight;

    public GlyphPagePacker(int pageWidth, int pageHeight, int padding) {
        if (pageWidth <= 0 || pageHeight <= 0) {
            throw new IllegalArgumentException("page dimensions must be positive");
        }
        if (padding < 0 || padding * 2 >= pageWidth || padding * 2 >= pageHeight) {
            throw new IllegalArgumentException("padding does not fit inside the page: " + padding);
        }
        this.pageWidth = pageWidth;
        this.pageHeight = pageHeight;
        this.padding = padding;
        this.cursorX = padding;
        this.cursorY = padding;
    }

    public Slot allocate(int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("glyph dimensions must not be negative");
        }
        if (width == 0 || height == 0) {
            return new Slot(cursorX, cursorY, width, height);
        }
        int rightLimit = pageWidth - padding;
        int bottomLimit = pageHeight - padding;
        if (width > rightLimit - padding || height > bottomLimit - padding) {
            return null;
        }
        if (cursorX + width > rightLimit) {
            cursorX = padding;
            cursorY += rowHeight + padding;
            rowHeight = 0;
        }
        if (cursorY + height > bottomLimit) {
            return null;
        }
        Slot slot = new Slot(cursorX, cursorY, width, height);
        cursorX += width + padding;
        rowHeight = Math.max(rowHeight, height);
        return slot;
    }

    public static final class Slot {
        private final int x;
        private final int y;
        private final int width;
        private final int height;

        private Slot(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public int x() { return x; }
        public int y() { return y; }
        public int width() { return width; }
        public int height() { return height; }
    }
}
