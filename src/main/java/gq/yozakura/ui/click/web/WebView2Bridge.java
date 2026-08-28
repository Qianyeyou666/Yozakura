package gq.yozakura.ui.click.web;

/** JNI boundary for the Windows WebView2 child view hosted by Minecraft. */
public final class WebView2Bridge {
    private static volatile String loadError;

    private WebView2Bridge() {
    }

    public static boolean prepare() {
        try {
            lastError0();
            return true;
        } catch (UnsatisfiedLinkError error) {
            if (WebView2NativeLibrary.ensureLoaded()) {
                return true;
            }
            loadError = WebView2NativeLibrary.lastError();
            return false;
        }
    }

    public static boolean show(String url) {
        try {
            return show0(url);
        } catch (UnsatisfiedLinkError error) {
            if (!WebView2NativeLibrary.ensureLoaded()) {
                loadError = WebView2NativeLibrary.lastError();
                return false;
            }
            try {
                return show0(url);
            } catch (UnsatisfiedLinkError retryError) {
                loadError = retryError.toString();
                return false;
            }
        }
    }

    public static boolean prewarm(String url) {
        if (!prepare()) {
            return false;
        }
        try {
            return prewarm0(url);
        } catch (UnsatisfiedLinkError error) {
            loadError = error.toString();
            return false;
        }
    }

    public static void hide() {
        try {
            hide0();
        } catch (UnsatisfiedLinkError ignored) {
        }
    }

    public static boolean consumeCloseRequest() {
        try {
            return consumeCloseRequest0();
        } catch (UnsatisfiedLinkError ignored) {
            return false;
        }
    }

    public static void syncBounds() {
        try {
            syncBounds0();
        } catch (UnsatisfiedLinkError ignored) {
        }
    }

    public static String lastError() {
        String javaLoadError = loadError;
        if (javaLoadError != null && !javaLoadError.isEmpty()) {
            return javaLoadError;
        }
        try {
            String value = lastError0();
            return value == null ? "WebView2 bridge unavailable" : value;
        } catch (UnsatisfiedLinkError ignored) {
            return "WebView2 bridge unavailable";
        }
    }

    private static native boolean show0(String url);
    private static native boolean prewarm0(String url);
    private static native void hide0();
    private static native boolean consumeCloseRequest0();
    private static native void syncBounds0();
    private static native String lastError0();
}
