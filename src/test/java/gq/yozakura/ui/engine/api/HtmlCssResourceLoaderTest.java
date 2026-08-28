package gq.yozakura.ui.engine.api;

import gq.yozakura.ui.engine.css.Stylesheet;
import gq.yozakura.ui.engine.dom.ElementNode;
import gq.yozakura.ui.engine.layout.MeasureContext;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 阶段 6 切片 6.2：HtmlCssResourceLoader 测试。
 *
 * <p>验证契约（AGENTS.md）：
 * <ul>
 *   <li>"Runtime resources live under src/main/resources"</li>
 *   <li>从 classpath 加载 HTML/CSS 文件，解析为 DOM + Stylesheet</li>
 *   <li>支持资源路径前缀（如 /assets/yozakura/ui/）</li>
 *   <li>缺失资源抛出明确异常（不静默 fallback）</li>
 * </ul>
 *
 * <p>测试用 classpath 中已存在的资源（测试资源目录）验证加载。
 */
public class HtmlCssResourceLoaderTest {

    private static final MeasureContext CTX = new MeasureContext() {
        @Override public int viewportWidth() { return 960; }
        @Override public int viewportHeight() { return 640; }
        @Override public float rootFontSizePx() { return 14f; }
    };

    @Test
    public void load_existing_html_and_css_returns_loaded_doc() {
        // 测试资源：src/test/resources/assets/yozakura/ui/test/Main.html + Main.css
        LoadedUiResource loaded = HtmlCssResourceLoader.load(
                "/assets/yozakura/ui/test/Main.html",
                "/assets/yozakura/ui/test/Main.css");

        assertNotNull(loaded);
        assertNotNull(loaded.html());
        assertNotNull(loaded.css());
        // html 应包含 <div 标签
        assertTrue("html contains div", loaded.html().contains("<div"));
        // css 应包含 background-color
        assertTrue("css contains background-color", loaded.css().contains("background-color"));
    }

    @Test
    public void load_and_create_document_context() {
        DocumentContext doc = HtmlCssResourceLoader.loadDocument(
                "/assets/yozakura/ui/test/Main.html",
                "/assets/yozakura/ui/test/Main.css",
                CTX);

        assertNotNull(doc);
        assertNotNull(doc.rootElement());
        assertNotNull(doc.stylesheet());
        // 应有 layout
        assertNotNull(doc.layoutRoot());
    }

    @Test
    public void loaded_doc_has_correct_root_tag() {
        DocumentContext doc = HtmlCssResourceLoader.loadDocument(
                "/assets/yozakura/ui/test/Main.html",
                "/assets/yozakura/ui/test/Main.css",
                CTX);
        assertEquals("div", doc.rootElement().tag());
    }

    @Test(expected = ResourceLoadException.class)
    public void missing_html_throws_resource_load_exception() {
        HtmlCssResourceLoader.load(
                "/assets/yozakura/ui/test/NonExistent.html",
                "/assets/yozakura/ui/test/Main.css");
    }

    @Test(expected = ResourceLoadException.class)
    public void missing_css_throws_resource_load_exception() {
        HtmlCssResourceLoader.load(
                "/assets/yozakura/ui/test/Main.html",
                "/assets/yozakura/ui/test/NonExistent.css");
    }

    @Test(expected = ResourceLoadException.class)
    public void malformed_html_throws_resource_load_exception() {
        // Malformed.html 为未闭合标签（缺 '>'），仅在解析阶段检测
        // 因此必须调用 loadDocument（触发 HtmlParser）而非 load（仅读文本）
        HtmlCssResourceLoader.loadDocument(
                "/assets/yozakura/ui/test/Malformed.html",
                "/assets/yozakura/ui/test/Main.css",
                CTX);
    }

    @Test(expected = IllegalArgumentException.class)
    public void null_html_path_rejected() {
        HtmlCssResourceLoader.load(null, "/assets/yozakura/ui/test/Main.css");
    }

    @Test(expected = IllegalArgumentException.class)
    public void null_css_path_rejected() {
        HtmlCssResourceLoader.load("/assets/yozakura/ui/test/Main.html", null);
    }

    @Test
    public void loaded_resource_carries_path_for_error_reporting() {
        try {
            HtmlCssResourceLoader.load(
                    "/assets/yozakura/ui/test/NonExistent.html",
                    "/assets/yozakura/ui/test/Main.css");
        } catch (ResourceLoadException e) {
            // 异常应携带资源路径
            assertTrue("path in message: " + e.getMessage(),
                    e.getMessage().contains("NonExistent.html"));
        }
    }
}
