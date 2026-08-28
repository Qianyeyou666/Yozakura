package gq.yozakura.ui.click.yozakura;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class PanelClickGuiCursorContractTest {
    @Test
    public void panelInstallsAndRestoresItsNativeCursorWithTheScreenLifecycle() throws IOException {
        String panel = source("src/main/java/gq/yozakura/ui/click/yozakura/YozakuraPanelClickGui.java");
        String init = between(panel, "    public void initGui() {", "    private void resetFrameClock() {");
        String close = between(panel, "    public void onGuiClosed() {", "    @Override\n    public boolean doesGuiPauseGame()");

        assertTrue(panel.contains("private final PanelClickGuiCursor cursor = new PanelClickGuiCursor();"));
        assertTrue(init.contains("cursor.install();"));
        assertTrue(close.contains("cursor.restore();"));
        assertTrue(close.indexOf("cursor.restore();") < close.indexOf("super.onGuiClosed();"));
    }

    @Test
    public void cursorRestoresThePreviousNativeCursorAndDestroysOnlyItsOwnHandle() throws IOException {
        String cursor = source("src/main/java/gq/yozakura/ui/click/yozakura/PanelClickGuiCursor.java");

        assertTrue(cursor.contains("previousCursor = Mouse.getNativeCursor();"));
        assertTrue(cursor.contains("image.lwjglHotspotY()"));
        assertTrue(cursor.contains("PanelClickGuiWindowsAlphaCursor.install(image)"));
        assertTrue(cursor.contains("Mouse.setNativeCursor(panelCursor);"));
        assertTrue(cursor.contains("PanelClickGuiWindowsAlphaCursor.restore();"));
        assertTrue(cursor.contains("Mouse.setNativeCursor(previousCursor);"));
        assertTrue(cursor.contains("panelCursor.destroy();"));
        assertTrue(!cursor.contains("previousCursor.destroy();"));
        assertTrue(cursor.contains("!Mouse.isCreated()"));
    }

    private static String between(String source, String beginMarker, String endMarker) {
        int begin = source.indexOf(beginMarker);
        int end = source.indexOf(endMarker, begin + beginMarker.length());
        assertTrue(begin >= 0 && end > begin);
        return source.substring(begin, end);
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }
}
