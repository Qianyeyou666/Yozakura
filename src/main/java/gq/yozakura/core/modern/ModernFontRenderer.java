package gq.yozakura.core.modern;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

final class ModernFontRenderer {
    private static final int ATLAS_SCALE = 2;
    private static final int ATLAS_WIDTH = 1024;
    private static final int GLYPH_PADDING = 8;
    private static final int GLYPH_INSET_X = 2;
    private static final int GLYPH_INSET_Y = 2;
    private static final int TRANSPARENT_WHITE = 0x00FFFFFF;
    private static final int WIDTH_CACHE_LIMIT = 1024;
    private static final int BITMAP_ROWS = 7;
    private static final String[] BLANK_PATTERN = new String[]{"0", "0", "0", "0", "0", "0", "0"};
    private static final boolean TEXTURE_FONT_ENABLED = true;
    private static final Map<Integer, ModernFontRenderer> SF_CACHE = new HashMap<Integer, ModernFontRenderer>();
    private static final Map<Character, String[]> BITMAP_FONT = createBitmapFont();
    private static Font sfFont;
    private static boolean fontFailureLogged;
    private static boolean geometryFontLogged;

    private final int size;
    private final Glyph[] glyphs = new Glyph[256];
    private final Map<String, Integer> widthCache = new LinkedHashMap<String, Integer>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
            return size() > WIDTH_CACHE_LIMIT;
        }
    };
    private BufferedImage atlas;
    private int atlasHeight;
    private int textureId;
    private boolean registrationFailed;
    private int visualHeight = 1;

    private ModernFontRenderer(int size) {
        this.size = Math.max(1, size);
        buildAtlas();
    }

    static ModernFontRenderer sf(int size) {
        int key = Math.max(1, size);
        ModernFontRenderer renderer = SF_CACHE.get(Integer.valueOf(key));
        if (renderer == null) {
            renderer = new ModernFontRenderer(key);
            SF_CACHE.put(Integer.valueOf(key), renderer);
        }
        return renderer;
    }

    int width(String text) {
        if (text == null || text.length() == 0) {
            return 0;
        }
        if (!TEXTURE_FONT_ENABLED) {
            return geometryTextWidth(text);
        }
        Integer cached = widthCache.get(text);
        if (cached != null) {
            return cached.intValue();
        }
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00a7' && i < text.length() - 1) {
                i++;
                continue;
            }
            Glyph glyph = c < glyphs.length ? glyphs[c] : null;
            width += glyph == null ? Math.max(3, size / 2) : glyph.advance;
        }
        widthCache.put(text, Integer.valueOf(width));
        return width;
    }

    int height() {
        return visualHeight;
    }

    boolean draw(Object minecraft, String text, int x, int y, int color, boolean shadow) {
        if (text == null || text.length() == 0 || alpha(color) <= 0) {
            return true;
        }
        if (!TEXTURE_FONT_ENABLED) {
            drawGeometryText(text, x, y, color, shadow);
            return true;
        }
        if (!ensureRegistered(minecraft)) {
            drawGeometryText(text, x, y, color, shadow);
            return true;
        }
        if (shadow) {
            draw(minecraft, text, x + 1, y + 1, shadowColor(color), false);
        }

        int currentColor = normalizeColor(color);
        int cursor = x;
        ModernLegacyRenderer.State state = ModernLegacyRenderer.begin(true);
        if (state == null) {
            return false;
        }
        try {
            ModernLegacyRenderer.bindTexture(textureId);
            ModernLegacyRenderer.color(currentColor);
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '\u00a7' && i < text.length() - 1) {
                    int code = minecraftColor(text.charAt(++i), color);
                    if (code != Integer.MIN_VALUE) {
                        currentColor = code;
                        ModernLegacyRenderer.color(currentColor);
                    }
                    continue;
                }
                Glyph glyph = c < glyphs.length ? glyphs[c] : null;
                if (glyph == null) {
                    cursor += Math.max(3, size / 2);
                    continue;
                }
                if (glyph.drawable) {
                    drawGlyph(cursor + glyph.xOffset, y + glyph.yOffset, glyph);
                }
                cursor += glyph.advance;
            }
        } finally {
            ModernLegacyRenderer.end(state);
        }
        return true;
    }

    private boolean ensureRegistered(Object minecraft) {
        if (!TEXTURE_FONT_ENABLED) {
            return false;
        }
        if (textureId != 0) {
            return true;
        }
        if (registrationFailed || atlas == null) {
            return false;
        }
        try {
            textureId = ModernLegacyRenderer.createTexture(atlas);
            if (textureId == 0) {
                registrationFailed = true;
                return false;
            }
            return true;
        } catch (Throwable throwable) {
            registrationFailed = true;
            ModernForgeEventBridge.log("Modern font texture registration failed", throwable);
            return false;
        }
    }

    private void buildAtlas() {
        if (!TEXTURE_FONT_ENABLED) {
            buildGeometryMetrics();
            return;
        }
        try {
            Font font = loadSfFont().deriveFont(Font.PLAIN, (float) (size * ATLAS_SCALE));
            BufferedImage metricsImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D metricsGraphics = metricsImage.createGraphics();
            setupGraphics(metricsGraphics, font);
            FontMetrics metrics = metricsGraphics.getFontMetrics();

            int x = 0;
            int y = ATLAS_SCALE;
            int rowHeight = 0;
            int measuredFontHeight = -1;
            for (int i = 0; i < glyphs.length; i++) {
                char c = (char) i;
                Glyph glyph = new Glyph();
                Rectangle2D bounds = metrics.getStringBounds(String.valueOf(c), metricsGraphics);
                int glyphWidth = Math.max(1, (int) Math.ceil(bounds.getWidth() / ATLAS_SCALE));
                int glyphHeight = Math.max(1, (int) Math.ceil(bounds.getHeight() / ATLAS_SCALE));
                glyph.width = glyphWidth + GLYPH_PADDING;
                glyph.height = glyphHeight + GLYPH_INSET_Y * 2;
                glyph.srcWidth = glyph.width * ATLAS_SCALE;
                glyph.srcHeight = glyph.height * ATLAS_SCALE;
                glyph.drawWidth = Math.max(1, glyph.width / ATLAS_SCALE);
                glyph.drawHeight = Math.max(1, glyph.height / ATLAS_SCALE);
                glyph.advance = Math.max(1, glyphWidth / ATLAS_SCALE);
                glyph.xOffset = -1;
                glyph.yOffset = -4;
                glyph.drawable = c > ' ';
                if (x + glyph.srcWidth >= ATLAS_WIDTH) {
                    x = 0;
                    y += rowHeight;
                    rowHeight = 0;
                }
                glyph.srcX = x;
                glyph.srcY = y;
                glyphs[i] = glyph;
                x += glyph.srcWidth;
                rowHeight = Math.max(rowHeight, glyph.srcHeight);
                measuredFontHeight = Math.max(measuredFontHeight, glyphHeight);
            }
            atlasHeight = Math.max(1, y + rowHeight + ATLAS_SCALE);
            metricsGraphics.dispose();

            atlas = new BufferedImage(ATLAS_WIDTH, atlasHeight, BufferedImage.TYPE_INT_ARGB);
            fillTransparentWhite(atlas);
            Graphics2D graphics = atlas.createGraphics();
            setupGraphics(graphics, font);
            graphics.setColor(Color.WHITE);
            FontMetrics renderMetrics = graphics.getFontMetrics();
            for (int i = 0; i < glyphs.length; i++) {
                Glyph glyph = glyphs[i];
                if (!glyph.drawable) {
                    continue;
                }
                graphics.drawString(String.valueOf((char) i),
                        glyph.srcX + GLYPH_INSET_X * ATLAS_SCALE,
                        glyph.srcY + GLYPH_INSET_Y * ATLAS_SCALE + renderMetrics.getAscent());
            }
            graphics.dispose();
            visualHeight = Math.max(1, (measuredFontHeight - GLYPH_PADDING) / ATLAS_SCALE);
        } catch (Throwable throwable) {
            atlas = null;
            if (!fontFailureLogged) {
                fontFailureLogged = true;
                ModernForgeEventBridge.log("Modern font atlas creation failed", throwable);
            }
        }
    }

    private void drawGlyph(int x, int y, Glyph glyph) {
        ModernLegacyRenderer.texturedQuad(x, y, glyph.drawWidth, glyph.drawHeight,
                glyph.srcX, glyph.srcY, glyph.srcWidth, glyph.srcHeight,
                ATLAS_WIDTH, atlasHeight);
    }

    private void drawGeometryText(String text, int x, int y, int color, boolean shadow) {
        if (!geometryFontLogged) {
            geometryFontLogged = true;
            ModernForgeEventBridge.log("Modern HUD font texture disabled; using bitmap geometry 1.20.1 text renderer");
        }
        if (shadow) {
            drawGeometryText(text, x + 1, y + 1, shadowColor(color), false);
        }
        ModernLegacyRenderer.State state = ModernLegacyRenderer.begin(false);
        if (state == null) {
            return;
        }
        try {
            int currentColor = normalizeColor(color);
            int cursor = x;
            int scale = bitmapScale();
            int glyphHeight = BITMAP_ROWS * scale;
            int drawY = y + Math.max(0, (visualHeight - glyphHeight) / 2);
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '\u00a7' && i < text.length() - 1) {
                    int code = minecraftColor(text.charAt(++i), color);
                    if (code != Integer.MIN_VALUE) {
                        currentColor = code;
                    }
                    continue;
                }
                if (c > ' ') {
                    drawBitmapGlyph(c, cursor, drawY, scale, currentColor);
                }
                int advance = bitmapAdvance(c, scale);
                cursor += advance;
            }
        } finally {
            ModernLegacyRenderer.end(state);
        }
    }

    private void drawBitmapGlyph(char c, int x, int y, int scale, int color) {
        String[] pattern = bitmapPattern(c);
        for (int row = 0; row < pattern.length; row++) {
            String line = pattern[row];
            int runStart = -1;
            for (int col = 0; col <= line.length(); col++) {
                boolean on = col < line.length() && line.charAt(col) == '1';
                if (on && runStart < 0) {
                    runStart = col;
                } else if (!on && runStart >= 0) {
                    ModernLegacyRenderer.rectInBatch(x + runStart * scale, y + row * scale,
                            x + col * scale, y + (row + 1) * scale, color);
                    runStart = -1;
                }
            }
        }
    }

    private void buildGeometryMetrics() {
        int scale = bitmapScale();
        for (int i = 0; i < glyphs.length; i++) {
            Glyph glyph = new Glyph();
            glyph.advance = bitmapAdvance((char) i, scale);
            glyph.drawable = i > ' ';
            glyphs[i] = glyph;
        }
        visualHeight = BITMAP_ROWS * scale;
        atlas = null;
        atlasHeight = 0;
    }

    private int geometryTextWidth(String text) {
        Integer cached = widthCache.get(text);
        if (cached != null) {
            return cached.intValue();
        }
        int scale = bitmapScale();
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00a7' && i < text.length() - 1) {
                i++;
                continue;
            }
            width += bitmapAdvance(c, scale);
        }
        widthCache.put(text, Integer.valueOf(width));
        return width;
    }

    private int bitmapScale() {
        return size >= 17 ? 2 : 1;
    }

    private static int bitmapAdvance(char c, int scale) {
        if (c == ' ') {
            return 4 * scale;
        }
        return (bitmapWidth(c) + 1) * scale;
    }

    private static int bitmapWidth(char c) {
        String[] pattern = bitmapPattern(c);
        int width = 1;
        for (String line : pattern) {
            for (int i = line.length() - 1; i >= 0; i--) {
                if (line.charAt(i) == '1') {
                    width = Math.max(width, i + 1);
                    break;
                }
            }
        }
        return width;
    }

    private static String[] bitmapPattern(char c) {
        String[] pattern = BITMAP_FONT.get(Character.valueOf(Character.toUpperCase(c)));
        if (pattern != null) {
            return pattern;
        }
        pattern = BITMAP_FONT.get(Character.valueOf('?'));
        return pattern == null ? BLANK_PATTERN : pattern;
    }

    private static Map<Character, String[]> createBitmapFont() {
        Map<Character, String[]> font = new HashMap<Character, String[]>();
        put(font, 'A', "01110", "10001", "10001", "11111", "10001", "10001", "10001");
        put(font, 'B', "11110", "10001", "10001", "11110", "10001", "10001", "11110");
        put(font, 'C', "01111", "10000", "10000", "10000", "10000", "10000", "01111");
        put(font, 'D', "11110", "10001", "10001", "10001", "10001", "10001", "11110");
        put(font, 'E', "11111", "10000", "10000", "11110", "10000", "10000", "11111");
        put(font, 'F', "11111", "10000", "10000", "11110", "10000", "10000", "10000");
        put(font, 'G', "01111", "10000", "10000", "10011", "10001", "10001", "01111");
        put(font, 'H', "10001", "10001", "10001", "11111", "10001", "10001", "10001");
        put(font, 'I', "111", "010", "010", "010", "010", "010", "111");
        put(font, 'J', "00111", "00010", "00010", "00010", "10010", "10010", "01100");
        put(font, 'K', "10001", "10010", "10100", "11000", "10100", "10010", "10001");
        put(font, 'L', "10000", "10000", "10000", "10000", "10000", "10000", "11111");
        put(font, 'M', "10001", "11011", "10101", "10101", "10001", "10001", "10001");
        put(font, 'N', "10001", "11001", "10101", "10011", "10001", "10001", "10001");
        put(font, 'O', "01110", "10001", "10001", "10001", "10001", "10001", "01110");
        put(font, 'P', "11110", "10001", "10001", "11110", "10000", "10000", "10000");
        put(font, 'Q', "01110", "10001", "10001", "10001", "10101", "10010", "01101");
        put(font, 'R', "11110", "10001", "10001", "11110", "10100", "10010", "10001");
        put(font, 'S', "01111", "10000", "10000", "01110", "00001", "00001", "11110");
        put(font, 'T', "11111", "00100", "00100", "00100", "00100", "00100", "00100");
        put(font, 'U', "10001", "10001", "10001", "10001", "10001", "10001", "01110");
        put(font, 'V', "10001", "10001", "10001", "10001", "10001", "01010", "00100");
        put(font, 'W', "10001", "10001", "10001", "10101", "10101", "10101", "01010");
        put(font, 'X', "10001", "10001", "01010", "00100", "01010", "10001", "10001");
        put(font, 'Y', "10001", "10001", "01010", "00100", "00100", "00100", "00100");
        put(font, 'Z', "11111", "00001", "00010", "00100", "01000", "10000", "11111");
        put(font, '0', "01110", "10001", "10011", "10101", "11001", "10001", "01110");
        put(font, '1', "010", "110", "010", "010", "010", "010", "111");
        put(font, '2', "01110", "10001", "00001", "00010", "00100", "01000", "11111");
        put(font, '3', "11110", "00001", "00001", "01110", "00001", "00001", "11110");
        put(font, '4', "00010", "00110", "01010", "10010", "11111", "00010", "00010");
        put(font, '5', "11111", "10000", "10000", "11110", "00001", "00001", "11110");
        put(font, '6', "01110", "10000", "10000", "11110", "10001", "10001", "01110");
        put(font, '7', "11111", "00001", "00010", "00100", "01000", "01000", "01000");
        put(font, '8', "01110", "10001", "10001", "01110", "10001", "10001", "01110");
        put(font, '9', "01110", "10001", "10001", "01111", "00001", "00001", "01110");
        put(font, '|', "1", "1", "1", "0", "1", "1", "1");
        put(font, ':', "0", "1", "1", "0", "1", "1", "0");
        put(font, '.', "0", "0", "0", "0", "0", "1", "1");
        put(font, ',', "0", "0", "0", "0", "0", "1", "1");
        put(font, '-', "0000", "0000", "0000", "1111", "0000", "0000", "0000");
        put(font, '_', "00000", "00000", "00000", "00000", "00000", "00000", "11111");
        put(font, '/', "00001", "00010", "00010", "00100", "01000", "01000", "10000");
        put(font, '\\', "10000", "01000", "01000", "00100", "00010", "00010", "00001");
        put(font, '+', "0000", "0100", "0100", "1110", "0100", "0100", "0000");
        put(font, '%', "10001", "00010", "00100", "01000", "10000", "00000", "10001");
        put(font, '?', "01110", "10001", "00001", "00010", "00100", "00000", "00100");
        put(font, '!', "1", "1", "1", "1", "1", "0", "1");
        put(font, '[', "111", "100", "100", "100", "100", "100", "111");
        put(font, ']', "111", "001", "001", "001", "001", "001", "111");
        put(font, '(', "011", "100", "100", "100", "100", "100", "011");
        put(font, ')', "110", "001", "001", "001", "001", "001", "110");
        return font;
    }

    private static void put(Map<Character, String[]> font, char c,
                            String first, String second, String third, String fourth,
                            String fifth, String sixth, String seventh) {
        font.put(Character.valueOf(c), new String[]{first, second, third, fourth, fifth, sixth, seventh});
    }

    private static Font loadSfFont() throws Exception {
        if (sfFont != null) {
            return sfFont;
        }
        InputStream stream = openResource("assets/minecraft/novo/fonts/SF.ttf");
        if (stream == null) {
            stream = openResource("assets/minecraft/font/Inter.ttf");
        }
        if (stream == null) {
            sfFont = new Font("Dialog", Font.PLAIN, 16);
            return sfFont;
        }
        try {
            sfFont = Font.createFont(Font.TRUETYPE_FONT, stream);
            return sfFont;
        } finally {
            stream.close();
        }
    }

    private static InputStream openResource(String path) {
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        InputStream stream = context == null ? null : context.getResourceAsStream(path);
        if (stream != null) {
            return stream;
        }
        ClassLoader own = ModernFontRenderer.class.getClassLoader();
        stream = own == null ? null : own.getResourceAsStream(path);
        if (stream != null) {
            return stream;
        }
        return ModernFontRenderer.class.getResourceAsStream("/" + path);
    }

    private static void setupGraphics(Graphics2D graphics, Font font) {
        graphics.setFont(font);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_LCD_CONTRAST, 140);
    }

    private static void fillTransparentWhite(BufferedImage image) {
        int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        Arrays.fill(pixels, TRANSPARENT_WHITE);
    }

    private static int normalizeColor(int color) {
        return (color & 0xFC000000) == 0 ? color | 0xFF000000 : color;
    }

    private static int shadowColor(int color) {
        int normalized = normalizeColor(color);
        int alpha = (normalized >>> 24) & 255;
        return Math.max(42, Math.min(120, Math.round(alpha * 0.44f))) << 24;
    }

    private static int minecraftColor(char code, int baseColor) {
        int index = "0123456789abcdef".indexOf(Character.toLowerCase(code));
        if (index < 0) {
            if (Character.toLowerCase(code) == 'r') {
                return normalizeColor(baseColor);
            }
            return Integer.MIN_VALUE;
        }
        int offset = (index >> 3 & 1) * 85;
        int red = (index >> 2 & 1) * 170 + offset;
        int green = (index >> 1 & 1) * 170 + offset;
        int blue = (index & 1) * 170 + offset;
        if (index == 6) {
            red += 85;
        }
        return (normalizeColor(baseColor) & 0xFF000000)
                | ((red & 255) << 16)
                | ((green & 255) << 8)
                | (blue & 255);
    }

    private static int alpha(int color) {
        return (color >>> 24) & 255;
    }

    private static final class Glyph {
        private int srcX;
        private int srcY;
        private int srcWidth;
        private int srcHeight;
        private int width;
        private int height;
        private int drawWidth;
        private int drawHeight;
        private int advance;
        private int xOffset;
        private int yOffset;
        private boolean drawable;
    }
}
