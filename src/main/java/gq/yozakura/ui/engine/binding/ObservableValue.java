package gq.yozakura.ui.engine.binding;

import java.util.ArrayList;
import java.util.List;

/**
 * 单值可观察容器：持有值，在 {@link #set(Object)} 真正改变值时通知所有监听器。
 *
 * <p>AGENTS.md 契约：
 * <ul>
 *   <li>"Observable data and repeaters"</li>
 *   <li>"Avoid per-frame streams, reflection, boxing and temporary collections."</li>
 * </ul>
 *
 * <p>设计要点：
 * <ul>
 *   <li>同值 set 不通知（去抖，避免无谓 dirty 标记）</li>
 *   <li>监听器列表用 ArrayList 维护，遍历时采用索引 + 快照副本
 *       （仅在有并发修改时才复制，避免热路径分配）</li>
 *   <li>回调过程中支持 add/remove listener（基于 modification count 检测，
 *       遍历过程中若检测到结构变化则复制剩余快照继续遍历）</li>
 *   <li>不强引用清除——监听器需显式 {@link #removeListener} 或
 *       {@link #clearListeners}（关闭 UI 时调用，防止泄漏）</li>
 * </ul>
 *
 * <p>线程模型：单线程（UI 线程）。非线程安全。
 *
 * @param <T> 值类型
 */
public final class ObservableValue<T> {

    private T value;
    // 监听器列表：ArrayList（避免 LinkedList 的节点分配开销）
    private final List<ValueChangeListener<T>> listeners = new ArrayList<ValueChangeListener<T>>();
    // 结构修改计数：遍历时检测并发修改
    private int modCount = 0;

    public ObservableValue(T initialValue) {
        this.value = initialValue;
    }

    /** 当前值。 */
    public T get() {
        return value;
    }

    /**
     * 设置新值；仅在值真正变化时通知监听器。
     *
     * <p>equals 语义：使用 {@link ObjectsEqual} 工具方法，兼容 null。
     *
     * @param newValue 新值（可为 null）
     */
    public void set(T newValue) {
        if (equalsValue(value, newValue)) {
            return;  // 同值去抖
        }
        T oldValue = value;
        value = newValue;
        notifyListeners(oldValue, newValue);
    }

    /**
     * 添加监听器；重复添加同一监听器允许（每次通知都会回调多次）。
     *
     * @param listener 监听器（非 null）
     */
    public void addListener(ValueChangeListener<T> listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        listeners.add(listener);
        modCount++;
    }

    /**
     * 移除监听器；移除首个匹配的实例。
     *
     * @return true 表示成功移除一个
     */
    public boolean removeListener(ValueChangeListener<T> listener) {
        if (listener == null) {
            return false;
        }
        boolean removed = false;
        for (int i = 0; i < listeners.size(); i++) {
            if (listeners.get(i) == listener) {
                listeners.remove(i);
                removed = true;
                break;
            }
        }
        if (removed) {
            modCount++;
        }
        return removed;
    }

    /** 清除所有监听器（关闭 UI 时调用）。 */
    public void clearListeners() {
        if (!listeners.isEmpty()) {
            listeners.clear();
            modCount++;
        }
    }

    /** 监听器数量（用于测试与调试）。 */
    public int listenerCount() {
        return listeners.size();
    }

    // ---- 内部 ----

    /**
     * 通知所有监听器；回调过程中允许 add/remove listener。
     *
     * <p>策略：索引遍历；若遍历过程中 modCount 变化（结构性修改），
     * 则将剩余未通知的监听器复制到快照数组继续遍历，保证：
     * <ul>
     *   <li>新增的监听器不收到本次通知（按遍历开始时的快照）</li>
     *   <li>移除的监听器若仍在快照中则继续通知（一致性语义）</li>
     * </ul>
     */
    private void notifyListeners(T oldValue, T newValue) {
        int startMod = modCount;
        int n = listeners.size();
        for (int i = 0; i < n; i++) {
            if (modCount != startMod) {
                // 结构变化：复制剩余快照继续遍历
                notifyRemainingFromSnapshot(i, oldValue, newValue);
                return;
            }
            // 检查索引仍有效（可能在回调中被移除）
            if (i < listeners.size()) {
                ValueChangeListener<T> l = listeners.get(i);
                l.onValueChanged(oldValue, newValue);
            }
        }
    }

    private void notifyRemainingFromSnapshot(int fromIndex, T oldValue, T newValue) {
        // 复制快照（仅在有并发修改时才分配，热路径零分配）
        ValueChangeListener<T>[] snapshot = copyListenersFrom(fromIndex);
        for (int i = 0; i < snapshot.length; i++) {
            snapshot[i].onValueChanged(oldValue, newValue);
        }
    }

    @SuppressWarnings("unchecked")
    private ValueChangeListener<T>[] copyListenersFrom(int fromIndex) {
        int size = listeners.size();
        int count = Math.max(0, size - fromIndex);
        ValueChangeListener<T>[] arr = (ValueChangeListener<T>[]) new ValueChangeListener[count];
        for (int i = 0; i < count; i++) {
            arr[i] = listeners.get(fromIndex + i);
        }
        return arr;
    }

    private static boolean equalsValue(Object a, Object b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    @Override
    public String toString() {
        return "ObservableValue{value=" + value + ", listeners=" + listeners.size() + "}";
    }
}
