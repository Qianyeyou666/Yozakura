package gq.yozakura.ui.engine.animation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 阶段 5 切片 5.2：AnimationClock 单调时钟测试。
 *
 * <p>验证契约（AGENTS.md）：
 * <ul>
 *   <li>"Use one monotonic clock. Animation progress is time-based, not frame-count-based."</li>
 *   <li>"Keep rendering active while an animation is running, then return to the static retained path."</li>
 * </ul>
 *
 * <p>设计：测试可控时钟（注入时间），不依赖真实 System.nanoTime。
 */
public class AnimationClockTest {

    @Test
    public void new_clock_starts_at_zero() {
        AnimationClock c = new AnimationClock();
        c.advanceTo(0L);
        assertEquals(0L, c.nowMillis());
    }

    @Test
    public void advanceTo_sets_absolute_time() {
        AnimationClock c = new AnimationClock();
        c.advanceTo(1000L);
        assertEquals(1000L, c.nowMillis());
        c.advanceTo(1500L);
        assertEquals(1500L, c.nowMillis());
    }

    @Test(expected = IllegalArgumentException.class)
    public void advanceTo_backward_in_time_rejected() {
        AnimationClock c = new AnimationClock();
        c.advanceTo(1000L);
        c.advanceTo(500L);  // 时间不可回退
    }

    @Test(expected = IllegalArgumentException.class)
    public void advanceTo_negative_rejected() {
        new AnimationClock().advanceTo(-1L);
    }

    @Test
    public void advanceBy_increments_time() {
        AnimationClock c = new AnimationClock();
        c.advanceTo(1000L);
        c.advanceBy(250L);
        assertEquals(1250L, c.nowMillis());
    }

    @Test(expected = IllegalArgumentException.class)
    public void advanceBy_negative_rejected() {
        AnimationClock c = new AnimationClock();
        c.advanceTo(100L);
        c.advanceBy(-10L);  // 不可负增量
    }

    @Test
    public void hasActiveTransitions_false_when_no_running() {
        AnimationClock c = new AnimationClock();
        c.advanceTo(0L);
        assertFalse(c.hasActiveAnimations());
    }

    @Test
    public void register_increments_active_count() {
        AnimationClock c = new AnimationClock();
        c.advanceTo(0L);
        c.registerActive();
        assertTrue(c.hasActiveAnimations());
        c.registerActive();
        c.registerActive();
        // 多次注册应仍为 active
        assertTrue(c.hasActiveAnimations());
    }

    @Test
    public void unregister_decrements_active_count() {
        AnimationClock c = new AnimationClock();
        c.advanceTo(0L);
        c.registerActive();
        c.registerActive();
        c.unregisterActive();
        assertTrue(c.hasActiveAnimations());
        c.unregisterActive();
        assertFalse(c.hasActiveAnimations());
    }

    @Test(expected = IllegalStateException.class)
    public void unregister_without_register_rejected() {
        AnimationClock c = new AnimationClock();
        c.advanceTo(0L);
        c.unregisterActive();  // 计数器下溢
    }

    @Test
    public void reset_clears_active_count() {
        AnimationClock c = new AnimationClock();
        c.advanceTo(1000L);
        c.registerActive();
        c.registerActive();
        c.reset();
        assertFalse(c.hasActiveAnimations());
        // 重置后可继续使用
        c.advanceTo(2000L);
        assertEquals(2000L, c.nowMillis());
    }

    @Test
    public void reset_to_negative_to_zero() {
        AnimationClock c = new AnimationClock();
        c.advanceTo(1000L);
        c.reset();
        assertEquals(0L, c.nowMillis());
    }

    @Test
    public void nowMillis_monotonic_under_repeated_calls() {
        AnimationClock c = new AnimationClock();
        c.advanceTo(1234L);
        // 同一时间多次查询应稳定
        for (int i = 0; i < 10; i++) {
            assertEquals(1234L, c.nowMillis());
        }
    }
}
