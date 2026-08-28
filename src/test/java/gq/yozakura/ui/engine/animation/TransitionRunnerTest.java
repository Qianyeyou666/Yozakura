package gq.yozakura.ui.engine.animation;

import gq.yozakura.ui.engine.dom.ElementNode;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 阶段 5 切片 5.4：TransitionRunner 测试。
 *
 * <p>验证契约（AGENTS.md）：
 * <ul>
 *   <li>"Transitions must support interruption and reversal."</li>
 *   <li>"Keep rendering active while an animation is running, then return to the
 *       static retained path." → 完成后自动 unregister clock active count</li>
 *   <li>"Separate paint-only transitions from layout transitions." → 回调标记正确 dirty</li>
 * </ul>
 *
 * <p>TransitionRunner 协调 {@link AnimationClock} + {@link TransitionStore}，
 * 并通过 {@link TransitionListener} 回调让 host 层标记 STYLE/LAYOUT/PAINT dirty。
 */
public class TransitionRunnerTest {

    private static ElementNode el(String tag) {
        return ElementNode.create(tag);
    }

    private static TransitionSpec spec(String property, long durationMs) {
        return new TransitionSpec(property, durationMs, 0L, TimingFunction.LINEAR);
    }

    /** 记录所有进度与完成回调的监听器。 */
    private static final class RecordingListener implements TransitionListener {
        final List<String> events = new ArrayList<String>();

        @Override
        public void onTransitionProgress(ElementNode element, String property,
                                          float value, boolean layoutAffecting) {
            events.add("progress:" + property + "=" + value + (layoutAffecting ? "/L" : "/P"));
        }

        @Override
        public void onTransitionCompleted(ElementNode element, String property,
                                           float finalValue, boolean layoutAffecting) {
            events.add("completed:" + property + "=" + finalValue + (layoutAffecting ? "/L" : "/P"));
        }
    }

    // ---- 基本启动 ----

    @Test
    public void startTransition_registers_clock_active() {
        AnimationClock clock = new AnimationClock();
        clock.advanceTo(0L);
        TransitionStore store = new TransitionStore();
        TransitionRunner runner = new TransitionRunner(clock, store, new RecordingListener());

        assertFalse(clock.hasActiveAnimations());
        runner.startTransition(el("div"), spec("opacity", 200L), 0f, 1f);
        assertTrue(clock.hasActiveAnimations());
    }

    @Test
    public void startTransition_same_property_interrupts_uses_current_value() {
        // 中断：当前值作为新 fromValue
        AnimationClock clock = new AnimationClock();
        clock.advanceTo(0L);
        TransitionStore store = new TransitionStore();
        TransitionRunner runner = new TransitionRunner(clock, store, new RecordingListener());

        ElementNode e = el("div");
        runner.startTransition(e, spec("opacity", 200L), 0f, 1f);  // 0->1, 200ms
        clock.advanceTo(100L);  // 中点，值 = 0.5
        // 反转：opacity 从当前 0.5 回到 0
        runner.startTransition(e, spec("opacity", 200L), 0f);  // fromValue 自动取当前值

        RunningTransition rt = store.getRunning(e, "opacity");
        assertEquals(0.5f, rt.fromValue(), 0.001f);
        assertEquals(0f, rt.toValue(), 0.0001f);
    }

    @Test
    public void startTransition_interrupt_when_not_transitioning_uses_explicit_from() {
        // 未在过渡时启动新过渡，使用显式 fromValue
        AnimationClock clock = new AnimationClock();
        clock.advanceTo(0L);
        TransitionStore store = new TransitionStore();
        TransitionRunner runner = new TransitionRunner(clock, store, new RecordingListener());

        ElementNode e = el("div");
        runner.startTransition(e, spec("opacity", 200L), 0f, 1f);
        RunningTransition rt = store.getRunning(e, "opacity");
        assertEquals(0f, rt.fromValue(), 0.0001f);
        assertEquals(1f, rt.toValue(), 0.0001f);
    }

    // ---- tick 推进 ----

    @Test
    public void tick_advances_clock_and_fires_progress() {
        AnimationClock clock = new AnimationClock();
        clock.advanceTo(0L);
        TransitionStore store = new TransitionStore();
        RecordingListener l = new RecordingListener();
        TransitionRunner runner = new TransitionRunner(clock, store, l);

        ElementNode e = el("div");
        runner.startTransition(e, spec("opacity", 200L), 0f, 1f);
        l.events.clear();

        runner.tick(100L);  // 中点
        assertTrue(clock.nowMillis() == 100L);
        // 应触发一次 progress 回调
        assertEquals(1, l.events.size());
        assertTrue(l.events.get(0).startsWith("progress:opacity=0.5"));
        assertTrue(l.events.get(0).endsWith("/P"));  // opacity 是 paint-only
    }

    @Test
    public void tick_marks_layout_affecting_property_correctly() {
        AnimationClock clock = new AnimationClock();
        clock.advanceTo(0L);
        TransitionStore store = new TransitionStore();
        RecordingListener l = new RecordingListener();
        TransitionRunner runner = new TransitionRunner(clock, store, l);

        ElementNode e = el("div");
        runner.startTransition(e, spec("width", 200L), 0f, 100f);
        l.events.clear();

        runner.tick(100L);  // 中点，值 = 50
        assertEquals(1, l.events.size());
        assertTrue(l.events.get(0).startsWith("progress:width=50"));
        assertTrue(l.events.get(0).endsWith("/L"));  // width 是 layout-affecting
    }

    @Test
    public void tick_at_end_completes_transition() {
        AnimationClock clock = new AnimationClock();
        clock.advanceTo(0L);
        TransitionStore store = new TransitionStore();
        RecordingListener l = new RecordingListener();
        TransitionRunner runner = new TransitionRunner(clock, store, l);

        ElementNode e = el("div");
        runner.startTransition(e, spec("opacity", 200L), 0f, 1f);
        l.events.clear();

        runner.tick(200L);  // 完成
        // 应有 progress（最终值）+ completed
        boolean hasCompleted = false;
        for (String ev : l.events) {
            if (ev.startsWith("completed:opacity=1.0")) hasCompleted = true;
        }
        assertTrue("completed callback fired", hasCompleted);
    }

    // ---- 完成后停止重建 ----

    @Test
    public void all_transitions_complete_unregisters_clock_active() {
        AnimationClock clock = new AnimationClock();
        clock.advanceTo(0L);
        TransitionStore store = new TransitionStore();
        TransitionRunner runner = new TransitionRunner(clock, store, new RecordingListener());

        ElementNode e = el("div");
        runner.startTransition(e, spec("opacity", 200L), 0f, 1f);
        assertTrue(clock.hasActiveAnimations());

        runner.tick(200L);  // 完成
        assertFalse("clock active cleared after all transitions complete",
                clock.hasActiveAnimations());
    }

    @Test
    public void multiple_transitions_one_completes_still_active() {
        AnimationClock clock = new AnimationClock();
        clock.advanceTo(0L);
        TransitionStore store = new TransitionStore();
        TransitionRunner runner = new TransitionRunner(clock, store, new RecordingListener());

        ElementNode e = el("div");
        runner.startTransition(e, spec("opacity", 200L), 0f, 1f);  // 200ms
        runner.startTransition(e, spec("left", 400L), 0f, 100f);   // 400ms

        runner.tick(200L);  // opacity 完成，left 仍在
        assertTrue("left still active", clock.hasActiveAnimations());

        runner.tick(400L);  // 全部完成
        assertFalse("all complete", clock.hasActiveAnimations());
    }

    @Test
    public void tick_with_no_transitions_is_noop() {
        AnimationClock clock = new AnimationClock();
        clock.advanceTo(0L);
        TransitionStore store = new TransitionStore();
        RecordingListener l = new RecordingListener();
        TransitionRunner runner = new TransitionRunner(clock, store, l);

        runner.tick(1000L);
        assertTrue(l.events.isEmpty());
        assertFalse(clock.hasActiveAnimations());
    }

    // ---- 中断反转 ----

    @Test
    public void reversal_at_midpoint_uses_current_value_as_new_from() {
        AnimationClock clock = new AnimationClock();
        clock.advanceTo(0L);
        TransitionStore store = new TransitionStore();
        RecordingListener l = new RecordingListener();
        TransitionRunner runner = new TransitionRunner(clock, store, l);

        ElementNode e = el("div");
        runner.startTransition(e, spec("opacity", 200L), 0f, 1f);  // 0->1
        runner.tick(100L);  // 中点 0.5

        // 反转：toValue=0，fromValue 自动取当前 0.5
        runner.startTransition(e, spec("opacity", 100L), 0f);
        RunningTransition rt = store.getRunning(e, "opacity");
        assertEquals(0.5f, rt.fromValue(), 0.001f);
        assertEquals(0f, rt.toValue(), 0.0001f);

        runner.tick(150L);  // 50ms 后到中点 → 0.25
        Float cur = store.currentValue(e, "opacity", 150L);
        assertEquals(0.25f, cur, 0.001f);
    }

    @Test
    public void interrupt_preserves_clock_active_count() {
        // 中断不应导致 clock active count 双重 register
        AnimationClock clock = new AnimationClock();
        clock.advanceTo(0L);
        TransitionStore store = new TransitionStore();
        TransitionRunner runner = new TransitionRunner(clock, store, new RecordingListener());

        ElementNode e = el("div");
        runner.startTransition(e, spec("opacity", 200L), 0f, 1f);
        int countAfterFirst = clock.activeCount();

        runner.startTransition(e, spec("opacity", 200L), 1f, 0f);  // 中断重启
        // active count 不应再次增加（同一 property）
        assertEquals("clock active count not doubled on interrupt",
                countAfterFirst, clock.activeCount());
    }

    @Test
    public void start_new_property_increments_clock_active() {
        AnimationClock clock = new AnimationClock();
        clock.advanceTo(0L);
        TransitionStore store = new TransitionStore();
        TransitionRunner runner = new TransitionRunner(clock, store, new RecordingListener());

        ElementNode e = el("div");
        runner.startTransition(e, spec("opacity", 200L), 0f, 1f);
        int count1 = clock.activeCount();
        runner.startTransition(e, spec("left", 200L), 0f, 100f);
        int count2 = clock.activeCount();
        assertEquals(count1 + 1, count2);  // 新 property → 新 active
    }

    // ---- tick 时序 ----

    @Test
    public void tick_progress_increases_monotonically() {
        AnimationClock clock = new AnimationClock();
        clock.advanceTo(0L);
        TransitionStore store = new TransitionStore();
        RecordingListener l = new RecordingListener();
        TransitionRunner runner = new TransitionRunner(clock, store, l);

        ElementNode e = el("div");
        runner.startTransition(e, spec("opacity", 100L), 0f, 1f);

        float prev = -1f;
        for (long t = 10L; t <= 100L; t += 10L) {
            l.events.clear();
            runner.tick(t);
            // 解析 progress 值
            for (String ev : l.events) {
                if (ev.startsWith("progress:opacity=")) {
                    String v = ev.substring("progress:opacity=".length());
                    v = v.substring(0, v.indexOf('/'));
                    float f = Float.parseFloat(v);
                    assertTrue("monotonic at t=" + t + ": " + f + " >= " + prev, f >= prev - 0.001f);
                    prev = f;
                }
            }
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void tick_rejects_null_listener_in_constructor() {
        new TransitionRunner(new AnimationClock(), new TransitionStore(), null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void tick_rejects_null_clock() {
        new TransitionRunner(null, new TransitionStore(), new RecordingListener());
    }

    @Test(expected = IllegalArgumentException.class)
    public void tick_rejects_null_store() {
        new TransitionRunner(new AnimationClock(), null, new RecordingListener());
    }

    // ---- cancel ----

    @Test
    public void cancel_property_clears_active_count_if_all_done() {
        AnimationClock clock = new AnimationClock();
        clock.advanceTo(0L);
        TransitionStore store = new TransitionStore();
        TransitionRunner runner = new TransitionRunner(clock, store, new RecordingListener());

        ElementNode e = el("div");
        runner.startTransition(e, spec("opacity", 200L), 0f, 1f);
        assertTrue(clock.hasActiveAnimations());

        runner.cancel(e, "opacity");
        assertFalse("active cleared after cancel", clock.hasActiveAnimations());
    }

    @Test
    public void cancel_all_for_element() {
        AnimationClock clock = new AnimationClock();
        clock.advanceTo(0L);
        TransitionStore store = new TransitionStore();
        TransitionRunner runner = new TransitionRunner(clock, store, new RecordingListener());

        ElementNode e = el("div");
        runner.startTransition(e, spec("opacity", 200L), 0f, 1f);
        runner.startTransition(e, spec("left", 200L), 0f, 100f);
        runner.cancelAll(e);
        assertFalse(clock.hasActiveAnimations());
    }

    @Test
    public void cancel_all_global() {
        AnimationClock clock = new AnimationClock();
        clock.advanceTo(0L);
        TransitionStore store = new TransitionStore();
        TransitionRunner runner = new TransitionRunner(clock, store, new RecordingListener());

        runner.startTransition(el("a"), spec("opacity", 200L), 0f, 1f);
        runner.startTransition(el("b"), spec("opacity", 200L), 0f, 1f);
        runner.cancelAll();
        assertFalse(clock.hasActiveAnimations());
    }

    // ---- delay 处理 ----

    @Test
    public void delay_period_progress_is_from_value() {
        AnimationClock clock = new AnimationClock();
        clock.advanceTo(0L);
        TransitionStore store = new TransitionStore();
        RecordingListener l = new RecordingListener();
        TransitionRunner runner = new TransitionRunner(clock, store, l);

        ElementNode e = el("div");
        TransitionSpec withDelay = new TransitionSpec("opacity", 200L, 100L, TimingFunction.LINEAR);
        runner.startTransition(e, withDelay, 0f, 1f);  // 0->1, 100ms delay, 200ms duration

        // delay 期间 progress = fromValue (0)
        runner.tick(50L);  // 仍在 delay
        boolean foundProgress = false;
        for (String ev : l.events) {
            if (ev.startsWith("progress:opacity=")) {
                foundProgress = true;
                assertTrue("delay period returns from value: " + ev, ev.contains("=0.0/"));
            }
        }
        assertTrue(foundProgress);
    }

    // ---- 启动时立即触发首次 progress ----

    @Test
    public void startTransition_fires_initial_progress() {
        AnimationClock clock = new AnimationClock();
        clock.advanceTo(0L);
        TransitionStore store = new TransitionStore();
        RecordingListener l = new RecordingListener();
        TransitionRunner runner = new TransitionRunner(clock, store, l);

        ElementNode e = el("div");
        runner.startTransition(e, spec("opacity", 200L), 0f, 1f);
        // 启动时应有首次 progress（值 = fromValue = 0）
        boolean found = false;
        for (String ev : l.events) {
            if (ev.startsWith("progress:opacity=0.0")) {
                found = true;
                break;
            }
        }
        assertTrue("initial progress fired on start", found);
    }

    @Test
    public void completed_callback_carries_final_value() {
        AnimationClock clock = new AnimationClock();
        clock.advanceTo(0L);
        TransitionStore store = new TransitionStore();
        RecordingListener l = new RecordingListener();
        TransitionRunner runner = new TransitionRunner(clock, store, l);

        ElementNode e = el("div");
        runner.startTransition(e, spec("opacity", 200L), 0f, 1f);
        l.events.clear();
        runner.tick(200L);
        // completed 应携带 finalValue = 1.0
        boolean found = false;
        for (String ev : l.events) {
            if (ev.startsWith("completed:opacity=1.0")) {
                found = true;
                break;
            }
        }
        assertTrue(found);
    }
}
