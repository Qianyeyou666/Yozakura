package gq.yozakura.engine.font;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

final class UnicodeGlyphCache {
    static final int GLYPH_SCALE = 3;
    static final int GLYPH_PADDING = 8;
    static final int GLYPH_INSET_X = 2;
    static final int GLYPH_INSET_Y = 2;

    private static final int CACHE_LIMIT = 768;
    private static final int TRANSPARENT_WHITE = 0x00FFFFFF;
    private static final Font SYSTEM_FALLBACK = new Font("Dialog", Font.PLAIN, 16);

    private final Font primary;
    private final Font[] fallbacks;
    private final boolean antiAlias;
    private final boolean fractionalMetrics;
    private final Map<Long, Glyph> glyphs = new LinkedHashMap<Long, Glyph>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Glyph> eldest) {
            if (size() <= CACHE_LIMIT) {
                return false;
            }
            eldest.getValue().deleteTexture();
            return true;
        }
    };

    UnicodeGlyphCache(Font primary, Font[] fallbacks, boolean antiAlias, boolean fractionalMetrics) {
        this.primary = primary;
        this.fallbacks = normalizedFallbacks(primary, fallbacks);
        this.antiAlias = antiAlias;
        this.fractionalMetrics = fractionalMetrics;
    }

    Glyph glyph(int codePoint, int style) {
        int normalizedStyle = style & (Font.BOLD | Font.ITALIC);
        long key = ((long) normalizedStyle << 32) | (codePoint & 0xFFFFFFFFL);
        Glyph cached = glyphs.get(key);
        if (cached != null) {
            return cached;
        }
        Glyph generated = createGlyph(codePoint, normalizedStyle);
        glyphs.put(key, generated);
        return generated;
    }

    void clear() {
        for (Glyph glyph : glyphs.values()) {
            glyph.deleteTexture();
        }
        glyphs.clear();
    }

    Font[] fallbackFonts() {
        return fallbacks.clone();
    }

    static Font selectDisplayFont(int codePoint, Font primary, Font[] fallbacks) {
        if (primary != null && primary.canDisplay(codePoint)) {
            return primary;
        }
        if (fallbacks != null) {
            for (Font fallback : fallbacks) {
                if (fallback != null && fallback.canDisplay(codePoint)) {
                    return fallback;
                }
            }
        }
        return primary != null ? primary : SYSTEM_FALLBACK;
    }

    private Glyph createGlyph(int codePoint, int style) {
        float size = Math.max(1.0f, primary.getSize2D());
        Font styledPrimary = primary.deriveFont(style, size);
        Font[] styledFallbacks = new Font[fallbacks.length];
        for (int i = 0; i < fallbacks.length; i++) {
            styledFallbacks[i] = fallbacks[i].deriveFont(style, size);
        }
        Font selected = selectDisplayFont(codePoint, styledPrimary, styledFallbacks);
        Font renderFont = selected.deriveFont(style, size * GLYPH_SCALE);
        String text = new String(Character.toChars(codePoint));

        BufferedImage metricsImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D metricsGraphics = metricsImage.createGraphics();
        setupGraphics(metricsGraphics, renderFont);
        FontMetrics metrics = metricsGraphics.getFontMetrics();
        Rectangle2D bounds = metrics.getStringBounds(text, metricsGraphics);
        int glyphWidth = Math.max(1, (int) Math.ceil(bounds.getWidth() / GLYPH_SCALE));
        int glyphHeight = Math.max(1, (int) Math.ceil(bounds.getHeight() / GLYPH_SCALE));
        int width = glyphWidth + GLYPH_PADDING;
        int height = glyphHeight + GLYPH_INSET_Y * 2;
        int ascent = metrics.getAscent();
        metricsGraphics.dispose();

        BufferedImage image = new BufferedImage(
                Math.max(1, width * GLYPH_SCALE),
                Math.max(1, height * GLYPH_SCALE),
                BufferedImage.TYPE_INT_ARGB);
        Arrays.fill(((DataBufferInt) image.getRaster().getDataBuffer()).getData(), TRANSPARENT_WHITE);
        Graphics2D graphics = image.createGraphics();
        setupGraphics(graphics, renderFont);
        graphics.setColor(Color.WHITE);
        graphics.drawString(text,
                GLYPH_INSET_X * GLYPH_SCALE,
                GLYPH_INSET_Y * GLYPH_SCALE + ascent);
        graphics.dispose();

        return new Glyph(image, width, height, glyphWidth, GLYPH_INSET_Y, codePoint > ' ');
    }

    private void setupGraphics(Graphics2D graphics, Font font) {
        graphics.setFont(font);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
                RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                fractionalMetrics ? RenderingHints.VALUE_FRACTIONALMETRICS_ON
                        : RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                antiAlias ? RenderingHints.VALUE_TEXT_ANTIALIAS_ON : RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                antiAlias ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_LCD_CONTRAST, 140);
    }

    private static Font[] normalizedFallbacks(Font primary, Font[] requested) {
        int requestedLength = requested == null ? 0 : requested.length;
        Font[] normalized = new Font[requestedLength + 1];
        int count = 0;
        if (requested != null) {
            for (Font fallback : requested) {
                if (fallback != null && fallback != primary) {
                    normalized[count++] = fallback;
                }
            }
        }
        normalized[count++] = SYSTEM_FALLBACK.deriveFont(Font.PLAIN,
                primary == null ? 16.0f : Math.max(1.0f, primary.getSize2D()));
        return Arrays.copyOf(normalized, count);
    }

    static final class Glyph {
        final int width;
        final int height;
        final int advance;
        final int yOffset;
        final boolean drawable;
        private final BufferedImage image;
        private DynamicTexture texture;

        private Glyph(BufferedImage image, int width, int height, int advance, int yOffset, boolean drawable) {
            this.image = image;
            this.width = width;
            this.height = height;
            this.advance = advance;
            this.yOffset = yOffset;
            this.drawable = drawable;
        }

        DynamicTexture texture() {
            if (texture == null) {
                texture = new DynamicTexture(image);
                configureTexture(texture);
            }
            return texture;
        }

        private void deleteTexture() {
            if (texture != null) {
                texture.deleteGlTexture();
                texture = null;
            }
        }

        private static void configureTexture(DynamicTexture texture) {
            int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            setActiveTexture(GL13.GL_TEXTURE0);
            int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture.getGlTextureId());
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            setActiveTexture(activeTexture);
        }

        private static void setActiveTexture(int textureUnit) {
            try {
                GlStateManager.setActiveTexture(textureUnit);
            } catch (Throwable ignored) {
                GL13.glActiveTexture(textureUnit);
            }
            GL13.glActiveTexture(textureUnit);
        }
    }
}
