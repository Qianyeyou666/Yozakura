package gq.vapulite.font;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

public class CFontRenderer extends CFont {
    private static final String FORMAT_CODES = "0123456789abcdefklmnor";

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
        float shadowWidth = drawString(text, x + 0.5, y + 0.5, color, true);
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
        if (text == null || text.length() == 0) {
            return 0.0f;
        }
        x -= 1.0;
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
        x *= 2.0;
        y = (y - 3.0) * 2.0;

        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LINE_BIT);
        GL11.glPushMatrix();
        try {
            setActiveTexture(GL13.GL_TEXTURE0);
            GlStateManager.scale(0.5, 0.5, 0.5);
            GlStateManager.enableAlpha();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.enableTexture2D();
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            applyGlColor(color, alpha);
            bindFontTexture(currentTexture);

            for (int i = 0; i < text.length(); i++) {
                char character = text.charAt(i);
                if (character == '\u00a7' && i < text.length() - 1) {
                    int colorIndex = FORMAT_CODES.indexOf(Character.toLowerCase(text.charAt(i + 1)));
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
                        bindFontTexture(currentTexture);
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
                        bindFontTexture(currentTexture);
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
                        bindFontTexture(currentTexture);
                    } else if (colorIndex == 21) {
                        bold = false;
                        italic = false;
                        randomCase = false;
                        underline = false;
                        strikethrough = false;
                        currentData = charData;
                        currentTexture = tex;
                        applyGlColor(color, alpha);
                        bindFontTexture(currentTexture);
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
                GL11.glBegin(GL11.GL_TRIANGLES);
                drawChar(currentData, character, (float) x, (float) y);
                GL11.glEnd();
                if (strikethrough) {
                    drawLine(x, y + glyph.height / 2.0, x + glyph.width - 8.0, y + glyph.height / 2.0, 1.0f);
                    bindFontTexture(currentTexture);
                }
                if (underline) {
                    drawLine(x, y + glyph.height - 2.0, x + glyph.width - 8.0, y + glyph.height - 2.0, 1.0f);
                    bindFontTexture(currentTexture);
                }
                x += glyph.width - 8 + charOffset;
            }
            GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            setActiveTexture(previousActiveTexture);
        }
        return (float) x / 2.0f;
    }

    @Override
    public int getStringWidth(String text) {
        if (text == null) {
            return 0;
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
                width += currentData[character].width - 8 + charOffset;
            }
        }
        return width / 2;
    }

    @Override
    public void setFont(Font font) {
        super.setFont(font);
        this.boldFont = font.deriveFont(Font.BOLD);
        this.italicFont = font.deriveFont(Font.ITALIC);
        this.boldItalicFont = font.deriveFont(Font.BOLD | Font.ITALIC);
        setupBoldItalicIDs();
    }

    @Override
    public void setAntiAlias(boolean antiAlias) {
        super.setAntiAlias(antiAlias);
        setupBoldItalicIDs();
    }

    @Override
    public void setFractionalMetrics(boolean fractionalMetrics) {
        super.setFractionalMetrics(fractionalMetrics);
        setupBoldItalicIDs();
    }

    private void setupBoldItalicIDs() {
        texBold = setupTexture(boldFont, antiAlias, fractionalMetrics, boldChars);
        texItalic = setupTexture(italicFont, antiAlias, fractionalMetrics, italicChars);
        texItalicBold = setupTexture(boldItalicFont, antiAlias, fractionalMetrics, boldItalicChars);
    }

    private void drawLine(double x, double y, double x1, double y1, float width) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glLineWidth(width);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2d(x, y);
        GL11.glVertex2d(x1, y1);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    private void bindFontTexture(DynamicTexture texture) {
        if (texture == null) {
            return;
        }
        setActiveTexture(GL13.GL_TEXTURE0);
        int textureId = texture.getGlTextureId();
        GlStateManager.bindTexture(textureId);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
    }

    private void setActiveTexture(int textureUnit) {
        try {
            GlStateManager.setActiveTexture(textureUnit);
        } catch (Throwable ignored) {
            GL13.glActiveTexture(textureUnit);
        }
        GL13.glActiveTexture(textureUnit);
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
        GlStateManager.color((color >>> 16 & 255) / 255.0f,
                (color >>> 8 & 255) / 255.0f,
                (color & 255) / 255.0f,
                alpha);
    }
}
