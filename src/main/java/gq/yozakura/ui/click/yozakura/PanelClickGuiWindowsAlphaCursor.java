package gq.yozakura.ui.click.yozakura;

import gq.yozakura.ui.click.web.WebView2Bridge;

/** Windows-only 32-bit Alpha hardware cursor path for the Panel ClickGUI. */
final class PanelClickGuiWindowsAlphaCursor {
    private PanelClickGuiWindowsAlphaCursor() {
    }

    static boolean install(PanelClickGuiCursorImage image) {
        if (!isWindows() || !WebView2Bridge.prepare()) {
            return false;
        }
        try {
            return install0(image.width(), image.height(), image.hotspotX(), image.hotspotY(),
                    image.topLeftArgbPixels());
        } catch (UnsatisfiedLinkError ignored) {
            return false;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    static void restore() {
        try {
            restore0();
        } catch (UnsatisfiedLinkError ignored) {
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static native boolean install0(int width, int height, int hotspotX, int hotspotY,
                                           int[] topLeftArgbPixels);

    private static native void restore0();
}
