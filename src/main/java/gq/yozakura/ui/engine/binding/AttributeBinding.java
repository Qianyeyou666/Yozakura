package gq.yozakura.ui.engine.binding;

import gq.yozakura.ui.engine.dom.ElementNode;

/**
 * 属性绑定：将 {@link ObservableValue}{@code <String>} 绑定到元素的某个 HTML 属性。
 *
 * <p>AGENTS.md 契约：
 * <ul>
 *   <li>"class/attribute 状态绑定"</li>
 *   <li>"STYLE_DIRTY: selector state, ... or CSS variable changed"
 *       — 属性变化可能影响 [attr=...] selector 匹配，故标 STYLE_DIRTY</li>
 * </ul>
 *
 * <p>语义：
 * <ul>
 *   <li>{@code value != null} → {@code element.setAttribute(name, value)}</li>
 *   <li>{@code value == null} → {@code element.removeAttribute(name)}</li>
 * </ul>
 *
 * <p>{@link #bind} 时立即同步当前值并标 dirty；
 * {@link #dispose()} 解除订阅。
 *
 * <p>线程模型：单线程（UI 线程）。
 */
public final class AttributeBinding implements ValueChangeListener<String> {

    private final ElementNode element;
    private final String attributeName;
    private final ObservableValue<String> source;
    private final DirtyFlagSink sink;
    private boolean disposed;

    private AttributeBinding(ElementNode element, String attributeName,
                              ObservableValue<String> source, DirtyFlagSink sink) {
        if (element == null) {
            throw new IllegalArgumentException("element must not be null");
        }
        if (attributeName == null || attributeName.isEmpty()) {
            throw new IllegalArgumentException("attributeName must not be null or empty");
        }
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        if (sink == null) {
            throw new IllegalArgumentException("sink must not be null");
        }
        this.element = element;
        this.attributeName = attributeName;
        this.source = source;
        this.sink = sink;
    }

    /**
     * 创建并激活绑定：立即同步当前值并标 STYLE_DIRTY。
     */
    public static AttributeBinding bind(ElementNode element, String attributeName,
                                          ObservableValue<String> source, DirtyFlagSink sink) {
        AttributeBinding b = new AttributeBinding(element, attributeName, source, sink);
        b.applyInitial();
        source.addListener(b);
        return b;
    }

    private void applyInitial() {
        String current = source.get();
        applyValue(current);
    }

    @Override
    public void onValueChanged(String oldValue, String newValue) {
        if (disposed) return;
        applyValue(newValue);
    }

    private void applyValue(String value) {
        String current = element.attribute(attributeName);
        // 比较当前值与目标值，相同则不 dirty
        if (value == null) {
            if (current == null) return;
            element.removeAttribute(attributeName);
        } else {
            if (value.equals(current)) return;
            element.setAttribute(attributeName, value);
        }
        sink.markStyleDirty();
    }

    /**
     * 解除订阅。重复调用安全。
     */
    public void dispose() {
        if (disposed) return;
        disposed = true;
        source.removeListener(this);
    }

    public boolean isDisposed() { return disposed; }
    public ElementNode element() { return element; }
    public String attributeName() { return attributeName; }
}
