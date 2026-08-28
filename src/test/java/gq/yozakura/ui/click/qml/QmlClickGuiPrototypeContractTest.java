package gq.yozakura.ui.click.qml;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class QmlClickGuiPrototypeContractTest {
    @Test
    public void buildPinsTheVerifiedJavaEightQmlEngineAndWindowsRenderer() throws Exception {
        String build = read("build.gradle");
        assertTrue(build.contains("io.github.timer-err:qml4j-core:0.2.23"));
        assertTrue(build.contains("io.github.humbleui:skija-windows-x64:0.143.16"));
    }

    @Test
    public void prototypeUsesCpuRasterBeforeUploadingOnTheMinecraftGlThread() throws Exception {
        String backend = read("src/main/java/gq/yozakura/ui/click/qml/MinecraftSkiaSurfaceBackend.java");
        assertTrue(backend.contains("implements SurfaceBackend"));
        assertTrue(backend.contains("new Framebuffer("));
        assertTrue(backend.contains("Surface.makeRasterDirect"));
        assertTrue(backend.contains("glTexSubImage2D"));
        assertTrue(backend.contains("canvas.clear(0x00000000)"));
        assertTrue(!backend.contains("DirectContext.makeGL"));
        assertTrue(!backend.contains("BackendRenderTarget.makeGL"));
    }

    @Test
    public void screenLoadsQmlAndForwardsMinecraftInput() throws Exception {
        String screen = read("src/main/java/gq/yozakura/ui/click/qml/QmlClickGuiScreen.java");
        assertTrue(screen.contains("QmlClickGuiRuntime.open"));
        assertTrue(screen.contains("runtime.renderIfNeeded"));
        assertTrue(screen.contains("dispatchPointerDown"));
        assertTrue(screen.contains("dispatchPointerMove"));
        assertTrue(screen.contains("dispatchPointerUp"));
        assertTrue(screen.contains("dispatchWheel"));
        assertTrue(screen.contains("dispatchKey"));
        assertTrue(screen.contains("doesGuiPauseGame"));
        assertTrue(screen.contains("new ScaledResolution(mc)"));
        assertTrue(screen.contains("QmlWindowGeometry"));
        assertTrue(screen.contains("geometry.beginMove"));
        assertTrue(screen.contains("geometry.beginResize"));
        assertTrue(screen.contains("Mouse.setNativeCursor(hiddenCursor)"));
        assertTrue(screen.contains("drawClientCursor(mouseX, mouseY)"));
    }

    @Test
    public void qmlPrototypeKeepsTheCurrentVisualHierarchy() throws Exception {
        String qml = read("src/main/resources/assets/yozakura/qmlclickgui/Main.qml");
        assertTrue(qml.contains("Yozakura"));
        assertTrue(qml.contains("Categories"));
        assertTrue(qml.contains("clickModel.categories"));
        assertTrue(qml.contains("clickModel.modules"));
        assertTrue(qml.contains("CategoryCard"));
        assertTrue(qml.contains("ModuleCard"));
        assertTrue(qml.contains("radius: 20"));
        assertTrue(qml.contains("color: \"transparent\""));
        assertTrue(qml.contains("Behavior on scale"));
        assertTrue(qml.contains("Component.onCompleted"));
    }

    @Test
    public void moduleCardsAcceptRightClickForSettings() throws Exception {
        String qml = read("src/main/resources/assets/yozakura/qmlclickgui/ModuleCard.qml");
        assertTrue(qml.contains("acceptedButtons: 3"));
        assertTrue(qml.contains("mouse.button === 2"));
        assertTrue(qml.contains("clickModel.toggleSettings(card.moduleName)"));
    }

    @Test
    public void runtimeUsesDirtyFramesAndAConsistentUiTypeface() throws Exception {
        String runtime = read("src/main/java/gq/yozakura/ui/click/qml/QmlClickGuiRuntime.java");
        assertTrue(runtime.contains("QmlFrameScheduler"));
        assertTrue(runtime.contains("renderIfNeeded"));
        assertTrue(runtime.contains("readBytes(\"/assets/minecraft/font/Inter.ttf\"),\n"
                + "                            readBytes(\"/assets/minecraft/font/Inter.ttf\")"));
    }

    @Test
    public void clickGuiDoesNotEnterTheRetiredQmlHost() throws Exception {
        String module = read("src/main/java/gq/yozakura/module/render/ClickGUI.java");
        assertTrue(module.contains("enum GuiStyle"));
        assertTrue(module.contains("Mode<GuiStyle> guiStyle"));
        assertTrue(module.contains("PANEL"));
        assertTrue(module.contains("new TimewarpClickGui()"));
        assertTrue(!module.contains("new YozakuraPanelClickGui()"));
        assertTrue(!module.contains("QmlClickGuiScreen.open(mc)"));
        assertTrue(!module.contains("WebView2ClickGuiScreen.open(mc)"));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }
}
