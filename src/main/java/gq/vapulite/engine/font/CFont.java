package gq.vapulite.engine.font;

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

public class CFont {
    private static final int ATLAS_SCALE = 2;
    private static final int ATLAS_WIDTH = 1024;
    private static final int GLYPH_PADDING = 8;
    private static final int GLYPH_INSET_X = 2;
    private static final int GLYPH_INSET_Y = 2;
    private static final int TRANSPARENT_WHITE = 0x00FFFFFF;
    protected static final int FONT_TEXTURE_FILTER = GL11.GL_LINEAR;
    protected CharData[] charData = new CharData[256];
    protected Font font;
    protected boolean antiAlias;
    protected boolean fractionalMetrics;
    protected int fontHeight = -1;
    protected int charOffset = 0;
    protected DynamicTexture tex;

    public CFont(Font font, boolean antiAlias, boolean fractionalMetrics) {
        this.font = font;
        this.antiAlias = antiAlias;
        this.fractionalMetrics = fractionalMetrics;
        this.tex = setupTexture(font, antiAlias, fractionalMetrics, charData);
    }

    protected DynamicTexture setupTexture(Font font, boolean antiAlias, boolean fractionalMetrics, CharData[] chars) {
        BufferedImage image = generateFontImage(font, antiAlias, fractionalMetrics, chars);
        try {
            DynamicTexture texture = new DynamicTexture(image);
            configureTexture(texture);
            return texture;
        } catch (Exception exception) {
            exception.printStackTrace();
            return null;
        }
    }

    protected BufferedImage generateFontImage(Font font, boolean antiAlias, boolean fractionalMetrics, CharData[] chars) {
        Font renderFont = scaledFont(font);
        BufferedImage metricsImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D metricsGraphics = metricsImage.createGraphics();
        setupGraphics(metricsGraphics, renderFont, antiAlias, fractionalMetrics);
        FontMetrics fontMetrics = metricsGraphics.getFontMetrics();
        int charHeight = 0;
        int positionX = 0;
        int positionY = ATLAS_SCALE;
        int measuredFontHeight = -1;

        for (int i = 0; i < chars.length; i++) {
            char character = (char) i;
            CharData data = new CharData();
            Rectangle2D bounds = fontMetrics.getStringBounds(String.valueOf(character), metricsGraphics);
            int glyphWidth = Math.max(1, (int) Math.ceil(bounds.getWidth() / ATLAS_SCALE));
            int glyphHeight = Math.max(1, (int) Math.ceil(bounds.getHeight() / ATLAS_SCALE));
            data.width = glyphWidth + GLYPH_PADDING;
            data.height = glyphHeight + GLYPH_INSET_Y * 2;
            data.advance = glyphWidth;
            data.yOffset = GLYPH_INSET_Y;
            data.srcWidth = data.width * ATLAS_SCALE;
            data.srcHeight = data.height * ATLAS_SCALE;
            data.drawable = character > ' ';
            if (positionX + data.srcWidth >= ATLAS_WIDTH) {
                positionX = 0;
                positionY += charHeight;
                charHeight = 0;
            }
            if (data.srcHeight > charHeight) {
                charHeight = data.srcHeight;
            }
            data.storedX = positionX;
            data.storedY = positionY;
            if (glyphHeight > measuredFontHeight) {
                measuredFontHeight = glyphHeight;
            }
            chars[i] = data;
            positionX += data.srcWidth;
        }

        int atlasHeight = Math.max(1, positionY + Math.max(charHeight, fontMetrics.getAscent()));
        metricsGraphics.dispose();

        BufferedImage bufferedImage = new BufferedImage(ATLAS_WIDTH, atlasHeight, BufferedImage.TYPE_INT_ARGB);
        fillTransparentWhite(bufferedImage);
        Graphics2D graphics = bufferedImage.createGraphics();
        setupGraphics(graphics, renderFont, antiAlias, fractionalMetrics);
        graphics.setColor(Color.WHITE);

        FontMetrics renderMetrics = graphics.getFontMetrics();
        for (int i = 0; i < chars.length; i++) {
            CharData data = chars[i];
            data.atlasWidth = ATLAS_WIDTH;
            data.atlasHeight = atlasHeight;
            data.u1 = (float) data.storedX / (float) ATLAS_WIDTH;
            data.v1 = (float) data.storedY / (float) atlasHeight;
            data.u2 = (float) (data.storedX + data.srcWidth) / (float) ATLAS_WIDTH;
            data.v2 = (float) (data.storedY + data.srcHeight) / (float) atlasHeight;
            graphics.drawString(String.valueOf((char) i),
                    data.storedX + GLYPH_INSET_X * ATLAS_SCALE,
                    data.storedY + GLYPH_INSET_Y * ATLAS_SCALE + renderMetrics.getAscent());
        }

        graphics.dispose();
        fontHeight = measuredFontHeight;
        return bufferedImage;
    }

    public void drawChar(CharData[] chars, char character, float x, float y) throws ArrayIndexOutOfBoundsException {
        if (character >= chars.length) {
            return;
        }
        CharData data = chars[character];
        drawQuad(x, y - data.yOffset, data.width, data.height, data.u1, data.v1, data.u2, data.v2);
    }

    protected void drawQuad(float x, float y, float width, float height, float srcX, float srcY,
                            float srcWidth, float srcHeight, float atlasWidth, float atlasHeight) {
        float renderSRCX = srcX / atlasWidth;
        float renderSRCY = srcY / atlasHeight;
        float renderSRCWidth = srcWidth / atlasWidth;
        float renderSRCHeight = srcHeight / atlasHeight;
        GL11.glTexCoord2f(renderSRCX + renderSRCWidth, renderSRCY);
        GL11.glVertex2d(x + width, y);
        GL11.glTexCoord2f(renderSRCX, renderSRCY);
        GL11.glVertex2d(x, y);
        GL11.glTexCoord2f(renderSRCX, renderSRCY + renderSRCHeight);
        GL11.glVertex2d(x, y + height);
        GL11.glTexCoord2f(renderSRCX, renderSRCY + renderSRCHeight);
        GL11.glVertex2d(x, y + height);
        GL11.glTexCoord2f(renderSRCX + renderSRCWidth, renderSRCY + renderSRCHeight);
        GL11.glVertex2d(x + width, y + height);
        GL11.glTexCoord2f(renderSRCX + renderSRCWidth, renderSRCY);
        GL11.glVertex2d(x + width, y);
    }

    protected void drawQuad(float x, float y, float width, float height, float u1, float v1, float u2, float v2) {
        float right = x + width;
        float bottom = y + height;
        GL11.glTexCoord2f(u2, v1);
        GL11.glVertex2f(right, y);
        GL11.glTexCoord2f(u1, v1);
        GL11.glVertex2f(x, y);
        GL11.glTexCoord2f(u1, v2);
        GL11.glVertex2f(x, bottom);
        GL11.glTexCoord2f(u2, v2);
        GL11.glVertex2f(right, bottom);
    }

    public int getStringHeight(String text) {
        return getHeight();
    }

    public int getHeight() {
        return Math.max(1, (fontHeight - 8) / 2);
    }

    public int getStringWidth(String text) {
        if (text == null) {
            return 0;
        }
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character < charData.length) {
                width += charData[character].advance + charOffset;
            }
        }
        return width / 2;
    }

    public boolean isAntiAlias() {
        return antiAlias;
    }

    public void setAntiAlias(boolean antiAlias) {
        if (this.antiAlias != antiAlias) {
            this.antiAlias = antiAlias;
            this.tex = setupTexture(font, antiAlias, fractionalMetrics, charData);
        }
    }

    public boolean isFractionalMetrics() {
        return fractionalMetrics;
    }

    public void setFractionalMetrics(boolean fractionalMetrics) {
        if (this.fractionalMetrics != fractionalMetrics) {
            this.fractionalMetrics = fractionalMetrics;
            this.tex = setupTexture(font, antiAlias, fractionalMetrics, charData);
        }
    }

    public Font getFont() {
        return font;
    }

    public void setFont(Font font) {
        this.font = font;
        this.tex = setupTexture(font, antiAlias, fractionalMetrics, charData);
    }

    private void configureTexture(DynamicTexture texture) {
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        setActiveTexture(GL13.GL_TEXTURE0);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int textureId = texture.getGlTextureId();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        applyFontTextureParameters();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
        setActiveTexture(activeTexture);
    }

    private void setupGraphics(Graphics2D graphics, Font font, boolean antiAlias, boolean fractionalMetrics) {
        graphics.setFont(font);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                fractionalMetrics ? RenderingHints.VALUE_FRACTIONALMETRICS_ON : RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                antiAlias ? RenderingHints.VALUE_TEXT_ANTIALIAS_ON : RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                antiAlias ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_LCD_CONTRAST, 140);
    }

    protected void applyFontTextureParameters() {
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, FONT_TEXTURE_FILTER);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, FONT_TEXTURE_FILTER);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
    }

    private void setActiveTexture(int textureUnit) {
        try {
            GlStateManager.setActiveTexture(textureUnit);
        } catch (Throwable ignored) {
            GL13.glActiveTexture(textureUnit);
        }
        GL13.glActiveTexture(textureUnit);
    }

    private Font scaledFont(Font font) {
        return font.deriveFont(font.getStyle(), font.getSize2D() * ATLAS_SCALE);
    }

    private void fillTransparentWhite(BufferedImage image) {
        int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        Arrays.fill(pixels, TRANSPARENT_WHITE);
    }

    protected class CharData {
        public int width;
        public int height;
        public int advance;
        public int yOffset;
        public int storedX;
        public int storedY;
        public int srcWidth;
        public int srcHeight;
        public int atlasWidth;
        public int atlasHeight;
        public boolean drawable;
        public float u1;
        public float v1;
        public float u2;
        public float v2;
    }
}
