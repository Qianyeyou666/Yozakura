package gq.yozakura.ui.engine.layout;

/**
 * CSS 长度/数值维度。不可变值对象，由 {@link #parse(String)} 从 CSS 字符串解析而来。
 *
 * <p>支持单位：{@code px}、{@code %}、{@code em}、{@code rem}、{@code vw}、{@code vh}、
 * {@code auto}、无单位数值（用于 line-height、flex-grow）。
 * {@code "0"} 按 CSS 规范视为 {@code 0px}。
 *
 * <p>解析失败（含 null/空/非法格式）返回 {@code null}，调用方决定降级策略。
 *
 * <p>{@link #resolveToPx(ResolveContext, float)} 把维度解析为像素值；
 * {@code auto} 单位返回 {@code autoFallback}（由调用方根据上下文提供）。
 */
public final class Dimension {

    /** 支持的单位。 */
    public enum Unit {
        PX,       // 像素
        PERCENT,  // 百分比，相对 percentBase
        EM,       // 相对当前元素字号
        REM,      // 相对根元素字号
        VW,       // 视口宽度的百分比
        VH,       // 视口高度的百分比
        AUTO,     // auto（width/height/margin）
        NUMBER    // 无单位数值（line-height、flex-grow）
    }

    private final float value;
    private final Unit unit;

    private Dimension(float value, Unit unit) {
        this.value = value;
        this.unit = unit;
    }

    // ---- 工厂 ----

    public static Dimension px(float value) {
        return new Dimension(value, Unit.PX);
    }

    public static Dimension zero() {
        return new Dimension(0f, Unit.PX);
    }

    public static Dimension auto() {
        return new Dimension(0f, Unit.AUTO);
    }

    /**
     * 解析 CSS 字符串为 Dimension。
     *
     * <p>接受 "12px"、"50%"、"1.5em"、"1.2rem"、"100vw"、"50vh"、"auto"、"0"、"1.5"、"-10px"。
     * 前后空白允许，内部空白非法。null/空/非法返回 null。
     */
    public static Dimension parse(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        // auto 单独处理
        if (s.equals("auto")) {
            return auto();
        }
        // 解析数值部分（含负数、小数）
        int end = s.length();
        int split = end;
        for (int i = 0; i < end; i++) {
            char c = s.charAt(i);
            boolean isNumChar = (c == '-' || c == '+' || c == '.' ||
                    (c >= '0' && c <= '9'));
            if (!isNumChar) {
                split = i;
                break;
            }
        }
        String numPart = s.substring(0, split);
        String unitPart = s.substring(split); // 不 trim：内部空格非法（前后空格已在入口 trim）
        if (numPart.isEmpty()) {
            return null;
        }
        float value;
        try {
            value = Float.parseFloat(numPart);
        } catch (NumberFormatException e) {
            return null;
        }
        // "+" 单独处理：parseFloat 不接受 "+12"，但 Java 实际接受，先尝试
        if (numPart.equals("+") || numPart.equals("-") || numPart.equals(".")) {
            return null;
        }
        // 单位识别
        Unit unit;
        if (unitPart.isEmpty()) {
            // 无单位：0 视为 px；其他视为 NUMBER（line-height、flex-grow）
            unit = (value == 0f) ? Unit.PX : Unit.NUMBER;
        } else if (unitPart.equals("px")) {
            unit = Unit.PX;
        } else if (unitPart.equals("%")) {
            unit = Unit.PERCENT;
        } else if (unitPart.equals("em")) {
            unit = Unit.EM;
        } else if (unitPart.equals("rem")) {
            unit = Unit.REM;
        } else if (unitPart.equals("vw")) {
            unit = Unit.VW;
        } else if (unitPart.equals("vh")) {
            unit = Unit.VH;
        } else {
            return null;
        }
        return new Dimension(value, unit);
    }

    // ---- 访问 ----

    public float value() {
        return value;
    }

    public Unit unit() {
        return unit;
    }

    public boolean isAuto() {
        return unit == Unit.AUTO;
    }

    /**
     * 解析为像素。
     *
     * @param ctx          解析上下文（提供 percentBase / emBase / remBase / viewport）
     * @param autoFallback auto 单位时返回的值（由调用方根据上下文决定，例如 0 或剩余空间）
     */
    public float resolveToPx(ResolveContext ctx, float autoFallback) {
        switch (unit) {
            case PX:
                return value;
            case PERCENT:
                return value * ctx.percentBase() / 100f;
            case EM:
                return value * ctx.emBase();
            case REM:
                return value * ctx.remBase();
            case VW:
                return value * ctx.viewportWidth() / 100f;
            case VH:
                return value * ctx.viewportHeight() / 100f;
            case AUTO:
                return autoFallback;
            case NUMBER:
                return value;
            default:
                return autoFallback;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Dimension)) return false;
        Dimension d = (Dimension) o;
        return Float.floatToIntBits(value) == Float.floatToIntBits(d.value)
                && unit == d.unit;
    }

    @Override
    public int hashCode() {
        return 31 * unit.hashCode() + Float.floatToIntBits(value);
    }

    @Override
    public String toString() {
        if (unit == Unit.AUTO) {
            return "auto";
        }
        if (unit == Unit.NUMBER) {
            return Float.toString(value);
        }
        String unitText;
        switch (unit) {
            case PX: unitText = "px"; break;
            case PERCENT: unitText = "%"; break;
            case EM: unitText = "em"; break;
            case REM: unitText = "rem"; break;
            case VW: unitText = "vw"; break;
            case VH: unitText = "vh"; break;
            default: unitText = ""; break;
        }
        return Float.toString(value) + unitText;
    }
}
