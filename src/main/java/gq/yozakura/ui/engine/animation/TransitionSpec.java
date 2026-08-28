package gq.yozakura.ui.engine.animation;

/**
 * CSS transition 规则：不可变值对象。
 *
 * <p>AGENTS.md 契约："typography, translate/scale transforms and transitions"。
 *
 * <p>对应 CSS 形如：
 * <pre>
 *   transition: opacity 200ms ease 50ms;
 *   transition: width 300ms linear;
 * </pre>
 *
 * <p>字段：
 * <ul>
 *   <li>{@code property}：CSS 属性名（如 "opacity", "width", "color"）</li>
 *   <li>{@code durationMs}：过渡时长（&gt;= 0；0 表示瞬时）</li>
 *   <li>{@code delayMs}：延迟启动（&gt;= 0）</li>
 *   <li>{@code timingFunction}：缓动函数（非 null）</li>
 * </ul>
 *
 * <p>不可变；相等性按全部字段判定。
 */
public final class TransitionSpec {

    private final String property;
    private final long durationMs;
    private final long delayMs;
    private final TimingFunction timingFunction;

    public TransitionSpec(String property, long durationMs, long delayMs,
                          TimingFunction timingFunction) {
        if (property == null || property.isEmpty()) {
            throw new IllegalArgumentException("property must not be null or empty");
        }
        if (durationMs < 0L) {
            throw new IllegalArgumentException("durationMs must not be negative: " + durationMs);
        }
        if (delayMs < 0L) {
            throw new IllegalArgumentException("delayMs must not be negative: " + delayMs);
        }
        if (timingFunction == null) {
            throw new IllegalArgumentException("timingFunction must not be null");
        }
        this.property = property;
        this.durationMs = durationMs;
        this.delayMs = delayMs;
        this.timingFunction = timingFunction;
    }

    public String property() { return property; }
    public long durationMs() { return durationMs; }
    public long delayMs() { return delayMs; }
    public TimingFunction timingFunction() { return timingFunction; }

    /** 该 spec 是否会影响 layout（vs 仅 paint）。 */
    public boolean isLayoutAffecting() {
        return TransitionStore.isLayoutAffectingProperty(property);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TransitionSpec)) return false;
        TransitionSpec s = (TransitionSpec) o;
        return durationMs == s.durationMs
                && delayMs == s.delayMs
                && property.equals(s.property)
                && timingFunction == s.timingFunction;
    }

    @Override
    public int hashCode() {
        int r = property.hashCode();
        r = 31 * r + (int) (durationMs ^ (durationMs >>> 32));
        r = 31 * r + (int) (delayMs ^ (delayMs >>> 32));
        r = 31 * r + timingFunction.hashCode();
        return r;
    }

    @Override
    public String toString() {
        return "TransitionSpec{" + property + " " + durationMs + "ms"
                + (delayMs > 0 ? " delay=" + delayMs + "ms" : "")
                + " " + timingFunction + "}";
    }
}
