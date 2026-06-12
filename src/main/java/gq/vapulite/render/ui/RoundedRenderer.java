package gq.vapulite.render.ui;

import java.awt.Color;

public final class RoundedRenderer {
    private final ShapeRenderer shapes;

    public RoundedRenderer(ShapeRenderer shapes) {
        this.shapes = shapes;
    }

    public void drawRound(float x, float y, float width, float height, float radius, Color color) {
        shapes.roundedWH(x, y, width, height, radius, color.getRGB());
    }

    public void drawRound(float x, float y, float width, float height, float radius, int color) {
        shapes.roundedWH(x, y, width, height, radius, color);
    }

    public void drawRoundOutline(float x, float y, float width, float height, float radius,
                                 float outlineThickness, Color color, Color outlineColor) {
        shapes.roundedBorderWH(x, y, width, height, radius, outlineThickness,
                color.getRGB(), outlineColor.getRGB());
    }

    public void drawGradientVertical(float x, float y, float width, float height,
                                     float radius, Color top, Color bottom) {
        shapes.gradient(x, y, x + width, y + height, top.getRGB(), bottom.getRGB());
    }

    public void drawGradientHorizontal(float x, float y, float width, float height,
                                       float radius, Color left, Color right) {
        shapes.gradient(x, y, x + width, y + height, left.getRGB(), right.getRGB());
    }
}
