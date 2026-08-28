package gq.yozakura.engine.font;

import gq.yozakura.engine.render.glow.GlowProfile;
import gq.yozakura.engine.render.ui.RenderServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Renders Minecraft 1.8.9's own bitmap glyphs through Yozakura's text path.
 * The source pixels and vanilla advances stay unchanged; a supersampled copy
 * only gives scaled HUD transforms more texels to sample instead of replacing
 * the typeface with an AWT/TTF font.
 */
public final class MinecraftFontRenderer implements IResourceManagerReloadListener {
    private static final ResourceLocation ASCII_TEXTURE =
            new ResourceLocation("textures/font/ascii.png");
    private static final ResourceLocation GLYPH_SIZES =
            new ResourceLocation("font/glyph_sizes.bin");
    private static final String FORMAT_CODES = "0123456789abcdefklmnor";
    private static final int FONT_HEIGHT = 9;
    private static final int ATLAS_SCALE = 4;
    private static final int FONT_ATTRIB_MASK = GL11.GL_ENABLE_BIT
            | GL11.GL_COLOR_BUFFER_BIT
            | GL11.GL_CURRENT_BIT
            | GL11.GL_DEPTH_BUFFER_BIT
            | GL11.GL_HINT_BIT
            | GL11.GL_LINE_BIT
            | GL11.GL_TEXTURE_BIT;

    private final int[] charWidths = new int[256];
    private final byte[] glyphWidths = new byte[65536];
    private final int[] colorCodes = new int[32];
    private final Map<Integer, DynamicTexture> unicodeTextures =
            new HashMap<Integer, DynamicTexture>();
    private BufferedImage asciiImage;
    private DynamicTexture asciiTexture;
    private IResourceManager registeredManager;
    private boolean metricsReady;

    public MinecraftFontRenderer() {
        setupMinecraftColorCodes();
    }

    public float drawStringWithShadow(String text, double x, double y, int color) {
        float shadow = drawStringInternal(text, x + 1.0D, y + 1.0D,
                vanillaShadowColor(color), false);
        return Math.max(shadow, drawStringInternal(text, x, y, color, false));
    }

    public float drawString(String text, double x, double y, int color) {
        return drawStringInternal(text, x, y, color, false);
    }

    public float drawGlowString(String text, double x, double y, int glowColor,
                                float strength, GlowProfile profile) {
        RenderServices.glow().queueMinecraftText(
                this, text, x, y, glowColor, strength, profile);
        return (float) (x + getStringWidth(text));
    }

    public float drawStringForGlowMask(String text, double x, double y) {
        return drawStringInternal(text, x, y, 0xFFFFFFFF, true);
    }

    public int getStringWidth(String text) {
        if (text == null || text.length() == 0) {
            return 0;
        }
        ensureMetrics();
        int width = 0;
        boolean bold = false;
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character == '\u00a7' && i + 1 < text.length()) {
                int format = FORMAT_CODES.indexOf(
                        Character.toLowerCase(text.charAt(++i)));
                if (format < 16 || format == 21) {
                    bold = false;
                } else if (format == 17) {
                    bold = true;
                }
                continue;
            }
            int advance = charWidth(character);
            width += advance;
            if (bold && advance > 0) {
                width++;
            }
        }
        return width;
    }

    public int getHeight() {
        return FONT_HEIGHT;
    }

    @Override
    public void onResourceManagerReload(IResourceManager resourceManager) {
        releaseTextures();
        asciiImage = null;
        metricsReady = false;
    }

    private float drawStringInternal(String text, double x, double y,
                                     int color, boolean maskPass) {
        if (text == null || text.length() == 0) {
            return (float) x;
        }
        ensureMetrics();
        int originalColor = normalizeColor(color);
        int currentColor = originalColor;
        float cursorX = snap(x);
        float startX = cursorX;
        float cursorY = snap(y);
        boolean bold = false;
        boolean italic = false;
        boolean underline = false;
        boolean strikethrough = false;

        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        GL11.glPushAttrib(FONT_ATTRIB_MASK);
        try {
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(770, 771);
            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            applyColor(currentColor);

            for (int i = 0; i < text.length(); i++) {
                char character = text.charAt(i);
                if (character == '\u00a7' && i + 1 < text.length()) {
                    int format = FORMAT_CODES.indexOf(
                            Character.toLowerCase(text.charAt(++i)));
                    if (format < 16) {
                        bold = false;
                        italic = false;
                        underline = false;
                        strikethrough = false;
                        int index = format < 0 ? 15 : format;
                        currentColor = maskPass
                                ? 0xFFFFFFFF
                                : (originalColor & 0xFF000000) | colorCodes[index];
                    } else if (format == 17) {
                        bold = true;
                    } else if (format == 18) {
                        strikethrough = true;
                    } else if (format == 19) {
                        underline = true;
                    } else if (format == 20) {
                        italic = true;
                    } else if (format == 21) {
                        bold = false;
                        italic = false;
                        underline = false;
                        strikethrough = false;
                        currentColor = originalColor;
                    }
                    applyColor(currentColor);
                    continue;
                }
                if (character == '\n') {
                    cursorX = startX;
                    cursorY += FONT_HEIGHT;
                    continue;
                }

                int advance = charWidth(character);
                if (advance <= 0) {
                    continue;
                }
                if (character != ' ') {
                    drawGlyph(character, cursorX, cursorY, italic);
                    if (bold) {
                        drawGlyph(character, cursorX + 1.0F, cursorY, italic);
                    }
                }
                int totalAdvance = advance + (bold ? 1 : 0);
                if (strikethrough) {
                    drawLine(cursorX, cursorY + 4.0F,
                            cursorX + totalAdvance, cursorY + 4.0F);
                }
                if (underline) {
                    drawLine(cursorX, cursorY + 8.0F,
                            cursorX + totalAdvance, cursorY + 8.0F);
                }
                cursorX += totalAdvance;
            }
        } finally {
            GL11.glPopAttrib();
            gq.yozakura.engine.render.GLStateManager.syncToCurrent();
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
            GlStateManager.bindTexture(previousTexture);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.setActiveTexture(previousActiveTexture);
        }
        return cursorX;
    }

    private void drawGlyph(char character, float x, float y, boolean italic) {
        if (isAscii(character)) {
            DynamicTexture texture = asciiTexture();
            if (texture == null) {
                return;
            }
            GlStateManager.bindTexture(texture.getGlTextureId());
            int index = character;
            float cellX = (index & 15) * 8.0F;
            float cellY = (index >> 4) * 8.0F;
            float drawWidth = Math.max(1.0F, charWidths[index] - 1.0F);
            drawGlyphQuad(x, y, drawWidth, 8.0F,
                    cellX / 128.0F, cellY / 128.0F,
                    (cellX + drawWidth) / 128.0F,
                    (cellY + 7.99F) / 128.0F, italic);
            return;
        }

        int packed = glyphWidths[character] & 255;
        if (packed == 0) {
            return;
        }
        int page = character >>> 8;
        DynamicTexture texture = unicodeTexture(page);
        if (texture == null) {
            return;
        }
        GlStateManager.bindTexture(texture.getGlTextureId());
        int left = packed >>> 4;
        int right = (packed & 15) + 1;
        float sourceX = (character & 15) * 16.0F + left;
        float sourceY = (character & 255) / 16 * 16.0F;
        float sourceWidth = Math.max(0.02F, right - left - 0.02F);
        drawGlyphQuad(x, y, sourceWidth / 2.0F, 8.0F,
                sourceX / 256.0F, sourceY / 256.0F,
                (sourceX + sourceWidth) / 256.0F,
                (sourceY + 15.98F) / 256.0F, italic);
    }

    private void drawGlyphQuad(float x, float y, float width, float height,
                               float u1, float v1, float u2, float v2,
                               boolean italic) {
        float topShift = italic ? 1.0F : 0.0F;
        float bottomShift = italic ? -1.0F : 0.0F;
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(u1, v1);
        GL11.glVertex2f(x + topShift, y);
        GL11.glTexCoord2f(u1, v2);
        GL11.glVertex2f(x + bottomShift, y + height);
        GL11.glTexCoord2f(u2, v2);
        GL11.glVertex2f(x + width + bottomShift, y + height);
        GL11.glTexCoord2f(u2, v1);
        GL11.glVertex2f(x + width + topShift, y);
        GL11.glEnd();
    }

    private void drawLine(float x1, float y1, float x2, float y2) {
        GlStateManager.disableTexture2D();
        GL11.glLineWidth(1.0F);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x2, y2);
        GL11.glEnd();
        GlStateManager.enableTexture2D();
    }

    private int charWidth(char character) {
        if (character == ' ') {
            return 4;
        }
        if (isAscii(character)) {
            return charWidths[character];
        }
        int packed = glyphWidths[character] & 255;
        if (packed == 0) {
            return 0;
        }
        int left = packed >>> 4;
        int right = (packed & 15) + 1;
        return (right - left) / 2 + 1;
    }

    private boolean isAscii(char character) {
        return character >= 32 && character <= 126;
    }

    private void ensureMetrics() {
        IResourceManager manager = resourceManager();
        registerReloadListener(manager);
        if (metricsReady) {
            return;
        }
        try {
            asciiImage = readImage(manager, ASCII_TEXTURE);
            readAsciiWidths(asciiImage);
            readGlyphWidths(manager);
            metricsReady = true;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load Minecraft bitmap font", exception);
        }
    }

    private void registerReloadListener(IResourceManager manager) {
        if (manager == registeredManager) {
            return;
        }
        registeredManager = manager;
        if (manager instanceof IReloadableResourceManager) {
            ((IReloadableResourceManager) manager).registerReloadListener(this);
        }
    }

    private IResourceManager resourceManager() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.getResourceManager() == null) {
            throw new IllegalStateException("Minecraft resource manager is unavailable");
        }
        return minecraft.getResourceManager();
    }

    private BufferedImage readImage(IResourceManager manager,
                                    ResourceLocation location) throws IOException {
        InputStream stream = manager.getResource(location).getInputStream();
        try {
            return TextureUtil.readBufferedImage(stream);
        } finally {
            stream.close();
        }
    }

    private void readAsciiWidths(BufferedImage image) {
        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();
        int cellWidth = imageWidth / 16;
        int cellHeight = imageHeight / 16;
        int[] pixels = new int[imageWidth * imageHeight];
        image.getRGB(0, 0, imageWidth, imageHeight, pixels, 0, imageWidth);
        float widthScale = 8.0F / cellWidth;
        for (int index = 0; index < charWidths.length; index++) {
            if (index == 32) {
                charWidths[index] = 4;
                continue;
            }
            int column = index & 15;
            int row = index >> 4;
            int right = cellWidth - 1;
            while (right >= 0 && isTransparentColumn(
                    pixels, imageWidth, cellHeight,
                    column * cellWidth + right, row * cellHeight)) {
                right--;
            }
            right++;
            charWidths[index] = (int) (0.5D + right * widthScale) + 1;
        }
    }

    private boolean isTransparentColumn(int[] pixels, int imageWidth,
                                        int cellHeight, int x, int startY) {
        for (int y = 0; y < cellHeight; y++) {
            if ((pixels[x + (startY + y) * imageWidth] >>> 24 & 255) != 0) {
                return false;
            }
        }
        return true;
    }

    private void readGlyphWidths(IResourceManager manager) throws IOException {
        InputStream stream = manager.getResource(GLYPH_SIZES).getInputStream();
        try {
            int offset = 0;
            while (offset < glyphWidths.length) {
                int count = stream.read(glyphWidths, offset,
                        glyphWidths.length - offset);
                if (count < 0) {
                    break;
                }
                offset += count;
            }
        } finally {
            stream.close();
        }
    }

    private DynamicTexture asciiTexture() {
        ensureMetrics();
        if (asciiTexture == null && asciiImage != null) {
            asciiTexture = createSupersampledTexture(asciiImage);
        }
        return asciiTexture;
    }

    private DynamicTexture unicodeTexture(int page) {
        DynamicTexture cached = unicodeTextures.get(page);
        if (cached != null) {
            return cached;
        }
        ResourceLocation location = new ResourceLocation(String.format(
                "textures/font/unicode_page_%02x.png", page));
        try {
            DynamicTexture texture = createSupersampledTexture(
                    readImage(resourceManager(), location));
            unicodeTextures.put(page, texture);
            return texture;
        } catch (IOException ignored) {
            return null;
        }
    }

    private DynamicTexture createSupersampledTexture(BufferedImage source) {
        int width = source.getWidth() * ATLAS_SCALE;
        int height = source.getHeight() * ATLAS_SCALE;
        BufferedImage scaled = new BufferedImage(width, height,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_OFF);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        DynamicTexture texture = new DynamicTexture(scaled);
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        GlStateManager.bindTexture(texture.getGlTextureId());
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER,
                GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER,
                GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S,
                GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T,
                GL12.GL_CLAMP_TO_EDGE);
        GlStateManager.bindTexture(previousTexture);
        GlStateManager.setActiveTexture(activeTexture);
        return texture;
    }

    private void releaseTextures() {
        if (asciiTexture != null) {
            asciiTexture.deleteGlTexture();
            asciiTexture = null;
        }
        for (DynamicTexture texture : unicodeTextures.values()) {
            texture.deleteGlTexture();
        }
        unicodeTextures.clear();
    }

    private int normalizeColor(int color) {
        return (color & 0xFF000000) == 0 ? color | 0xFF000000 : color;
    }

    private int vanillaShadowColor(int color) {
        color = normalizeColor(color);
        return (color & 0xFCFCFC) >> 2 | color & 0xFF000000;
    }

    private void applyColor(int color) {
        GlStateManager.color(
                (color >>> 16 & 255) / 255.0F,
                (color >>> 8 & 255) / 255.0F,
                (color & 255) / 255.0F,
                (color >>> 24 & 255) / 255.0F);
    }

    private float snap(double value) {
        return (float) (Math.round(value * 2.0D) / 2.0D);
    }

    private void setupMinecraftColorCodes() {
        for (int index = 0; index < colorCodes.length; index++) {
            int shade = (index >> 3 & 1) * 85;
            int red = (index >> 2 & 1) * 170 + shade;
            int green = (index >> 1 & 1) * 170 + shade;
            int blue = (index & 1) * 170 + shade;
            if (index == 6) {
                red += 85;
            }
            if (index >= 16) {
                red /= 4;
                green /= 4;
                blue /= 4;
            }
            colorCodes[index] = (red & 255) << 16
                    | (green & 255) << 8
                    | blue & 255;
        }
    }
}
