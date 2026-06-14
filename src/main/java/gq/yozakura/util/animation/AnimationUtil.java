package gq.yozakura.util.animation;

/**
 * Small easing and interpolation helpers for frame-based UI animation.
 */
public final class AnimationUtil {
    private AnimationUtil() {
    }

    public enum Ease {
        LINEAR,
        OUT_CUBIC,
        OUT_QUART,
        IN_OUT_CUBIC,
        OUT_BACK
    }

    public static float approach(float current, float target, float speed, float frameScale) {
        float factor = 1.0f - (float) Math.pow(1.0f - clamp(speed, 0.0f, 1.0f), Math.max(0.01f, frameScale));
        if (Math.abs(target - current) <= 0.0005f) {
            return target;
        }
        return current + (target - current) * factor;
    }

    public static float ease(float value, Ease ease) {
        float v = clamp(value, 0.0f, 1.0f);
        if (ease == Ease.OUT_CUBIC) {
            return 1.0f - (float) Math.pow(1.0f - v, 3.0D);
        }
        if (ease == Ease.OUT_QUART) {
            return 1.0f - (float) Math.pow(1.0f - v, 4.0D);
        }
        if (ease == Ease.IN_OUT_CUBIC) {
            return v < 0.5f
                    ? 4.0f * v * v * v
                    : 1.0f - (float) Math.pow(-2.0f * v + 2.0f, 3.0D) / 2.0f;
        }
        if (ease == Ease.OUT_BACK) {
            float c1 = 1.70158f;
            float c3 = c1 + 1.0f;
            return 1.0f + c3 * (float) Math.pow(v - 1.0f, 3.0D) + c1 * (float) Math.pow(v - 1.0f, 2.0D);
        }
        return v;
    }

    public static float lerp(float from, float to, float progress) {
        return from + (to - from) * clamp(progress, 0.0f, 1.0f);
    }

    public static int blend(int from, int to, float progress) {
        float p = clamp(progress, 0.0f, 1.0f);
        int a = Math.round(((from >>> 24) & 255) + (((to >>> 24) & 255) - ((from >>> 24) & 255)) * p);
        int r = Math.round(((from >>> 16) & 255) + (((to >>> 16) & 255) - ((from >>> 16) & 255)) * p);
        int g = Math.round(((from >>> 8) & 255) + (((to >>> 8) & 255) - ((from >>> 8) & 255)) * p);
        int b = Math.round((from & 255) + ((to & 255) - (from & 255)) * p);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
