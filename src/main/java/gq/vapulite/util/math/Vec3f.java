package gq.vapulite.util.math;

import gq.vapulite.util.render.GLUtils;

/**
 * Mutable 3D double vector for world-space math.
 * 可变三维 double 向量，用于世界空间计算。
 */
public final class Vec3f {
    private double x;
    private double y;
    private double z;

    public Vec3f() {
        this(0.0, 0.0, 0.0);
    }

    public Vec3f(Vec3f vec) {
        this(vec.x, vec.y, vec.z);
    }

    public Vec3f(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public Vec3f setX(double x) {
        this.x = x;
        return this;
    }

    public Vec3f setY(double y) {
        this.y = y;
        return this;
    }

    public Vec3f setZ(double z) {
        this.z = z;
        return this;
    }

    public Vec3f set(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    public Vec3f add(Vec3f vec) {
        return add(vec.x, vec.y, vec.z);
    }

    public Vec3f add(double x, double y, double z) {
        return new Vec3f(this.x + x, this.y + y, this.z + z);
    }

    public Vec3f subtract(Vec3f vec) {
        return subtract(vec.x, vec.y, vec.z);
    }

    public Vec3f subtract(double x, double y, double z) {
        return new Vec3f(this.x - x, this.y - y, this.z - z);
    }

    public Vec3f multiply(double scale) {
        return new Vec3f(x * scale, y * scale, z * scale);
    }

    public Vec3f divide(double divisor) {
        if (Math.abs(divisor) < MathUtil.EPSILON) {
            return new Vec3f();
        }
        return new Vec3f(x / divisor, y / divisor, z / divisor);
    }

    public double dot(Vec3f vec) {
        return x * vec.x + y * vec.y + z * vec.z;
    }

    /**
     * Cross product.
     * 叉积，常用于法线和方向向量计算。
     */
    public Vec3f cross(Vec3f vec) {
        return new Vec3f(
                y * vec.z - z * vec.y,
                z * vec.x - x * vec.z,
                x * vec.y - y * vec.x);
    }

    public double lengthSq() {
        return x * x + y * y + z * z;
    }

    public double length() {
        return Math.sqrt(lengthSq());
    }

    public Vec3f normalize() {
        double length = length();
        return length < MathUtil.EPSILON ? new Vec3f() : divide(length);
    }

    public double distanceSq(Vec3f vec) {
        double dx = x - vec.x;
        double dy = y - vec.y;
        double dz = z - vec.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public double distanceTo(Vec3f vec) {
        return Math.sqrt(distanceSq(vec));
    }

    public Vec3f midpoint(Vec3f vec) {
        return new Vec3f((x + vec.x) * 0.5, (y + vec.y) * 0.5, (z + vec.z) * 0.5);
    }

    public Vec3f lerp(Vec3f target, double amount) {
        return new Vec3f(
                MathUtil.lerp(x, target.x, amount),
                MathUtil.lerp(y, target.y, amount),
                MathUtil.lerp(z, target.z, amount));
    }

    /**
     * Minecraft-style yaw/pitch from this position to target.
     * 从当前坐标朝向目标坐标的 Minecraft 风格 yaw/pitch。
     */
    public Vec2f rotationsTo(Vec3f target) {
        double dx = target.x - x;
        double dy = target.y - y;
        double dz = target.z - z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        return new Vec2f(Math.toDegrees(Math.atan2(dz, dx)) - 90.0,
                -Math.toDegrees(Math.atan2(dy, horizontal)));
    }

    public Vec3f toScreen() {
        return GLUtils.toScreen(this);
    }

    public Vec3f copy() {
        return new Vec3f(this);
    }

    public Vec3f transfer(Vec3f vec) {
        this.x = vec.x;
        this.y = vec.y;
        this.z = vec.z;
        return this;
    }

    @Override
    public String toString() {
        return "Vec3f{x=" + x + ", y=" + y + ", z=" + z + '}';
    }
}
