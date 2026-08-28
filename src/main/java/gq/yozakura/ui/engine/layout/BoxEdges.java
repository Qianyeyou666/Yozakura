package gq.yozakura.ui.engine.layout;

/**
 * Box model 四向边距基类。存储 top/right/bottom/left 四个 float。
 *
 * <p>子类 {@link MarginEdges}、{@link PaddingEdges}、{@link BorderEdges} 提供语义化解析与约束。
 * 不可变值对象。
 */
public class BoxEdges {
    private final float top;
    private final float right;
    private final float bottom;
    private final float left;

    public BoxEdges(float top, float right, float bottom, float left) {
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.left = left;
    }

    public float top() { return top; }
    public float right() { return right; }
    public float bottom() { return bottom; }
    public float left() { return left; }

    /** left + right。 */
    public float horizontalSum() { return left + right; }

    /** top + bottom。 */
    public float verticalSum() { return top + bottom; }

    public boolean isAllZero() {
        return top == 0f && right == 0f && bottom == 0f && left == 0f;
    }

    public static BoxEdges zero() {
        return new BoxEdges(0, 0, 0, 0);
    }

    public static BoxEdges uniform(float v) {
        return new BoxEdges(v, v, v, v);
    }

    /** 转为 PaddingEdges（不带 auto 语义）。 */
    public PaddingEdges asPadding() {
        return new PaddingEdges(top, right, bottom, left);
    }

    /** 转为 BorderEdges（不带 auto 语义）。 */
    public BorderEdges asBorder() {
        return new BorderEdges(top, right, bottom, left);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BoxEdges)) return false;
        BoxEdges e = (BoxEdges) o;
        return Float.floatToIntBits(top) == Float.floatToIntBits(e.top)
                && Float.floatToIntBits(right) == Float.floatToIntBits(e.right)
                && Float.floatToIntBits(bottom) == Float.floatToIntBits(e.bottom)
                && Float.floatToIntBits(left) == Float.floatToIntBits(e.left);
    }

    @Override
    public int hashCode() {
        int r = Float.floatToIntBits(top);
        r = 31 * r + Float.floatToIntBits(right);
        r = 31 * r + Float.floatToIntBits(bottom);
        r = 31 * r + Float.floatToIntBits(left);
        return r;
    }

    @Override
    public String toString() {
        return top + "/" + right + "/" + bottom + "/" + left;
    }
}
