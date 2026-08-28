package gq.yozakura.ui.engine.animation;

/**
 * CSS transition 缓动函数。
 *
 * <p>AGENTS.md 契约："typography, translate/scale transforms and transitions"。
 *
 * <p>MVP 子集：
 * <ul>
 *   <li>{@link #LINEAR}：线性 t → t</li>
 *   <li>{@link #EASE}：默认 ease（近似 cubic-bezier(0.25, 0.1, 0.25, 1)）</li>
 *   <li>{@link #EASE_IN}：cubic-bezier(0.42, 0, 1, 1)</li>
 *   <li>{@link #EASE_OUT}：cubic-bezier(0, 0, 0.58, 1)</li>
 *   <li>{@link #EASE_IN_OUT}：cubic-bezier(0.42, 0, 0.58, 1)</li>
 * </ul>
 *
 * <p>实现采用预计算三次贝塞尔曲线采样（避免每帧解析）；
 * 输入 t 被钳制到 [0, 1]，输出范围同 [0, 1] 且单调递增。
 */
public enum TimingFunction {

    LINEAR,
    EASE,
    EASE_IN,
    EASE_OUT,
    EASE_IN_OUT;

    /**
     * 解析 CSS 缓动函数字符串；未识别值（含 cubic-bezier(...)）回退为 LINEAR。
     *
     * <p>MVP 不解析任意 cubic-bezier 参数，仅匹配命名值。
     */
    public static TimingFunction parse(String raw) {
        if (raw == null) return LINEAR;
        String s = raw.trim();
        if (s.equals("linear")) return LINEAR;
        if (s.equals("ease")) return EASE;
        if (s.equals("ease-in")) return EASE_IN;
        if (s.equals("ease-out")) return EASE_OUT;
        if (s.equals("ease-in-out")) return EASE_IN_OUT;
        // cubic-bezier(...) 等不支持的语法回退 linear（MVP）
        return LINEAR;
    }

    /**
     * 应用缓动函数；输入钳制到 [0, 1]，输出范围 [0, 1]。
     *
     * <p>使用三次贝塞尔曲线 P(t) = 3(1-t)²t·P1 + 3(1-t)t²·P2 + t³，
     * 其中 P0=(0,0), P3=(1,1)，P1/P2 由各 enum 的控制点决定。
     */
    public float apply(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        switch (this) {
            case LINEAR:
                return t;
            case EASE:
                return bezier(t, 0.25f, 0.1f, 0.25f, 1.0f);
            case EASE_IN:
                return bezier(t, 0.42f, 0.0f, 1.0f, 1.0f);
            case EASE_OUT:
                return bezier(t, 0.0f, 0.0f, 0.58f, 1.0f);
            case EASE_IN_OUT:
                return bezier(t, 0.42f, 0.0f, 0.58f, 1.0f);
            default:
                return t;
        }
    }

    /**
     * 三次贝塞尔 y 值（控制点 (x1,y1)-(x2,y2)，端点 (0,0)-(1,1)）。
     *
     * <p>采用牛顿迭代求给定 X 坐标对应的参数 t，再求 Y(t)。
     * 控制点的 X 须在 [0, 1]（CSS 规范），保证 X(t) 单调可逆。
     */
    private static float bezier(float x, float x1, float y1, float x2, float y2) {
        // 牛顿迭代求参数 u，使 X(u) = x
        float u = x;  // 初始猜测
        for (int i = 0; i < 8; i++) {
            float xu = bezierComponent(u, x1, x2) - x;
            if (Math.abs(xu) < 1e-5f) break;
            float deriv = bezierDerivative(u, x1, x2);
            if (Math.abs(deriv) < 1e-6f) break;
            u -= xu / deriv;
            if (u < 0f) u = 0f;
            if (u > 1f) u = 1f;
        }
        return bezierComponent(u, y1, y2);
    }

    /** 贝塞尔分量 B(u) = 3(1-u)²u·p1 + 3(1-u)u²·p2 + u³ */
    private static float bezierComponent(float u, float p1, float p2) {
        float oneMinusU = 1f - u;
        return 3f * oneMinusU * oneMinusU * u * p1
                + 3f * oneMinusU * u * u * p2
                + u * u * u;
    }

    /** 贝塞尔导数 B'(u) = 3(1-u)²·p1 + 6(1-u)u·(p2-p1) + 3u²·(1-p2) */
    private static float bezierDerivative(float u, float p1, float p2) {
        float oneMinusU = 1f - u;
        return 3f * oneMinusU * oneMinusU * p1
                + 6f * oneMinusU * u * (p2 - p1)
                + 3f * u * u * (1f - p2);
    }
}
