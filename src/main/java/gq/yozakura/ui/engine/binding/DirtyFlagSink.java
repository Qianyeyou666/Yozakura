package gq.yozakura.ui.engine.binding;

/**
 * Dirty 标记接收器：由 host 层（DocumentContext）实现，binding 调用以通知 dirty。
 *
 * <p>AGENTS.md 契约（"Dirty-State Model"）：
 * <ul>
 *   <li>STYLE_DIRTY: selector state, inherited property or CSS variable changed</li>
 *   <li>LAYOUT_DIRTY: dimensions, position, font metrics or child geometry changed</li>
 *   <li>PAINT_DIRTY: color, opacity, border, shadow or visual content changed</li>
 *   <li>COMMANDS_DIRTY: paint command list must be regenerated</li>
 *   <li>RESOURCE_DIRTY: image, glyph or atlas region must be uploaded</li>
 * </ul>
 *
 * <p>binding 触发的 dirty：
 * <ul>
 *   <li>ClassBinding / AttributeBinding：STYLE_DIRTY（selector 匹配可能变化）</li>
 *   <li>TransitionRunner 回调：PAINT 或 LAYOUT（取决于属性）</li>
 * </ul>
 *
 * <p>线程模型：单线程（UI 线程）。
 */
public interface DirtyFlagSink {

    /** 标记 STYLE_DIRTY：需要重新解析 selector 与 cascade。 */
    void markStyleDirty();

    /** 标记 LAYOUT_DIRTY：需要重新计算 layout。 */
    void markLayoutDirty();

    /** 标记 PAINT_DIRTY：需要重新生成 paint commands。 */
    void markPaintDirty();
}
