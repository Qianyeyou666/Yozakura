package gq.yozakura.ui.click.yozakura;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertTrue;

public class PanelClickGuiWindowsAlphaCursorContractTest {
    @Test
    public void panelCursorUsesTheWindowsAlphaBridgeBeforeTheLwjglFallback() throws Exception {
        String cursor = read("src/main/java/gq/yozakura/ui/click/yozakura/PanelClickGuiCursor.java");

        assertTrue(cursor.contains("PanelClickGuiWindowsAlphaCursor.install(image)"));
        assertTrue(cursor.contains("PanelClickGuiWindowsAlphaCursor.restore()"));
        assertTrue(cursor.contains("new Cursor("));
    }

    @Test
    public void imageKeepsPngAlphaForTheWindowsAlphaBridge() throws Exception {
        String image = read("src/main/java/gq/yozakura/ui/click/yozakura/PanelClickGuiCursorImage.java");

        assertTrue(image.contains("topLeftArgbPixels()"));
        assertTrue(!image.contains("forcePortableBinaryAlpha"));
    }

    @Test
    public void nativeBridgeBuildsAndDestroysAnAlphaCursor() throws Exception {
        String nativeSource = read("native/yozakura_webview2.cpp");

        assertTrue(nativeSource.contains("BITMAPV5HEADER"));
        assertTrue(nativeSource.contains("bV5AlphaMask"));
        assertTrue(nativeSource.contains("CreateIconIndirect"));
        assertTrue(nativeSource.contains("DestroyCursor"));
        assertTrue(nativeSource.contains("panelCursorVisible"));
        assertTrue(nativeSource.contains("registerYozakuraPanelClickGuiCursor"));
        assertTrue(nativeSource.contains("Java_gq_yozakura_ui_click_yozakura_PanelClickGuiWindowsAlphaCursor_install0"));
        assertTrue(nativeSource.contains("Java_gq_yozakura_ui_click_yozakura_PanelClickGuiWindowsAlphaCursor_restore0"));
    }

    private static String read(String path) throws IOException {
        FileInputStream input = new FileInputStream(new File(path));
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            input.close();
        }
    }
}
