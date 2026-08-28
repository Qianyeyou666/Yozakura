package gq.yozakura.ui.engine.css;

/**
 * CSS 值。阶段 1 持有原始文本；切片 5 扩展为带类型（长度、颜色、var 等）的结构化值。
 *
 * <p>不可变值对象。{@link #raw()} 返回去除首尾空白后的原始文本，
 * 保留内部空格与括号结构（如 "rgba(0, 0, 0, 0.5)"、"1px solid #ccc"、"var(--a, #fff)"）。
 */
public final class CssValue {
    private final String raw;

    public CssValue(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        this.raw = raw.trim();
    }

    public String raw() {
        return raw;
    }

    public boolean isVar() {
        return raw.startsWith("var(") && raw.endsWith(")");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CssValue)) return false;
        return raw.equals(((CssValue) o).raw);
    }

    @Override
    public int hashCode() {
        return raw.hashCode();
    }

    @Override
    public String toString() {
        return raw;
    }
}
