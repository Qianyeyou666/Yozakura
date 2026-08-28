package gq.yozakura.ui.engine.animation;

import gq.yozakura.ui.engine.dom.ElementNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 过渡存储：按 (ElementNode, property) 索引运行中的过渡。
 *
 * <p>AGENTS.md 契约：
 * <ul>
 *   <li>"Transitions must support interruption and reversal." →
 *       {@link #startTransition} 同 property 启动新过渡时替换旧实例</li>
 *   <li>"Separate paint-only transitions from layout transitions." →
 *       {@link RunningTransition#isLayoutAffecting()} 区分两类</li>
 *   <li>"Keep rendering active while an animation is running, then return to the
 *       static retained path." → {@link #hasActiveTransitions()} 由 host 层查询</li>
 * </ul>
 *
 * <p>设计：
 * <ul>
 *   <li>用 IdentityHashMap&lt;ElementNode, Map&lt;String, RunningTransition&gt;&gt; 索引
 *       （DOM 节点按引用相等）</li>
 *   <li>每个元素的 property → RunningTransition 是 HashMap，O(1) 查询</li>
 *   <li>{@link #update(long)} 遍历所有运行中过渡，移除已完成并返回快照列表</li>
 *   <li>{@link #currentValue} 提供即时插值查询，供 paint 阶段读取</li>
 * </ul>
 *
 * <p>线程模型：单线程（UI 线程）。非线程安全。
 *
 * <p>注意：本类不负责注册 {@link AnimationClock#registerActive()}；
 * 调用方（TransitionRunner，5.4）应在过渡启动/结束时维护时钟活跃计数。
 */
public final class TransitionStore {

    /** 影响 layout 的属性集合（其余视为 paint-only）。 */
    private static final java.util.Set<String> LAYOUT_AFFECTING = Collections.unmodifiableSet(
            new java.util.HashSet<String>(java.util.Arrays.asList(
                    "width", "height",
                    "left", "top", "right", "bottom",
                    "margin", "margin-top", "margin-right", "margin-bottom", "margin-left",
                    "padding", "padding-top", "padding-right", "padding-bottom", "padding-left",
                    "min-width", "min-height", "max-width", "max-height",
                    "font-size"
            )));

    /** 判断属性是否影响 layout（用于标 LAYOUT_DIRTY 而非 PAINT_DIRTY）。 */
    public static boolean isLayoutAffectingProperty(String property) {
        return LAYOUT_AFFECTING.contains(property);
    }

    private final Map<ElementNode, Map<String, RunningTransition>> byElement =
            new IdentityHashMap<ElementNode, Map<String, RunningTransition>>();

    public TransitionStore() {
    }

    /**
     * 启动一个过渡。同 (element, property) 的旧过渡被替换（中断语义）。
     *
     * @param element    目标元素
     * @param spec       过渡规则（非 null）
     * @param fromValue  起始值
     * @param toValue    目标值
     * @param nowMs      当前时间（用于计算 startTime = nowMs + delay）
     */
    public void startTransition(ElementNode element, TransitionSpec spec,
                                 float fromValue, float toValue, long nowMs) {
        if (element == null) {
            throw new IllegalArgumentException("element must not be null");
        }
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        long startTime = nowMs + spec.delayMs();
        long endTime = startTime + spec.durationMs();
        RunningTransition rt = new RunningTransition(spec, fromValue, toValue, startTime, endTime);
        Map<String, RunningTransition> perElement = byElement.get(element);
        if (perElement == null) {
            perElement = new java.util.HashMap<String, RunningTransition>();
            byElement.put(element, perElement);
        }
        perElement.put(spec.property(), rt);
    }

    /** 查询 (element, property) 当前运行中的过渡；不存在返回 null。 */
    public RunningTransition getRunning(ElementNode element, String property) {
        Map<String, RunningTransition> perElement = byElement.get(element);
        if (perElement == null) return null;
        return perElement.get(property);
    }

    /** (element, property) 是否在过渡中（含 delay 阶段）。 */
    public boolean isTransitioning(ElementNode element, String property) {
        return getRunning(element, property) != null;
    }

    /**
     * 查询当前插值结果。
     *
     * @return 当前值；若未在过渡中返回 null
     */
    public Float currentValue(ElementNode element, String property, long nowMs) {
        RunningTransition rt = getRunning(element, property);
        if (rt == null) return null;
        return rt.valueAt(nowMs);
    }

    /**
     * 推进时间，移除所有已完成过渡并返回快照列表。
     *
     * <p>调用方应遍历返回列表标记对应 dirty（PAINT 或 LAYOUT），
     * 并应用 finalValue 到 ComputedStyle。
     *
     * @return 已完成过渡列表（不可变）
     */
    public List<CompletedTransition> update(long nowMs) {
        if (byElement.isEmpty()) {
            return Collections.emptyList();
        }
        List<CompletedTransition> completed = null;
        // 遍历所有元素的过渡，移除已完成
        // 注意：边遍历边移除 → 使用 Iterator
        for (Map.Entry<ElementNode, Map<String, RunningTransition>> entry : byElement.entrySet()) {
            Map<String, RunningTransition> perElement = entry.getValue();
            if (perElement.isEmpty()) continue;
            java.util.Iterator<Map.Entry<String, RunningTransition>> it =
                    perElement.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, RunningTransition> e = it.next();
                RunningTransition rt = e.getValue();
                if (rt.isCompletedAt(nowMs)) {
                    if (completed == null) {
                        completed = new ArrayList<CompletedTransition>();
                    }
                    completed.add(new CompletedTransition(
                            rt.property(), rt.toValue(), rt.isLayoutAffecting()));
                    it.remove();
                }
            }
        }
        // 清理空元素的条目（避免 IdentityHashMap 累积空 map）
        if (completed != null) {
            java.util.Iterator<Map.Entry<ElementNode, Map<String, RunningTransition>>> outerIt =
                    byElement.entrySet().iterator();
            while (outerIt.hasNext()) {
                if (outerIt.next().getValue().isEmpty()) {
                    outerIt.remove();
                }
            }
        }
        return completed == null ? Collections.<CompletedTransition>emptyList() : completed;
    }

    /** 是否有任意活跃过渡。 */
    public boolean hasActiveTransitions() {
        if (byElement.isEmpty()) return false;
        for (Map<String, RunningTransition> perElement : byElement.values()) {
            if (!perElement.isEmpty()) return true;
        }
        return false;
    }

    /** 取消 (element, property) 的过渡（若有）。 */
    public void cancel(ElementNode element, String property) {
        Map<String, RunningTransition> perElement = byElement.get(element);
        if (perElement == null) return;
        perElement.remove(property);
        if (perElement.isEmpty()) {
            byElement.remove(element);
        }
    }

    /** 取消某元素的所有过渡。 */
    public void cancelAll(ElementNode element) {
        byElement.remove(element);
    }

    /** 取消所有元素的所有过渡（关闭 UI 时调用）。 */
    public void cancelAll() {
        byElement.clear();
    }

    /** 当前活跃过渡总数（用于测试与调试）。 */
    public int activeCount() {
        int n = 0;
        for (Map<String, RunningTransition> perElement : byElement.values()) {
            n += perElement.size();
        }
        return n;
    }

    /**
     * 遍历所有运行中过渡。
     *
     * <p>用于 {@link TransitionRunner#tick} 收集 progress 快照。
     * 遍历期间不应修改 store（回调中 startTransition/cancel 应在遍历后执行）。
     */
    public void forEach(Visitor visitor) {
        if (visitor == null) {
            throw new IllegalArgumentException("visitor must not be null");
        }
        if (byElement.isEmpty()) {
            return;
        }
        for (Map.Entry<ElementNode, Map<String, RunningTransition>> entry : byElement.entrySet()) {
            ElementNode element = entry.getKey();
            for (RunningTransition rt : entry.getValue().values()) {
                visitor.visit(element, rt);
            }
        }
    }

    /** 过渡遍历器。 */
    public interface Visitor {
        void visit(ElementNode element, RunningTransition rt);
    }
}
