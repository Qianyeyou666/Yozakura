package gq.yozakura.ui.engine.binding;

import gq.yozakura.ui.engine.dom.ElementNode;

/**
 * Class 绑定：将 {@link ObservableValue}{@code <Boolean>} 绑定到元素的某个 class 名。
 *
 * <p>AGENTS.md 契约：
 * <ul>
 *   <li>"class/attribute 状态绑定"</li>
 *   <li>"STYLE_DIRTY: selector state, inherited property or CSS variable changed"
 *       — class 变化可能影响 selector 匹配，故标 STYLE_DIRTY</li>
 * </ul>
 *
 * <p>语义：
 * <ul>
 *   <li>{@code value=true} → {@code element.addClass(name)}</li>
 *   <li>{@code value=false} → {@code element.removeClass(name)}</li>
 *   <li>{@code null} → 视为 false（移除 class）</li>
 * </ul>
 *
 * <p>{@link #bind} 时立即同步当前值并标 dirty；
 * {@link #dispose()} 解除订阅，防止内存泄漏与关闭 UI 后回调。
 *
 * <p>线程模型：单线程（UI 线程）。
 */
public final class ClassBinding implements ValueChangeListener<Boolean> {

    private final ElementNode element;
    private final String className;
    private final ObservableValue<Boolean> source;
    private final DirtyFlagSink sink;
    private boolean disposed;

    private ClassBinding(ElementNode element, String className,
                          ObservableValue<Boolean> source, DirtyFlagSink sink) {
        if (element == null) {
            throw new IllegalArgumentException("element must not be null");
        }
        if (className == null || className.isEmpty()) {
            throw new IllegalArgumentException("className must not be null or empty");
        }
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        if (sink == null) {
            throw new IllegalArgumentException("sink must not be null");
        }
        this.element = element;
        this.className = className;
        this.source = source;
        this.sink = sink;
    }

    /**
     * 创建并激活绑定：立即同步当前值并标 STYLE_DIRTY。
     *
     * @return binding 实例，调用方可持有以便后续 dispose
     */
    public static ClassBinding bind(ElementNode element, String className,
                                     ObservableValue<Boolean> source, DirtyFlagSink sink) {
        ClassBinding b = new ClassBinding(element, className, source, sink);
        b.applyInitial();
        source.addListener(b);
        return b;
    }

    private void applyInitial() {
        Boolean current = source.get();
        applyValue(current);
    }

    @Override
    public void onValueChanged(Boolean oldValue, Boolean newValue) {
        if (disposed) return;
        applyValue(newValue);
    }

    private void applyValue(Boolean value) {
        boolean shouldHave = value != null && value.booleanValue();
        boolean currentlyHas = element.hasClass(className);
        if (shouldHave == currentlyHas) {
            return;  // 同状态，不 dirty（不应发生，ObservableValue 已去抖）
        }
        if (shouldHave) {
            element.addClass(className);
        } else {
            element.removeClass(className);
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
    public String className() { return className; }
}
