package gq.yozakura.ui.click.engine;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class YozakuraUiClickGuiContractTest {
    @Test
    public void clickGuiUsesTheDirectOpenGlTimewarpScreenInsteadOfTheHtmlHost() throws Exception {
        String source = read("src/main/java/gq/yozakura/module/render/ClickGUI.java");
        assertTrue(source.contains("new TimewarpClickGui()"));
        assertTrue(!source.contains("new YozakuraPanelClickGui()"));
        assertTrue(!source.contains("YozakuraUiClickGuiScreen.open(mc)"));
        assertTrue(!source.contains("QmlClickGuiScreen.open(mc)"));
        assertTrue(!source.contains("WebView2ClickGuiScreen.open(mc)"));
    }

    @Test
    public void clickGuiHtmlAndCssAreRealClasspathResources() {
        assertTrue(Files.isRegularFile(Paths.get(
                "src/main/resources/assets/yozakura/ui/clickgui/index.html")));
        assertTrue(Files.isRegularFile(Paths.get(
                "src/main/resources/assets/yozakura/ui/clickgui/style.css")));
    }

    @Test
    public void fullscreenReinitReusesTheRetainedDocumentAndRenderer() throws Exception {
        String source = read("src/main/java/gq/yozakura/ui/click/engine/YozakuraUiClickGuiScreen.java");
        assertTrue(source.contains("if (document != null && renderer != null)"));
        assertTrue(source.contains("installCustomCursor();\n            return;"));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }
}
