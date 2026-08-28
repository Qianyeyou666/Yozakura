package gq.yozakura.ui.engine.dom;

/**
 * 文本节点，叶子节点，无子节点。持有原始文本内容。
 *
 * <p>文本在解析期已做实体解码（由 HtmlParser 负责），此处存储最终字符串。
 */
public final class TextNode extends DomNode {
    private final String text;

    private TextNode(String text, SourcePosition sourcePosition) {
        super(sourcePosition);
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }
        this.text = text;
    }

    public static TextNode of(String text) {
        return new TextNode(text, SourcePosition.unknown());
    }

    public static TextNode of(String text, SourcePosition sourcePosition) {
        return new TextNode(text, sourcePosition);
    }

    @Override
    public Kind kind() {
        return Kind.TEXT;
    }

    public String text() {
        return text;
    }

    @Override
    public boolean hasChildren() {
        return false;
    }

    @Override
    public int childCount() {
        return 0;
    }

    @Override
    public String toString() {
        return "TextNode{" + text + "}";
    }
}
