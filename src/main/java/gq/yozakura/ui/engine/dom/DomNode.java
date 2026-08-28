package gq.yozakura.ui.engine.dom;

/**
 * DOM 节点抽象基类。元素节点与文本节点共享父引用与源位置信息。
 *
 * <p>节点本身可变（树结构、交互状态），由解析器构造后由引擎修改。
 */
public abstract class DomNode {
    private ElementNode parent;
    private SourcePosition sourcePosition;

    protected DomNode(SourcePosition sourcePosition) {
        this.sourcePosition = sourcePosition == null ? SourcePosition.unknown() : sourcePosition;
    }

    /** 节点类型：元素或文本。 */
    public enum Kind {
        ELEMENT,
        TEXT
    }

    public abstract Kind kind();

    /** 父节点；根节点返回 null。 */
    public ElementNode parent() {
        return parent;
    }

    /** 是否有子节点。文本节点始终返回 false。 */
    public abstract boolean hasChildren();

    /** 子节点数量。文本节点始终为 0。 */
    public abstract int childCount();

    /** 源位置，用于错误报告。 */
    public SourcePosition sourcePosition() {
        return sourcePosition;
    }

    /** 由父节点在 append/remove 时调用，维护双向链接不变量。 */
    void reparentTo(ElementNode newParent) {
        this.parent = newParent;
    }

    /** 覆盖源位置；仅在解析期间使用。 */
    void sourcePosition(SourcePosition sourcePosition) {
        this.sourcePosition = sourcePosition == null ? SourcePosition.unknown() : sourcePosition;
    }
}
