package gq.yozakura.ui.engine.binding;

import gq.yozakura.ui.engine.dom.ElementNode;

import java.util.List;

/**
 * 列表 repeater：根据数据列表全量重建容器子节点。
 *
 * <p>AGENTS.md 契约："controlled repeaters/templates for module and setting lists"。
 *
 * <p>语义：
 * <ul>
 *   <li>{@link #setData}：清空容器子节点，对每个数据项调用 {@link ItemTemplate#create}
 *       生成新节点并 appendChild</li>
 *   <li>每次重建标记 STYLE_DRITY + LAYOUT_DRITY（子树结构变化触发 selector 重算与 reflow）</li>
 *   <li>{@link #bindTo}：订阅 ObservableValue&lt;List&gt;，初始同步 + 后续变化时同步</li>
 *   <li>{@link #dispose}：解除订阅；之后 setData 为 no-op</li>
 * </ul>
 *
 * <p>MVP 策略：全量重建（简单正确）。diff 优化（保留稳定节点、最小化 dirty）
 * 记录为后续阶段任务——当前模块/设置列表规模下（约 100 项）全量重建仍可接受。
 *
 * <p>线程模型：单线程（UI 线程）。
 *
 * @param <T> 数据项类型
 */
public final class ListRepeater<T> implements ValueChangeListener<List<T>> {

    private final ElementNode container;
    private final ItemTemplate<T> template;
    private final DirtyFlagSink sink;
    private ObservableValue<List<T>> boundSource;
    private boolean disposed;

    public ListRepeater(ElementNode container, ItemTemplate<T> template, DirtyFlagSink sink) {
        if (container == null) {
            throw new IllegalArgumentException("container must not be null");
        }
        if (template == null) {
            throw new IllegalArgumentException("template must not be null");
        }
        if (sink == null) {
            throw new IllegalArgumentException("sink must not be null");
        }
        this.container = container;
        this.template = template;
        this.sink = sink;
    }

    /**
     * 设置数据并重建子节点。
     *
     * @param data 数据列表（非 null；不允许包含 null 元素）
     */
    public void setData(List<T> data) {
        if (disposed) return;
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        // 校验 null 元素
        for (int i = 0; i < data.size(); i++) {
            if (data.get(i) == null) {
                throw new IllegalArgumentException(
                        "data item must not be null at index " + i);
            }
        }

        // 清空容器
        container.clearChildren();

        // 为每个数据项生成节点并挂载
        for (int i = 0; i < data.size(); i++) {
            T item = data.get(i);
            ElementNode node = template.create(item, i);
            if (node == null) {
                throw new IllegalStateException(
                        "template returned null node at index " + i);
            }
            container.appendChild(node);
        }

        // 子树结构变化 → STYLE + LAYOUT dirty
        sink.markStyleDirty();
        sink.markLayoutDirty();
    }

    /**
     * 订阅 ObservableValue&lt;List&gt; 并立即同步当前值。
     *
     * <p>注意：调用方应保证 ObservableValue 持有的 List 在变化时为<strong>新实例</strong>，
     * 否则 ObservableValue 的同值去抖不会触发通知（即使 List 内容被 mutate）。
     * 推荐用法：每次数据变化时构造新的 List 实例再 set。
     *
     * @return this 便于链式调用
     */
    public ListRepeater<T> bindTo(ObservableValue<List<T>> source) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        this.boundSource = source;
        // 立即同步当前值
        setData(source.get());
        // 订阅后续变化
        source.addListener(this);
        return this;
    }

    @Override
    public void onValueChanged(List<T> oldValue, List<T> newValue) {
        if (disposed) return;
        setData(newValue);
    }

    /**
     * 解除订阅。重复调用安全。
     *
     * <p>若通过 {@link #bindTo} 订阅，会主动从 source 移除监听器。
     */
    public void dispose() {
        if (disposed) return;
        disposed = true;
        if (boundSource != null) {
            boundSource.removeListener(this);
            boundSource = null;
        }
    }

    public boolean isDisposed() { return disposed; }
    public ElementNode container() { return container; }
}
