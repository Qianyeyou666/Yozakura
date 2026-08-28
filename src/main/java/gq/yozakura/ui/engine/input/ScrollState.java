package gq.yozakura.ui.engine.input;

/**
 * 滚动容器状态：维护 scrollTop 与 maxScroll，处理 clamp 与 dirty 标记。
 *
 * <p>每个 overflow:auto/scroll 元素持有一个 ScrollState。
 * host 层（阶段 6）在收到 WheelEvent 时，沿 target 祖先链查找最近的 ScrollState，
 * 调用 {@link #scrollBy(float)} 应用增量。
 *
 * <p>scroll 变化标记 PAINT_DIRTY（content offset 变化需重画，但不需 reflow）。
 * 已到边界时 scrollBy 不变化、不 dirty。
 *
 * <p>clamp 规则：scrollTop 始终在 [0, maxScroll]。
 * maxScroll 减小时 scrollTop 自动 clamp 到新 max。
 *
 * <p>线程模型：单线程（UI 线程）。非线程安全。
 */
public final class ScrollState {

    private float scrollTop;
    private float maxScroll;
    private boolean paintDirty;

    public ScrollState() {
    }

    public float scrollTop() {
        return scrollTop;
    }

    public float maxScroll() {
        return maxScroll;
    }

    /**
     * 设置最大滚动量；负数视为 0；NaN 抛 IllegalArgumentException。
     * scrollTop 自动 clamp 到新范围。
     */
    public void setMaxScroll(float max) {
        if (Float.isNaN(max)) {
            throw new IllegalArgumentException("maxScroll must not be NaN");
        }
        if (max < 0f) {
            max = 0f;
        }
        this.maxScroll = max;
        clampScrollTop();
    }

    /** 直接设置 scrollTop；clamp 到 [0, maxScroll]。变化时标记 PAINT_DIRTY。 */
    public void setScrollTop(float value) {
        float clamped = clamp(value);
        if (clamped == scrollTop) {
            return;
        }
        scrollTop = clamped;
        paintDirty = true;
    }

    /**
     * 按增量滚动；返回实际增量（被 clamp 截断的部分不计）。
     * delta > 0 向下滚动（scrollTop 增大）。
     * 实际变化时标记 PAINT_DIRTY；已到边界无变化时不 dirty。
     */
    public float scrollBy(float delta) {
        float target = scrollTop + delta;
        float clamped = clamp(target);
        float actual = clamped - scrollTop;
        if (actual == 0f) {
            return 0f;
        }
        scrollTop = clamped;
        paintDirty = true;
        return actual;
    }

    public boolean canScrollUp() {
        return scrollTop > 0f;
    }

    public boolean canScrollDown() {
        return scrollTop < maxScroll;
    }

    public boolean isScrollable() {
        return maxScroll > 0f;
    }

    public boolean isPaintDirty() {
        return paintDirty;
    }

    public void clearPaintDirty() {
        paintDirty = false;
    }

    private void clampScrollTop() {
        float clamped = clamp(scrollTop);
        if (clamped != scrollTop) {
            scrollTop = clamped;
            paintDirty = true;
        }
    }

    private float clamp(float v) {
        if (v < 0f) return 0f;
        if (v > maxScroll) return maxScroll;
        return v;
    }
}
