package gq.yozakura.engine.render.ui;

import gq.yozakura.module.render.HUD;
import gq.yozakura.util.render.RenderUtil;
import gq.yozakura.engine.render.ShaderRenderer;

public final class BlurRenderer {
    private final ShapeRenderer shapes;

    public BlurRenderer(ShapeRenderer shapes) {
        this.shapes = shapes;
    }

    public void glass(float x, float y, float x2, float y2, float radius,
                      float borderWidth, int fillColor, int borderColor) {
        if (HUD.isGrayTheme()) {
            if (!ShaderRenderer.drawFrostedGlass(x, y, x2, y2, radius, borderWidth, fillColor, borderColor)) {
                shapes.roundedBorder(x, y, x2, y2, radius, borderWidth, fillColor, borderColor);
            }
            return;
        }
        RenderUtil.drawFrostedGlassRect(x, y, x2, y2, radius, borderWidth, fillColor, borderColor);
    }
}
