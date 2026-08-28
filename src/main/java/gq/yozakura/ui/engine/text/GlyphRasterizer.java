package gq.yozakura.ui.engine.text;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphMetrics;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.Arrays;

/** Rasterizes a missing glyph once before it is uploaded into an atlas page. */
public final class GlyphRasterizer {
    private static final FontRenderContext FONT_CONTEXT = new FontRenderContext(null, true, false);

    public GlyphBitmap rasterize(Font font, char character) {
        return rasterize(font, (int) character);
    }

    public GlyphBitmap rasterize(Font font, int codePoint) {
        if (font == null) {
            throw new IllegalArgumentException("font must not be null");
        }
        if (!Character.isValidCodePoint(codePoint)) {
            throw new IllegalArgumentException("invalid Unicode code point: " + codePoint);
        }
        GlyphVector vector = font.createGlyphVector(FONT_CONTEXT, Character.toChars(codePoint));
        GlyphMetrics metrics = vector.getGlyphMetrics(0);
        Rectangle bounds = vector.getGlyphPixelBounds(0, FONT_CONTEXT, 0.0F, 0.0F);
        int width = Math.max(0, bounds.width);
        int height = Math.max(0, bounds.height);
        if (width == 0 || height == 0) {
            return new GlyphBitmap(new byte[0], 0, 0, metrics.getAdvanceX(),
                    bounds.x, -bounds.y);
        }

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_GASP);
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                    RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
            graphics.setColor(Color.WHITE);
            graphics.drawGlyphVector(vector, -bounds.x, -bounds.y);
        } finally {
            graphics.dispose();
        }
        byte[] pixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        return new GlyphBitmap(Arrays.copyOf(pixels, pixels.length), width, height,
                metrics.getAdvanceX(), bounds.x, -bounds.y);
    }
}
