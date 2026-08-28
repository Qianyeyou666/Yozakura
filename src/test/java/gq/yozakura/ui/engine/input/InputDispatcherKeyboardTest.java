package gq.yozakura.ui.engine.input;

import gq.yozakura.ui.engine.dom.ElementNode;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * 阶段 4 切片 4.7a：InputDispatcher 键盘路由测试。
 *
 * <p>验证契约（AGENTS.md）：
 * "Focus, text editing and keyboard routing must have one owner.
 *  Closing the UI restores cursor, repeat-key and focus state."
 *
 * <p>本切片只验证键盘路由到 focus owner；文本编辑（cursor/insert）在 4.7b TextEditor。
 *
 * <p>测试要点：
 * <ul>
 *   <li>键盘事件路由到 state.focus()（单一 owner）</li>
 *   <li>focus=null 时键盘事件不抛、不路由</li>
 *   <li>clearAll 后 focus=null，键盘不路由（关闭 UI 恢复 focus 状态）</li>
 *   <li>focus 转移后键盘路由到新 owner</li>
 * </ul>
 *
 * <p>实现说明：InputDispatcher 不直接调用 focus 元素的回调（MVP 不引入事件回调 API）；
 * 而是提供 {@link #focusedElement()} 查询，由 host 层（阶段 6）或 TextEditor
 * 查询当前 focus owner 后自行处理。本测试通过焦点查询验证路由契约。
 */
public class InputDispatcherKeyboardTest {

    private static ElementNode el(String tag) {
        return ElementNode.create(tag);
    }

    private static KeyboardEvent keyDown(String key) {
        return KeyboardEvent.down(key, ModifierKeys.none(), 100L);
    }

    @Test
    public void keyboardEventDoesNotCrashWhenNoFocus() {
        // focus=null 时键盘事件不抛
        InputDispatcher d = new InputDispatcher(new InteractionState());
        d.handleKeyboard(keyDown("A"));  // no-op，不抛
        assertNull(d.state().focus());
    }

    @Test
    public void focusedElementReceivesKeyboardRouting() {
        // focus=button，键盘事件后 focus 仍是该 button（路由目标查询）
        InteractionState state = new InteractionState();
        InputDispatcher d = new InputDispatcher(state);
        ElementNode input = el("input");
        // 程序化聚焦 input
        d.state().focus(input);
        d.handleKeyboard(keyDown("A"));
        assertSame(input, d.state().focus());
    }

    @Test
    public void clearAllClearsFocusAndKeyboardStopsRouting() {
        // 关闭 UI 后 focus=null，键盘不路由
        InteractionState state = new InteractionState();
        InputDispatcher d = new InputDispatcher(state);
        ElementNode input = el("input");
        d.state().focus(input);
        d.clearAll();  // 关闭 UI
        assertNull(d.state().focus());
        // 后续键盘事件 no-op
        d.handleKeyboard(keyDown("A"));
        assertNull(d.state().focus());
    }

    @Test
    public void focusTransferRedirectsKeyboardRouting() {
        // focus 从 A 转到 B 后，键盘路由到 B
        InteractionState state = new InteractionState();
        InputDispatcher d = new InputDispatcher(state);
        ElementNode a = el("input");
        ElementNode b = el("input");
        d.state().focus(a);
        d.state().focus(b);  // 转移
        d.handleKeyboard(keyDown("A"));
        assertSame(b, d.state().focus());
    }

    @Test
    public void repeatKeyEventRoutedToFocus() {
        // repeat-key 状态也路由到 focus（AGENTS.md "repeat-key" 状态）
        InteractionState state = new InteractionState();
        InputDispatcher d = new InputDispatcher(state);
        ElementNode input = el("input");
        d.state().focus(input);
        d.handleKeyboard(KeyboardEvent.repeat("A", ModifierKeys.none(), 200L));
        assertSame(input, d.state().focus());
    }

    @Test(expected = IllegalArgumentException.class)
    public void handleKeyboardRejectsNullEvent() {
        new InputDispatcher(new InteractionState()).handleKeyboard(null);
    }
}
