package gq.yozakura.core.modern;

final class ModernRender2D {
    private static final float GLASS_NOISE = 0.018f;
    private static final float GLASS_HIGHLIGHT = 1.05f;
    private static final boolean STABLE_HUD_SHADERS = true;
    private static final boolean STABLE_TEXTURE_GLASS = false;
    private static final int SAKURA = 0xFFFFB7D1;
    private static final int SAKURA_LIGHT = 0xFFFFEAF3;
    private static final int SAKURA_CORE = 0xFFFFF3FA;
    private static final int SAKURA_PETAL_VERTEX_COUNT = 5 * 12 * 3;
    private static final int SAKURA_CORE_VERTEX_COUNT = 12 * 3;
    private static final int SAKURA_VERTEX_COUNT = SAKURA_PETAL_VERTEX_COUNT + SAKURA_CORE_VERTEX_COUNT;
    private static final float[] SAKURA_VERTICES = new float[SAKURA_VERTEX_COUNT * 6];
    private static final float[][] SAKURA_PETAL_POINTS = new float[][]{
            {0.00f, -0.18f}, {-0.30f, -0.07f}, {-0.64f, 0.25f}, {-0.66f, 0.62f},
            {-0.36f, 0.94f}, {-0.10f, 0.82f}, {0.00f, 0.74f}, {0.10f, 0.82f},
            {0.36f, 0.94f}, {0.66f, 0.62f}, {0.64f, 0.25f}, {0.30f, -0.07f},
            {0.00f, -0.18f}
    };
    private static boolean glassFallbackLogged;

    private final Object graphics;
    private final Object minecraft;

    ModernRender2D(Object graphics, Object minecraft) {
        this.graphics = graphics;
        this.minecraft = minecraft;
    }

    ModernFontRenderer font(int size) {
        return ModernFontRenderer.sf(size);
    }

    void rect(int x1, int y1, int x2, int y2, int color) {
        if (x2 <= x1 || y2 <= y1 || alpha(color) <= 0) {
            return;
        }
        ModernLegacyRenderer.rect(x1, y1, x2, y2, color);
    }

    void rounded(int x, int y, int width, int height, int radius, int color) {
        if (width <= 0 || height <= 0 || alpha(color) <= 0) {
            return;
        }
        if (STABLE_HUD_SHADERS && ModernShaderRenderer.drawRoundedRect(x, y, x + width, y + height, radius, color)) {
            return;
        }
        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        if (r <= 1) {
            rect(x, y, x + width, y + height, color);
            return;
        }
        rect(x + r, y, x + width - r, y + height, color);
        rect(x, y + r, x + width, y + height - r, color);
        rect(x + r / 2, y + 2, x + width - r / 2, y + height - 2, color);
        rect(x + 2, y + r / 2, x + width - 2, y + height - r / 2, color);
    }

    void roundedBorder(int x, int y, int width, int height, int radius,
                       int fillColor, int borderColor) {
        if (STABLE_HUD_SHADERS && ModernShaderRenderer.drawRoundedBorder(x, y, x + width, y + height, radius,
                1.0f, fillColor, borderColor)) {
            return;
        }
        rounded(x, y, width, height, radius, fillColor);
        border(x, y, x + width, y + height, borderColor);
    }

    void border(int x1, int y1, int x2, int y2, int color) {
        if (alpha(color) <= 0 || x2 <= x1 || y2 <= y1) {
            return;
        }
        rect(x1 + 1, y1, x2 - 1, y1 + 1, color);
        rect(x1 + 1, y2 - 1, x2 - 1, y2, color);
        rect(x1, y1 + 1, x1 + 1, y2 - 1, color);
        rect(x2 - 1, y1 + 1, x2, y2 - 1, color);
    }

    void shadow(int x1, int y1, int x2, int y2, int color, int layers) {
        int a = alpha(color);
        if (a <= 0 || layers <= 0) {
            return;
        }
        int rgb = color & 0x00FFFFFF;
        for (int i = layers; i >= 1; i--) {
            int layerAlpha = Math.max(1, Math.round(a * (layers - i + 1) / (float) (layers * layers)));
            rect(x1 - i, y1 - i, x2 + i, y2 + i, (layerAlpha << 24) | rgb);
        }
    }

    void glass(int x, int y, int width, int height, int radius,
               int fillColor, int borderColor, int glowColor, boolean accent) {
        boolean rendered = false;
        if (STABLE_HUD_SHADERS) {
            rendered = ModernShaderRenderer.drawLiquidGlass(x, y, x + width, y + height, radius,
                    0.55f, fillColor, borderColor);
        }
        if (!rendered && STABLE_TEXTURE_GLASS) {
            rendered = ModernGlassRenderer.draw(graphics, minecraft, x, y, width, height, radius,
                    fillColor, borderColor, glowColor, GLASS_NOISE, GLASS_HIGHLIGHT);
        }
        if (!rendered) {
            logGlassFallback();
            shadow(x, y, x + width, y + height, glowColor, 3);
            shadow(x, y, x + width, y + height, 0x18000000, 2);
            roundedBorder(x, y, width, height, radius, fillColor, borderColor);
            if (accent) {
                rect(x + 2, y + 1, x + width - 2, y + 2, 0x18FFFFFF);
            }
        }
        if (accent) {
            rect(x + 2, y + 1, x + width - 2, y + 2, 0x12FFFFFF);
        }
    }

    private static void logGlassFallback() {
        if (!glassFallbackLogged) {
            glassFallbackLogged = true;
            ModernForgeEventBridge.log("Modern HUD liquid glass shader unavailable; using geometry-only 1.20.1 fallback renderer");
        }
    }

    void verticalGradient(int x1, int y1, int x2, int y2, int topColor, int bottomColor) {
        if (x2 <= x1 || y2 <= y1 || (alpha(topColor) <= 0 && alpha(bottomColor) <= 0)) {
            return;
        }
        ModernLegacyRenderer.gradientQuad(x1, y1, x2, y2, topColor, bottomColor, topColor, bottomColor);
    }

    void horizontalGradient(int x1, int y1, int x2, int y2, int leftColor, int rightColor) {
        if (x2 <= x1 || y2 <= y1 || (alpha(leftColor) <= 0 && alpha(rightColor) <= 0)) {
            return;
        }
        ModernLegacyRenderer.gradientQuad(x1, y1, x2, y2, leftColor, leftColor, rightColor, rightColor);
    }

    void line(int x1, int y1, int x2, int y2, int thickness, int color) {
        if (thickness <= 1) {
            if (Math.abs(x2 - x1) >= Math.abs(y2 - y1)) {
                rect(Math.min(x1, x2), y1, Math.max(x1, x2), y1 + 1, color);
            } else {
                rect(x1, Math.min(y1, y2), x1 + 1, Math.max(y1, y2), color);
            }
            return;
        }
        if (Math.abs(x2 - x1) >= Math.abs(y2 - y1)) {
            rect(Math.min(x1, x2), y1 - thickness / 2, Math.max(x1, x2), y1 + (thickness + 1) / 2, color);
        } else {
            rect(x1 - thickness / 2, Math.min(y1, y2), x1 + (thickness + 1) / 2, Math.max(y1, y2), color);
        }
    }

    void progressBar(int x, int y, int width, int height, int radius, float progress,
                     int backgroundColor, int fillColor) {
        rounded(x, y, width, height, radius, backgroundColor);
        int fillWidth = Math.round(width * Math.max(0.0f, Math.min(1.0f, progress)));
        if (fillWidth > 0) {
            rounded(x, y, fillWidth, height, Math.min(radius, Math.max(1, fillWidth / 2)), fillColor);
        }
    }

    void textGlow(Object font, String text, int x, int y, int color, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0f)));
        if (a <= 0) {
            return;
        }
        int glow = withAlpha(color, Math.round(a * 0.14f));
        int near = withAlpha(color, Math.round(a * 0.22f));
        text(font, text, x - 1, y, glow, false);
        text(font, text, x + 1, y, glow, false);
        text(font, text, x, y - 1, glow, false);
        text(font, text, x, y + 1, glow, false);
        text(font, text, x - 1, y - 1, near, false);
        text(font, text, x + 1, y + 1, near, false);
    }

    void sakuraFlower(int centerX, int centerY, int size, int alpha) {
        if (alpha <= 0 || size <= 0) {
            return;
        }
        shadow(centerX - size, centerY - size, centerX + size, centerY + size,
                withAlpha(SAKURA, Math.round(alpha * 0.29f)), 4);
        sakuraLogo(centerX, centerY, size, alpha);
    }

    void circleBadge(int centerX, int centerY, int radius, int progress,
                     int fillColor, int trackColor, int progressColor) {
        rounded(centerX - radius, centerY - radius, radius * 2, radius * 2, radius, fillColor);
        border(centerX - radius, centerY - radius, centerX + radius, centerY + radius, trackColor);
        int clamped = Math.max(0, Math.min(100, progress));
        int start = centerX - radius + 2;
        int end = centerX + radius - 2;
        int y = centerY + radius - 3;
        rect(start, y, start + Math.round((end - start) * clamped / 100.0f), y + 2, progressColor);
    }

    void text(Object font, String text, int x, int y, int color, boolean shadow) {
        if (text == null || text.length() == 0 || alpha(color) <= 0) {
            return;
        }
        if (font instanceof ModernFontRenderer) {
            ((ModernFontRenderer) font).draw(minecraft, text, x, y, color, shadow);
        }
    }

    void centeredText(Object font, String text, int x, int y, int width, int height, int color, boolean shadow) {
        int textX = x + Math.max(0, width - textWidth(font, text)) / 2;
        int textY = centeredTextY(font, y, height);
        text(font, text, textX, textY, color, shadow);
    }

    int centeredTextY(Object font, int y, int height) {
        return y + Math.max(0, height - textHeight(font)) / 2;
    }

    int textWidth(Object font, String text) {
        if (font instanceof ModernFontRenderer) {
            return ((ModernFontRenderer) font).width(text);
        }
        return text == null ? 0 : text.length() * 6;
    }

    int textHeight(Object font) {
        if (font instanceof ModernFontRenderer) {
            return ((ModernFontRenderer) font).height();
        }
        return 9;
    }

    String trim(String text, int maxWidth, Object font) {
        if (text == null || maxWidth <= 0) {
            return "";
        }
        if (textWidth(font, text) <= maxWidth) {
            return text;
        }
        String result = text;
        while (result.length() > 1 && textWidth(font, result + "...") > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result.length() <= 1 ? "..." : result + "...";
    }

    void sakuraMark(int centerX, int centerY, int size, int alpha) {
        if (alpha <= 0 || size <= 0) {
            return;
        }
        sakuraLogo(centerX, centerY, size, alpha);
    }

    void sakuraLogo(int centerX, int centerY, int size, int alpha) {
        if (alpha <= 0 || size <= 0) {
            return;
        }
        float drawSize = Math.max(1.75f, size);
        buildSakuraVertices(centerX, centerY, drawSize,
                withAlpha(SAKURA_LIGHT, Math.round(alpha * 0.96f)),
                withAlpha(SAKURA, Math.round(alpha * 0.70f)),
                withAlpha(SAKURA_CORE, Math.round(alpha * 0.92f)));
        ModernLegacyRenderer.coloredTriangles(SAKURA_VERTICES, SAKURA_VERTEX_COUNT);
    }

    private static void buildSakuraVertices(float centerX, float centerY, float size,
                                            int centerColor, int petalColor, int coreColor) {
        int cursor = 0;
        for (int i = 0; i < 5; i++) {
            float radians = (float) Math.toRadians(i * 72.0f);
            cursor = appendSakuraPetal(SAKURA_VERTICES, cursor, centerX, centerY, size, radians,
                    centerColor, petalColor);
        }
        appendSakuraCore(SAKURA_VERTICES, cursor, centerX, centerY, size * 0.30f, coreColor);
    }

    private static int appendSakuraPetal(float[] target, int cursor, float centerX, float centerY,
                                         float size, float radians, int centerColor, int petalColor) {
        float width = size * 0.58f;
        float length = size * 1.12f;
        float offsetY = size * 0.20f;
        float fanY = length * 0.36f + offsetY;
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        float fanX = rotatedX(centerX, 0.0f, fanY, cos, sin);
        float fanScreenY = rotatedY(centerY, 0.0f, fanY, cos, sin);
        for (int i = 0; i < SAKURA_PETAL_POINTS.length - 1; i++) {
            float[] first = SAKURA_PETAL_POINTS[i];
            float[] second = SAKURA_PETAL_POINTS[i + 1];
            float x1 = first[0] * width;
            float y1 = first[1] * length + offsetY;
            float x2 = second[0] * width;
            float y2 = second[1] * length + offsetY;
            cursor = appendColoredVertex(target, cursor, fanX, fanScreenY, centerColor);
            cursor = appendColoredVertex(target, cursor,
                    rotatedX(centerX, x1, y1, cos, sin), rotatedY(centerY, x1, y1, cos, sin), petalColor);
            cursor = appendColoredVertex(target, cursor,
                    rotatedX(centerX, x2, y2, cos, sin), rotatedY(centerY, x2, y2, cos, sin), petalColor);
        }
        return cursor;
    }

    private static int appendSakuraCore(float[] target, int cursor, float centerX, float centerY,
                                        float radius, int color) {
        int segments = SAKURA_CORE_VERTEX_COUNT / 3;
        for (int i = 0; i < segments; i++) {
            float first = i * 6.2831855f / segments;
            float second = (i + 1) * 6.2831855f / segments;
            cursor = appendColoredVertex(target, cursor, centerX, centerY, color);
            cursor = appendColoredVertex(target, cursor,
                    centerX + (float) Math.cos(first) * radius,
                    centerY + (float) Math.sin(first) * radius, color);
            cursor = appendColoredVertex(target, cursor,
                    centerX + (float) Math.cos(second) * radius,
                    centerY + (float) Math.sin(second) * radius, color);
        }
        return cursor;
    }

    private static int appendColoredVertex(float[] target, int cursor, float x, float y, int color) {
        target[cursor++] = x;
        target[cursor++] = y;
        target[cursor++] = ((color >>> 16) & 255) / 255.0f;
        target[cursor++] = ((color >>> 8) & 255) / 255.0f;
        target[cursor++] = (color & 255) / 255.0f;
        target[cursor++] = ((color >>> 24) & 255) / 255.0f;
        return cursor;
    }

    private static float rotatedX(float centerX, float localX, float localY, float cos, float sin) {
        return centerX + localX * cos - localY * sin;
    }

    private static float rotatedY(float centerY, float localX, float localY, float cos, float sin) {
        return centerY + localX * sin + localY * cos;
    }

    void watermarkPetals(int x, int y, int width, int height, long now) {
        float time = (now % 3600L) / 3600.0f;
        for (int i = 0; i < 4; i++) {
            float phase = fract(time * (0.66f + i * 0.08f) + i * 0.21f);
            int px = x + width - 38 + Math.round(phase * 27.0f
                    + (float) Math.sin(time * 6.2831855f + i * 1.3f) * 2.0f);
            int py = y + 5 + (i % 3) * 4
                    + Math.round((float) Math.sin((phase + i * 0.17f) * 6.2831855f) * 1.3f);
            int a = Math.round((42.0f + i * 8.0f) * (1.0f - phase * 0.35f));
            rect(px, py, px + 3, py + 2, withAlpha(0xFFFFB7D1, a));
            rect(px + 1, py - 1, px + 2, py + 3, withAlpha(0xFFFFF3FA, Math.max(8, a / 3)));
        }
    }

    static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    static int alpha(int color) {
        return (color >>> 24) & 255;
    }

    private static int mix(int first, int second, float progress) {
        float t = Math.max(0.0f, Math.min(1.0f, progress));
        int a = Math.round(((first >>> 24) & 255) + (((second >>> 24) & 255) - ((first >>> 24) & 255)) * t);
        int r = Math.round(((first >>> 16) & 255) + (((second >>> 16) & 255) - ((first >>> 16) & 255)) * t);
        int g = Math.round(((first >>> 8) & 255) + (((second >>> 8) & 255) - ((first >>> 8) & 255)) * t);
        int b = Math.round((first & 255) + ((second & 255) - (first & 255)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static float fract(float value) {
        return value - (float) Math.floor(value);
    }

    private static int intValue(Object value, int fallback) {
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }
}
