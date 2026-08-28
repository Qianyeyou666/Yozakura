package gq.yozakura.ui.engine.animation;

import gq.yozakura.ui.engine.dom.ElementNode;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 阶段 5 切片 5.3：TransitionSpec + TransitionStore 测试。
 *
 * <p>验证契约（AGENTS.md）：
 * <ul>
 *   <li>"Transitions must support interruption and reversal."</li>
 *   <li>"Separate paint-only transitions from layout transitions."</li>
 *   <li>启动同 property 的新过渡 → 中断旧过渡（替换）</li>
 *   <li>区分 paint-only（color/opacity）vs layout-affecting（width/height/left/top/...）</li>
 *   <li>update(nowMs) 移除已完成过渡并返回完成列表，便于调用方标 dirty</li>
 * </ul>
 *
 * <p>本切片仅覆盖 spec 解析与 store 跟踪；插值在 5.4。
 */
public class TransitionStoreTest {

    private static ElementNode el(String tag) {
        return ElementNode.create(tag);
    }

    private static TransitionSpec spec(String property, long durationMs) {
        return new TransitionSpec(property, durationMs, 0L, TimingFunction.LINEAR);
    }

    private static TransitionSpec specDelay(String property, long durationMs, long delayMs) {
        return new TransitionSpec(property, durationMs, delayMs, TimingFunction.EASE);
    }

    // ---- TransitionSpec ----

    @Test
    public void spec_holds_property_duration_delay_timing() {
        TransitionSpec s = new TransitionSpec("opacity", 300L, 50L, TimingFunction.EASE_IN);
        assertEquals("opacity", s.property());
        assertEquals(300L, s.durationMs());
        assertEquals(50L, s.delayMs());
        assertEquals(TimingFunction.EASE_IN, s.timingFunction());
    }

    @Test(expected = IllegalArgumentException.class)
    public void spec_null_property_rejected() {
        new TransitionSpec(null, 100L, 0L, TimingFunction.LINEAR);
    }

    @Test(expected = IllegalArgumentException.class)
    public void spec_empty_property_rejected() {
        new TransitionSpec("", 100L, 0L, TimingFunction.LINEAR);
    }

    @Test(expected = IllegalArgumentException.class)
    public void spec_negative_duration_rejected() {
        new TransitionSpec("opacity", -1L, 0L, TimingFunction.LINEAR);
    }

    @Test(expected = IllegalArgumentException.class)
    public void spec_negative_delay_rejected() {
        new TransitionSpec("opacity", 100L, -1L, TimingFunction.LINEAR);
    }

    @Test(expected = IllegalArgumentException.class)
    public void spec_null_timing_rejected() {
        new TransitionSpec("opacity", 100L, 0L, null);
    }

    @Test
    public void zero_duration_allowed_for_instant_transitions() {
        TransitionSpec s = new TransitionSpec("opacity", 0L, 0L, TimingFunction.LINEAR);
        assertEquals(0L, s.durationMs());
    }

    @Test
    public void spec_is_immutable_value_object() {
        TransitionSpec a = new TransitionSpec("opacity", 100L, 0L, TimingFunction.LINEAR);
        TransitionSpec b = new TransitionSpec("opacity", 100L, 0L, TimingFunction.LINEAR);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    // ---- TimingFunction ----

    @Test
    public void timing_parse_linear() {
        assertEquals(TimingFunction.LINEAR, TimingFunction.parse("linear"));
    }

    @Test
    public void timing_parse_ease() {
        assertEquals(TimingFunction.EASE, TimingFunction.parse("ease"));
        assertEquals(TimingFunction.EASE_IN, TimingFunction.parse("ease-in"));
        assertEquals(TimingFunction.EASE_OUT, TimingFunction.parse("ease-out"));
        assertEquals(TimingFunction.EASE_IN_OUT, TimingFunction.parse("ease-in-out"));
    }

    @Test
    public void timing_parse_unknown_returns_linear() {
        assertEquals(TimingFunction.LINEAR, TimingFunction.parse("cubic-bezier(0.1, 0.2, 0.3, 0.4)"));
        assertEquals(TimingFunction.LINEAR, TimingFunction.parse(null));
        assertEquals(TimingFunction.LINEAR, TimingFunction.parse(""));
    }

    @Test
    public void timing_apply_linear_identity() {
        // linear: t -> t
        assertEquals(0f, TimingFunction.LINEAR.apply(0f), 0.0001f);
        assertEquals(0.5f, TimingFunction.LINEAR.apply(0.5f), 0.0001f);
        assertEquals(1f, TimingFunction.LINEAR.apply(1f), 0.0001f);
    }

    @Test
    public void timing_apply_clamps_input_to_01() {
        assertEquals(0f, TimingFunction.LINEAR.apply(-0.5f), 0.0001f);
        assertEquals(1f, TimingFunction.LINEAR.apply(1.5f), 0.0001f);
    }

    @Test
    public void timing_apply_ease_endpoints() {
        // ease 等曲线端点应为 0 和 1（连续性）
        assertEquals(0f, TimingFunction.EASE.apply(0f), 0.0001f);
        assertEquals(1f, TimingFunction.EASE.apply(1f), 0.0001f);
        assertEquals(0f, TimingFunction.EASE_IN.apply(0f), 0.0001f);
        assertEquals(1f, TimingFunction.EASE_IN.apply(1f), 0.0001f);
        assertEquals(0f, TimingFunction.EASE_OUT.apply(0f), 0.0001f);
        assertEquals(1f, TimingFunction.EASE_OUT.apply(1f), 0.0001f);
        assertEquals(0f, TimingFunction.EASE_IN_OUT.apply(0f), 0.0001f);
        assertEquals(1f, TimingFunction.EASE_IN_OUT.apply(1f), 0.0001f);
    }

    @Test
    public void timing_ease_monotonic_increasing() {
        // ease 等曲线应单调递增（不倒退）
        float prev = 0f;
        for (int i = 1; i <= 20; i++) {
            float t = i / 20f;
            float v = TimingFunction.EASE.apply(t);
            assertTrue("ease monotonic at t=" + t, v >= prev - 0.0001f);
            prev = v;
        }
    }

    // ---- TransitionStore: 启动与中断 ----

    @Test
    public void startTransition_registers_running_transition() {
        TransitionStore store = new TransitionStore();
        ElementNode e = el("div");
        store.startTransition(e, spec("opacity", 200L), 0f, 1f, 1000L);
        assertTrue(store.isTransitioning(e, "opacity"));
        RunningTransition rt = store.getRunning(e, "opacity");
        assertEquals(0f, rt.fromValue(), 0.0001f);
        assertEquals(1f, rt.toValue(), 0.0001f);
        assertEquals(1000L, rt.startTimeMs());
        assertEquals(1200L, rt.endTimeMs());  // 1000 + 0 delay + 200 duration
    }

    @Test
    public void startTransition_with_delay_defers_active_window() {
        TransitionStore store = new TransitionStore();
        ElementNode e = el("div");
        store.startTransition(e, specDelay("opacity", 200L, 100L), 0f, 1f, 1000L);
        RunningTransition rt = store.getRunning(e, "opacity");
        // startTime = launch + delay = 1000 + 100 = 1100（实际插值开始时间）
        assertEquals(1100L, rt.startTimeMs());
        // endTime = startTime + duration = 1100 + 200 = 1300
        assertEquals(1300L, rt.endTimeMs());
        // delay 期间也算 transitioning
        assertTrue(store.isTransitioning(e, "opacity"));
    }

    @Test
    public void startTransition_same_property_interrupts_previous() {
        // AGENTS.md: "Transitions must support interruption and reversal."
        TransitionStore store = new TransitionStore();
        ElementNode e = el("div");
        store.startTransition(e, spec("opacity", 200L), 0f, 1f, 1000L);
        // 在 1100ms 时反转：从当前值（应被插值）回 0
        store.startTransition(e, spec("opacity", 200L), 0.5f, 0f, 1100L);
        RunningTransition rt = store.getRunning(e, "opacity");
        assertEquals(0.5f, rt.fromValue(), 0.0001f);
        assertEquals(0f, rt.toValue(), 0.0001f);
        assertEquals(1100L, rt.startTimeMs());
        assertEquals(1300L, rt.endTimeMs());
    }

    @Test
    public void different_properties_coexist() {
        TransitionStore store = new TransitionStore();
        ElementNode e = el("div");
        store.startTransition(e, spec("opacity", 200L), 0f, 1f, 1000L);
        store.startTransition(e, spec("left", 300L), 0f, 100f, 1000L);
        assertTrue(store.isTransitioning(e, "opacity"));
        assertTrue(store.isTransitioning(e, "left"));
    }

    @Test
    public void different_elements_independent() {
        TransitionStore store = new TransitionStore();
        ElementNode a = el("div");
        ElementNode b = el("div");
        store.startTransition(a, spec("opacity", 200L), 0f, 1f, 1000L);
        assertTrue(store.isTransitioning(a, "opacity"));
        assertFalse(store.isTransitioning(b, "opacity"));
    }

    // ---- TransitionStore: update 移除已完成 ----

    @Test
    public void update_removes_completed_transitions() {
        TransitionStore store = new TransitionStore();
        ElementNode e = el("div");
        store.startTransition(e, spec("opacity", 200L), 0f, 1f, 1000L);
        // 时间未到 → 不移除
        List<CompletedTransition> completed1 = store.update(1100L);
        assertTrue(completed1.isEmpty());
        assertTrue(store.isTransitioning(e, "opacity"));
        // 时间到 → 移除
        List<CompletedTransition> completed2 = store.update(1200L);
        assertEquals(1, completed2.size());
        assertFalse(store.isTransitioning(e, "opacity"));
    }

    @Test
    public void update_returns_completed_with_target_value() {
        TransitionStore store = new TransitionStore();
        ElementNode e = el("div");
        store.startTransition(e, spec("opacity", 200L), 0f, 1f, 1000L);
        List<CompletedTransition> completed = store.update(1500L);
        assertEquals(1, completed.size());
        assertEquals("opacity", completed.get(0).property());
        assertEquals(1f, completed.get(0).finalValue(), 0.0001f);
    }

    @Test
    public void update_at_exact_end_time_completes() {
        TransitionStore store = new TransitionStore();
        ElementNode e = el("div");
        store.startTransition(e, spec("opacity", 200L), 0f, 1f, 1000L);
        // endTime = 1200，恰好到 endTime 应完成
        List<CompletedTransition> completed = store.update(1200L);
        assertEquals(1, completed.size());
    }

    @Test
    public void update_no_transitions_returns_empty() {
        TransitionStore store = new TransitionStore();
        assertTrue(store.update(1000L).isEmpty());
    }

    @Test
    public void update_with_delay_only_not_completed_before_delay_end() {
        TransitionStore store = new TransitionStore();
        ElementNode e = el("div");
        store.startTransition(e, specDelay("opacity", 200L, 100L), 0f, 1f, 1000L);
        // endTime = 1300；1050ms 仍未完成
        assertTrue(store.update(1050L).isEmpty());
        assertTrue(store.isTransitioning(e, "opacity"));
    }

    // ---- paint vs layout 区分 ----

    @Test
    public void opacity_is_paint_only() {
        TransitionStore store = new TransitionStore();
        ElementNode e = el("div");
        store.startTransition(e, spec("opacity", 200L), 0f, 1f, 1000L);
        RunningTransition rt = store.getRunning(e, "opacity");
        assertFalse(rt.isLayoutAffecting());
    }

    @Test
    public void color_is_paint_only() {
        TransitionStore store = new TransitionStore();
        ElementNode e = el("div");
        store.startTransition(e, spec("color", 200L), 0f, 1f, 1000L);
        RunningTransition rt = store.getRunning(e, "color");
        assertFalse(rt.isLayoutAffecting());
    }

    @Test
    public void width_is_layout_affecting() {
        TransitionStore store = new TransitionStore();
        ElementNode e = el("div");
        store.startTransition(e, spec("width", 200L), 0f, 100f, 1000L);
        RunningTransition rt = store.getRunning(e, "width");
        assertTrue(rt.isLayoutAffecting());
    }

    @Test
    public void left_top_right_bottom_are_layout_affecting() {
        TransitionStore store = new TransitionStore();
        ElementNode e = el("div");
        for (String p : new String[]{"left", "top", "right", "bottom"}) {
            store.startTransition(e, spec(p, 200L), 0f, 100f, 1000L);
            RunningTransition rt = store.getRunning(e, p);
            assertTrue(p + " should be layout-affecting", rt.isLayoutAffecting());
        }
    }

    @Test
    public void has_active_transitions_global_query() {
        TransitionStore store = new TransitionStore();
        assertFalse(store.hasActiveTransitions());
        ElementNode e = el("div");
        store.startTransition(e, spec("opacity", 200L), 0f, 1f, 1000L);
        assertTrue(store.hasActiveTransitions());
        store.update(1500L);
        assertFalse(store.hasActiveTransitions());
    }

    // ---- RunningTransition: progress ----

    @Test
    public void running_progress_zero_at_start() {
        TransitionStore store = new TransitionStore();
        ElementNode e = el("div");
        store.startTransition(e, spec("opacity", 200L), 0f, 1f, 1000L);
        RunningTransition rt = store.getRunning(e, "opacity");
        // startTime=1000, endTime=1200, duration=200
        assertEquals(0f, rt.progressAt(1000L), 0.0001f);
    }

    @Test
    public void running_progress_half_at_midpoint() {
        TransitionStore store = new TransitionStore();
        ElementNode e = el("div");
        store.startTransition(e, spec("opacity", 200L), 0f, 1f, 1000L);
        RunningTransition rt = store.getRunning(e, "opacity");
        assertEquals(0.5f, rt.progressAt(1100L), 0.0001f);
    }

    @Test
    public void running_progress_one_at_end() {
        TransitionStore store = new TransitionStore();
        ElementNode e = el("div");
        store.startTransition(e, spec("opacity", 200L), 0f, 1f, 1000L);
        RunningTransition rt = store.getRunning(e, "opacity");
        assertEquals(1f, rt.progressAt(1200L), 0.0001f);
    }

    @Test
    public void running_progress_during_delay_is_zero() {
        TransitionStore store = new TransitionStore();
        ElementNode e = el("div");
        store.startTransition(e, specDelay("opacity", 200L, 100L), 0f, 1f, 1000L);
        RunningTransition rt = store.getRunning(e, "opacity");
        // startTime=1000, delay=100, duration=200, endTime=1300
        assertEquals(0f, rt.progressAt(1050L), 0.0001f);  // 仍在 delay
        assertEquals(0f, rt.progressAt(1100L), 0.0001f);  // delay 末尾
        assertEquals(0.5f, rt.progressAt(1200L), 0.0001f);  // 中点
    }

    @Test
    public void running_progress_clamped_before_start() {
        TransitionStore store = new TransitionStore();
        ElementNode e = el("div");
        store.startTransition(e, spec("opacity", 200L), 0f, 1f, 1000L);
        RunningTransition rt = store.getRunning(e, "opacity");
        // 早于 startTime（理论上不应发生，但需安全）
        assertEquals(0f, rt.progressAt(500L), 0.0001f);
    }

    @Test
    public void interpolated_value_at_progress() {
        TransitionStore store = new TransitionStore();
        ElementNode e = el("div");
        store.startTransition(e, spec("opacity", 200L), 0f, 1f, 1000L);
        RunningTransition rt = store.getRunning(e, "opacity");
        // linear: at t=0.5, value = 0.5
        assertEquals(0.5f, rt.valueAt(1100L), 0.0001f);
        // at t=0.25, value = 0.25
        assertEquals(0.25f, rt.valueAt(1050L), 0.0001f);
    }

    @Test
    public void interpolated_value_with_ease_curve() {
        TransitionStore store = new TransitionStore();
        ElementNode e = el("div");
        store.startTransition(e,
                new TransitionSpec("opacity", 200L, 0L, TimingFunction.EASE_IN),
                0f, 100f, 1000L);
        RunningTransition rt = store.getRunning(e, "opacity");
        // ease-in at midpoint produces < 0.5 * 100 (慢启动)
        float v = rt.valueAt(1100L);
        assertTrue("ease-in midpoint < linear midpoint: " + v, v < 50f);
        assertTrue("ease-in midpoint > 0: " + v, v > 0f);
    }

    @Test
    public void current_value_query_from_store() {
        TransitionStore store = new TransitionStore();
        ElementNode e = el("div");
        store.startTransition(e, spec("opacity", 200L), 0f, 1f, 1000L);
        Float v = store.currentValue(e, "opacity", 1100L);
        assertEquals(0.5f, v, 0.0001f);
    }

    @Test
    public void current_value_null_when_not_transitioning() {
        TransitionStore store = new TransitionStore();
        ElementNode e = el("div");
        assertNull(store.currentValue(e, "opacity", 1000L));
    }

    @Test
    public void cancel_transition_removes_specific_property() {
        TransitionStore store = new TransitionStore();
        ElementNode e = el("div");
        store.startTransition(e, spec("opacity", 200L), 0f, 1f, 1000L);
        store.startTransition(e, spec("left", 200L), 0f, 100f, 1000L);
        store.cancel(e, "opacity");
        assertFalse(store.isTransitioning(e, "opacity"));
        assertTrue(store.isTransitioning(e, "left"));
    }

    @Test
    public void cancel_all_for_element() {
        TransitionStore store = new TransitionStore();
        ElementNode e = el("div");
        store.startTransition(e, spec("opacity", 200L), 0f, 1f, 1000L);
        store.startTransition(e, spec("left", 200L), 0f, 100f, 1000L);
        store.cancelAll(e);
        assertFalse(store.isTransitioning(e, "opacity"));
        assertFalse(store.isTransitioning(e, "left"));
        assertFalse(store.hasActiveTransitions());
    }

    @Test
    public void cancel_all_global() {
        TransitionStore store = new TransitionStore();
        ElementNode a = el("div");
        ElementNode b = el("div");
        store.startTransition(a, spec("opacity", 200L), 0f, 1f, 1000L);
        store.startTransition(b, spec("opacity", 200L), 0f, 1f, 1000L);
        store.cancelAll();
        assertFalse(store.hasActiveTransitions());
    }
}
