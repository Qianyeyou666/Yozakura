package gq.vapulite.engine.render.ui;

import gq.vapulite.engine.render.GLStateManager;
import gq.vapulite.engine.render.ShaderRenderer;

public final class LiquidGlassRenderer {
    private static final float DEFAULT_BLUR_RADIUS = 28.0f;
    private static final float DEFAULT_REFRACTION = 0.92f;
    private static final float DEFAULT_HIGHLIGHT = 0.98f;
    private static final float DEFAULT_GRAIN = 0.14f;

    public LiquidGlassRenderer() {
    }

    public void rect(float x, float y, float x2, float y2, int fillColor) {
        roundedBorder(x, y, x2, y2, 0.0f, 0.0f, fillColor, 0);
    }

    public void rounded(float x, float y, float x2, float y2, float radius, int fillColor) {
        roundedBorder(x, y, x2, y2, radius, 0.0f, fillColor, 0);
    }

    public void roundedBorder(float x, float y, float x2, float y2, float radius,
                              float borderWidth, int fillColor, int borderColor) {
        roundedBorder(x, y, x2, y2, radius, borderWidth, fillColor, borderColor,
                DEFAULT_BLUR_RADIUS, DEFAULT_REFRACTION, DEFAULT_HIGHLIGHT, DEFAULT_GRAIN);
    }

    public void roundedBorder(float x, float y, float x2, float y2, float radius,
                              float borderWidth, int fillColor, int borderColor,
                              float blurRadius, float refraction, float highlight,
                              float grainStrength) {
        if (getAlpha(fillColor) <= 0 && getAlpha(borderColor) <= 0) {
            return;
        }
        GLStateManager.begin2D();
        try {
            ShaderRenderer.drawLiquidGlass(x, y, x2, y2, radius, borderWidth, fillColor, borderColor,
                    blurRadius, refraction, highlight, grainStrength);
        } finally {
            GLStateManager.end2D();
        }
    }

    private static int getAlpha(int color) {
        return (color >>> 24) & 255;
    }
}
