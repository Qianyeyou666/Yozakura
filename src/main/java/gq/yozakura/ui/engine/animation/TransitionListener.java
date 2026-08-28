package gq.yozakura.ui.engine.animation;

import gq.yozakura.ui.engine.dom.ElementNode;

/**
 * 过渡回调：由 {@link TransitionRunner} 在每帧 progress 与完成时调用。
 *
 * <p>AGENTS.md 契约：
 * <ul>
 *   <li>"Separate paint-only transitions from layout transitions."</li>
 *   <li>{@code layoutAffecting} 参数让调用方决定标 LAYOUT 还是 PAINT dirty</li>
 * </ul>
 *
 * <p>调用方（host 层）应在回调中：
 * <ul>
 *   <li>progress：将 currentValue 应用到 ComputedStyle（覆盖），并标记对应 dirty</li>
 *   <li>completed：将 finalValue 应用到 ComputedStyle，并标记对应 dirty</li>
 * </ul>
 *
 * <p>线程模型：单线程（UI 线程）。回调在 {@link TransitionRunner#tick(long)} 内同步调用。
 */
public interface TransitionListener {

    /**
     * 过渡进行中：每帧（tick）调用一次。
     *
     * @param element         目标元素
     * @param property        CSS 属性名
     * @param value           当前插值结果
     * @param layoutAffecting 是否影响 layout（true → LAYOUT dirty, false → PAINT dirty）
     */
    void onTransitionProgress(ElementNode element, String property,
                               float value, boolean layoutAffecting);

    /**
     * 过渡完成：调用后该 (element, property) 不再被 tick 通知。
     *
     * @param element         目标元素
     * @param property        CSS 属性名
     * @param finalValue      最终值（= spec.toValue）
     * @param layoutAffecting 是否影响 layout
     */
    void onTransitionCompleted(ElementNode element, String property,
                                float finalValue, boolean layoutAffecting);
}
