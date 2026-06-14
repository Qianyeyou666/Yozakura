package gq.yozakura.util.math;

/**
 * Mutable four-float rectangle/vector container.
 * 可变四 float 容器，常用于 x/y/width/height 矩形数据。
 */
public final class Vec4f {
    private float x;
    private float y;
    private float w;
    private float h;

    public Vec4f() {
        this(0.0f, 0.0f, 0.0f, 0.0f);
    }

    public Vec4f(Vec4f vec) {
        this(vec.x, vec.y, vec.w, vec.h);
    }

    public Vec4f(float x, float y, float w, float h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getW() {
        return w;
    }

    public float getH() {
        return h;
    }

    public Vec4f setX(float x) {
        this.x = x;
        return this;
    }

    public Vec4f setY(float y) {
        this.y = y;
        return this;
    }

    public Vec4f setW(float w) {
        this.w = w;
        return this;
    }

    public Vec4f setH(float h) {
        this.h = h;
        return this;
    }

    public Vec4f set(float x, float y, float w, float h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        return this;
    }

    public float left() {
        return x;
    }

    public float top() {
        return y;
    }

    public float right() {
        return x + w;
    }

    public float bottom() {
        return y + h;
    }

    public float area() {
        return Math.max(0.0f, w) * Math.max(0.0f, h);
    }

    /**
     * Point containment in rectangle bounds.
     * 判断点是否在矩形范围内。
     */
    public boolean contains(float px, float py) {
        return px >= left() && px <= right() && py >= top() && py <= bottom();
    }

    public boolean intersects(Vec4f other) {
        return right() > other.left()
                && other.right() > left()
                && bottom() > other.top()
                && other.bottom() > top();
    }

    public Vec4f translate(float dx, float dy) {
        return new Vec4f(x + dx, y + dy, w, h);
    }

    public Vec4f inset(float amount) {
        return new Vec4f(x + amount, y + amount, w - amount * 2.0f, h - amount * 2.0f);
    }

    public Vec4f copy() {
        return new Vec4f(this);
    }

    @Override
    public String toString() {
        return "Vec4f{x=" + x + ", y=" + y + ", w=" + w + ", h=" + h + '}';
    }
}
