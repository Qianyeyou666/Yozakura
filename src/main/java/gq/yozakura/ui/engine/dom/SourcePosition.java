package gq.yozakura.ui.engine.dom;

/**
 * 源文件中的位置（行号、列号），用于解析错误报告与调试。
 *
 * <p>行号从 1 开始，列号从 1 开始。0 表示未知位置。
 * 不可变值对象。
 */
public final class SourcePosition {
    private static final SourcePosition UNKNOWN = new SourcePosition(0, 0);

    private final int line;
    private final int column;

    private SourcePosition(int line, int column) {
        this.line = line;
        this.column = column;
    }

    /** 已知位置；line 与 column 必须为正。 */
    public static SourcePosition of(int line, int column) {
        if (line < 1 || column < 1) {
            throw new IllegalArgumentException(
                    "line and column must be >= 1, got line=" + line + " column=" + column);
        }
        return new SourcePosition(line, column);
    }

    /** 未知位置占位符，用于未关联源信息的节点。 */
    public static SourcePosition unknown() {
        return UNKNOWN;
    }

    public int line() {
        return line;
    }

    public int column() {
        return column;
    }

    public boolean isUnknown() {
        return line == 0 && column == 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SourcePosition)) return false;
        SourcePosition that = (SourcePosition) o;
        return line == that.line && column == that.column;
    }

    @Override
    public int hashCode() {
        return 31 * line + column;
    }

    @Override
    public String toString() {
        return isUnknown() ? "unknown" : (line + ":" + column);
    }
}
