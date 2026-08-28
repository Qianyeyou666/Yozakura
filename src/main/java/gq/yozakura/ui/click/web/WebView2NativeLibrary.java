package gq.yozakura.ui.click.web;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Loads the small WebView2 JNI bridge for normal Forge JAR launches. */
final class WebView2NativeLibrary {
    private static final String RESOURCE_VERSION = "1.0.2903.40-v2";
    private static boolean loaded;
    private static String error = "WebView2 native library was not loaded";

    private WebView2NativeLibrary() {
    }

    static synchronized boolean ensureLoaded() {
        if (loaded) {
            return true;
        }
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            error = "WebView2 ClickGUI requires Windows";
            return false;
        }

        String arch = System.getProperty("os.arch", "").contains("64") ? "x64" : "x86";
        try {
            File directory = new File(System.getProperty("java.io.tmpdir"),
                    "yozakura-webview2/" + RESOURCE_VERSION + "-" + arch);
            if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
                throw new IOException("Could not create native directory: " + directory);
            }
            File loader = extract("/assets/yozakura/native/" + arch + "/WebView2Loader.dll",
                    new File(directory, "WebView2Loader.dll"));
            File bridge = extract("/assets/yozakura/native/" + arch + "/YozakuraWebView2Bridge.dll",
                    new File(directory, "YozakuraWebView2Bridge.dll"));
            load(loader, bridge);
            return true;
        } catch (Throwable throwable) {
            error = throwable.getClass().getSimpleName() + ": "
                    + (throwable.getMessage() == null ? "native bridge load failed" : throwable.getMessage());
            return false;
        }
    }

    static String lastError() {
        return error;
    }

    private static void load(File loader, File bridge) {
        System.load(loader.getAbsolutePath());
        System.load(bridge.getAbsolutePath());
        loaded = true;
        error = "";
    }

    private static File extract(String resourcePath, File target) throws IOException {
        InputStream input = WebView2NativeLibrary.class.getResourceAsStream(resourcePath);
        if (input == null) {
            throw new IOException("Missing bundled native resource: " + resourcePath);
        }
        File temporary = new File(target.getParentFile(), target.getName() + ".tmp");
        OutputStream output = new FileOutputStream(temporary);
        try {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
        } finally {
            try {
                input.close();
            } finally {
                output.close();
            }
        }
        if (target.exists() && !target.delete()) {
            throw new IOException("Could not replace native resource: " + target);
        }
        if (!temporary.renameTo(target)) {
            throw new IOException("Could not install native resource: " + target);
        }
        return target;
    }
}
