package gq.yozakura.ui.engine.layout;

/**
 * 单位解析上下文：提供 Dimension 解析为像素所需的所有基数。
 *
 * <p>不可变值对象。各字段含义：
 * <ul>
 *   <li>{@code percentBase}：百分比解析的基准（通常是父容器内容区尺寸）</li>
 *   <li>{@code percentBaseSecondary}：保留字段，部分场景（如 translate 百分比）的次要基准</li>
 *   <li>{@code emBase}：当前元素计算后的字号（px），用于 em</li>
 *   <li>{@code remBase}：根元素字号（px），用于 rem</li>
 *   <li>{@code viewportWidth/Height}：视口尺寸（物理像素），用于 vw/vh</li>
 * </ul>
 *
 * <p>字体相关基数非负；视口尺寸非负。percentBase 允许为 0（如尚未布局的根）。
 */
public final class ResolveContext {
    private final float percentBase;
    private final float percentBaseSecondary;
    private final float emBase;
    private final float remBase;
    private final int viewportWidth;
    private final int viewportHeight;

    private ResolveContext(float percentBase, float percentBaseSecondary,
                          float emBase, float remBase,
                          int viewportWidth, int viewportHeight) {
        this.percentBase = percentBase;
        this.percentBaseSecondary = percentBaseSecondary;
        this.emBase = emBase;
        this.remBase = remBase;
        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewportHeight;
    }

    public static ResolveContext of(float percentBase, float percentBaseSecondary,
                                    float emBase, float remBase,
                                    int viewportWidth, int viewportHeight) {
        if (emBase < 0) {
            throw new IllegalArgumentException("emBase must not be negative: " + emBase);
        }
        if (remBase < 0) {
            throw new IllegalArgumentException("remBase must not be negative: " + remBase);
        }
        if (viewportWidth < 0) {
            throw new IllegalArgumentException(
                    "viewportWidth must not be negative: " + viewportWidth);
        }
        if (viewportHeight < 0) {
            throw new IllegalArgumentException(
                    "viewportHeight must not be negative: " + viewportHeight);
        }
        return new ResolveContext(percentBase, percentBaseSecondary,
                emBase, remBase, viewportWidth, viewportHeight);
    }

    public float percentBase() { return percentBase; }
    public float percentBaseSecondary() { return percentBaseSecondary; }
    public float emBase() { return emBase; }
    public float remBase() { return remBase; }
    public int viewportWidth() { return viewportWidth; }
    public int viewportHeight() { return viewportHeight; }
}
