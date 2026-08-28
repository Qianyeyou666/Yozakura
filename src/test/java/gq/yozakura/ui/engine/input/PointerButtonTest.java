package gq.yozakura.ui.engine.input;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 阶段 4 切片 4.2：PointerButton 枚举契约测试。
 *
 * <p>验证契约（AGENTS.md）：
 * "Pointer events carry explicit values for left, right and middle buttons.
 *  Do not treat every click as a left click."
 *
 * <p>本测试覆盖 GLFW 鼠标按钮常量到 PointerButton 的映射，
 * 重点验证右键 (GLFW_MOUSE_BUTTON_RIGHT=1) 不被错误映射为左键。
 */
public class PointerButtonTest {

    @Test
    public void enumContainsNoneLeftRightMiddle() {
        // 必须显式区分左/中/右——不能合并为单一 BUTTON
        PointerButton[] values = PointerButton.values();
        assertEquals(4, values.length);
        assertEquals(PointerButton.NONE, values[0]);
        assertEquals(PointerButton.LEFT, values[1]);
        assertEquals(PointerButton.RIGHT, values[2]);
        assertEquals(PointerButton.MIDDLE, values[3]);
    }

    @Test
    public void parseGlfwButtonZeroIsLeft() {
        assertEquals(PointerButton.LEFT, PointerButton.fromGlfw(0));
    }

    @Test
    public void parseGlfwButtonOneIsRight() {
        // 关键测试：GLFW_MOUSE_BUTTON_RIGHT=1，不能被错误映射为 LEFT
        assertEquals(PointerButton.RIGHT, PointerButton.fromGlfw(1));
    }

    @Test
    public void parseGlfwButtonTwoIsMiddle() {
        assertEquals(PointerButton.MIDDLE, PointerButton.fromGlfw(2));
    }

    @Test
    public void parseUnknownGlfwButtonIsNone() {
        // 3 及以上、负数 → NONE（不静默退化为 LEFT）
        assertEquals(PointerButton.NONE, PointerButton.fromGlfw(3));
        assertEquals(PointerButton.NONE, PointerButton.fromGlfw(4));
        assertEquals(PointerButton.NONE, PointerButton.fromGlfw(-1));
    }

    @Test
    public void conveniencePredicatesAreExclusive() {
        assertTrue(PointerButton.LEFT.isLeft());
        assertFalse(PointerButton.LEFT.isRight());
        assertFalse(PointerButton.LEFT.isMiddle());

        assertTrue(PointerButton.RIGHT.isRight());
        assertFalse(PointerButton.RIGHT.isLeft());
        assertFalse(PointerButton.RIGHT.isMiddle());

        assertTrue(PointerButton.MIDDLE.isMiddle());
        assertFalse(PointerButton.MIDDLE.isLeft());
        assertFalse(PointerButton.MIDDLE.isRight());

        // NONE 全为 false
        assertFalse(PointerButton.NONE.isLeft());
        assertFalse(PointerButton.NONE.isRight());
        assertFalse(PointerButton.NONE.isMiddle());
    }

    @Test
    public void noneIndicatesNoButtonPressed() {
        // move/wheel 事件应使用 NONE，不能默认 LEFT
        assertTrue(PointerButton.NONE.isNone());
        assertFalse(PointerButton.LEFT.isNone());
        assertFalse(PointerButton.RIGHT.isNone());
        assertFalse(PointerButton.MIDDLE.isNone());
    }
}
