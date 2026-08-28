package gq.yozakura.ui.engine.input;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 阶段 4 切片 4.6：ScrollState 滚动容器状态测试。
 *
 * <p>验证契约：
 * <ul>
 *   <li>scrollTop 始终在 [0, maxScroll] 范围内（clamp）</li>
 *   <li>scrollBy 返回实际增量（被 clamp 截断的部分不计）</li>
 *   <li>maxScroll 减小时 scrollTop 自动 clamp</li>
 *   <li>边界：maxScroll=0 不可滚动；scrollBy 返回 0</li>
 *   <li>canScrollUp/canScrollDown 准确反映边界</li>
 *   <li>scroll 变化标记 PAINT_DIRTY（content offset 变化需重画）</li>
 *   <li>scroll 未变化（已到边界）不 dirty</li>
 * </ul>
 *
 * <p>本切片只覆盖 ScrollState 纯逻辑；与 WheelEvent 的集成由 host 层
 * （阶段 6）在 dispatch 时查询 target 祖先链上的 ScrollState 完成。
 */
public class ScrollStateTest {

    @Test
    public void initialStateIsZero() {
        ScrollState s = new ScrollState();
        assertEquals(0f, s.scrollTop(), 0.0001f);
        assertEquals(0f, s.maxScroll(), 0.0001f);
        assertFalse(s.isScrollable());
    }

    @Test
    public void setMaxScrollEnablesScrolling() {
        ScrollState s = new ScrollState();
        s.setMaxScroll(100f);
        assertEquals(100f, s.maxScroll(), 0.0001f);
        assertTrue(s.isScrollable());
        assertEquals(0f, s.scrollTop(), 0.0001f);
    }

    @Test
    public void scrollByAdvancesScrollTop() {
        ScrollState s = new ScrollState();
        s.setMaxScroll(100f);
        float delta = s.scrollBy(50f);
        assertEquals(50f, delta, 0.0001f);
        assertEquals(50f, s.scrollTop(), 0.0001f);
    }

    @Test
    public void scrollByClampsToMaxAndReturnsActualDelta() {
        ScrollState s = new ScrollState();
        s.setMaxScroll(100f);
        s.scrollBy(50f);
        // 再滚 60，应只到 100，实际增量 50
        float delta = s.scrollBy(60f);
        assertEquals(50f, delta, 0.0001f);
        assertEquals(100f, s.scrollTop(), 0.0001f);
    }

    @Test
    public void scrollByNegativeClampsToZero() {
        ScrollState s = new ScrollState();
        s.setMaxScroll(100f);
        s.scrollBy(70f);
        // 回滚 -100，应只到 0，实际增量 -70
        float delta = s.scrollBy(-100f);
        assertEquals(-70f, delta, 0.0001f);
        assertEquals(0f, s.scrollTop(), 0.0001f);
    }

    @Test
    public void canScrollUpAndDownReflectBoundaries() {
        ScrollState s = new ScrollState();
        s.setMaxScroll(100f);
        assertFalse(s.canScrollUp());     // scrollTop=0
        assertTrue(s.canScrollDown());

        s.scrollBy(50f);
        assertTrue(s.canScrollUp());
        assertTrue(s.canScrollDown());

        s.scrollBy(50f);  // 到 100
        assertTrue(s.canScrollUp());
        assertFalse(s.canScrollDown());
    }

    @Test
    public void maxScrollZeroMeansNotScrollable() {
        ScrollState s = new ScrollState();
        s.setMaxScroll(0f);
        assertFalse(s.isScrollable());
        assertEquals(0f, s.scrollBy(100f), 0.0001f);
        assertEquals(0f, s.scrollTop(), 0.0001f);
        assertFalse(s.canScrollUp());
        assertFalse(s.canScrollDown());
    }

    @Test
    public void setMaxScrollShrinksClampsScrollTop() {
        // 内容缩短时 scrollTop 自动 clamp
        ScrollState s = new ScrollState();
        s.setMaxScroll(100f);
        s.scrollBy(80f);  // scrollTop=80
        s.setMaxScroll(50f);  // max 减小
        assertEquals(50f, s.scrollTop(), 0.0001f);  // clamp 到新 max
    }

    @Test
    public void setScrollTopClampsToRange() {
        ScrollState s = new ScrollState();
        s.setMaxScroll(100f);
        s.setScrollTop(-10f);
        assertEquals(0f, s.scrollTop(), 0.0001f);
        s.setScrollTop(150f);
        assertEquals(100f, s.scrollTop(), 0.0001f);
        s.setScrollTop(40f);
        assertEquals(40f, s.scrollTop(), 0.0001f);
    }

    @Test
    public void negativeMaxScrollTreatedAsZero() {
        ScrollState s = new ScrollState();
        s.setMaxScroll(-50f);
        assertEquals(0f, s.maxScroll(), 0.0001f);
        assertFalse(s.isScrollable());
    }

    @Test
    public void nanMaxScrollRejected() {
        ScrollState s = new ScrollState();
        try {
            s.setMaxScroll(Float.NaN);
            org.junit.Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void scrollChangeMarksPaintDirty() {
        ScrollState s = new ScrollState();
        s.setMaxScroll(100f);
        s.scrollBy(50f);
        assertTrue(s.isPaintDirty());
    }

    @Test
    public void scrollAtBoundaryDoesNotMarkDirty() {
        // 已到边界，再滚不变化 → 不 dirty
        ScrollState s = new ScrollState();
        s.setMaxScroll(100f);
        s.scrollBy(100f);  // 到底
        s.clearPaintDirty();
        assertFalse(s.isPaintDirty());

        s.scrollBy(50f);  // 已到底，无变化
        assertFalse(s.isPaintDirty());
    }

    @Test
    public void clearPaintDirtyResetsFlag() {
        ScrollState s = new ScrollState();
        s.setMaxScroll(100f);
        s.scrollBy(50f);
        s.clearPaintDirty();
        assertFalse(s.isPaintDirty());
    }

    @Test
    public void setScrollTopToSameValueDoesNotMarkDirty() {
        ScrollState s = new ScrollState();
        s.setMaxScroll(100f);
        s.setScrollTop(30f);
        s.clearPaintDirty();
        s.setScrollTop(30f);
        assertFalse(s.isPaintDirty());
    }
}
