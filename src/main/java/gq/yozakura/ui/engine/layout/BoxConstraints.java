package gq.yozakura.ui.engine.layout;

/**
 * 布局约束：传递给子元素的可用空间与 min/max 限制。
 *
 * <p>不可变值对象。{@code availableWidth/Height} 是父容器为子元素分配的空间；
 * {@code min/maxWidth/Height} 是子元素最终尺寸的钳制范围。
 *
 * <p>max 值为 {@link Float#MAX_VALUE} 表示无上界；min 值为 0 表示无下界。
 */
public final class BoxConstraints {
    private final float minWidth;
    private final float maxWidth;
    private final float minHeight;
    private final float maxHeight;
    private final float availableWidth;
    private final float availableHeight;

    public BoxConstraints(float minWidth, float maxWidth,
                          float minHeight, float maxHeight,
                          float availableWidth, float availableHeight) {
        this.minWidth = minWidth;
        this.maxWidth = maxWidth;
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
        this.availableWidth = availableWidth;
        this.availableHeight = availableHeight;
    }

    /** 强制精确尺寸：min == max == size。 */
    public static BoxConstraints tight(float width, float height) {
        return new BoxConstraints(width, width, height, height, width, height);
    }

    /** 仅设 max，min=0：子元素可在 [0, max] 内自由收缩。 */
    public static BoxConstraints loose(float maxWidth, float maxHeight) {
        return new BoxConstraints(0, maxWidth, 0, maxHeight, maxWidth, maxHeight);
    }

    public float minWidth() { return minWidth; }
    public float maxWidth() { return maxWidth; }
    public float minHeight() { return minHeight; }
    public float maxHeight() { return maxHeight; }
    public float availableWidth() { return availableWidth; }
    public float availableHeight() { return availableHeight; }

    /** 将宽度钳制到 [minWidth, maxWidth]。 */
    public float clampWidth(float w) {
        if (w < minWidth) return minWidth;
        if (w > maxWidth) return maxWidth;
        return w;
    }

    /** 将高度钳制到 [minHeight, maxHeight]。 */
    public float clampHeight(float h) {
        if (h < minHeight) return minHeight;
        if (h > maxHeight) return maxHeight;
        return h;
    }
}
