package gq.yozakura.ui.engine.paint;

/**
 * 矩形填充命令：在 (x, y) 处绘制 width × height 的实心矩形，颜色为 color。
 *
 * <p>不可变值对象。坐标为逻辑像素（与 layout 一致），由 renderer 在 host/viewport 层
 * 转换为物理 framebuffer 坐标。
 *
 * <p>width/height 为 0 合法（渲染器跳过）；为负非法（构造抛异常）。
 */
public final class RectFillCommand extends PaintCommand {
    public static final int EFFECT_NORMAL = 0;
    public static final int EFFECT_HUE = 1;
    public static final int EFFECT_PALETTE = 2;
    private final float x;
    private final float y;
    private final float width;
    private final float height;
    private final Color color;
    private final Color endColor;
    private final float gradientAngleDegrees;
    private final float topLeftRadius;
    private final float topRightRadius;
    private final float bottomRightRadius;
    private final float bottomLeftRadius;
    private final float shadowBlur;
    private final int effect;
    private final float effectValue;

    public RectFillCommand(float x, float y, float width, float height, Color color) {
        this(x, y, width, height, color, 0.0F);
    }

    public RectFillCommand(float x, float y, float width, float height, Color color, float radius) {
        this(x, y, width, height, color, null, 0.0F,
                radius, radius, radius, radius);
    }

    public RectFillCommand(float x, float y, float width, float height, Color color,
                           float topLeftRadius, float topRightRadius,
                           float bottomRightRadius, float bottomLeftRadius) {
        this(x, y, width, height, color, null, 0.0F,
                topLeftRadius, topRightRadius, bottomRightRadius, bottomLeftRadius);
    }

    public RectFillCommand(float x, float y, float width, float height,
                           Color startColor, Color endColor,
                           float gradientAngleDegrees, float radius) {
        this(x, y, width, height, startColor, endColor, gradientAngleDegrees,
                radius, radius, radius, radius);
    }

    public RectFillCommand(float x, float y, float width, float height,
                           Color startColor, Color endColor, float gradientAngleDegrees,
                           float topLeftRadius, float topRightRadius,
                           float bottomRightRadius, float bottomLeftRadius) {
        this(x, y, width, height, startColor, endColor, gradientAngleDegrees,
                topLeftRadius, topRightRadius, bottomRightRadius, bottomLeftRadius, 0.0F,
                EFFECT_NORMAL, 0.0F);
    }

    private RectFillCommand(float x, float y, float width, float height,
                            Color startColor, Color endColor, float gradientAngleDegrees,
                            float topLeftRadius, float topRightRadius,
                            float bottomRightRadius, float bottomLeftRadius,
                            float shadowBlur, int effect, float effectValue) {
        if (width < 0) {
            throw new IllegalArgumentException("width must not be negative: " + width);
        }
        if (height < 0) {
            throw new IllegalArgumentException("height must not be negative: " + height);
        }
        if (startColor == null) {
            throw new IllegalArgumentException("color must not be null");
        }
        if (topLeftRadius < 0.0F || topRightRadius < 0.0F
                || bottomRightRadius < 0.0F || bottomLeftRadius < 0.0F) {
            throw new IllegalArgumentException("corner radii must not be negative");
        }
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.color = startColor;
        this.endColor = endColor;
        this.gradientAngleDegrees = gradientAngleDegrees;
        float[] radii = normalizedRadii(width, height, topLeftRadius, topRightRadius,
                bottomRightRadius, bottomLeftRadius);
        this.topLeftRadius = radii[0];
        this.topRightRadius = radii[1];
        this.bottomRightRadius = radii[2];
        this.bottomLeftRadius = radii[3];
        this.shadowBlur = Math.max(0.0F, shadowBlur);
        this.effect = effect;
        this.effectValue = effectValue;
    }

    public static RectFillCommand shadow(float x, float y, float width, float height,
                                         Color color, float blur,
                                         float topLeftRadius, float topRightRadius,
                                         float bottomRightRadius, float bottomLeftRadius) {
        if (blur <= 0.0F) {
            throw new IllegalArgumentException("shadow blur must be positive");
        }
        return new RectFillCommand(x, y, width, height, color, null, 0.0F,
                topLeftRadius, topRightRadius, bottomRightRadius, bottomLeftRadius, blur,
                EFFECT_NORMAL, 0.0F);
    }

    public static RectFillCommand hue(float x, float y, float width, float height, float radius) {
        return new RectFillCommand(x, y, width, height, Color.fromRgba(1, 1, 1, 1),
                null, 0.0F, radius, radius, radius, radius, 0.0F, EFFECT_HUE, 0.0F);
    }

    public static RectFillCommand palette(float x, float y, float width, float height,
                                           float radius, float hue) {
        return new RectFillCommand(x, y, width, height, Color.fromRgba(1, 1, 1, 1),
                null, 0.0F, radius, radius, radius, radius, 0.0F,
                EFFECT_PALETTE, Math.max(0.0F, Math.min(1.0F, hue)));
    }

    public float x() { return x; }
    public float y() { return y; }
    public float width() { return width; }
    public float height() { return height; }
    public Color color() { return color; }
    public boolean isGradient() { return endColor != null; }
    public Color endColor() { return endColor; }
    public float gradientAngleDegrees() { return gradientAngleDegrees; }
    public float radius() { return topLeftRadius; }
    public float topLeftRadius() { return topLeftRadius; }
    public float topRightRadius() { return topRightRadius; }
    public float bottomRightRadius() { return bottomRightRadius; }
    public float bottomLeftRadius() { return bottomLeftRadius; }
    public boolean isShadow() { return shadowBlur > 0.0F; }
    public float shadowBlur() { return shadowBlur; }
    public int effect() { return effect; }
    public float effectValue() { return effectValue; }

    public RectFillCommand withOpacity(float opacity) {
        float alpha = Math.max(0.0F, Math.min(1.0F, opacity));
        Color start = Color.fromRgba(color.r(), color.g(), color.b(), color.a() * alpha);
        Color end = endColor == null ? null : Color.fromRgba(
                endColor.r(), endColor.g(), endColor.b(), endColor.a() * alpha);
        return new RectFillCommand(x, y, width, height, start, end, gradientAngleDegrees,
                topLeftRadius, topRightRadius, bottomRightRadius, bottomLeftRadius,
                shadowBlur, effect, effectValue);
    }

    @Override
    public int type() {
        return TYPE_RECT_FILL;
    }

    @Override
    public void accept(PaintCommandVisitor visitor) {
        visitor.visitRectFill(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RectFillCommand)) return false;
        RectFillCommand c = (RectFillCommand) o;
        return Float.floatToIntBits(x) == Float.floatToIntBits(c.x)
                && Float.floatToIntBits(y) == Float.floatToIntBits(c.y)
                && Float.floatToIntBits(width) == Float.floatToIntBits(c.width)
                && Float.floatToIntBits(height) == Float.floatToIntBits(c.height)
                && Float.floatToIntBits(topLeftRadius) == Float.floatToIntBits(c.topLeftRadius)
                && Float.floatToIntBits(topRightRadius) == Float.floatToIntBits(c.topRightRadius)
                && Float.floatToIntBits(bottomRightRadius) == Float.floatToIntBits(c.bottomRightRadius)
                && Float.floatToIntBits(bottomLeftRadius) == Float.floatToIntBits(c.bottomLeftRadius)
                && Float.floatToIntBits(gradientAngleDegrees) == Float.floatToIntBits(c.gradientAngleDegrees)
                && Float.floatToIntBits(shadowBlur) == Float.floatToIntBits(c.shadowBlur)
                && effect == c.effect
                && Float.floatToIntBits(effectValue) == Float.floatToIntBits(c.effectValue)
                && color.equals(c.color)
                && (endColor == null ? c.endColor == null : endColor.equals(c.endColor));
    }

    @Override
    public int hashCode() {
        int result = color.hashCode();
        result = 31 * result + Float.floatToIntBits(x);
        result = 31 * result + Float.floatToIntBits(y);
        result = 31 * result + Float.floatToIntBits(width);
        result = 31 * result + Float.floatToIntBits(height);
        result = 31 * result + Float.floatToIntBits(topLeftRadius);
        result = 31 * result + Float.floatToIntBits(topRightRadius);
        result = 31 * result + Float.floatToIntBits(bottomRightRadius);
        result = 31 * result + Float.floatToIntBits(bottomLeftRadius);
        result = 31 * result + Float.floatToIntBits(gradientAngleDegrees);
        result = 31 * result + Float.floatToIntBits(shadowBlur);
        result = 31 * result + effect;
        result = 31 * result + Float.floatToIntBits(effectValue);
        result = 31 * result + (endColor == null ? 0 : endColor.hashCode());
        return result;
    }

    @Override
    public String toString() {
        return "RectFill(" + x + "," + y + " " + width + "x" + height
                + " radii=" + topLeftRadius + "," + topRightRadius + ","
                + bottomRightRadius + "," + bottomLeftRadius + " " + color + ")";
    }

    private static float[] normalizedRadii(float width, float height,
                                           float topLeft, float topRight,
                                           float bottomRight, float bottomLeft) {
        float scale = 1.0F;
        scale = radiusScale(scale, width, topLeft + topRight);
        scale = radiusScale(scale, width, bottomLeft + bottomRight);
        scale = radiusScale(scale, height, topLeft + bottomLeft);
        scale = radiusScale(scale, height, topRight + bottomRight);
        return new float[]{topLeft * scale, topRight * scale,
                bottomRight * scale, bottomLeft * scale};
    }

    private static float radiusScale(float current, float side, float sum) {
        return sum > side && sum > 0.0F ? Math.min(current, side / sum) : current;
    }
}
