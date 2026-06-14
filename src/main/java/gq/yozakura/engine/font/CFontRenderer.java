package gq.yozakura.engine.font;

import gq.yozakura.engine.render.GLStateManager;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.awt.Font;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CFontRenderer extends CFont {
    private static final String FORMAT_CODES = "0123456789abcdefklmnor";
    private static final int WIDTH_CACHE_LIMIT = 1024;
    private static boolean scaleCompensationEnabled = false;
    private static int scaleCompensationDepth = 0;
    private static final int FONT_ATTRIB_MASK = GL11.GL_ENABLE_BIT
            | GL11.GL_COLOR_BUFFER_BIT
            | GL11.GL_CURRENT_BIT
            | GL11.GL_DEPTH_BUFFER_BIT
            | GL11.GL_HINT_BIT
            | GL11.GL_LINE_BIT
            | GL11.GL_TEXTURE_BIT;
    private static final FloatBuffer MODELVIEW = BufferUtils.createFloatBuffer(16);

    protected CharData[] boldChars = new CharData[256];
    protected CharData[] italicChars = new CharData[256];
    protected CharData[] boldItalicChars = new CharData[256];
    private final int[] colorCode = new int[32];
    protected DynamicTexture texBold;
    protected DynamicTexture texItalic;
    protected DynamicTexture texItalicBold;
    private Font boldFont;
    private Font italicFont;
    private Font boldItalicFont;
    private final Map<Integer, CFontRenderer> scaledRenderers = new HashMap<Integer, CFontRenderer>();
    private final Map<String, Integer> widthCache = new LinkedHashMap<String, Integer>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
            return size() > WIDTH_CACHE_LIMIT;
        }
    };

    public CFontRenderer(Font font, boolean antiAlias, boolean fractionalMetrics) {
        this(font, font.deriveFont(Font.BOLD), font.deriveFont(Font.ITALIC),
                font.deriveFont(Font.BOLD | Font.ITALIC), antiAlias, fractionalMetrics);
    }

    public CFontRenderer(Font font, Font boldFont, Font italicFont, Font boldItalicFont,
                         boolean antiAlias, boolean fractionalMetrics, Font... ignoredFallbacks) {
        super(font, antiAlias, fractionalMetrics);
        this.boldFont = boldFont == null ? font.deriveFont(Font.BOLD) : boldFont;
        this.italicFont = italicFont == null ? font.deriveFont(Font.ITALIC) : italicFont;
        this.boldItalicFont = boldItalicFont == null ? font.deriveFont(Font.BOLD | Font.ITALIC) : boldItalicFont;
        setupMinecraftColorcodes();
        setupBoldItalicIDs();
    }

    public float drawStringWithShadow(String text, double x, double y, int color) {
        float shadowWidth = drawString(text, x + 1.0, y + 1.0, shadowColor(color), false);
        return Math.max(shadowWidth, drawString(text, x, y, color, false));
    }

    public float drawString(String text, float x, float y, int color) {
        return drawString(text, x, y, color, false);
    }

    public float drawCenteredString(String text, double x, double y, int color) {
        return drawString(text, x - getStringWidth(text) / 2.0, y, color, false);
    }

    public float drawCenteredStringWithShadow(String text, float x, float y, int color) {
        return drawStringWithShadow(text, x - getStringWidth(text) / 2.0f, y, color);
    }

    public float drawCenteredStringWithShadow(String text, double x, double y, int color) {
        return drawStringWithShadow(text, x - getStringWidth(text) / 2.0, y, color);
    }

    public float drawString(String text, double x, double y, int color, boolean shadow) {
        return drawStringInternal(text, x, y, color, shadow, true);
    }

    public static void setScaleCompensationEnabled(boolean enabled) {
        scaleCompensationEnabled = enabled;
        scaleCompensationDepth = enabled ? Math.max(1, scaleCompensationDepth) : 0;
    }

    public static void pushScaleCompensation() {
        scaleCompensationDepth++;
        scaleCompensationEnabled = true;
    }

    public static void popScaleCompensation() {
        if (scaleCompensationDepth > 0) {
            scaleCompensationDepth--;
        }
        scaleCompensationEnabled = scaleCompensationDepth > 0;
    }

    private float drawStringInternal(String text, double x, double y, int color, boolean shadow,
                                     boolean allowScaleCompensation) {
        if (text == null || text.length() == 0) {
            return 0.0f;
        }
        if (allowScaleCompensation && scaleCompensationEnabled) {
            ParentScale parentScale = getParentScale();
            if (parentScale.scaled) {
                return drawScaleCompensatedString(text, x, y, color, shadow, parentScale);
            }
        }

        if (color == 553648127) {
            color = 16777215;
        }
        if ((color & 0xFC000000) == 0) {
            color |= 0xFF000000;
        }
        if (shadow) {
            color &= 0xFF000000;
        }

        CharData[] currentData = charData;
        DynamicTexture currentTexture = tex;
        float alpha = (color >>> 24 & 255) / 255.0f;
        boolean randomCase = false;
        boolean bold = false;
        boolean italic = false;
        boolean strikethrough = false;
        boolean underline = false;
        x = snapToTextGrid(x);
        y = snapToTextGrid(y);
        x = (x - 1.0) * 2.0;
        y = (y - 3.0) * 2.0;

        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        setActiveTexture(GL13.GL_TEXTURE0);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glPushAttrib(FONT_ATTRIB_MASK);
        GL11.glPushMatrix();
        boolean drawingBatch = false;
        int boundTextureId = -1;
        try {
            GlStateManager.scale(0.5, 0.5, 0.5);
            GLStateManager.enableAlpha();
            GLStateManager.enableBlend();
            GLStateManager.blendFuncSeparate(770, 771, 1, 0);
            GLStateManager.enableTexture2D();
            GLStateManager.disableDepth();
            GLStateManager.depthMask(false);
            GLStateManager.lineSmooth(false);
            GLStateManager.polygonSmooth(false);
            GLStateManager.multisample(false);
            GLStateManager.textureEnvModulate();
            applyGlColor(color, alpha);
            boundTextureId = bindFontTexture(currentTexture, boundTextureId);

            for (int i = 0; i < text.length(); i++) {
                char character = text.charAt(i);
                if (character == '\u00a7' && i < text.length() - 1) {
                    int colorIndex = FORMAT_CODES.indexOf(Character.toLowerCase(text.charAt(i + 1)));
                    if (drawingBatch) {
                        GL11.glEnd();
                        drawingBatch = false;
                    }
                    if (colorIndex < 16) {
                        bold = false;
                        italic = false;
                        randomCase = false;
                        underline = false;
                        strikethrough = false;
                        currentData = charData;
                        currentTexture = tex;
                        if (colorIndex < 0) {
                            colorIndex = 15;
                        }
                        if (shadow) {
                            colorIndex += 16;
                        }
                        applyGlColor((color & 0xFF000000) | colorCode[colorIndex], alpha);
                        boundTextureId = bindFontTexture(currentTexture, boundTextureId);
                    } else if (colorIndex == 16) {
                        randomCase = true;
                    } else if (colorIndex == 17) {
                        bold = true;
                        if (italic) {
                            currentData = boldItalicChars;
                            currentTexture = texItalicBold;
                        } else {
                            currentData = boldChars;
                            currentTexture = texBold;
                        }
                        boundTextureId = bindFontTexture(currentTexture, boundTextureId);
                    } else if (colorIndex == 18) {
                        strikethrough = true;
                    } else if (colorIndex == 19) {
                        underline = true;
                    } else if (colorIndex == 20) {
                        italic = true;
                        if (bold) {
                            currentData = boldItalicChars;
                            currentTexture = texItalicBold;
                        } else {
                            currentData = italicChars;
                            currentTexture = texItalic;
                        }
                        boundTextureId = bindFontTexture(currentTexture, boundTextureId);
                    } else if (colorIndex == 21) {
                        bold = false;
                        italic = false;
                        randomCase = false;
                        underline = false;
                        strikethrough = false;
                        currentData = charData;
                        currentTexture = tex;
                        applyGlColor(color, alpha);
                        boundTextureId = bindFontTexture(currentTexture, boundTextureId);
                    }
                    i++;
                    continue;
                }

                if (randomCase && Character.isLetter(character)) {
                    character = Character.isUpperCase(character)
                            ? Character.toUpperCase(character)
                            : Character.toLowerCase(character);
                }
                if (character >= currentData.length) {
                    continue;
                }

                CharData glyph = currentData[character];
                if (glyph.drawable) {
                    if (!drawingBatch) {
                        GL11.glBegin(GL11.GL_QUADS);
                        drawingBatch = true;
                    }
                    drawChar(currentData, character, (float) x, (float) y);
                }
                if (strikethrough) {
                    if (drawingBatch) {
                        GL11.glEnd();
                        drawingBatch = false;
                    }
                    double lineY = y + Math.max(1, fontHeight) / 2.0;
                    drawLine(x, lineY, x + glyph.advance, lineY, 1.0f);
                    boundTextureId = bindFontTexture(currentTexture, boundTextureId);
                }
                if (underline) {
                    if (drawingBatch) {
                        GL11.glEnd();
                        drawingBatch = false;
                    }
                    double lineY = y + Math.max(1, fontHeight) - 2.0;
                    drawLine(x, lineY, x + glyph.advance, lineY, 1.0f);
                    boundTextureId = bindFontTexture(currentTexture, boundTextureId);
                }
                x += glyph.advance + charOffset;
            }
            if (drawingBatch) {
                GL11.glEnd();
                drawingBatch = false;
            }
            GLStateManager.hint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        } finally {
            if (drawingBatch) {
                GL11.glEnd();
            }
            GL11.glPopMatrix();
            GL11.glPopAttrib();
            GLStateManager.syncToCurrent();
            GLStateManager.setActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.bindTexture2D(previousTexture);
            GLStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            GLStateManager.setActiveTexture(previousActiveTexture);
        }
        return (float) x / 2.0f;
    }

    private double snapToTextGrid(double value) {
        return Math.round(value * 2.0) / 2.0;
    }

    private float drawScaleCompensatedString(String text, double x, double y, int color, boolean shadow,
                                             ParentScale parentScale) {
        int scaledSize = Math.max(1, Math.min(96, Math.round(font.getSize2D() * parentScale.scale)));
        CFontRenderer renderer = getScaledRenderer(scaledSize);

        GL11.glPushMatrix();
        try {
            GL11.glTranslated(x, y, 0.0);
            GL11.glScaled(1.0 / parentScale.scale, 1.0 / parentScale.scale, 1.0);
            GL11.glTranslated(-x, -y, 0.0);
            renderer.drawStringInternal(text, x, y, color, shadow, false);
        } finally {
            GL11.glPopMatrix();
        }
        return (float) (x + getStringWidth(text));
    }

    private CFontRenderer getScaledRenderer(int size) {
        int currentSize = Math.max(1, Math.round(font.getSize2D()));
        if (size == currentSize) {
            return this;
        }
        CFontRenderer cached = scaledRenderers.get(size);
        if (cached != null) {
            return cached;
        }
        CFontRenderer renderer = new CFontRenderer(
                font.deriveFont(font.getStyle(), (float) size),
                boldFont.deriveFont(Font.BOLD, (float) size),
                italicFont.deriveFont(Font.ITALIC, (float) size),
                boldItalicFont.deriveFont(Font.BOLD | Font.ITALIC, (float) size),
                antiAlias,
                fractionalMetrics);
        scaledRenderers.put(size, renderer);
        return renderer;
    }

    private ParentScale getParentScale() {
        MODELVIEW.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MODELVIEW);
        float m00 = MODELVIEW.get(0);
        float m01 = MODELVIEW.get(1);
        float m10 = MODELVIEW.get(4);
        float m11 = MODELVIEW.get(5);
        float scaleX = (float) Math.sqrt(m00 * m00 + m01 * m01);
        float scaleY = (float) Math.sqrt(m10 * m10 + m11 * m11);
        if (scaleX <= 0.01f || scaleY <= 0.01f) {
            return ParentScale.IDENTITY;
        }

        float dot = m00 * m10 + m01 * m11;
        float scale = (scaleX + scaleY) * 0.5f;
        boolean simpleScale = Math.abs(dot) < 0.001f && Math.abs(scaleX - scaleY) < 0.015f;
        boolean scaled = simpleScale && Math.abs(scale - 1.0f) > 0.035f && scale > 0.25f && scale < 4.0f;
        return scaled ? new ParentScale(true, scale) : ParentScale.IDENTITY;
    }

    @Override
    public int getStringWidth(String text) {
        if (text == null) {
            return 0;
        }
        String cacheKey = charOffset + "\u0000" + text;
        Integer cachedWidth = widthCache.get(cacheKey);
        if (cachedWidth != null) {
            return cachedWidth.intValue();
        }
        int width = 0;
        CharData[] currentData = charData;
        boolean bold = false;
        boolean italic = false;
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character == '\u00a7' && i < text.length() - 1) {
                int colorIndex = FORMAT_CODES.indexOf(Character.toLowerCase(text.charAt(i + 1)));
                if (colorIndex < 16) {
                    bold = false;
                    italic = false;
                    currentData = charData;
                } else if (colorIndex == 17) {
                    bold = true;
                    currentData = italic ? boldItalicChars : boldChars;
                } else if (colorIndex == 20) {
                    italic = true;
                    currentData = bold ? boldItalicChars : italicChars;
                } else if (colorIndex == 21) {
                    bold = false;
                    italic = false;
                    currentData = charData;
                }
                i++;
                continue;
            }
            if (character < currentData.length) {
                width += currentData[character].advance + charOffset;
            }
        }
        int result = width / 2;
        widthCache.put(cacheKey, result);
        return result;
    }

    @Override
    public void setFont(Font font) {
        super.setFont(font);
        this.boldFont = font.deriveFont(Font.BOLD);
        this.italicFont = font.deriveFont(Font.ITALIC);
        this.boldItalicFont = font.deriveFont(Font.BOLD | Font.ITALIC);
        widthCache.clear();
        scaledRenderers.clear();
        setupBoldItalicIDs();
    }

    @Override
    public void setAntiAlias(boolean antiAlias) {
        super.setAntiAlias(antiAlias);
        widthCache.clear();
        scaledRenderers.clear();
        setupBoldItalicIDs();
    }

    @Override
    public void setFractionalMetrics(boolean fractionalMetrics) {
        super.setFractionalMetrics(fractionalMetrics);
        widthCache.clear();
        scaledRenderers.clear();
        setupBoldItalicIDs();
    }

    private void setupBoldItalicIDs() {
        texBold = setupTexture(boldFont, antiAlias, fractionalMetrics, boldChars);
        texItalic = setupTexture(italicFont, antiAlias, fractionalMetrics, italicChars);
        texItalicBold = setupTexture(boldItalicFont, antiAlias, fractionalMetrics, boldItalicChars);
    }

    private void drawLine(double x, double y, double x1, double y1, float width) {
        GLStateManager.disableTexture2D();
        GLStateManager.lineWidth(width);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2d(x, y);
        GL11.glVertex2d(x1, y1);
        GL11.glEnd();
        GLStateManager.enableTexture2D();
    }

    private int bindFontTexture(DynamicTexture texture, int boundTextureId) {
        if (texture == null) {
            return boundTextureId;
        }
        int textureId = texture.getGlTextureId();
        if (textureId != boundTextureId) {
            GLStateManager.setActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.bindTexture2D(textureId);
        }
        return textureId;
    }

    private int shadowColor(int color) {
        if ((color & 0xFC000000) == 0) {
            color |= 0xFF000000;
        }
        int alpha = color >>> 24 & 255;
        int shadowAlpha = Math.max(42, Math.min(120, Math.round(alpha * 0.44f)));
        return shadowAlpha << 24;
    }

    private static final class ParentScale {
        private static final ParentScale IDENTITY = new ParentScale(false, 1.0f);

        private final boolean scaled;
        private final float scale;

        private ParentScale(boolean scaled, float scale) {
            this.scaled = scaled;
            this.scale = scale;
        }
    }

    private void setActiveTexture(int textureUnit) {
        GLStateManager.setActiveTexture(textureUnit);
    }

    public List<String> wrapWords(String text, double width) {
        ArrayList<String> finalWords = new ArrayList<String>();
        if (getStringWidth(text) > width) {
            String[] words = text.split(" ");
            String currentWord = "";
            int lastColorCode = 65535;
            for (String word : words) {
                char[] chars = word.toCharArray();
                for (int i = 0; i < chars.length; i++) {
                    if (chars[i] == '\u00a7' && i < chars.length - 1) {
                        lastColorCode = chars[i + 1];
                    }
                }
                if (getStringWidth(currentWord + word + " ") < width) {
                    currentWord = currentWord + word + " ";
                } else {
                    finalWords.add(currentWord);
                    currentWord = String.valueOf((char) 167) + (char) lastColorCode + word + " ";
                }
            }
            if (currentWord.length() > 0) {
                if (getStringWidth(currentWord) < width) {
                    finalWords.add(String.valueOf((char) 167) + (char) lastColorCode + currentWord + " ");
                } else {
                    finalWords.addAll(formatString(currentWord, width));
                }
            }
        } else {
            finalWords.add(text);
        }
        return finalWords;
    }

    public List<String> formatString(String string, double width) {
        ArrayList<String> finalWords = new ArrayList<String>();
        String currentWord = "";
        int lastColorCode = 65535;
        char[] chars = string.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (c == '\u00a7' && i < chars.length - 1) {
                lastColorCode = chars[i + 1];
            }
            if (getStringWidth(currentWord + c) < width) {
                currentWord = currentWord + c;
            } else {
                finalWords.add(currentWord);
                currentWord = String.valueOf((char) 167) + (char) lastColorCode + c;
            }
        }
        if (currentWord.length() > 0) {
            finalWords.add(currentWord);
        }
        return finalWords;
    }

    private void setupMinecraftColorcodes() {
        for (int index = 0; index < 32; index++) {
            int noClue = (index >> 3 & 1) * 85;
            int red = (index >> 2 & 1) * 170 + noClue;
            int green = (index >> 1 & 1) * 170 + noClue;
            int blue = (index & 1) * 170 + noClue;
            if (index == 6) {
                red += 85;
            }
            if (index >= 16) {
                red /= 4;
                green /= 4;
                blue /= 4;
            }
            colorCode[index] = (red & 255) << 16 | (green & 255) << 8 | blue & 255;
        }
    }

    private void applyGlColor(int color, float alpha) {
        GLStateManager.color((color >>> 16 & 255) / 255.0f,
                (color >>> 8 & 255) / 255.0f,
                (color & 255) / 255.0f,
                alpha);
    }
}
