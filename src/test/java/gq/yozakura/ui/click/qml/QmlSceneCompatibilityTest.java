package gq.yozakura.ui.click.qml;

import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.render.QmlView;
import io.github.timer_err.qml4j.render.SurfaceBackend;
import io.github.timer_err.qml4j.render.items.core.Item;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Data;
import io.github.humbleui.skija.EncodedImageFormat;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Surface;
import gq.yozakura.module.ModuleType;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class QmlSceneCompatibilityTest {
    @Test
    public void mainSceneCompilesWithTheJavaEightRhinoRuntime() throws Exception {
        QmlView view = QmlView.withStockTypes(new QmlEngine())
                .resources(QmlClickGuiRuntime.classpathResources())
                .context("clickModel", previewModel())
                .uiTypefaces(readResourceBytes("/assets/minecraft/font/Inter.ttf"),
                        readResourceBytes("/assets/minecraft/font/Inter.ttf"))
                .cjkTypeface(readResourceBytes("/assets/minecraft/font/AlibabaSans-Regular.otf"));
        RasterBackend backend = new RasterBackend();
        try {
            Item root = view.load(readResource("/assets/yozakura/qmlclickgui/Main.qml"),
                    "assets/yozakura/qmlclickgui");

            assertNotNull(root);
            assertEquals(960.0F, root.width.peekFloat(), 0.01F);
            assertEquals(640.0F, root.height.peekFloat(), 0.01F);
            backend.init(960, 640);
            view.renderFrame(backend);
            Image snapshot = backend.snapshot();
            try {
                assertNotNull(snapshot);
                writePreview(snapshot);
            } finally {
                if (snapshot != null) {
                    snapshot.close();
                }
            }
        } finally {
            view.dispose();
            backend.dispose();
        }
    }

    private static void writePreview(Image snapshot) throws Exception {
        Data png = snapshot.encodeToData(EncodedImageFormat.PNG);
        try {
            Path directory = Paths.get("build", "reports", "qmlclickgui");
            Files.createDirectories(directory);
            Files.write(directory.resolve("Main.png"), png.getBytes());
        } finally {
            png.close();
        }
    }

    private static QmlClickGuiModel previewModel() {
        return new QmlClickGuiModel(Arrays.<QmlClickGuiModel.Entry>asList(
                new PreviewEntry("AimAssist", "Mouse-like first-person aim assistance", true, true),
                new PreviewEntry("AntiBot", "Make modules exclude invalid entities", true, false),
                new PreviewEntry("AutoClicker", "Clicks automatically while attacking", false, true)
        ), false);
    }

    private static String readResource(String path) throws Exception {
        return new String(readResourceBytes(path), StandardCharsets.UTF_8);
    }

    private static byte[] readResourceBytes(String path) throws Exception {
        InputStream input = QmlSceneCompatibilityTest.class.getResourceAsStream(path);
        assertNotNull(input);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } finally {
            input.close();
        }
    }

    private static final class RasterBackend implements SurfaceBackend {
        private int width;
        private int height;
        private Surface surface;

        @Override
        public void init(int width, int height) {
            this.width = width;
            this.height = height;
            surface = Surface.makeRasterN32Premul(width, height);
        }

        @Override
        public Canvas acquireCanvas() {
            Canvas canvas = surface.getCanvas();
            canvas.clear(0x00000000);
            return canvas;
        }

        @Override
        public void present() {
        }

        @Override
        public void resize(int width, int height) {
            dispose();
            init(width, height);
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return height;
        }

        private Image snapshot() {
            return surface.makeImageSnapshot();
        }

        @Override
        public void dispose() {
            if (surface != null) {
                surface.close();
                surface = null;
            }
        }
    }

    private static final class PreviewEntry implements QmlClickGuiModel.Entry {
        private final String name;
        private final String description;
        private boolean state;
        private final boolean settings;

        private PreviewEntry(String name, String description, boolean state, boolean settings) {
            this.name = name;
            this.description = description;
            this.state = state;
            this.settings = settings;
        }

        @Override public String name() { return name; }
        @Override public String chineseName() { return name; }
        @Override public String description() { return description; }
        @Override public ModuleType category() { return ModuleType.Combat; }
        @Override public boolean state() { return state; }
        @Override public void setState(boolean state) { this.state = state; }
        @Override public int key() { return 0; }
        @Override public boolean hasSettings() { return settings; }
        @Override public List<QmlClickGuiModel.Setting> settings() { return Collections.emptyList(); }
    }
}
