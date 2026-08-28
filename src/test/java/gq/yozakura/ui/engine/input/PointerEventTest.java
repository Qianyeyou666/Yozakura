package gq.yozakura.ui.engine.input;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 阶段 4 切片 4.2：PointerEvent 值对象契约测试。
 *
 * <p>验证契约：
 * <ul>
 *   <li>DOWN/UP 事件显式携带 button（左/中/右），不退化为左键</li>
 *   <li>MOVE/WHEEL 事件 button=NONE</li>
 *   <li>右键路径完整：右键 down → 右键 up，button 始终为 RIGHT</li>
 *   <li>事件类型、坐标、modifiers、clickCount、wheelDelta、timestamp 全程保留</li>
 *   <li>不可变值对象 equals/hashCode</li>
 *   <li>便捷构造器强约束：down/up 不允许 NONE；move/wheel 强制 NONE</li>
 * </ul>
 */
public class PointerEventTest {

    private static final long T0 = 12345L;

    // ---- DOWN / UP 显式 button ----

    @Test
    public void leftClickDownCarriesLeftButton() {
        PointerEvent e = PointerEvent.down(10f, 20f, PointerButton.LEFT,
                ModifierKeys.none(), 1, T0);
        assertEquals(PointerEventType.DOWN, e.type());
        assertEquals(PointerButton.LEFT, e.button());
        assertEquals(10f, e.x(), 0.0001f);
        assertEquals(20f, e.y(), 0.0001f);
        assertEquals(1, e.clickCount());
        assertEquals(T0, e.timestamp());
    }

    @Test
    public void rightClickDownCarriesRightButton() {
        // 关键测试：右键 down 必须保留 button=RIGHT
        PointerEvent e = PointerEvent.down(10f, 20f, PointerButton.RIGHT,
                ModifierKeys.none(), 1, T0);
        assertEquals(PointerEventType.DOWN, e.type());
        assertEquals(PointerButton.RIGHT, e.button());
        assertTrue(e.button().isRight());
        assertFalse(e.button().isLeft());
    }

    @Test
    public void middleClickDownCarriesMiddleButton() {
        PointerEvent e = PointerEvent.down(5f, 5f, PointerButton.MIDDLE,
                ModifierKeys.none(), 1, T0);
        assertEquals(PointerButton.MIDDLE, e.button());
        assertTrue(e.button().isMiddle());
    }

    @Test
    public void rightClickUpCarriesRightButton() {
        // 关键测试：右键 up 必须保留 button=RIGHT
        PointerEvent e = PointerEvent.up(10f, 20f, PointerButton.RIGHT,
                ModifierKeys.none(), 1, T0);
        assertEquals(PointerEventType.UP, e.type());
        assertEquals(PointerButton.RIGHT, e.button());
    }

    @Test
    public void downRejectsNoneButton() {
        // down 事件必须显式 button，不允许 NONE
        try {
            PointerEvent.down(0f, 0f, PointerButton.NONE, ModifierKeys.none(), 1, T0);
            fail("expected IllegalArgumentException for down with NONE button");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void upRejectsNoneButton() {
        try {
            PointerEvent.up(0f, 0f, PointerButton.NONE, ModifierKeys.none(), 1, T0);
            fail("expected IllegalArgumentException for up with NONE button");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    // ---- MOVE / WHEEL button=NONE ----

    @Test
    public void moveEventHasNoneButton() {
        PointerEvent e = PointerEvent.move(10f, 20f, ModifierKeys.none(), T0);
        assertEquals(PointerEventType.MOVE, e.type());
        assertEquals(PointerButton.NONE, e.button());
        assertTrue(e.button().isNone());
    }

    @Test
    public void wheelEventHasNoneButtonAndDelta() {
        PointerEvent e = PointerEvent.wheel(10f, 20f, 1.5f, ModifierKeys.none(), T0);
        assertEquals(PointerEventType.WHEEL, e.type());
        assertEquals(PointerButton.NONE, e.button());
        assertEquals(1.5f, e.wheelDelta(), 0.0001f);
    }

    @Test
    public void moveEventHasZeroWheelDelta() {
        PointerEvent e = PointerEvent.move(10f, 20f, ModifierKeys.none(), T0);
        assertEquals(0f, e.wheelDelta(), 0.0001f);
    }

    @Test
    public void wheelRejectsZeroDelta() {
        // delta=0 的 wheel 事件无意义
        try {
            PointerEvent.wheel(0f, 0f, 0f, ModifierKeys.none(), T0);
            fail("expected IllegalArgumentException for wheel with zero delta");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    // ---- 完整右键路径：down → up ----

    @Test
    public void rightClickDownUpSequencePreservesButton() {
        // 模拟一次完整右键点击：down 在 (5,5)，up 在 (5,5)
        // 两个事件 button 都必须为 RIGHT
        PointerEvent down = PointerEvent.down(5f, 5f, PointerButton.RIGHT,
                ModifierKeys.none(), 1, T0);
        PointerEvent up = PointerEvent.up(5f, 5f, PointerButton.RIGHT,
                ModifierKeys.none(), 1, T0 + 50L);
        assertEquals(PointerButton.RIGHT, down.button());
        assertEquals(PointerButton.RIGHT, up.button());
        assertEquals(PointerEventType.DOWN, down.type());
        assertEquals(PointerEventType.UP, up.type());
    }

    @Test
    public void rightClickWithShiftModifierPreservesButtonAndModifier() {
        // 右键 + Shift 不应丢失右键标识
        ModifierKeys shift = ModifierKeys.builder().shift(true).build();
        PointerEvent e = PointerEvent.down(0f, 0f, PointerButton.RIGHT, shift, 1, T0);
        assertEquals(PointerButton.RIGHT, e.button());
        assertTrue(e.modifiers().shift());
        assertFalse(e.modifiers().ctrl());
    }

    // ---- modifiers ----

    @Test
    public void modifiersArePreservedAcrossEventTypes() {
        ModifierKeys mods = ModifierKeys.builder().ctrl(true).alt(true).build();
        PointerEvent e = PointerEvent.down(0f, 0f, PointerButton.LEFT, mods, 1, T0);
        assertTrue(e.modifiers().ctrl());
        assertTrue(e.modifiers().alt());
        assertFalse(e.modifiers().shift());
    }

    @Test
    public void noneModifiersIsSharedEmpty() {
        // ModifierKeys.none() 应为共享空实例（零分配友好）
        ModifierKeys a = ModifierKeys.none();
        ModifierKeys b = ModifierKeys.none();
        assertTrue(a == b);  // 同一引用
        assertFalse(a.shift());
        assertFalse(a.ctrl());
        assertFalse(a.alt());
    }

    // ---- clickCount ----

    @Test
    public void doubleClickCarriesClickCountTwo() {
        PointerEvent e = PointerEvent.down(0f, 0f, PointerButton.LEFT,
                ModifierKeys.none(), 2, T0);
        assertEquals(2, e.clickCount());
    }

    @Test
    public void downRejectsNegativeClickCount() {
        try {
            PointerEvent.down(0f, 0f, PointerButton.LEFT, ModifierKeys.none(), -1, T0);
            fail("expected IllegalArgumentException for negative clickCount");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void downRejectsZeroClickCount() {
        try {
            PointerEvent.down(0f, 0f, PointerButton.LEFT, ModifierKeys.none(), 0, T0);
            fail("expected IllegalArgumentException for zero clickCount");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    // ---- 不可变值对象 ----

    @Test
    public void equalsAndHashCodeByAllFields() {
        ModifierKeys mods = ModifierKeys.builder().shift(true).build();
        PointerEvent a = PointerEvent.down(10f, 20f, PointerButton.LEFT, mods, 1, T0);
        PointerEvent b = PointerEvent.down(10f, 20f, PointerButton.LEFT, mods, 1, T0);
        PointerEvent diffButton = PointerEvent.down(10f, 20f, PointerButton.RIGHT, mods, 1, T0);
        PointerEvent diffX = PointerEvent.down(11f, 20f, PointerButton.LEFT, mods, 1, T0);
        PointerEvent diffType = PointerEvent.up(10f, 20f, PointerButton.LEFT, mods, 1, T0);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, diffButton);
        assertNotEquals(a, diffX);
        assertNotEquals(a, diffType);
        assertNotEquals(a, null);
        assertNotEquals(a, "not an event");
    }

    @Test
    public void toStringContainsTypeButtonAndCoordinates() {
        PointerEvent e = PointerEvent.down(10f, 20f, PointerButton.RIGHT,
                ModifierKeys.none(), 1, T0);
        String s = e.toString();
        assertTrue("toString should contain type: " + s, s.contains("DOWN"));
        assertTrue("toString should contain button: " + s, s.contains("RIGHT"));
        assertTrue("toString should contain x: " + s, s.contains("10"));
    }

    // ---- 非法参数 ----

    @Test
    public void downRejectsNullButton() {
        try {
            PointerEvent.down(0f, 0f, null, ModifierKeys.none(), 1, T0);
            fail("expected IllegalArgumentException for null button");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void downRejectsNullModifiers() {
        try {
            PointerEvent.down(0f, 0f, PointerButton.LEFT, null, 1, T0);
            fail("expected IllegalArgumentException for null modifiers");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void downRejectsNegativeTimestamp() {
        try {
            PointerEvent.down(0f, 0f, PointerButton.LEFT, ModifierKeys.none(), 1, -1L);
            fail("expected IllegalArgumentException for negative timestamp");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void nanCoordinatesRejected() {
        try {
            PointerEvent.down(Float.NaN, 0f, PointerButton.LEFT, ModifierKeys.none(), 1, T0);
            fail("expected IllegalArgumentException for NaN x");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }
}
