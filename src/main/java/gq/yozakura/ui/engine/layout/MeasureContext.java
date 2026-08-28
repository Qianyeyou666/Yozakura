package gq.yozakura.ui.engine.layout;

/**
 * 布局度量上下文：提供与 Minecraft 主机相关的环境度量。
 *
 * <p>由 {@code MinecraftUiHost} 在阶段 9 实现；阶段 2-8 测试用 stub 实现。
 *
 * <p>提供：
 * <ul>
 *   <li>{@code viewportWidth/Height}：视口物理像素尺寸，用于 vw/vh 单位解析</li>
 *   <li>{@code rootFontSizePx}：根元素默认字号（来自 Minecraft 主机或 :root 样式前的回退），
 *       作为 rem 单位的基准以及根元素 em 的初始 emBase</li>
 * </ul>
 *
 * <p>不提供 em 基准：em 基于元素自身计算后的 font-size，由布局递归传递，不在环境上下文中。
 */
public interface MeasureContext {
    int viewportWidth();
    int viewportHeight();
    float rootFontSizePx();
}
