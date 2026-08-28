package gq.yozakura.ui.engine.api;

/**
 * 加载结果值对象：HTML/CSS 文本与其 classpath 路径。
 *
 * <p>由 {@link HtmlCssResourceLoader#load(String, String)} 返回，作为加载与解析之间的
 * 中间数据载体。不可变；构造后字段不可变。
 *
 * <p>路径字段用于错误报告与日志定位，不参与内容解析。
 */
public final class LoadedUiResource {

    private final String html;
    private final String css;
    private final String htmlPath;
    private final String cssPath;

    public LoadedUiResource(String html, String css, String htmlPath, String cssPath) {
        if (html == null) {
            throw new IllegalArgumentException("html must not be null");
        }
        if (css == null) {
            throw new IllegalArgumentException("css must not be null");
        }
        this.html = html;
        this.css = css;
        this.htmlPath = htmlPath;
        this.cssPath = cssPath;
    }

    /** HTML 文本内容。 */
    public String html() {
        return html;
    }

    /** CSS 文本内容。 */
    public String css() {
        return css;
    }

    /** HTML classpath 路径（用于错误报告），可能为 null。 */
    public String htmlPath() {
        return htmlPath;
    }

    /** CSS classpath 路径（用于错误报告），可能为 null。 */
    public String cssPath() {
        return cssPath;
    }
}
