package gq.yozakura.ui.engine.dom;

/**
 * HTML 解析异常，携带资源路径、行号、列号与消息，用于定位语法错误。
 *
 * <p>不静默降级：解析失败必须抛出此异常，由调用方决定如何呈现错误。
 *
 * <p>resourcePath 可能为 null（如内存中的字符串解析），表示未知来源；
 * 当通过 {@code parse(source, resourcePath)} 调用时，异常会带上资源路径，
 * 让上层能在多资源加载场景下精确定位错误来源。
 */
public class HtmlParseException extends RuntimeException {
    private final String resourcePath;
    private final int line;
    private final int column;

    public HtmlParseException(String message, int line, int column) {
        this(message, null, line, column);
    }

    public HtmlParseException(String message, int line, int column, Throwable cause) {
        this(message, null, line, column, cause);
    }

    public HtmlParseException(String message, String resourcePath, int line, int column) {
        super(formatMessage(message, resourcePath, line, column));
        this.resourcePath = resourcePath;
        this.line = line;
        this.column = column;
    }

    public HtmlParseException(String message, String resourcePath, int line, int column, Throwable cause) {
        super(formatMessage(message, resourcePath, line, column), cause);
        this.resourcePath = resourcePath;
        this.line = line;
        this.column = column;
    }

    public String resourcePath() {
        return resourcePath;
    }

    public int line() {
        return line;
    }

    public int column() {
        return column;
    }

    private static String formatMessage(String message, String resourcePath, int line, int column) {
        StringBuilder sb = new StringBuilder();
        sb.append(message);
        sb.append(" (at line ").append(line).append(":").append(column).append(")");
        if (resourcePath != null) {
            sb.append(" in ").append(resourcePath);
        }
        return sb.toString();
    }
}
