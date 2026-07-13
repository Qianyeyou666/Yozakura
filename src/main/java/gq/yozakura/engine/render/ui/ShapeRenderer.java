package gq.yozakura.engine.render.ui;

import gq.yozakura.util.render.RenderUtil;

public final class ShapeRenderer {
    private final RenderContext context;

    public ShapeRenderer(RenderContext context) {
        this.context = context;
    }

    public void rect(float x, float y, float x2, float y2, int color) {
        RenderUtil.drawRect(x, y, x2, y2, color);
    }

    public void rectWH(float x, float y, float width, float height, int color) {
        rect(x, y, x + width, y + height, color);
    }

    public void borderedRect(float x, float y, float x2, float y2, float thickness, int color) {
        RenderUtil.drawBorderedRect(x, y, x2, y2, thickness, color);
    }

    public void gradient(float x, float y, float x2, float y2, int topColor, int bottomColor) {
        RenderUtil.drawGradientRect(x, y, x2, y2, topColor, bottomColor);
    }

    public void horizontalGradient(float x, float y, float x2, float y2, int leftColor, int rightColor) {
        RenderUtil.drawHorizontalGradientRect(x, y, x2, y2, leftColor, rightColor);
    }

    public void verticalGradient(float x, float y, float x2, float y2, int topColor, int bottomColor) {
        RenderUtil.drawVerticalGradientRect(x, y, x2, y2, topColor, bottomColor);
    }
    public void roundedGradient(float x, float y, float x2, float y2, float radius,
                                int topLeft, int bottomLeft, int topRight, int bottomRight) {
        RenderUtil.drawRoundedGradientRect(x, y, x2, y2, radius, topLeft, bottomLeft, topRight, bottomRight);
    }


    public void roundedHue(float x, float y, float x2, float y2, float radius, float alpha) {
        RenderUtil.drawRoundedHueRect(x, y, x2, y2, radius, alpha);
    }

    public void roundedPalette(float x, float y, float x2, float y2, float radius, float hue, float alpha) {
        RenderUtil.drawRoundedPaletteRect(x, y, x2, y2, radius, hue, alpha);
    }

    public void progressBar(float x, float y, float x2, float y2, float radius,
                            float progress, int backgroundColor, int fillColor) {
        RenderUtil.drawProgressBar(x, y, x2, y2, radius, progress, backgroundColor, fillColor);
    }

    public void line(float x, float y, float x2, float y2, float width, int color) {
        RenderUtil.drawLine(x, y, x2, y2, width, color);
    }

    public void circle(float x, float y, int start, int end, float radius, int color) {
        RenderUtil.drawCircle(x, y, start, end, radius, color);
    }

    public void circleOutline(float x, float y, float radius, float width, int color) {
        RenderUtil.drawCircleOutline(x, y, radius, width, color);
    }

    public void circleBadge(float centerX, float centerY, float radius, float ringWidth,
                            float progress, int fillColor, int trackColor, int progressColor) {
        RenderUtil.drawCircleBadge(centerX, centerY, radius, ringWidth, progress, fillColor, trackColor, progressColor);
    }

    public void rounded(float x, float y, float x2, float y2, float radius, int color) {
        RenderUtil.drawRoundedRect(x, y, x2, y2, radius, color);
    }

    public void joinedRounded(float x, float y, float x2, float y2,
                              float topLeftRadius, float topRightRadius,
                              float bottomRightRadius, float bottomLeftRadius, int color) {
        RenderUtil.drawJoinedRoundedRect(x, y, x2, y2,
                topLeftRadius, topRightRadius, bottomRightRadius, bottomLeftRadius, color);
    }

    public void joinedRounded(float x, float y, float x2, float y2,
                              float topLeftRadius, float topRightRadius,
                              float bottomRightRadius, float bottomLeftRadius,
                              float topJoinStart, float topJoinEnd,
                              float bottomJoinStart, float bottomJoinEnd, int color) {
        RenderUtil.drawJoinedRoundedRect(x, y, x2, y2,
                topLeftRadius, topRightRadius, bottomRightRadius, bottomLeftRadius,
                topJoinStart, topJoinEnd, bottomJoinStart, bottomJoinEnd, color);
    }

    public void roundedWH(float x, float y, float width, float height, float radius, int color) {
        rounded(x, y, x + width, y + height, radius, color);
    }

    public void roundedBorder(float x, float y, float x2, float y2, float radius,
                              float borderWidth, int fillColor, int borderColor) {
        RenderUtil.drawRoundedBorderedRect(x, y, x2, y2, radius, borderWidth, fillColor, borderColor);
    }

    public void roundedBorderWH(float x, float y, float width, float height, float radius,
                                float borderWidth, int fillColor, int borderColor) {
        roundedBorder(x, y, x + width, y + height, radius, borderWidth, fillColor, borderColor);
    }

    public void shadow(float x, float y, float x2, float y2, float radius,
                       int color, int layers, float spread) {
        RenderUtil.drawSoftShadow(x, y, x2, y2, radius, color, layers, spread);
    }

    /**
     * Draws a rounded shadow translated independently from the source panel.
     * Offsets and spread are expressed in the same logical GUI units as the
     * rectangle coordinates.
     */
    public void shadowOffset(float x, float y, float x2, float y2, float radius,
                             float offsetX, float offsetY, int color, int layers, float blurSpread) {
        RenderUtil.drawSoftShadowOffset(x, y, x2, y2, radius,
                offsetX, offsetY, color, layers, blurSpread);
    }

    public RenderContext context() {
        return context;
    }
}
