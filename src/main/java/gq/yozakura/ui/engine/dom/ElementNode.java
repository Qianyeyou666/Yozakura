package gq.yozakura.ui.engine.dom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 元素节点：持有标签名、id、class 列表、属性、内联 style 文本与子节点。
 *
 * <p>节点可变：树结构（append/remove）与解析期赋值通过 builder 风格方法链式设置。
 * 父子双向链接由 {@link #appendChild(DomNode)} 维护。
 *
 * <p>class 列表去重并保留首次出现顺序（CSS class 顺序不影响匹配，但保留顺序便于调试）。
 */
public final class ElementNode extends DomNode {
    private final String tag;
    private String id;
    private List<String> classes;
    private AttributeMap attributes;
    private String inlineStyle;
    private final List<DomNode> children = new ArrayList<DomNode>();

    // 交互状态（由输入系统维护，选择器匹配时读取）
    private boolean hovered;
    private boolean active;
    private boolean focused;
    private boolean checked;

    private ElementNode(String tag, SourcePosition sourcePosition) {
        super(sourcePosition);
        if (tag == null || tag.isEmpty()) {
            throw new IllegalArgumentException("tag must not be null or empty");
        }
        this.tag = tag;
        this.id = null;
        this.classes = Collections.emptyList();
        this.attributes = AttributeMap.empty();
        this.inlineStyle = "";
    }

    public static ElementNode create(String tag) {
        return new ElementNode(tag, SourcePosition.unknown());
    }

    public static ElementNode create(String tag, SourcePosition sourcePosition) {
        return new ElementNode(tag, sourcePosition);
    }

    @Override
    public Kind kind() {
        return Kind.ELEMENT;
    }

    public String tag() {
        return tag;
    }

    public String id() {
        return id;
    }

    /** 不可变 class 列表视图，去重保序。 */
    public List<String> classes() {
        return classes;
    }

    /** 属性值；缺失返回 null。 */
    public String attribute(String name) {
        return attributes.get(name);
    }

    public AttributeMap attributes() {
        return attributes;
    }

    public String inlineStyle() {
        return inlineStyle;
    }

    // ---- 交互状态（由输入系统维护） ----

    public boolean isHovered() { return hovered; }
    public void setHovered(boolean v) { this.hovered = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
    public boolean isFocused() { return focused; }
    public void setFocused(boolean v) { this.focused = v; }
    public boolean isChecked() { return checked; }
    public void setChecked(boolean v) { this.checked = v; }

    public boolean hasClass(String name) {
        for (int i = 0; i < classes.size(); i++) {
            if (classes.get(i).equals(name)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasChildren() {
        return !children.isEmpty();
    }

    @Override
    public int childCount() {
        return children.size();
    }

    public DomNode child(int index) {
        return children.get(index);
    }

    /** 不可变子节点视图。 */
    public List<DomNode> children() {
        return Collections.unmodifiableList(children);
    }

    // ---- 解析/构造期 builder 风格 API ----

    public ElementNode withId(String id) {
        this.id = id;
        return this;
    }

    public ElementNode withClasses(List<String> classes) {
        this.classes = deduplicatePreservingOrder(classes);
        return this;
    }

    public ElementNode withAttributes(AttributeMap attributes) {
        this.attributes = attributes == null ? AttributeMap.empty() : attributes;
        return this;
    }

    public ElementNode withInlineStyle(String inlineStyle) {
        this.inlineStyle = inlineStyle == null ? "" : inlineStyle;
        return this;
    }

    /**
     * 添加 class（若不存在）；返回 this 便于链式调用。
     *
     * <p>用于阶段 5 ClassBinding 在 ObservableValue 变化时同步 DOM。
     */
    public ElementNode addClass(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("class name must not be null or empty");
        }
        if (hasClass(name)) {
            return this;
        }
        java.util.List<String> next = new java.util.ArrayList<String>(classes.size() + 1);
        for (int i = 0; i < classes.size(); i++) {
            next.add(classes.get(i));
        }
        next.add(name);
        this.classes = Collections.unmodifiableList(next);
        return this;
    }

    /**
     * 移除 class（若存在）；返回 this。
     */
    public ElementNode removeClass(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("class name must not be null or empty");
        }
        if (!hasClass(name)) {
            return this;
        }
        java.util.List<String> next = new java.util.ArrayList<String>(classes.size() - 1);
        for (int i = 0; i < classes.size(); i++) {
            String c = classes.get(i);
            if (!c.equals(name)) {
                next.add(c);
            }
        }
        this.classes = Collections.unmodifiableList(next);
        return this;
    }

    /**
     * 设置属性值；null 表示移除属性。返回 this。
     *
     * <p>用于阶段 5 AttributeBinding 与 data-* 状态绑定。
     * AttributeMap 不可变，故每次重建；热路径仅在变化时调用。
     */
    public ElementNode setAttribute(String name, String value) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("attribute name must not be null or empty");
        }
        AttributeMap.AttributeMapBuilder b = AttributeMap.builder();
        boolean found = false;
        for (java.util.Map.Entry<String, String> e : attributes.entries()) {
            if (e.getKey().equals(name)) {
                found = true;
                if (value != null) {
                    b.set(name, value);
                }
                // value == null → 移除（不重新设置）
            } else {
                b.set(e.getKey(), e.getValue());
            }
        }
        if (!found && value != null) {
            b.set(name, value);
        }
        this.attributes = b.build();
        return this;
    }

    /** 移除属性；若不存在为 no-op。返回 this。 */
    public ElementNode removeAttribute(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("attribute name must not be null or empty");
        }
        if (!attributes.has(name)) {
            return this;
        }
        AttributeMap.AttributeMapBuilder b = AttributeMap.builder();
        for (java.util.Map.Entry<String, String> e : attributes.entries()) {
            if (!e.getKey().equals(name)) {
                b.set(e.getKey(), e.getValue());
            }
        }
        this.attributes = b.build();
        return this;
    }

    /** 追加子节点，维护双向链接。若子节点已挂载到其他父节点，先从原父节点移除。 */
    public ElementNode appendChild(DomNode child) {
        if (child == null) {
            throw new IllegalArgumentException("child must not be null");
        }
        if (child.parent() != null) {
            child.parent().removeChild(child);
        }
        children.add(child);
        child.reparentTo(this);
        return this;
    }

    /** 移除子节点并断开父链接；若非当前子节点则为 no-op。 */
    public void removeChild(DomNode child) {
        if (child == null) {
            return;
        }
        int idx = children.indexOf(child);
        if (idx < 0) {
            return;
        }
        children.remove(idx);
        child.reparentTo(null);
    }

    /**
     * 移除所有子节点并断开它们的父链接。
     *
     * <p>用于阶段 5 ListRepeater 全量重建子节点前清空容器。
     */
    public void clearChildren() {
        if (children.isEmpty()) {
            return;
        }
        // 先解除所有子节点的 parent 引用（避免遍历中状态不一致）
        for (int i = 0; i < children.size(); i++) {
            children.get(i).reparentTo(null);
        }
        children.clear();
    }

    private static List<String> deduplicatePreservingOrder(List<String> input) {
        if (input == null || input.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> seen = new LinkedHashSet<String>(input);
        return new ArrayList<String>(seen);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ElementNode{").append(tag);
        if (id != null) sb.append("#").append(id);
        for (int i = 0; i < classes.size(); i++) {
            sb.append(".").append(classes.get(i));
        }
        sb.append(", children=").append(children.size()).append("}");
        return sb.toString();
    }
}
