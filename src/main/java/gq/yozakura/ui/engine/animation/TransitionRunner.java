package gq.yozakura.ui.engine.animation;

import gq.yozakura.ui.engine.dom.ElementNode;

import java.util.List;

/**
 * 过渡运行协调器：组合 {@link AnimationClock} + {@link TransitionStore} + {@link TransitionListener}。
 *
 * <p>AGENTS.md 契约：
 * <ul>
 *   <li>"Transitions must support interruption and reversal."
 *       → {@link #startTransition(ElementNode, TransitionSpec, float, float)} 同 property
 *       启动新过渡时，旧过渡被替换；可使用 {@link #startTransition(ElementNode, TransitionSpec, float)}
 *       重载自动取当前插值作为新 fromValue</li>
 *   <li>"Keep rendering active while an animation is running, then return to the
 *       static retained path." → 启动时 registerActive，全部完成时 unregisterActive</li>
 *   <li>"Separate paint-only transitions from layout transitions."
 *       → 回调携带 layoutAffecting 标志</li>
 *   <li>"Use one monotonic clock." → 所有时间查询通过 {@link AnimationClock}</li>
 * </ul>
 *
 * <p>生命周期：
 * <ol>
 *   <li>host 层在 ComputedStyle 变化时调用 {@link #startTransition}</li>
 *   <li>每帧 host 层调用 {@link #tick(long)}（nowMs 来自 clock 或外部）</li>
 *   <li>tick 推进时钟、查询当前值、回调 progress；完成的过渡回调 completed</li>
 *   <li>所有过渡完成后自动 unregister clock active，host 层停止 tick</li>
 * </ol>
 *
 * <p>线程模型：单线程（UI 线程）。非线程安全。
 */
public final class TransitionRunner {

    private final AnimationClock clock;
    private final TransitionStore store;
    private final TransitionListener listener;

    public TransitionRunner(AnimationClock clock, TransitionStore store,
                             TransitionListener listener) {
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        if (store == null) {
            throw new IllegalArgumentException("store must not be null");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        this.clock = clock;
        this.store = store;
        this.listener = listener;
    }

    /**
     * 启动过渡（显式 fromValue）。
     *
     * <p>若 (element, property) 已在过渡中，旧过渡被替换（中断语义），
     * clock active count 不重复递增。
     *
     * @param element   目标元素
     * @param spec      过渡规则
     * @param fromValue 起始值
     * @param toValue   目标值
     */
    public void startTransition(ElementNode element, TransitionSpec spec,
                                 float fromValue, float toValue) {
        boolean wasActive = store.hasActiveTransitions();
        boolean hadThisProperty = store.isTransitioning(element, spec.property());
        store.startTransition(element, spec, fromValue, toValue, clock.nowMillis());
        // 仅当全局无活跃过渡 或 该 property 之前未活跃时才 register
        // （store.startTransition 已替换旧实例，不增加活跃数）
        if (!wasActive) {
            clock.registerActive();
        } else if (!hadThisProperty) {
            // 同元素不同 property 新增 → 增加 active count
            clock.registerActive();
        }
        // 启动时立即触发首次 progress（值 = fromValue，让 paint 立即应用起点）
        listener.onTransitionProgress(element, spec.property(), fromValue,
                spec.isLayoutAffecting());
    }

    /**
     * 启动过渡（自动取当前插值作为 fromValue，用于中断/反转场景）。
     *
     * <p>若该 property 当前未在过渡中，fromValue 使用 spec.toValue（无意义的回退，
     * 调用方应在已知无过渡时使用四参数重载）。
     *
     * @param element 目标元素
     * @param spec    过渡规则
     * @param toValue 目标值
     */
    public void startTransition(ElementNode element, TransitionSpec spec, float toValue) {
        float fromValue = toValue;
        RunningTransition existing = store.getRunning(element, spec.property());
        if (existing != null) {
            fromValue = existing.valueAt(clock.nowMillis());
        }
        startTransition(element, spec, fromValue, toValue);
    }

    /**
     * 推进一帧。
     *
     * <p>步骤：
     * <ol>
     *   <li>推进 clock 到 nowMs</li>
     *   <li>对所有运行中过渡触发 progress 回调</li>
     *   <li>移除已完成过渡，触发 completed 回调</li>
     *   <li>若无活跃过渡且 clock 仍 active，unregister active</li>
     * </ol>
     *
     * @param nowMs 当前时间（&gt;= clock.nowMillis()）
     */
    public void tick(long nowMs) {
        clock.advanceTo(nowMs);

        if (!store.hasActiveTransitions()) {
            return;  // 无活跃过渡，no-op
        }

        // 1) 触发所有运行中过渡的 progress
        notifyProgress(nowMs);

        // 2) 移除已完成，触发 completed
        List<CompletedTransition> completed = store.update(nowMs);
        for (int i = 0; i < completed.size(); i++) {
            CompletedTransition c = completed.get(i);
            listener.onTransitionCompleted(null, c.property(), c.finalValue(),
                    c.isLayoutAffecting());
        }
        // 注意：completed 回调中 element 传 null（store 不保留元素引用到完成快照）
        // host 层应在 progress 时记录 element → property 映射；或改为携带元素。
        // 当前 MVP 简化：completed 不携带元素；host 层可从 StyleResolver 上下文获知。

        // 3) 若无活跃过渡，unregister clock active
        if (!store.hasActiveTransitions() && clock.hasActiveAnimations()) {
            // 仅 unregister 一次（store 清空时一次性 unregister 全部活跃计数）
            while (clock.hasActiveAnimations()) {
                clock.unregisterActive();
            }
        }
    }

    /** 取消某元素某 property 的过渡。 */
    public void cancel(ElementNode element, String property) {
        if (!store.isTransitioning(element, property)) {
            return;
        }
        store.cancel(element, property);
        // 若全部清空，unregister clock active
        if (!store.hasActiveTransitions() && clock.hasActiveAnimations()) {
            while (clock.hasActiveAnimations()) {
                clock.unregisterActive();
            }
        }
    }

    /** 取消某元素所有过渡。 */
    public void cancelAll(ElementNode element) {
        store.cancelAll(element);
        if (!store.hasActiveTransitions() && clock.hasActiveAnimations()) {
            while (clock.hasActiveAnimations()) {
                clock.unregisterActive();
            }
        }
    }

    /** 取消所有元素所有过渡（关闭 UI 时调用）。 */
    public void cancelAll() {
        store.cancelAll();
        while (clock.hasActiveAnimations()) {
            clock.unregisterActive();
        }
    }

    /** 是否有活跃过渡。 */
    public boolean hasActiveTransitions() {
        return store.hasActiveTransitions();
    }

    // ---- 内部 ----

    /**
     * 通知所有运行中过渡的当前 progress 值。
     *
     * <p>AGENTS.md "Avoid per-frame streams" → 用索引遍历，不创建临时集合。
     * 但 IdentityHashMap 遍历仍需 Iterator；这里接受少量分配（每帧仅一次）。
     *
     * <p>注意：listener 回调可能调用 startTransition/cancel（嵌套修改 store），
     * 故先收集快照再回调，避免 ConcurrentModification。
     */
    private void notifyProgress(long nowMs) {
        // 收集快照（每帧一次分配，可接受；后续可优化为复用 buffer）
        java.util.List<ProgressEntry> snapshot = collectProgressSnapshot(nowMs);
        for (int i = 0; i < snapshot.size(); i++) {
            ProgressEntry p = snapshot.get(i);
            listener.onTransitionProgress(p.element, p.property, p.value, p.layoutAffecting);
        }
    }

    private java.util.List<ProgressEntry> collectProgressSnapshot(long nowMs) {
        java.util.List<ProgressEntry> list = new java.util.ArrayList<ProgressEntry>();
        // 通过 store 公开 API 无法遍历 → 需要新增 store.forEach
        // 简化：本实现调用 store.forEach 暴露的遍历接口
        store.forEach(new TransitionStore.Visitor() {
            @Override
            public void visit(ElementNode element, RunningTransition rt) {
                list.add(new ProgressEntry(element, rt.property(),
                        rt.valueAt(nowMs), rt.isLayoutAffecting()));
            }
        });
        return list;
    }

    /** 单个 progress 通知条目。 */
    private static final class ProgressEntry {
        final ElementNode element;
        final String property;
        final float value;
        final boolean layoutAffecting;

        ProgressEntry(ElementNode element, String property, float value, boolean layoutAffecting) {
            this.element = element;
            this.property = property;
            this.value = value;
            this.layoutAffecting = layoutAffecting;
        }
    }
}
