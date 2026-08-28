package gq.yozakura.ui.engine.text;

import java.util.Arrays;

/** Immutable grayscale glyph bitmap and its baseline-relative metrics. */
public final class GlyphBitmap {
    private final byte[] alpha;
    private final int width;
    private final int height;
    private final float advance;
    private final int bearingX;
    private final int bearingY;

    public GlyphBitmap(byte[] alpha, int width, int height, float advance,
                       int bearingX, int bearingY) {
        if (alpha == null) {
            throw new IllegalArgumentException("alpha must not be null");
        }
        if (width < 0 || height < 0 || alpha.length != width * height) {
            throw new IllegalArgumentException("alpha length must equal width * height");
        }
        this.alpha = Arrays.copyOf(alpha, alpha.length);
        this.width = width;
        this.height = height;
        this.advance = advance;
        this.bearingX = bearingX;
        this.bearingY = bearingY;
    }

    public byte[] alpha() { return Arrays.copyOf(alpha, alpha.length); }
    public int width() { return width; }
    public int height() { return height; }
    public float advance() { return advance; }
    public int bearingX() { return bearingX; }
    public int bearingY() { return bearingY; }
}
