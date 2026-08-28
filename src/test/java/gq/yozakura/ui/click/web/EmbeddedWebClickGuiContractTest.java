package gq.yozakura.ui.click.web;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class EmbeddedWebClickGuiContractTest {
    @Test
    public void webViewBackendRemainsAvailableWithoutOwningTheClickGuiEntryPoint() throws Exception {
        String source = read("src/main/java/gq/yozakura/module/render/ClickGUI.java");
        assertTrue(source.contains("new TimewarpClickGui()"));
        assertTrue(!source.contains("new YozakuraPanelClickGui()"));
        assertTrue(!source.contains("QmlClickGuiScreen.open(mc)"));
        assertTrue(!source.contains("WebView2ClickGuiScreen.open(mc)"));
        assertTrue(Files.exists(Paths.get(
                "src/main/java/gq/yozakura/ui/click/web/WebView2ClickGuiScreen.java")));
    }

    @Test
    public void screenOwnsNativeVisibilityAndServerLifecycle() throws Exception {
        String source = read("src/main/java/gq/yozakura/ui/click/web/WebView2ClickGuiScreen.java");
        assertTrue(source.contains("WebClickGuiService.embeddedUrl()"));
        assertTrue(source.contains("WebView2Bridge.show("));
        assertTrue(source.contains("WebView2Bridge.hide()"));
        assertTrue(!source.contains("WebClickGuiService.stop()"));
        String nativeSource = read("native/yozakura_webview2.cpp");
        assertTrue(nativeSource.contains("PostWebMessageAsString(L\"open\")"));
    }

    @Test
    public void bridgeContractIsSmallAndVersionless() throws Exception {
        String source = read("src/main/java/gq/yozakura/ui/click/web/WebView2Bridge.java");
        assertTrue(source.contains("native boolean show0(String url)"));
        assertTrue(source.contains("native boolean prewarm0(String url)"));
        assertTrue(source.contains("native void hide0()"));
        assertTrue(source.contains("native boolean consumeCloseRequest0()"));
        assertTrue(source.contains("native void syncBounds0()"));
        assertTrue(!source.contains("enterBorderlessFullscreen0"));
        assertTrue(!source.contains("leaveBorderlessFullscreen0"));
        assertTrue(source.contains("WebView2NativeLibrary.ensureLoaded()"));
        String module = read("src/main/java/gq/yozakura/module/render/ClickGUI.java");
        assertTrue(!module.contains("WebView2Bridge.prewarm("));
    }

    @Test
    public void forgeLaunchLoadsBundledNativeBridgeWithoutExternalBrowser() throws Exception {
        String loader = read("src/main/java/gq/yozakura/ui/click/web/WebView2NativeLibrary.java");
        String service = read("src/main/java/gq/yozakura/ui/click/web/WebClickGuiService.java");
        assertTrue(loader.contains("/assets/yozakura/native/"));
        assertTrue(loader.contains("System.load(loader.getAbsolutePath())"));
        assertTrue(loader.contains("System.load(bridge.getAbsolutePath())"));
        assertTrue(!service.contains("Desktop.getDesktop().browse"));
        assertTrue(!service.contains("cmd\", \"/c\", \"start"));
    }

    @Test
    public void webPageDoesNotSilentlyFallBackToMockState() throws Exception {
        String page = read("src/main/resources/assets/yozakura/webclickgui/index.html");
        assertTrue(page.contains("hydrateFromClient().catch(error=>"));
        assertTrue(page.contains("showFatalError(error)"));
        assertTrue(page.contains("ClickGUI connection failed"));
        assertTrue(!page.contains("hydrateFromClient().catch(()=>"));
        assertTrue(page.contains("background:transparent!important"));
        assertTrue(!page.contains("body{background:var(--bg)!important;}"));
        assertTrue(!page.contains("Press <kbd>Right Shift</kbd> to open ClickGUI"));
        assertTrue(!page.contains("backdrop.addEventListener('click', closeGUI)"));
        assertTrue(page.contains("beginWindowInteraction(event, 'move')"));
        assertTrue(page.contains("['n','s','e','w','ne','nw','se','sw']"));
        assertTrue(page.contains("closeKeyName = String(data.clickGuiKeyName"));
        assertTrue(!page.contains("e.location === KeyboardEvent.DOM_KEY_LOCATION_RIGHT"));
        assertTrue(page.contains("window.chrome.webview.postMessage('ready')"));
        assertTrue(page.contains("gui.classList.add('no-intro')"));
        assertTrue(page.contains("const COPY ="));
        assertTrue(page.contains("value:'Language'"));
        assertTrue(page.contains("data.language === 'zh'"));
        assertTrue(page.contains("data:image/svg+xml"));
        assertTrue(page.contains("class=\"client-cursor\""));
        assertTrue(page.contains("html,body,body *,body *::before,body *::after{cursor:none!important;"));
        assertTrue(page.contains("function buildSettings(module)"));
        assertTrue(page.contains("channels:{r:red.name,g:green.name,b:blue.name}"));
        assertTrue(page.contains("await refreshModuleFromClient(m.name)"));
        int refreshStart = page.indexOf("async function refreshModuleFromClient(moduleName)");
        int refreshEnd = page.indexOf("function commitColorSetting", refreshStart);
        String refreshBody = page.substring(refreshStart, refreshEnd);
        assertTrue(refreshBody.contains("inner.innerHTML=buildExpandContent(clientModule)"));
        assertTrue(!refreshBody.contains("renderModules("));
        String service = read("src/main/java/gq/yozakura/ui/click/web/WebClickGuiService.java");
        assertTrue(service.contains("queueOnMainThreadAndWait"));
        assertTrue(service.contains("task.get(2L, TimeUnit.SECONDS)"));
    }

    @Test
    public void runtimeStateIsTheOnlySourceOfModuleSettings() throws Exception {
        String page = read("src/main/resources/assets/yozakura/webclickgui/index.html");
        assertTrue(page.contains("const MODULES = {};"));
        assertTrue(!page.contains("{name:'ChestStealer',desc:"));
        assertTrue(!page.contains("{name:'InventoryManager',desc:"));
        assertTrue(!page.contains("options:['Instant','Smart','Smooth']"));
        assertTrue(!page.contains("options:['Basic','Advanced']"));
        assertTrue(page.contains("const settings = buildSettings(module);"));
        assertTrue(page.contains("settingValues[module.name] = {};"));

        String chestStealer = read("src/main/java/gq/yozakura/module/player/ChestStealer.java");
        String inventoryManager = read("src/main/java/gq/yozakura/module/player/InventoryManager.java");
        assertTrue(!chestStealer.contains("new Mode<"));
        assertTrue(inventoryManager.contains("new Mode<InventoryMode>(\"Mode\", \"Mode\""));
    }

    @Test
    public void nativeControllerUsesTransparentCompositionBackground() throws Exception {
        String source = read("native/yozakura_webview2.cpp");
        assertTrue(source.contains("ICoreWebView2Controller2"));
        assertTrue(source.contains("put_DefaultBackgroundColor(transparent)"));
        assertTrue(source.contains("COREWEBVIEW2_COLOR transparent = { 0, 0, 0, 0 }"));
        assertTrue(source.contains("controller->put_IsVisible(FALSE)"));
        assertTrue(source.contains("_wcsicmp(message, L\"ready\")"));
        assertTrue(source.contains("controller->NotifyParentWindowPositionChanged()"));
        assertTrue(!source.contains("WS_POPUP | WS_VISIBLE"));
        assertTrue(!source.contains("info.rcMonitor.right - info.rcMonitor.left"));
    }

    @Test
    public void vanillaFullscreenIsNotInterceptedByTheClickGui() throws Exception {
        String source = read("src/main/java/gq/yozakura/ui/click/web/WebView2ClickGuiScreen.java");
        assertTrue(!source.contains("minecraft.isFullScreen()"));
        assertTrue(!source.contains("Display.setFullscreen("));
        assertTrue(!source.contains("Display.setDisplayMode("));
        assertTrue(!source.contains("enterBorderlessFullscreen"));
        assertTrue(!source.contains("leaveBorderlessFullscreen"));
        String page = read("src/main/resources/assets/yozakura/webclickgui/index.html");
        assertTrue(page.contains("expand.style.maxHeight = expand.scrollHeight + 'px'"));
        assertTrue(!page.contains("'<span class=\"cat-badge\">'"));
        assertTrue(!page.contains("<span class=\"title-count\""));
        String nativeSource = read("native/yozakura_webview2.cpp");
        assertTrue(nativeSource.contains("overlayBounds"));
        assertTrue(nativeSource.contains("controller->put_Bounds(overlayBounds)"));
        String forgeClient = read("src/main/java/gq/yozakura/core/Client.java");
        String standaloneClient = read("src/main/java/gq/yozakura/core/StandaloneClient.java");
        assertTrue(!forgeClient.contains("BorderlessFullscreenPolicy"));
        assertTrue(!standaloneClient.contains("BorderlessFullscreenPolicy"));
        assertTrue(!nativeSource.contains("BorderlessFullscreenPolicy"));
        assertTrue(!nativeSource.contains("togglePolicyBorderlessFullscreen"));
        assertTrue(!nativeSource.contains("maintainBorderlessFullscreenPolicy"));
        assertTrue(!Files.exists(Paths.get(
                "src/main/java/gq/yozakura/ui/click/web/BorderlessFullscreenPolicy.java")));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }
}
