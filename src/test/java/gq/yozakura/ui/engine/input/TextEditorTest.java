package gq.yozakura.ui.engine.input;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 阶段 4 切片 4.7b：TextEditor 单行文本输入测试。
 *
 * <p>验证契约（AGENTS.md）：
 * <ul>
 *   <li>"Focus, text editing and keyboard routing must have one owner."</li>
 *   <li>单行文本：cursor 定位、字符插入、Backspace/Delete、Home/End、Left/Right</li>
 *   <li>由 {@link KeyboardEvent} 驱动；不可打印字符（控制键）被忽略</li>
 *   <li>cursor 范围 [0, length]；插入在 cursor 处；删除边界安全</li>
 * </ul>
 *
 * <p>不覆盖：多行（Enter 忽略）、选区、剪贴板（阶段 6+ 视需要）。
 */
public class TextEditorTest {

    private static KeyboardEvent charEvent(char c) {
        return KeyboardEvent.character(c, String.valueOf(c), ModifierKeys.none(), 100L);
    }

    private static KeyboardEvent keyDown(String key) {
        return KeyboardEvent.down(key, ModifierKeys.none(), 100L);
    }

    @Test
    public void emptyEditorHasZeroCursor() {
        TextEditor ed = new TextEditor();
        assertEquals("", ed.text());
        assertEquals(0, ed.cursor());
        assertTrue(ed.isEmpty());
    }

    @Test
    public void insertPrintableAppendsAtCursor() {
        TextEditor ed = new TextEditor();
        ed.handleKey(charEvent('a'));
        ed.handleKey(charEvent('b'));
        ed.handleKey(charEvent('c'));
        assertEquals("abc", ed.text());
        assertEquals(3, ed.cursor());
    }

    @Test
    public void insertInMiddleShiftsTail() {
        TextEditor ed = new TextEditor();
        ed.setText("ac");
        ed.setCursor(1);
        ed.handleKey(charEvent('b'));
        assertEquals("abc", ed.text());
        assertEquals(2, ed.cursor());
    }

    @Test
    public void nonPrintableControlKeyDoesNotInsert() {
        TextEditor ed = new TextEditor();
        // DOWN 事件（无 character）不应插入
        ed.handleKey(keyDown("A"));
        assertEquals("", ed.text());
        assertEquals(0, ed.cursor());
    }

    @Test
    public void backspaceDeletesBeforeCursor() {
        TextEditor ed = new TextEditor();
        ed.setText("abc");
        ed.setCursor(3);
        ed.handleKey(keyDown("BACKSPACE"));
        assertEquals("ab", ed.text());
        assertEquals(2, ed.cursor());
    }

    @Test
    public void backspaceAtStartIsNoOp() {
        TextEditor ed = new TextEditor();
        ed.setText("abc");
        ed.setCursor(0);
        ed.handleKey(keyDown("BACKSPACE"));
        assertEquals("abc", ed.text());
        assertEquals(0, ed.cursor());
    }

    @Test
    public void deleteDeletesAfterCursor() {
        TextEditor ed = new TextEditor();
        ed.setText("abc");
        ed.setCursor(1);
        ed.handleKey(keyDown("DELETE"));
        assertEquals("ac", ed.text());
        assertEquals(1, ed.cursor());
    }

    @Test
    public void deleteAtEndIsNoOp() {
        TextEditor ed = new TextEditor();
        ed.setText("abc");
        ed.setCursor(3);
        ed.handleKey(keyDown("DELETE"));
        assertEquals("abc", ed.text());
        assertEquals(3, ed.cursor());
    }

    @Test
    public void leftArrowMovesCursorBack() {
        TextEditor ed = new TextEditor();
        ed.setText("abc");
        ed.setCursor(3);
        ed.handleKey(keyDown("LEFT"));
        assertEquals(2, ed.cursor());
        assertEquals("abc", ed.text());
    }

    @Test
    public void leftArrowAtStartIsNoOp() {
        TextEditor ed = new TextEditor();
        ed.setText("abc");
        ed.setCursor(0);
        ed.handleKey(keyDown("LEFT"));
        assertEquals(0, ed.cursor());
    }

    @Test
    public void rightArrowMovesCursorForward() {
        TextEditor ed = new TextEditor();
        ed.setText("abc");
        ed.setCursor(0);
        ed.handleKey(keyDown("RIGHT"));
        assertEquals(1, ed.cursor());
    }

    @Test
    public void rightArrowAtEndIsNoOp() {
        TextEditor ed = new TextEditor();
        ed.setText("abc");
        ed.setCursor(3);
        ed.handleKey(keyDown("RIGHT"));
        assertEquals(3, ed.cursor());
    }

    @Test
    public void homeMovesCursorToStart() {
        TextEditor ed = new TextEditor();
        ed.setText("abc");
        ed.setCursor(2);
        ed.handleKey(keyDown("HOME"));
        assertEquals(0, ed.cursor());
    }

    @Test
    public void endMovesCursorToEnd() {
        TextEditor ed = new TextEditor();
        ed.setText("abc");
        ed.setCursor(0);
        ed.handleKey(keyDown("END"));
        assertEquals(3, ed.cursor());
    }

    @Test
    public void enterIsIgnoredInSingleLineEditor() {
        TextEditor ed = new TextEditor();
        ed.setText("abc");
        ed.setCursor(3);
        // Enter 不应插入换行（单行编辑器）
        ed.handleKey(KeyboardEvent.character('\n', "ENTER", ModifierKeys.none(), 100L));
        assertEquals("abc", ed.text());
        assertEquals(3, ed.cursor());
    }

    @Test
    public void setTextClampsCursorToLength() {
        TextEditor ed = new TextEditor();
        ed.setText("abc");
        ed.setCursor(5);  // 越界 → 钳制到 3
        assertEquals(3, ed.cursor());
    }

    @Test
    public void clearResetsTextAndCursor() {
        TextEditor ed = new TextEditor();
        ed.setText("abc");
        ed.setCursor(2);
        ed.clear();
        assertEquals("", ed.text());
        assertEquals(0, ed.cursor());
        assertTrue(ed.isEmpty());
    }

    @Test
    public void isDirtySetOnContentChange() {
        TextEditor ed = new TextEditor();
        assertFalse(ed.isDirty());
        ed.handleKey(charEvent('a'));
        assertTrue(ed.isDirty());
        ed.clearDirty();
        assertFalse(ed.isDirty());
    }

    @Test
    public void cursorOnlyMovementDoesNotSetDirty() {
        TextEditor ed = new TextEditor();
        ed.setText("abc");
        ed.clearDirty();
        ed.handleKey(keyDown("LEFT"));
        assertFalse(ed.isDirty());
    }

    @Test
    public void maxLengthEnforcedOnInsert() {
        TextEditor ed = new TextEditor(3);
        ed.handleKey(charEvent('a'));
        ed.handleKey(charEvent('b'));
        ed.handleKey(charEvent('c'));
        ed.handleKey(charEvent('d'));  // 超长 → 忽略
        assertEquals("abc", ed.text());
        assertEquals(3, ed.cursor());
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeMaxLengthRejected() {
        new TextEditor(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setCursorNegativeRejected() {
        TextEditor ed = new TextEditor();
        ed.setCursor(-1);
    }
}
