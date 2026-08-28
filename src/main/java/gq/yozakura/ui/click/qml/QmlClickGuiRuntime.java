package gq.yozakura.ui.click.qml;

import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.render.QmlView;
import io.github.timer_err.qml4j.render.ResourceLoader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class QmlClickGuiRuntime {
    public static final int WIDTH = 960;
    public static final int HEIGHT = 640;
    private static final String QML = "/assets/yozakura/qmlclickgui/Main.qml";

    private final MinecraftSkiaSurfaceBackend backend;
    private final QmlView view;
    private final QmlClickGuiModel model;
    private final QmlFrameScheduler scheduler;
    private boolean pointerKnown;
    private float pointerX;
    private float pointerY;

    private QmlClickGuiRuntime(MinecraftSkiaSurfaceBackend backend, QmlView view,
                               QmlClickGuiModel model) {
        this.backend = backend;
        this.view = view;
        this.model = model;
        this.scheduler = new QmlFrameScheduler(System.nanoTime());
    }

    public static QmlClickGuiRuntime open() throws IOException {
        MinecraftSkiaSurfaceBackend backend = new MinecraftSkiaSurfaceBackend();
        QmlView view = null;
        try {
            backend.init(WIDTH, HEIGHT);
            QmlClickGuiModel model = new QmlClickGuiModel();
            view = QmlView.withStockTypes(new QmlEngine())
                    .resources(classpathResources())
                    .context("clickModel", model)
                    .uiTypefaces(readBytes("/assets/minecraft/font/Inter.ttf"),
                            readBytes("/assets/minecraft/font/Inter.ttf"))
                    .cjkTypeface(readBytes("/assets/minecraft/font/AlibabaSans-Regular.otf"))
                    .iconTypeface(readBytes("/assets/minecraft/font/NovICON.ttf"));
            view.load(new String(readBytes(QML), StandardCharsets.UTF_8),
                    "assets/yozakura/qmlclickgui");
            return new QmlClickGuiRuntime(backend, view, model);
        } catch (Throwable throwable) {
            if (view != null) {
                view.dispose();
            }
            backend.dispose();
            if (throwable instanceof IOException) {
                throw (IOException) throwable;
            }
            throw new IOException("Unable to initialize QML ClickGUI", throwable);
        }
    }

    public boolean renderIfNeeded() {
        long nowNanos = System.nanoTime();
        if (model.sync()) {
            scheduler.invalidateForAnimation(nowNanos);
        }
        if (!scheduler.shouldRender(nowNanos)) return false;
        try {
            view.renderFrame(backend);
            scheduler.didRender(nowNanos);
            return true;
        } finally {
            backend.endFrameIfNeeded();
        }
    }

    public int textureId() {
        return backend.textureId();
    }

    public boolean consumeCloseRequest() {
        return model.consumeCloseRequest();
    }

    public boolean dispatchPointerDown(float x, float y, int button) {
        boolean handled = view.dispatchPointerDown(x, y, qmlButton(button));
        if (handled) scheduler.invalidateForAnimation(System.nanoTime());
        return handled;
    }

    public boolean dispatchPointerMove(float x, float y) {
        if (pointerKnown && x == pointerX && y == pointerY) return false;
        pointerKnown = true;
        pointerX = x;
        pointerY = y;
        boolean handled = view.dispatchPointerMove(x, y);
        scheduler.invalidateForAnimation(System.nanoTime());
        return handled;
    }

    public boolean dispatchPointerUp(float x, float y, int button) {
        boolean handled = view.dispatchPointerUp(x, y, qmlButton(button));
        if (handled) scheduler.invalidateForAnimation(System.nanoTime());
        return handled;
    }

    public boolean dispatchWheel(float x, float y, float delta) {
        boolean handled = view.dispatchWheel(x, y, 0.0F, delta);
        if (handled) scheduler.invalidateForAnimation(System.nanoTime());
        return handled;
    }

    public boolean dispatchKey(int keyCode, String text, boolean down, boolean shift) {
        boolean handled = view.dispatchKey(keyCode, text, down, shift);
        if (handled) scheduler.invalidateForAnimation(System.nanoTime());
        return handled;
    }

    public void dispose() {
        try {
            view.dispose();
        } finally {
            backend.dispose();
        }
    }

    private static int qmlButton(int minecraftButton) {
        if (minecraftButton == 1) {
            return 2;
        }
        if (minecraftButton == 2) {
            return 4;
        }
        return 1;
    }

    static ResourceLoader classpathResources() {
        return new ResourceLoader() {
            @Override
            public byte[] load(String source) {
                if (source == null || source.trim().isEmpty()) {
                    return null;
                }
                String path = source.startsWith("/") ? source : "/" + source;
                try {
                    return readBytes(path);
                } catch (IOException ignored) {
                    return null;
                }
            }
        };
    }

    static byte[] readBytes(String path) throws IOException {
        InputStream input = QmlClickGuiRuntime.class.getResourceAsStream(path);
        if (input == null) {
            throw new IOException("Missing ClickGUI resource: " + path);
        }
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } finally {
            input.close();
        }
    }
}
