package gq.yozakura.ui.engine.input;

import gq.yozakura.ui.engine.dom.ElementNode;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * 阶段 4 切片 4.4：InteractionState 状态机测试。
 *
 * <p>验证契约（AGENTS.md）：
 * <ul>
 *   <li>hover 由 MOVE 更新；命中元素即 hover，未命中则 null</li>
 *   <li>active 由 LEFT DOWN 触发，LEFT UP 清除</li>
 *   <li>右键 DOWN/UP 不触发 active（active 是主键状态）</li>
 *   <li>focus 由 LEFT DOWN on focusable 元素触发（button/input/a）</li>
 *   <li>focus 转移：新 focusable 元素获得焦点，旧元素失焦</li>
 *   <li>状态变化触发 STYLE_DIRTY；同元素重复事件不 dirty</li>
 *   <li>blur() 显式失焦；clearAll() 关闭 UI 重置全部状态</li>
 *   <li>dirty 标记独立：STYLE/LAYOUT/PAINT 互不影响</li>
 * </ul>
 *
 * <p>右键路径是重点测试项：右键不应触发 :active 伪类。
 */
public class InteractionStateTest {

    private static ElementNode el(String tag) {
        return ElementNode.create(tag);
    }

    private static PointerEvent leftDown(float x, float y) {
        return PointerEvent.down(x, y, PointerButton.LEFT, ModifierKeys.none(), 1, 100L);
    }

    private static PointerEvent leftUp(float x, float y) {
        return PointerEvent.up(x, y, PointerButton.LEFT, ModifierKeys.none(), 1, 200L);
    }

    private static PointerEvent rightDown(float x, float y) {
        return PointerEvent.down(x, y, PointerButton.RIGHT, ModifierKeys.none(), 1, 100L);
    }

    private static PointerEvent rightUp(float x, float y) {
        return PointerEvent.up(x, y, PointerButton.RIGHT, ModifierKeys.none(), 1, 200L);
    }

    private static PointerEvent middleDown(float x, float y) {
        return PointerEvent.down(x, y, PointerButton.MIDDLE, ModifierKeys.none(), 1, 100L);
    }

    private static PointerEvent move(float x, float y) {
        return PointerEvent.move(x, y, ModifierKeys.none(), 100L);
    }

    // ---- hover ----

    @Test
    public void moveOverElementSetsHover() {
        InteractionState s = new InteractionState();
        ElementNode a = el("div");
        s.handlePointer(move(10, 10), a);
        assertSame(a, s.hover());
        assertTrue(s.isStyleDirty());
    }

    @Test
    public void hoveringDescendantAlsoHoversItsAncestors() {
        InteractionState s = new InteractionState();
        ElementNode parent = el("button");
        ElementNode child = el("span");
        parent.appendChild(child);

        s.handlePointer(move(10, 10), child);

        assertTrue(child.isHovered());
        assertTrue(parent.isHovered());
    }

    @Test
    public void moveOverDifferentElementUpdatesHoverAndMarksDirty() {
        InteractionState s = new InteractionState();
        ElementNode a = el("div");
        ElementNode b = el("div");
        s.handlePointer(move(10, 10), a);
        s.clearStyleDirty();
        assertFalse(s.isStyleDirty());

        s.handlePointer(move(20, 20), b);
        assertSame(b, s.hover());
        assertTrue(s.isStyleDirty());
    }

    @Test
    public void moveOverSameElementDoesNotMarkDirty() {
        // 关键：避免无谓重建（AGENTS.md 性能目标）
        InteractionState s = new InteractionState();
        ElementNode a = el("div");
        s.handlePointer(move(10, 10), a);
        s.clearStyleDirty();

        s.handlePointer(move(11, 11), a);  // 同元素，新坐标
        assertSame(a, s.hover());
        assertFalse(s.isStyleDirty());
    }

    @Test
    public void moveOverNullClearsHoverAndMarksDirty() {
        InteractionState s = new InteractionState();
        ElementNode a = el("div");
        s.handlePointer(move(10, 10), a);
        s.clearStyleDirty();

        s.handlePointer(move(100, 100), null);
        assertNull(s.hover());
        assertTrue(s.isStyleDirty());
    }

    @Test
    public void moveFromNullToNullDoesNotMarkDirty() {
        InteractionState s = new InteractionState();
        s.handlePointer(move(10, 10), null);
        assertFalse(s.isStyleDirty());
        s.handlePointer(move(20, 20), null);
        assertFalse(s.isStyleDirty());
    }

    // ---- active (LEFT only) ----

    @Test
    public void leftDownOnHoverSetsActive() {
        InteractionState s = new InteractionState();
        ElementNode a = el("div");
        s.handlePointer(move(10, 10), a);
        s.clearStyleDirty();

        s.handlePointer(leftDown(10, 10), a);
        assertSame(a, s.active());
        assertTrue(s.isStyleDirty());
    }

    @Test
    public void leftUpClearsActive() {
        InteractionState s = new InteractionState();
        ElementNode a = el("div");
        s.handlePointer(move(10, 10), a);
        s.handlePointer(leftDown(10, 10), a);
        s.clearStyleDirty();

        s.handlePointer(leftUp(10, 10), a);
        assertNull(s.active());
        assertTrue(s.isStyleDirty());
    }

    @Test
    public void leftUpWithoutActiveDoesNotMarkDirty() {
        InteractionState s = new InteractionState();
        s.handlePointer(leftUp(10, 10), null);
        assertFalse(s.isStyleDirty());
    }

    // ---- 右键不触发 active（关键测试）----

    @Test
    public void rightDownDoesNotSetActive() {
        // 关键：右键不应触发 :active 伪类
        InteractionState s = new InteractionState();
        ElementNode a = el("div");
        s.handlePointer(move(10, 10), a);
        s.clearStyleDirty();

        s.handlePointer(rightDown(10, 10), a);
        assertNull(s.active());
        assertFalse(s.isStyleDirty());  // 右键不改变 active 状态
    }

    @Test
    public void rightUpDoesNotClearActive() {
        // 假设先 LEFT DOWN 设置了 active，RIGHT UP 不应错误清除它
        InteractionState s = new InteractionState();
        ElementNode a = el("div");
        s.handlePointer(move(10, 10), a);
        s.handlePointer(leftDown(10, 10), a);
        s.clearStyleDirty();

        s.handlePointer(rightUp(10, 10), a);
        assertSame(a, s.active());  // active 仍为 a
        assertFalse(s.isStyleDirty());
    }

    @Test
    public void middleDownDoesNotSetActive() {
        InteractionState s = new InteractionState();
        ElementNode a = el("div");
        s.handlePointer(move(10, 10), a);
        s.clearStyleDirty();

        s.handlePointer(middleDown(10, 10), a);
        assertNull(s.active());
        assertFalse(s.isStyleDirty());
    }

    @Test
    public void rightDownDoesNotChangeHover() {
        // 右键不应改变 hover（hover 由 MOVE 更新）
        InteractionState s = new InteractionState();
        ElementNode a = el("div");
        s.handlePointer(move(10, 10), a);
        s.clearStyleDirty();

        s.handlePointer(rightDown(10, 10), a);
        assertSame(a, s.hover());
        assertFalse(s.isStyleDirty());
    }

    // ---- focus 自动转移 ----

    @Test
    public void leftDownOnButtonSetsFocus() {
        InteractionState s = new InteractionState();
        ElementNode btn = el("button");
        s.handlePointer(move(10, 10), btn);
        s.clearStyleDirty();

        s.handlePointer(leftDown(10, 10), btn);
        assertSame(btn, s.focus());
        assertTrue(s.isStyleDirty());
    }

    @Test
    public void leftDownOnInputSetsFocus() {
        InteractionState s = new InteractionState();
        ElementNode input = el("input");
        s.handlePointer(move(10, 10), input);
        s.handlePointer(leftDown(10, 10), input);
        assertSame(input, s.focus());
    }

    @Test
    public void leftDownOnNonFocusableDoesNotChangeFocus() {
        InteractionState s = new InteractionState();
        ElementNode div = el("div");  // div 默认不可 focus
        ElementNode btn = el("button");
        s.handlePointer(move(10, 10), btn);
        s.handlePointer(leftDown(10, 10), btn);
        s.clearStyleDirty();

        s.handlePointer(move(50, 50), div);
        s.clearStyleDirty();
        s.handlePointer(leftDown(50, 50), div);
        assertSame(btn, s.focus());  // focus 仍在 button
    }

    @Test
    public void leftDownOnNewFocusableTransfersFocus() {
        InteractionState s = new InteractionState();
        ElementNode btnA = el("button");
        ElementNode btnB = el("button");
        s.handlePointer(move(10, 10), btnA);
        s.handlePointer(leftDown(10, 10), btnA);
        s.clearStyleDirty();

        s.handlePointer(move(50, 50), btnB);
        s.clearStyleDirty();
        s.handlePointer(leftDown(50, 50), btnB);
        assertSame(btnB, s.focus());
        assertTrue(s.isStyleDirty());
    }

    @Test
    public void rightDownOnButtonDoesNotSetFocus() {
        // 右键不应转移 focus（与 active 一致）
        InteractionState s = new InteractionState();
        ElementNode btn = el("button");
        s.handlePointer(move(10, 10), btn);
        s.clearStyleDirty();

        s.handlePointer(rightDown(10, 10), btn);
        assertNull(s.focus());
        assertFalse(s.isStyleDirty());
    }

    // ---- 显式 blur / focus ----

    @Test
    public void blurClearsFocusAndMarksDirty() {
        InteractionState s = new InteractionState();
        ElementNode btn = el("button");
        s.handlePointer(move(10, 10), btn);
        s.handlePointer(leftDown(10, 10), btn);
        s.clearStyleDirty();

        s.blur();
        assertNull(s.focus());
        assertTrue(s.isStyleDirty());
    }

    @Test
    public void blurWithoutFocusDoesNotMarkDirty() {
        InteractionState s = new InteractionState();
        s.blur();
        assertFalse(s.isStyleDirty());
    }

    @Test
    public void explicitFocusSetsAndMarksDirty() {
        InteractionState s = new InteractionState();
        ElementNode btn = el("button");
        s.focus(btn);
        assertSame(btn, s.focus());
        assertTrue(s.isStyleDirty());
    }

    @Test
    public void explicitFocusToSameElementDoesNotMarkDirty() {
        InteractionState s = new InteractionState();
        ElementNode btn = el("button");
        s.focus(btn);
        s.clearStyleDirty();
        s.focus(btn);
        assertFalse(s.isStyleDirty());
    }

    // ---- clearAll（关闭 UI）----

    @Test
    public void clearAllResetsAllStateAndMarksDirty() {
        InteractionState s = new InteractionState();
        ElementNode btn = el("button");
        s.handlePointer(move(10, 10), btn);
        s.handlePointer(leftDown(10, 10), btn);
        s.clearStyleDirty();

        s.clearAll();
        assertNull(s.hover());
        assertNull(s.active());
        assertNull(s.focus());
        assertTrue(s.isStyleDirty());
    }

    @Test
    public void clearAllOnEmptyStateDoesNotMarkDirty() {
        InteractionState s = new InteractionState();
        s.clearAll();
        assertFalse(s.isStyleDirty());
    }

    // ---- dirty 标记独立性 ----

    @Test
    public void styleDirtyIndependentFromLayoutAndPaint() {
        InteractionState s = new InteractionState();
        ElementNode a = el("div");
        s.handlePointer(move(10, 10), a);
        assertTrue(s.isStyleDirty());
        assertFalse(s.isLayoutDirty());
        assertFalse(s.isPaintDirty());
    }

    @Test
    public void clearStyleDirtyOnlyClearsStyle() {
        InteractionState s = new InteractionState();
        ElementNode a = el("div");
        s.handlePointer(move(10, 10), a);
        s.markLayoutDirty();
        s.markPaintDirty();

        s.clearStyleDirty();
        assertFalse(s.isStyleDirty());
        assertTrue(s.isLayoutDirty());
        assertTrue(s.isPaintDirty());
    }

    // ---- 便捷查询 ----

    @Test
    public void isHoverReturnsTrueOnlyForHoveredElement() {
        InteractionState s = new InteractionState();
        ElementNode a = el("div");
        ElementNode b = el("div");
        s.handlePointer(move(10, 10), a);
        assertTrue(s.isHover(a));
        assertFalse(s.isHover(b));
        assertFalse(s.isHover(null));
    }

    @Test
    public void isActiveReturnsTrueOnlyForActiveElement() {
        InteractionState s = new InteractionState();
        ElementNode a = el("div");
        ElementNode b = el("div");
        s.handlePointer(move(10, 10), a);
        s.handlePointer(leftDown(10, 10), a);
        assertTrue(s.isActive(a));
        assertFalse(s.isActive(b));
    }

    @Test
    public void isFocusReturnsTrueOnlyForFocusedElement() {
        InteractionState s = new InteractionState();
        ElementNode a = el("button");
        ElementNode b = el("button");
        s.handlePointer(move(10, 10), a);
        s.handlePointer(leftDown(10, 10), a);
        assertTrue(s.isFocus(a));
        assertFalse(s.isFocus(b));
    }

    // ---- 非法参数 ----

    @Test(expected = IllegalArgumentException.class)
    public void handlePointerRejectsNullEvent() {
        new InteractionState().handlePointer(null, null);
    }
}
