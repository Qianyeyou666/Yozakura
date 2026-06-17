package gq.yozakura.ui.click.web;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import gq.yozakura.module.render.ClickGUI;
import gq.yozakura.util.minecraft.Helper;
import net.minecraft.client.Minecraft;

import java.awt.Desktop;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public final class WebClickGuiService {
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static final int BACKLOG = 4;
    private static final String TOKEN = createToken();
    private static HttpServer server;
    private static Executor executor;
    private static int activePort = -1;

    private WebClickGuiService() {
    }

    public static synchronized void open() {
        try {
            ensureStarted();
            URI uri = new URI("http://127.0.0.1:" + activePort + "/?token=" + TOKEN);
            openBrowser(uri);
            Helper.sendMessage("WebClickGUI opened at " + uri);
        } catch (Throwable throwable) {
            log("Failed to open WebClickGUI", throwable);
            Helper.sendMessage("WebClickGUI failed: " + throwable.getClass().getSimpleName());
        }
    }

    public static synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
            activePort = -1;
        }
    }

    private static void ensureStarted() throws IOException {
        int port = Math.max(1024, Math.min(65535, ClickGUI.webPort.getValue().intValue()));
        if (server != null && activePort == port) {
            return;
        }
        if (server != null) {
            server.stop(0);
            server = null;
        }

        HttpServer next = HttpServer.create(new InetSocketAddress("127.0.0.1", port), BACKLOG);
        next.createContext("/", new PageHandler());
        next.createContext("/api/state", new StateHandler());
        next.createContext("/api/module/toggle", new ToggleModuleHandler());
        next.createContext("/api/value/set", new SetValueHandler());
        next.createContext("/api/key/set", new SetKeyHandler());
        if (executor == null) {
            executor = Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, "Yozakura WebClickGUI");
                thread.setDaemon(true);
                thread.setContextClassLoader(WebClickGuiService.class.getClassLoader());
                return thread;
            });
        }
        next.setExecutor(executor);
        next.start();
        server = next;
        activePort = port;
    }

    private static void openBrowser(URI uri) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uri);
                return;
            }
        } catch (Throwable throwable) {
            log("Desktop browse failed", throwable);
        }
        try {
            Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", "", uri.toString()});
        } catch (Throwable throwable) {
            log("Shell browse failed", throwable);
        }
    }

    private static String createToken() {
        byte[] data = new byte[16];
        new SecureRandom().nextBytes(data);
        StringBuilder builder = new StringBuilder(data.length * 2);
        for (byte datum : data) {
            builder.append(String.format(Locale.ROOT, "%02x", datum & 255));
        }
        return builder.toString();
    }

    private static boolean acceptsJson(HttpExchange exchange) {
        String path = exchange.getRequestURI().getPath();
        return path != null && path.startsWith("/api/");
    }

    private static boolean hasValidToken(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("X-Yozakura-Token");
        if (TOKEN.equals(header)) {
            return true;
        }
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null) {
            return false;
        }
        String[] parts = query.split("&");
        for (String part : parts) {
            if (part.equals("token=" + TOKEN)) {
                return true;
            }
        }
        return false;
    }

    private static String readRequestBody(HttpExchange exchange) throws IOException {
        InputStream inputStream = exchange.getRequestBody();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[2048];
        int read;
        while ((read = inputStream.read(buffer)) >= 0) {
            output.write(buffer, 0, read);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void sendJson(HttpExchange exchange, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("Cache-Control", "no-store");
        headers.set("Access-Control-Allow-Origin", "http://127.0.0.1:" + activePort);
        headers.set("Access-Control-Allow-Headers", "Content-Type, X-Yozakura-Token");
        headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.sendResponseHeaders(code, bytes.length);
        OutputStream output = exchange.getResponseBody();
        try {
            output.write(bytes);
        } finally {
            output.close();
        }
    }

    private static void sendText(HttpExchange exchange, int code, String text, String contentType) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType + "; charset=utf-8");
        headers.set("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(code, bytes.length);
        OutputStream output = exchange.getResponseBody();
        try {
            output.write(bytes);
        } finally {
            output.close();
        }
    }

    private static void handleOptions(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "http://127.0.0.1:" + activePort);
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, X-Yozakura-Token");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }

    private static String loadResource(String path) throws IOException {
        InputStream stream = WebClickGuiService.class.getResourceAsStream(path);
        if (stream == null) {
            return "";
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        try {
            while ((read = stream.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
        } finally {
            stream.close();
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void queueOnMainThread(Runnable runnable) {
        try {
            MC.addScheduledTask(runnable);
        } catch (Throwable throwable) {
            runnable.run();
        }
    }

    private static String path(HttpExchange exchange) {
        String path = exchange.getRequestURI().getPath();
        return path == null || path.length() == 0 ? "/" : path;
    }

    private static void log(String message, Throwable throwable) {
        try {
            File log = new File(System.getProperty("java.io.tmpdir"), "YozakuraWebClickGui.log");
            PrintWriter writer = new PrintWriter(new FileWriter(log, true));
            try {
                writer.println(message);
                if (throwable != null) {
                    throwable.printStackTrace(writer);
                }
            } finally {
                writer.close();
            }
        } catch (Throwable ignored) {
        }
    }

    private abstract static class ApiHandler implements HttpHandler {
        @Override
        public final void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            if (acceptsJson(exchange) && !hasValidToken(exchange)) {
                sendJson(exchange, 403, "{\"ok\":false,\"error\":\"invalid token\"}");
                return;
            }
            try {
                handleApi(exchange);
            } catch (Throwable throwable) {
                log("WebClickGUI API failed: " + path(exchange), throwable);
                sendJson(exchange, 500, "{\"ok\":false,\"error\":\"internal error\"}");
            }
        }

        protected abstract void handleApi(HttpExchange exchange) throws Exception;
    }

    private static final class PageHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestPath = path(exchange);
            if ("/".equals(requestPath) || "/index.html".equals(requestPath)) {
                String html = loadResource("/assets/yozakura/webclickgui/index.html")
                        .replace("__YOZAKURA_TOKEN_VALUE__", TOKEN)
                        .replace("__YOZAKURA_PORT_VALUE__", String.valueOf(activePort));
                sendText(exchange, 200, html, "text/html");
            } else if ("/styles.css".equals(requestPath)) {
                sendText(exchange, 200, loadResource("/assets/yozakura/webclickgui/styles.css"), "text/css");
            } else if ("/script.js".equals(requestPath)) {
                sendText(exchange, 200, loadResource("/assets/yozakura/webclickgui/script.js"), "application/javascript");
            } else {
                sendText(exchange, 404, "Not Found", "text/plain");
            }
        }
    }

    private static final class StateHandler extends ApiHandler {
        @Override
        protected void handleApi(HttpExchange exchange) throws Exception {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"ok\":false,\"error\":\"method not allowed\"}");
                return;
            }
            sendJson(exchange, 200, WebClickGuiController.stateJson(activePort));
        }
    }

    private static final class ToggleModuleHandler extends ApiHandler {
        @Override
        protected void handleApi(HttpExchange exchange) throws Exception {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"ok\":false,\"error\":\"method not allowed\"}");
                return;
            }
            final String body = readRequestBody(exchange);
            queueOnMainThread(new Runnable() {
                @Override
                public void run() {
                    WebClickGuiController.toggleModule(body);
                }
            });
            sendJson(exchange, 200, "{\"ok\":true}");
        }
    }

    private static final class SetValueHandler extends ApiHandler {
        @Override
        protected void handleApi(HttpExchange exchange) throws Exception {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"ok\":false,\"error\":\"method not allowed\"}");
                return;
            }
            final String body = readRequestBody(exchange);
            queueOnMainThread(new Runnable() {
                @Override
                public void run() {
                    WebClickGuiController.setValue(body);
                }
            });
            sendJson(exchange, 200, "{\"ok\":true}");
        }
    }

    private static final class SetKeyHandler extends ApiHandler {
        @Override
        protected void handleApi(HttpExchange exchange) throws Exception {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"ok\":false,\"error\":\"method not allowed\"}");
                return;
            }
            final String body = readRequestBody(exchange);
            queueOnMainThread(new Runnable() {
                @Override
                public void run() {
                    WebClickGuiController.setKey(body);
                }
            });
            sendJson(exchange, 200, "{\"ok\":true}");
        }
    }
}
