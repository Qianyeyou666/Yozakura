package gq.vapulite.font;

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

public class CFont {
    private static final int ATLAS_WIDTH = 512;
    private static final int GLYPH_PADDING = 8;
    protected static final int FONT_TEXTURE_FILTER = GL11.GL_NEAREST;
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
        BufferedImage metricsImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D metricsGraphics = metricsImage.createGraphics();
        setupGraphics(metricsGraphics, font, antiAlias, fractionalMetrics);
        FontMetrics fontMetrics = metricsGraphics.getFontMetrics();
        int charHeight = 0;
        int positionX = 0;
        int positionY = 1;
        int measuredFontHeight = -1;

        for (int i = 0; i < chars.length; i++) {
            char character = (char) i;
            CharData data = new CharData();
            Rectangle2D bounds = fontMetrics.getStringBounds(String.valueOf(character), metricsGraphics);
            data.width = Math.max(1, bounds.getBounds().width + GLYPH_PADDING);
            data.height = bounds.getBounds().height;
            if (positionX + data.width >= ATLAS_WIDTH) {
                positionX = 0;
                positionY += charHeight;
                charHeight = 0;
            }
            if (data.height > charHeight) {
                charHeight = data.height;
            }
            data.storedX = positionX;
            data.storedY = positionY;
            if (data.height > measuredFontHeight) {
                measuredFontHeight = data.height;
            }
            chars[i] = data;
            positionX += data.width;
        }

        int atlasHeight = Math.max(1, positionY + Math.max(charHeight, fontMetrics.getAscent()));
        metricsGraphics.dispose();

        BufferedImage bufferedImage = new BufferedImage(ATLAS_WIDTH, atlasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = bufferedImage.createGraphics();
        setupGraphics(graphics, font, antiAlias, fractionalMetrics);
        graphics.setColor(new Color(255, 255, 255, 0));
        graphics.fillRect(0, 0, ATLAS_WIDTH, atlasHeight);
        graphics.setColor(Color.WHITE);

        FontMetrics renderMetrics = graphics.getFontMetrics();
        for (int i = 0; i < chars.length; i++) {
            CharData data = chars[i];
            data.atlasWidth = ATLAS_WIDTH;
            data.atlasHeight = atlasHeight;
            graphics.drawString(String.valueOf((char) i), data.storedX + 2, data.storedY + renderMetrics.getAscent());
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
        drawQuad(x, y, data.width, data.height, data.storedX, data.storedY, data.width, data.height,
                data.atlasWidth, data.atlasHeight);
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
                width += charData[character].width - 8 + charOffset;
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
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                fractionalMetrics ? RenderingHints.VALUE_FRACTIONALMETRICS_ON : RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                antiAlias ? RenderingHints.VALUE_TEXT_ANTIALIAS_ON : RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                antiAlias ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
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

    protected class CharData {
        public int width;
        public int height;
        public int storedX;
        public int storedY;
        public int atlasWidth;
        public int atlasHeight;
    }
}
