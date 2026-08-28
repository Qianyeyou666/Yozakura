package gq.yozakura.ui.click.yozakura;

import gq.yozakura.engine.font.CFontRenderer;
import gq.yozakura.engine.font.FontLoaders;

/** Adapts Epsilon's 48px TTF scale model to CFontRenderer's internal half scale. */
public final class EpsilonPanelFonts {
    public static final float SOURCE_PIXEL_SIZE = 48.0f;
    public static final float DEFAULT_SCALE = 0.35f;
    public static final float LOCAL_RENDER_SCALE = 2.0f;
    public static final float BASELINE_COMPENSATION = 3.0f;

    private EpsilonPanelFonts() {
    }

    public static int pixelSize(float scale) {
        return Math.max(1, Math.round(SOURCE_PIXEL_SIZE * DEFAULT_SCALE * LOCAL_RENDER_SCALE * scale));
    }

    public static float lineHeight(float scale) {
        return SOURCE_PIXEL_SIZE * DEFAULT_SCALE * scale;
    }

    public static CFontRenderer text(float scale) {
        return FontLoaders.epsilonPanel(pixelSize(scale));
    }

    public static CFontRenderer icons(float scale) {
        return FontLoaders.epsilonIcons(pixelSize(scale));
    }

    public static float centeredY(float top, float height, float scale) {
        return top + (height - lineHeight(scale)) * 0.5f + BASELINE_COMPENSATION;
    }

    public static float drawText(String text, float x, float top, float scale, int color) {
        return text(scale).drawString(text, x, top + BASELINE_COMPENSATION, color);
    }

    public static void drawCenteredText(String text, float centerX, float top, float height,
                                        float scale, int color) {
        CFontRenderer font = text(scale);
        float x = centerX - font.getStringWidth(text) * 0.5f;
        font.drawString(text, x, centeredY(top, height, scale), color);
    }

    public static void drawCenteredIcon(String glyph, float centerX, float centerY,
                                        float scale, int color) {
        CFontRenderer font = icons(scale);
        float x = centerX - font.getStringWidth(glyph) * 0.5f;
        float y = centerY - lineHeight(scale) * 0.5f + BASELINE_COMPENSATION;
        font.drawString(glyph, x, y, color);
    }
}
