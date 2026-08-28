package gq.yozakura.ui.engine.animation;

/**
 * 已完成过渡的快照：由 {@link TransitionStore#update(long)} 返回。
 *
 * <p>不可变值对象；调用方据此标记 PAINT/LAYOUT dirty 并应用 finalValue。
 *
 * <p>{@link #isLayoutAffecting()} 用于决定标 LAYOUT 还是 PAINT dirty。
 */
public final class CompletedTransition {

    private final String property;
    private final float finalValue;
    private final boolean layoutAffecting;

    public CompletedTransition(String property, float finalValue, boolean layoutAffecting) {
        if (property == null || property.isEmpty()) {
            throw new IllegalArgumentException("property must not be null or empty");
        }
        this.property = property;
        this.finalValue = finalValue;
        this.layoutAffecting = layoutAffecting;
    }

    public String property() { return property; }
    public float finalValue() { return finalValue; }
    public boolean isLayoutAffecting() { return layoutAffecting; }

    @Override
    public String toString() {
        return "CompletedTransition{" + property + "=" + finalValue
                + (layoutAffecting ? " layout" : " paint") + "}";
    }
}
