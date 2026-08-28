package gq.yozakura.ui.engine.input;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 阶段 4 切片 4.7a：KeyboardEvent 值对象测试。
 *
 * <p>验证契约：
 * <ul>
 *   <li>支持 DOWN / UP / REPEAT 三种类型（repeat-key 状态）</li>
 *   <li>显式携带 key（逻辑键名）、char（可打印字符，无则为 0）、modifiers、timestamp</li>
 *   <li>不可变值对象 equals/hashCode</li>
 *   <li>关闭 UI 时由调用方清空 focus，键盘事件不再路由（见 InputDispatcherKeyboardTest）</li>
 * </ul>
 */
public class KeyboardEventTest {

    private static final long T0 = 1000L;

    @Test
    public void downEventCarriesKeyAndModifiers() {
        KeyboardEvent e = KeyboardEvent.down("A", ModifierKeys.none(), T0);
        assertEquals(KeyType.DOWN, e.type());
        assertEquals("A", e.key());
        assertEquals(0, e.character());
        assertTrue(e.modifiers().equals(ModifierKeys.none()));
        assertEquals(T0, e.timestamp());
    }

    @Test
    public void upEventHasUpType() {
        KeyboardEvent e = KeyboardEvent.up("A", ModifierKeys.none(), T0);
        assertEquals(KeyType.UP, e.type());
    }

    @Test
    public void repeatEventHasRepeatType() {
        // 长按重复键（AGENTS.md "repeat-key" 状态）
        KeyboardEvent e = KeyboardEvent.repeat("A", ModifierKeys.none(), T0);
        assertEquals(KeyType.REPEAT, e.type());
    }

    @Test
    public void printableCharacterEvent() {
        // 字符输入事件：key="CHAR"，character='a'
        KeyboardEvent e = KeyboardEvent.character('a', "a", ModifierKeys.none(), T0);
        assertEquals('a', e.character());
        assertEquals("a", e.key());
    }

    @Test
    public void equalsAndHashCodeByAllFields() {
        ModifierKeys mods = ModifierKeys.builder().shift(true).build();
        KeyboardEvent a = KeyboardEvent.down("A", mods, T0);
        KeyboardEvent b = KeyboardEvent.down("A", mods, T0);
        KeyboardEvent diffType = KeyboardEvent.up("A", mods, T0);
        KeyboardEvent diffKey = KeyboardEvent.down("B", mods, T0);
        KeyboardEvent diffMods = KeyboardEvent.down("A", ModifierKeys.none(), T0);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, diffType);
        assertNotEquals(a, diffKey);
        assertNotEquals(a, diffMods);
        assertNotEquals(a, null);
        assertNotEquals(a, "not a keyboard event");
    }

    @Test
    public void modifiersPreservedAcrossTypes() {
        ModifierKeys mods = ModifierKeys.builder().ctrl(true).build();
        KeyboardEvent e = KeyboardEvent.down("C", mods, T0);
        assertTrue(e.modifiers().ctrl());
        assertFalse(e.modifiers().shift());
    }

    @Test
    public void nullKeyRejected() {
        try {
            KeyboardEvent.down(null, ModifierKeys.none(), T0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void nullModifiersRejected() {
        try {
            KeyboardEvent.down("A", null, T0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void negativeTimestampRejected() {
        try {
            KeyboardEvent.down("A", ModifierKeys.none(), -1L);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void toStringContainsTypeAndKey() {
        KeyboardEvent e = KeyboardEvent.down("A", ModifierKeys.none(), T0);
        String s = e.toString();
        assertTrue("toString should contain type: " + s, s.contains("DOWN"));
        assertTrue("toString should contain key: " + s, s.contains("A"));
    }
}
