package gq.yozakura.ui.click.engine;

import gq.yozakura.ui.engine.api.DocumentContext;
import gq.yozakura.ui.engine.api.HtmlCssResourceLoader;
import gq.yozakura.ui.engine.css.ComputedStyle;
import gq.yozakura.ui.engine.dom.DomNode;
import gq.yozakura.ui.engine.dom.ElementNode;
import gq.yozakura.ui.engine.layout.MeasureContext;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class YozakuraUiResourceTest {
    @Test
    public void productionClickGuiResourceParsesAndBuildsPaintCommands() {
        DocumentContext document = loadDocument();
        assertNotNull(document.layoutRoot());
        assertNotNull(document.paintCommands());
        assertTrue(document.paintCommands().size() > 10);
    }

    @Test
    public void productionStyleCarriesWebViewElevationAndCornerHierarchy() throws Exception {
        DocumentContext document = loadDocument();

        ComputedStyle window = style(document, "window");
        ComputedStyle titlebar = style(document, "titlebar");
        ComputedStyle sidebar = style(document, "sidebar");
        ComputedStyle main = style(document, "main");
        ComputedStyle brandMark = style(document, "brand-mark");

        assertEquals("0 10px 24px rgba(0,0,0,0.30)", window.get("box-shadow"));
        assertEquals("20px 20px 0 0", titlebar.get("border-radius"));
        assertEquals("0 0 0 20px", sidebar.get("border-radius"));
        assertEquals("0 0 20px 0", main.get("border-radius"));
        assertEquals("linear-gradient(135deg, #f08bb0, #f5a6c7)",
                brandMark.get("background"));
        assertEquals("6px 20px 20px", style(document, "modules").get("padding"));
        String css = new String(Files.readAllBytes(Paths.get(
                "src/main/resources/assets/yozakura/ui/clickgui/style.css")), StandardCharsets.UTF_8);
        assertTrue(css.contains(".module-card { width: 700px; height: 56px; flex-shrink: 0; padding: 0 16px;"));
        assertTrue(css.contains(".category { width: 198px; height: 38px;"));
        assertTrue(css.contains("padding: 9px 13px;"));
        assertTrue(css.contains(".category-name { width: 140px; height: 16px; flex-shrink: 0; font-size: 12.5px;"));
        assertTrue(css.contains(".module-name { width: 470px; height: 17px; flex-shrink: 0; font-family: Bricolage Grotesque;"));
        assertTrue(css.contains(".settings-button { width: 30px; height: 30px; padding: 7px 0; text-align: center; background-color: var(--card-hover); border: 1px solid var(--border-2);"));
        assertTrue(css.contains("font-family: NovICON; font-size: 14px;"));
        assertTrue(css.contains(".toggle.on .toggle-knob { left: 18px;"));
        assertTrue(css.contains(".setting-toggle.on .setting-toggle-knob { left: 18px;"));
        assertTrue(css.contains(".modules { width: 740px; height: 523px; flex-shrink: 0; position: relative;"));
    }

    private static DocumentContext loadDocument() {
        return HtmlCssResourceLoader.loadDocument(
                "/assets/yozakura/ui/clickgui/index.html",
                "/assets/yozakura/ui/clickgui/style.css",
                new MeasureContext() {
                    @Override public int viewportWidth() { return 960; }
                    @Override public int viewportHeight() { return 640; }
                    @Override public float rootFontSizePx() { return 14.0F; }
                });
    }

    private static ComputedStyle style(DocumentContext document, String className) {
        ElementNode element = findByClass(document.rootElement(), className);
        assertNotNull("missing ." + className, element);
        ComputedStyle style = document.styles().get(element);
        assertNotNull("missing computed style for ." + className, style);
        return style;
    }

    private static ElementNode findByClass(ElementNode root, String className) {
        if (root.hasClass(className)) return root;
        for (int i = 0; i < root.childCount(); i++) {
            DomNode child = root.child(i);
            if (child instanceof ElementNode) {
                ElementNode found = findByClass((ElementNode) child, className);
                if (found != null) return found;
            }
        }
        return null;
    }
}
