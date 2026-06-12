package gq.vapulite.util.math;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.ThreadLocalRandom;

public final class MathUtil {
    public static final double EPSILON = 1.0E-9;
    public static final double PI2 = Math.PI * 2.0;
    public static final double DEG_TO_RAD = Math.PI / 180.0;
    public static final double RAD_TO_DEG = 180.0 / Math.PI;

    private MathUtil() {
    }

    /**
     * Clamp a number into [min, max].
     * 将数值限制在 [min, max] 范围内。
     */
    public static int clamp(int value, int min, int max) {
        return value < min ? min : Math.min(value, max);
    }

    public static long clamp(long value, long min, long max) {
        return value < min ? min : Math.min(value, max);
    }

    public static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }

    public static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }

    public static float saturate(float value) {
        return clamp(value, 0.0f, 1.0f);
    }

    public static double saturate(double value) {
        return clamp(value, 0.0, 1.0);
    }

    /**
     * Linear interpolation.
     * 线性插值。
     */
    public static float lerp(float start, float end, float amount) {
        return start + (end - start) * amount;
    }

    public static double lerp(double start, double end, double amount) {
        return start + (end - start) * amount;
    }

    public static double inverseLerp(double start, double end, double value) {
        double range = end - start;
        return Math.abs(range) < EPSILON ? 0.0 : (value - start) / range;
    }

    public static double map(double value, double inMin, double inMax, double outMin, double outMax) {
        return lerp(outMin, outMax, inverseLerp(inMin, inMax, value));
    }

    public static double mapClamped(double value, double inMin, double inMax, double outMin, double outMax) {
        return lerp(outMin, outMax, saturate(inverseLerp(inMin, inMax, value)));
    }

    /**
     * Smooth Hermite interpolation in [0, 1].
     * 平滑 Hermite 插值，输入会限制在 [0, 1]。
     */
    public static double smoothStep(double value) {
        double t = saturate(value);
        return t * t * (3.0 - 2.0 * t);
    }

    public static double easeOutCubic(double value) {
        double t = 1.0 - saturate(value);
        return 1.0 - t * t * t;
    }

    public static double square(double value) {
        return value * value;
    }

    public static float square(float value) {
        return value * value;
    }

    public static double distanceSq(double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        return dx * dx + dy * dy;
    }

    public static double distance(double x1, double y1, double x2, double y2) {
        return Math.sqrt(distanceSq(x1, y1, x2, y2));
    }

    public static double distanceSq(double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        return dx * dx + dy * dy + dz * dz;
    }

    public static double distance(double x1, double y1, double z1, double x2, double y2, double z2) {
        return Math.sqrt(distanceSq(x1, y1, z1, x2, y2, z2));
    }

    /**
     * Round with BigDecimal to avoid String.format overhead.
     * 使用 BigDecimal 四舍五入，避免 String.format 的额外开销。
     */
    public static double round(double value, int places) {
        if (places < 0) {
            throw new IllegalArgumentException("places must be >= 0");
        }
        return BigDecimal.valueOf(value).setScale(places, RoundingMode.HALF_UP).doubleValue();
    }

    public static double roundToIncrement(double value, double increment) {
        if (Math.abs(increment) < EPSILON) {
            return value;
        }
        return Math.round(value / increment) * increment;
    }

    public static int floorToInt(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }

    public static int ceilToInt(double value) {
        int i = (int) value;
        return value > i ? i + 1 : i;
    }

    public static int randomInt(int minInclusive, int maxInclusive) {
        if (maxInclusive < minInclusive) {
            int temp = minInclusive;
            minInclusive = maxInclusive;
            maxInclusive = temp;
        }
        return ThreadLocalRandom.current().nextInt(minInclusive, maxInclusive + 1);
    }

    public static float randomFloat(float min, float max) {
        if (max < min) {
            float temp = min;
            min = max;
            max = temp;
        }
        return min + ThreadLocalRandom.current().nextFloat() * (max - min);
    }

    public static double randomDouble(double min, double max) {
        if (max < min) {
            double temp = min;
            min = max;
            max = temp;
        }
        return min + ThreadLocalRandom.current().nextDouble() * (max - min);
    }

    /**
     * Wrap degrees into [-180, 180).
     * 将角度归一化到 [-180, 180)。
     */
    public static float wrapDegrees(float degrees) {
        degrees %= 360.0f;
        if (degrees >= 180.0f) {
            degrees -= 360.0f;
        }
        if (degrees < -180.0f) {
            degrees += 360.0f;
        }
        return degrees;
    }

    public static double wrapDegrees(double degrees) {
        degrees %= 360.0;
        if (degrees >= 180.0) {
            degrees -= 360.0;
        }
        if (degrees < -180.0) {
            degrees += 360.0;
        }
        return degrees;
    }

    public static double wrapRadians(double radians) {
        radians %= PI2;
        if (radians >= Math.PI) {
            radians -= PI2;
        }
        if (radians < -Math.PI) {
            radians += PI2;
        }
        return radians;
    }

    public static float angleDifference(float from, float to) {
        return wrapDegrees(to - from);
    }

    public static double angleDifference(double from, double to) {
        return wrapDegrees(to - from);
    }

    public static float approach(float current, float target, float maxDelta) {
        if (current < target) {
            return Math.min(current + maxDelta, target);
        }
        return Math.max(current - maxDelta, target);
    }

    public static double approach(double current, double target, double maxDelta) {
        if (current < target) {
            return Math.min(current + maxDelta, target);
        }
        return Math.max(current - maxDelta, target);
    }

    public static boolean approximately(double a, double b) {
        return approximately(a, b, EPSILON);
    }

    public static boolean approximately(double a, double b, double epsilon) {
        return Math.abs(a - b) <= epsilon;
    }

    public static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    public static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    /**
     * Remove Minecraft formatting codes, e.g. section sign + color.
     * 移除 Minecraft 颜色/格式控制码，例如 §a。
     */
    public static String removeColorCode(String text) {
        if (text == null || text.indexOf('\u00A7') < 0) {
            return text;
        }
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00A7' && i + 1 < text.length()) {
                i++;
                continue;
            }
            builder.append(c);
        }
        return builder.toString();
    }

    public static int withAlpha(int color, float alpha) {
        int a = clamp(Math.round(saturate(alpha) * 255.0f), 0, 255);
        return (color & 0x00FFFFFF) | (a << 24);
    }

    public static boolean isParsable(String value, NumberType type) {
        if (value == null) {
            return false;
        }
        try {
            switch (type) {
                case SHORT:
                    Short.parseShort(value);
                    return true;
                case BYTE:
                    Byte.parseByte(value);
                    return true;
                case INT:
                    Integer.parseInt(value);
                    return true;
                case FLOAT:
                    Float.parseFloat(value);
                    return true;
                case DOUBLE:
                    Double.parseDouble(value);
                    return true;
                case LONG:
                    Long.parseLong(value);
                    return true;
                default:
                    return false;
            }
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    public enum NumberType {
        SHORT,
        BYTE,
        INT,
        FLOAT,
        DOUBLE,
        LONG
    }
}
