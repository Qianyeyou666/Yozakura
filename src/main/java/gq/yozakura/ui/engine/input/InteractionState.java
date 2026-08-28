package gq.yozakura.ui.engine.input;

import gq.yozakura.ui.engine.dom.ElementNode;

/**
 * 交互状态机：维护 hover / active / focus 三个伪类状态，并在变化时标记 STYLE_DIRTY。
 *
 * <p>AGENTS.md 契约：
 * <ul>
 *   <li>":hover, :active, :focus" 伪类</li>
 *   <li>"Pointer events carry explicit values for left, right and middle buttons.
 *        Do not treat every click as a left click." → 右键不触发 active/focus</li>
 *   <li>状态变化触发 STYLE_DIRTY；同元素重复事件不 dirty（避免无谓重建）</li>
 *   <li>关闭 UI 时重置全部状态（{@link #clearAll()}）</li>
 * </ul>
 *
 * <p>状态规则：
 * <ul>
 *   <li>hover：MOVE 事件更新；命中元素即 hover，未命中（hit=null）则 hover=null</li>
 *   <li>active：LEFT DOWN 设置（active=hit）；LEFT UP 清除；RIGHT/MIDDLE 不触发 active</li>
 *   <li>focus：LEFT DOWN on focusable 元素（button/input/a）触发转移；RIGHT/MIDDLE 不触发</li>
 *   <li>显式 {@link #focus(ElementNode)} / {@link #blur()} 用于程序化聚焦/失焦</li>
 * </ul>
 *
 * <p>dirty 标记独立（AGENTS.md "Dirty-State Model"）：
 * STYLE / LAYOUT / PAINT 互不影响。本类只主动设 STYLE_DIRTY；
 * LAYOUT/PAINT 由调用方通过 {@link #markLayoutDirty()} / {@link #markPaintDirty()} 设置
 * （focus 转移可能触发 layout 重排，但本类不直接判断）。
 *
 * <p>线程模型：单线程（UI 线程）。非线程安全。
 */
public final class InteractionState {

    private ElementNode hover;
    private ElementNode active;
    private ElementNode focus;

    private boolean styleDirty;
    private boolean layoutDirty;
    private boolean paintDirty;

    public InteractionState() {
    }

    // ---- 事件处理 ----

    /**
     * 处理指针事件，更新 hover/active/focus。
     *
     * @param event 指针事件（非 null）
     * @param hit   命中测试结果（LayoutBox.element()），可为 null 表示未命中任何元素
     */
    public void handlePointer(PointerEvent event, ElementNode hit) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        switch (event.type()) {
            case MOVE:
                setHover(hit);
                break;
            case DOWN:
                // 仅 LEFT 触发 active / focus 转移
                if (event.button().isLeft()) {
                    if (hit != null) {
                        setActive(hit);
                        if (isFocusable(hit)) {
                            setFocus(hit);
                        }
                    } else {
                        setActive(null);
                    }
                }
                // RIGHT/MIDDLE DOWN 不改变 active / focus（也不改变 hover，hover 仅由 MOVE 更新）
                break;
            case UP:
                // 仅 LEFT UP 清除 active
                if (event.button().isLeft()) {
                    setActive(null);
                }
                // RIGHT/MIDDLE UP 不改变 active
                break;
            case WHEEL:
                // 滚轮不改变交互状态
                break;
            default:
                break;
        }
    }

    // ---- 显式状态控制 ----

    /** 程序化聚焦；同元素不 dirty。 */
    public void focus(ElementNode element) {
        setFocus(element);
    }

    /** 程序化失焦；无 focus 不 dirty。 */
    public void blur() {
        setFocus(null);
    }

    /**
     * 关闭 UI 时重置全部状态。
     * 任意非空状态被清空时标记 STYLE_DIRTY。
     */
    public void clearAll() {
        boolean changed = hover != null || active != null || focus != null;
        setHoveredChain(hover, false);
        if (active != null) active.setActive(false);
        if (focus != null) focus.setFocused(false);
        hover = null;
        active = null;
        focus = null;
        if (changed) {
            styleDirty = true;
        }
    }

    // ---- 状态查询 ----

    public ElementNode hover() { return hover; }
    public ElementNode active() { return active; }
    public ElementNode focus() { return focus; }

    public boolean isHover(ElementNode e) { return e != null && e == hover; }
    public boolean isActive(ElementNode e) { return e != null && e == active; }
    public boolean isFocus(ElementNode e) { return e != null && e == focus; }

    // ---- dirty 标记 ----

    public boolean isStyleDirty() { return styleDirty; }
    public boolean isLayoutDirty() { return layoutDirty; }
    public boolean isPaintDirty() { return paintDirty; }

    public void clearStyleDirty() { styleDirty = false; }
    public void clearLayoutDirty() { layoutDirty = false; }
    public void clearPaintDirty() { paintDirty = false; }

    /** 外部标记 layout 需重算（如 focus 转移导致 reflow）。 */
    public void markLayoutDirty() { layoutDirty = true; }
    /** 外部标记 paint 需重算。 */
    public void markPaintDirty() { paintDirty = true; }

    // ---- 内部 setter（仅在变化时标 dirty）----

    private void setHover(ElementNode newHover) {
        if (same(newHover, hover)) return;
        setHoveredChain(hover, false);
        hover = newHover;
        setHoveredChain(hover, true);
        styleDirty = true;
    }

    private void setActive(ElementNode newActive) {
        if (same(newActive, active)) return;
        if (active != null) active.setActive(false);
        active = newActive;
        if (active != null) active.setActive(true);
        styleDirty = true;
    }

    private void setFocus(ElementNode newFocus) {
        if (same(newFocus, focus)) return;
        if (focus != null) focus.setFocused(false);
        focus = newFocus;
        if (focus != null) focus.setFocused(true);
        styleDirty = true;
    }

    private static void setHoveredChain(ElementNode element, boolean value) {
        ElementNode current = element;
        while (current != null) {
            current.setHovered(value);
            current = current.parent();
        }
    }

    private static boolean same(ElementNode a, ElementNode b) {
        return a == b;  // 引用相等（DOM 节点唯一）
    }

    /** MVP 可 focus 标签集合：button / input / a。 */
    private static boolean isFocusable(ElementNode e) {
        if (e == null) return false;
        String tag = e.tag();
        if (tag == null) return false;
        return tag.equals("button") || tag.equals("input") || tag.equals("a");
    }
}
