package gq.yozakura.ui.engine.binding;

/**
 * 可观察值变化监听器：当 {@link ObservableValue#set(Object)} 改变值时回调。
 *
 * <p>AGENTS.md 契约："Observable data and repeaters"。
 *
 * <p>语义：
 * <ul>
 *   <li>仅在值真正变化时回调（同值 set 不通知）</li>
 *   <li>oldValue 为变化前的值，newValue 为变化后的值</li>
 *   <li>回调过程中对同一 ObservableValue 调用 set/removeListener 必须安全
 *       （实现采用快照遍历，避免 ConcurrentModification）</li>
 * </ul>
 *
 * @param <T> 值类型
 */
public interface ValueChangeListener<T> {
    /**
     * 值变化回调。
     *
     * @param oldValue 变化前的值（可能为 null）
     * @param newValue 变化后的值（可能为 null）
     */
    void onValueChanged(T oldValue, T newValue);
}
