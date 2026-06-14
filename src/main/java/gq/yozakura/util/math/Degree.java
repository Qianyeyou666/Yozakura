package gq.yozakura.util.math;

/**
 * Immutable angle value stored in degrees.
 * 不可变角度值，内部统一以 degree 存储。
 */
public final class Degree {
    private final double value;

    private Degree(double value) {
        this.value = value;
    }

    public static Degree of(double degrees) {
        return new Degree(degrees);
    }

    public static Degree fromRadians(double radians) {
        return new Degree(radians * MathUtil.RAD_TO_DEG);
    }

    public double degrees() {
        return value;
    }

    public double radians() {
        return value * MathUtil.DEG_TO_RAD;
    }

    /**
     * Wrap angle into [-180, 180).
     * 将角度归一化到 [-180, 180)。
     */
    public Degree wrapped() {
        return new Degree(MathUtil.wrapDegrees(value));
    }

    public Degree add(double degrees) {
        return new Degree(value + degrees);
    }

    public Degree subtract(double degrees) {
        return new Degree(value - degrees);
    }

    public Degree lerp(Degree target, double amount) {
        return new Degree(MathUtil.lerp(value, target.value, amount));
    }

    /**
     * Shortest signed difference to target.
     * 到目标角度的最短有符号差值。
     */
    public double differenceTo(Degree target) {
        return MathUtil.angleDifference(value, target.value);
    }

    @Override
    public String toString() {
        return "Degree{value=" + value + '}';
    }
}
