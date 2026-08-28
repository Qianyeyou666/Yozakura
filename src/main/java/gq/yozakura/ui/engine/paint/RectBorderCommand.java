package gq.yozakura.ui.engine.paint;

/**
 * 矩形描边命令：在 (x, y) 处绘制 width × height 的矩形边框，四边宽度独立，
 * 可选圆角 radius（MVP 阶段所有角共享同一 radius；后续可扩展为四角独立）。
 *
 * <p>不可变值对象。坐标为逻辑像素。radius 为 0 表示直角。
 *
 * <p>渲染顺序：border 绘制于元素 background 之上、内容之下；border 盒为 (x, y, width, height)，
 * 描边向外不扩张（border 在 border 盒内部，与 CSS 一致）。
 */
public final class RectBorderCommand extends PaintCommand {
    private final float x;
    private final float y;
    private final float width;
    private final float height;
    private final float borderTop;
    private final float borderRight;
    private final float borderBottom;
    private final float borderLeft;
    private final Color color;
    private final float radius;

    public RectBorderCommand(float x, float y, float width, float height,
                             float borderTop, float borderRight,
                             float borderBottom, float borderLeft,
                             Color color, float radius) {
        if (width < 0) {
            throw new IllegalArgumentException("width must not be negative: " + width);
        }
        if (height < 0) {
            throw new IllegalArgumentException("height must not be negative: " + height);
        }
        if (borderTop < 0 || borderRight < 0 || borderBottom < 0 || borderLeft < 0) {
            throw new IllegalArgumentException("border edges must not be negative");
        }
        if (radius < 0) {
            throw new IllegalArgumentException("radius must not be negative: " + radius);
        }
        if (color == null) {
            throw new IllegalArgumentException("color must not be null");
        }
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.borderTop = borderTop;
        this.borderRight = borderRight;
        this.borderBottom = borderBottom;
        this.borderLeft = borderLeft;
        this.color = color;
        this.radius = radius;
    }

    public float x() { return x; }
    public float y() { return y; }
    public float width() { return width; }
    public float height() { return height; }
    public float borderTop() { return borderTop; }
    public float borderRight() { return borderRight; }
    public float borderBottom() { return borderBottom; }
    public float borderLeft() { return borderLeft; }
    public Color color() { return color; }
    public float radius() { return radius; }

    /** 是否为圆角（radius > 0 且至少有一条 border > 0）。 */
    public boolean isRounded() {
        return radius > 0 && (borderTop > 0 || borderRight > 0 || borderBottom > 0 || borderLeft > 0);
    }

    @Override
    public int type() {
        return TYPE_RECT_BORDER;
    }

    @Override
    public void accept(PaintCommandVisitor visitor) {
        visitor.visitRectBorder(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RectBorderCommand)) return false;
        RectBorderCommand c = (RectBorderCommand) o;
        return Float.floatToIntBits(x) == Float.floatToIntBits(c.x)
                && Float.floatToIntBits(y) == Float.floatToIntBits(c.y)
                && Float.floatToIntBits(width) == Float.floatToIntBits(c.width)
                && Float.floatToIntBits(height) == Float.floatToIntBits(c.height)
                && Float.floatToIntBits(borderTop) == Float.floatToIntBits(c.borderTop)
                && Float.floatToIntBits(borderRight) == Float.floatToIntBits(c.borderRight)
                && Float.floatToIntBits(borderBottom) == Float.floatToIntBits(c.borderBottom)
                && Float.floatToIntBits(borderLeft) == Float.floatToIntBits(c.borderLeft)
                && Float.floatToIntBits(radius) == Float.floatToIntBits(c.radius)
                && color.equals(c.color);
    }

    @Override
    public int hashCode() {
        int result = color.hashCode();
        result = 31 * result + Float.floatToIntBits(x);
        result = 31 * result + Float.floatToIntBits(y);
        result = 31 * result + Float.floatToIntBits(width);
        result = 31 * result + Float.floatToIntBits(height);
        result = 31 * result + Float.floatToIntBits(borderTop);
        result = 31 * result + Float.floatToIntBits(borderRight);
        result = 31 * result + Float.floatToIntBits(borderBottom);
        result = 31 * result + Float.floatToIntBits(borderLeft);
        result = 31 * result + Float.floatToIntBits(radius);
        return result;
    }

    @Override
    public String toString() {
        return "RectBorder(" + x + "," + y + " " + width + "x" + height
                + " t=" + borderTop + " r=" + borderRight
                + " b=" + borderBottom + " l=" + borderLeft
                + " r=" + radius + " " + color + ")";
    }
}
