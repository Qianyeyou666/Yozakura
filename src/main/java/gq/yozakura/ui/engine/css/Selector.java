package gq.yozakura.ui.engine.css;

/**
 * CSS 选择器。阶段 1 持有原始文本；切片 4 扩展为结构化 compound + combinator。
 *
 * <p>不可变值对象。{@link #text()} 返回去除首尾空白后的原始选择器文本，
 * 如 "div.window > .sidebar .category:hover"。
 */
public final class Selector {
    private final String text;

    public Selector(String text) {
        if (text == null) {
            throw new IllegalArgumentException("selector text must not be null");
        }
        this.text = text.trim();
        if (this.text.isEmpty()) {
            throw new IllegalArgumentException("selector text must not be empty");
        }
    }

    public String text() {
        return text;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Selector)) return false;
        return text.equals(((Selector) o).text);
    }

    @Override
    public int hashCode() {
        return text.hashCode();
    }

    @Override
    public String toString() {
        return text;
    }
}
