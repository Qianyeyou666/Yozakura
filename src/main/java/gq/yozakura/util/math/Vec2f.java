package gq.yozakura.util.math;

/**
 * Mutable 2D float vector for UI and screen-space math.
 * 可变二维 float 向量，用于 UI 和屏幕空间计算。
 */
public final class Vec2f {
    private float x;
    private float y;

    public Vec2f() {
        this(0.0f, 0.0f);
    }

    public Vec2f(Vec2f vec) {
        this(vec.x, vec.y);
    }

    public Vec2f(double x, double y) {
        this((float) x, (float) y);
    }

    public Vec2f(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public Vec2f setX(float x) {
        this.x = x;
        return this;
    }

    public Vec2f setY(float y) {
        this.y = y;
        return this;
    }

    public Vec2f set(float x, float y) {
        this.x = x;
        this.y = y;
        return this;
    }

    public Vec2f add(Vec2f vec) {
        return new Vec2f(x + vec.x, y + vec.y);
    }

    public Vec2f add(float x, float y) {
        return new Vec2f(this.x + x, this.y + y);
    }

    public Vec2f subtract(Vec2f vec) {
        return new Vec2f(x - vec.x, y - vec.y);
    }

    public Vec2f subtract(float x, float y) {
        return new Vec2f(this.x - x, this.y - y);
    }

    public Vec2f multiply(float scale) {
        return new Vec2f(x * scale, y * scale);
    }

    public Vec2f divide(float divisor) {
        if (Math.abs(divisor) < 1.0E-6f) {
            return new Vec2f();
        }
        return new Vec2f(x / divisor, y / divisor);
    }

    /**
     * Dot product, useful for projection and angle checks.
     * 点积，用于投影和角度判断。
     */
    public float dot(Vec2f vec) {
        return x * vec.x + y * vec.y;
    }

    public float cross(Vec2f vec) {
        return x * vec.y - y * vec.x;
    }

    public float lengthSq() {
        return x * x + y * y;
    }

    public float length() {
        return (float) Math.sqrt(lengthSq());
    }

    public Vec2f normalize() {
        float length = length();
        return length < 1.0E-6f ? new Vec2f() : divide(length);
    }

    public float distanceSq(Vec2f vec) {
        float dx = x - vec.x;
        float dy = y - vec.y;
        return dx * dx + dy * dy;
    }

    public float distanceTo(Vec2f vec) {
        return (float) Math.sqrt(distanceSq(vec));
    }

    public Vec2f midpoint(Vec2f vec) {
        return new Vec2f((x + vec.x) * 0.5f, (y + vec.y) * 0.5f);
    }

    public Vec2f lerp(Vec2f target, float amount) {
        return new Vec2f(MathUtil.lerp(x, target.x, amount), MathUtil.lerp(y, target.y, amount));
    }

    /**
     * Rotate around origin in degrees.
     * 围绕原点按角度旋转。
     */
    public Vec2f rotateDegrees(float degrees) {
        double radians = degrees * MathUtil.DEG_TO_RAD;
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        return new Vec2f((float) (x * cos - y * sin), (float) (x * sin + y * cos));
    }

    public float angleDegrees() {
        return (float) (Math.atan2(y, x) * MathUtil.RAD_TO_DEG);
    }

    public Vec3f toVec3() {
        return new Vec3f(x, y, 0.0);
    }

    public Vec2f copy() {
        return new Vec2f(this);
    }

    public Vec2f transfer(Vec2f vec) {
        this.x = vec.x;
        this.y = vec.y;
        return this;
    }

    @Override
    public String toString() {
        return "Vec2f{x=" + x + ", y=" + y + '}';
    }
}
