package gq.yozakura.ui.engine.api;

import gq.yozakura.ui.engine.css.CssParseException;
import gq.yozakura.ui.engine.css.CssParser;
import gq.yozakura.ui.engine.css.Stylesheet;
import gq.yozakura.ui.engine.dom.DomNode;
import gq.yozakura.ui.engine.dom.ElementNode;
import gq.yozakura.ui.engine.dom.HtmlParseException;
import gq.yozakura.ui.engine.dom.HtmlParser;
import gq.yozakura.ui.engine.layout.MeasureContext;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * HTML/CSS 资源加载器：从 classpath 加载文件并解析为 DOM + Stylesheet。
 *
 * <p>AGENTS.md 契约：
 * <ul>
 *   <li>"Runtime resources live under src/main/resources"</li>
 *   <li>"Do not add silent fallbacks. A parser, shader, font, texture or renderer failure
 *       must identify the resource and root cause."</li>
 * </ul>
 *
 * <p>加载顺序：
 * <ol>
 *   <li>从 classpath 读取 HTML/CSS 文本（UTF-8）</li>
 *   <li>HTML → {@link HtmlParser} → {@link ElementNode}</li>
 *   <li>CSS → {@link CssParser} → {@link Stylesheet}</li>
 *   <li>解析失败抛 {@link ResourceLoadException}（携带资源路径与原因）</li>
 * </ol>
 *
 * <p>资源路径约定：以 '/' 开头的 classpath 绝对路径
 * （如 /assets/yozakura/ui/clickgui/Main.html）。
 *
 * <p>线程模型：单线程。读取流在调用线程同步完成。
 */
public final class HtmlCssResourceLoader {

    private HtmlCssResourceLoader() {
        // 工具类，不可实例化
    }

    /**
     * 加载 HTML 与 CSS 文本。
     *
     * @param htmlPath HTML classpath 路径（非 null，以 '/' 开头）
     * @param cssPath  CSS classpath 路径（非 null，以 '/' 开头）
     * @return 加载结果（html + css 文本）
     * @throws ResourceLoadException 资源缺失或读取失败
     */
    public static LoadedUiResource load(String htmlPath, String cssPath) {
        if (htmlPath == null) {
            throw new IllegalArgumentException("htmlPath must not be null");
        }
        if (cssPath == null) {
            throw new IllegalArgumentException("cssPath must not be null");
        }
        String html = readResource(htmlPath);
        String css = readResource(cssPath);
        return new LoadedUiResource(html, css, htmlPath, cssPath);
    }

    /**
     * 加载并构造 {@link DocumentContext}。
     *
     * @param htmlPath   HTML classpath 路径
     * @param cssPath    CSS classpath 路径
     * @param measureCtx 布局度量上下文
     * @return 文档上下文
     * @throws ResourceLoadException 资源缺失或解析失败
     */
    public static DocumentContext loadDocument(String htmlPath, String cssPath,
                                                 MeasureContext measureCtx) {
        LoadedUiResource loaded = load(htmlPath, cssPath);

        ElementNode root;
        try {
            DomNode node = new HtmlParser().parse(loaded.html(), htmlPath);
            if (!(node instanceof ElementNode)) {
                throw new ResourceLoadException(htmlPath,
                        "HTML root is not an ElementNode: " + node);
            }
            root = (ElementNode) node;
        } catch (HtmlParseException e) {
            throw new ResourceLoadException(htmlPath,
                    "HTML parse failed: " + e.getMessage(), e);
        }

        Stylesheet stylesheet;
        try {
            stylesheet = new CssParser().parse(loaded.css(), cssPath);
        } catch (CssParseException e) {
            throw new ResourceLoadException(cssPath,
                    "CSS parse failed: " + e.getMessage(), e);
        }

        return new DocumentContext(root, stylesheet, measureCtx);
    }

    /**
     * 从 classpath 读取资源文本（UTF-8）。
     *
     * @param path classpath 路径（以 '/' 开头）
     * @return 文本内容
     * @throws ResourceLoadException 资源缺失或读取失败
     */
    private static String readResource(String path) {
        InputStream stream = HtmlCssResourceLoader.class.getResourceAsStream(path);
        if (stream == null) {
            // 尝试 ClassLoader 路径（去掉前导 '/'）
            String altPath = path.startsWith("/") ? path.substring(1) : path;
            stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(altPath);
        }
        if (stream == null) {
            throw new ResourceLoadException(path,
                    "resource not found on classpath: " + path);
        }
        try {
            return readAll(stream);
        } catch (IOException e) {
            throw new ResourceLoadException(path,
                    "failed to read resource: " + path, e);
        } finally {
            try {
                stream.close();
            } catch (IOException ignored) {
                // 关闭失败忽略
            }
        }
    }

    private static String readAll(InputStream stream) throws IOException {
        // 用 ArrayList<String> 累积行，避免 StringBuilder 大小估计
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8));
        List<String> lines = new ArrayList<String>();
        String line;
        while ((line = reader.readLine()) != null) {
            lines.add(line);
        }
        // 拼接（保留换行）
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(lines.get(i));
        }
        return sb.toString();
    }
}
