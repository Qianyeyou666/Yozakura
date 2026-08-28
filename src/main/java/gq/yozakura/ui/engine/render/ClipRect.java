package gq.yozakura.ui.engine.render;

/**
 * Clip 矩形值对象：逻辑像素坐标的裁剪区域。
 *
 * <p>由 {@link BatchedRectangleRenderer} 在 ClipPush 时记录当前 clip 上下文，
 * 随每个 {@link RectangleBatch} 一同提交给 sink，供 renderer 设置 GL_SCISSOR。
 *
 * <p>null 引用表示"无裁剪"（区别于任意非 null 的 ClipRect 实例）。
 * renderer 不应假定 ClipRect 与上层 clip 的关系；批处理已保证同一批内 clip 一致。
 *
 * <p>不可变值对象。坐标非负（构造时校验）。
 */
public final class ClipRect {

    private final float x;
    private final float y;
    private final float width;
    private final float height;

    public ClipRect(float x, float y, float width, float height) {
        if (width < 0) {
            throw new IllegalArgumentException("width must not be negative: " + width);
        }
        if (height < 0) {
            throw new IllegalArgumentException("height must not be negative: " + height);
        }
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public float x() { return x; }
    public float y() { return y; }
    public float width() { return width; }
    public float height() { return height; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClipRect)) return false;
        ClipRect c = (ClipRect) o;
        return Float.floatToIntBits(x) == Float.floatToIntBits(c.x)
                && Float.floatToIntBits(y) == Float.floatToIntBits(c.y)
                && Float.floatToIntBits(width) == Float.floatToIntBits(c.width)
                && Float.floatToIntBits(height) == Float.floatToIntBits(c.height);
    }

    @Override
    public int hashCode() {
        int result = Float.floatToIntBits(x);
        result = 31 * result + Float.floatToIntBits(y);
        result = 31 * result + Float.floatToIntBits(width);
        result = 31 * result + Float.floatToIntBits(height);
        return result;
    }

    @Override
    public String toString() {
        return "ClipRect(" + x + "," + y + " " + width + "x" + height + ")";
    }
}
