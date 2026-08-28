package gq.yozakura.ui.engine.text;

/** Immutable glyph metrics and normalized texture coordinates in one atlas page. */
public final class AtlasGlyph {
    private final int textureId;
    private final float u0;
    private final float v0;
    private final float u1;
    private final float v1;
    private final int width;
    private final int height;
    private final float advance;
    private final int bearingX;
    private final int bearingY;

    AtlasGlyph(int textureId, float u0, float v0, float u1, float v1,
               int width, int height, float advance, int bearingX, int bearingY) {
        this.textureId = textureId;
        this.u0 = u0;
        this.v0 = v0;
        this.u1 = u1;
        this.v1 = v1;
        this.width = width;
        this.height = height;
        this.advance = advance;
        this.bearingX = bearingX;
        this.bearingY = bearingY;
    }

    public int textureId() { return textureId; }
    public float u0() { return u0; }
    public float v0() { return v0; }
    public float u1() { return u1; }
    public float v1() { return v1; }
    public int width() { return width; }
    public int height() { return height; }
    public float advance() { return advance; }
    public int bearingX() { return bearingX; }
    public int bearingY() { return bearingY; }
}
