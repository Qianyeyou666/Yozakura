package gq.yozakura.ui.click;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClickGuiRendererActivationContractTest {
    @Test
    public void timewarpIsTheOnlyClickGuiEntryPoint() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/render/ClickGUI.java");

        assertTrue(source.contains("GuiStyle.PANEL"));
        assertTrue(source.contains("new TimewarpClickGui()"));
        assertFalse(source.contains("new YozakuraPanelClickGui()"));
        assertFalse(source.contains("GuiStyle.YOZAKURA"));
        assertFalse(source.contains("new YozakuraClickGui()"));
        assertFalse(source.contains("new SakuraClickGui()"));
        assertFalse(source.contains("new MaterialClickGui()"));
    }

    @Test
    public void webAndQmlRenderersDoNotOwnTheClickGuiEntryPoint() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/render/ClickGUI.java");

        assertFalse(source.contains("OPENGL_PANEL"));
        assertFalse(source.contains("WEBVIEW"));
        assertFalse(source.contains("QML"));
        assertFalse(source.contains("WebClickGuiService"));
        assertFalse(source.contains("webPort"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }
}
