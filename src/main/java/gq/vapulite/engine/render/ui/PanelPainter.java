package gq.vapulite.engine.render.ui;

import gq.vapulite.module.render.HUD;

public final class PanelPainter {
    private final ShapeRenderer shapes;
    private final BlurRenderer blur;

    public PanelPainter(ShapeRenderer shapes, BlurRenderer blur) {
        this.shapes = shapes;
        this.blur = blur;
    }

    public void panel(float x, float y, float x2, float y2, float radius, float borderWidth,
                      int fillColor, int borderColor) {
        if (HUD.isSakuraTheme() || HUD.isLightTheme()) {
            shapes.roundedBorder(x, y, x2, y2, radius, borderWidth, fillColor, borderColor);
            return;
        }
        blur.glass(x, y, x2, y2, radius, borderWidth, fillColor, borderColor);
    }

    public void shadow(float x, float y, float x2, float y2, float radius,
                       int color, int layers, float spread) {
        shapes.shadow(x, y, x2, y2, radius, color, layers, spread);
    }
}
