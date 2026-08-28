package gq.yozakura.bridge;

import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Contracts for the Forge event surface emulated by the Lunar standalone bridge.
 */
public class LunarStandaloneBridgeContractTest {
    @Test
    public void nekoRenamedApplicationClassesStillPassThroughTheLunarRemapper() throws Exception {
        Method predicate = VanillaRemapClassLoader.class
                .getDeclaredMethod("shouldLoadChildFirst", String.class);
        predicate.setAccessible(true);

        assertTrue("Neko's n.* classes must be defined by the remap loader before Lunar linkage",
                ((Boolean) predicate.invoke(null, "n.l")).booleanValue());
        assertFalse("The JNIC-bound authentication gate must stay in the parent isolated loader",
                ((Boolean) predicate.invoke(null, "gq.yozakura.k.B")).booleanValue());
    }

    @Test
    public void lunarMarkersTakePrecedenceOverForgeCompatibilityClasses() throws IOException {
        String bootstrap = source("src/main/java/gq/yozakura/YozakuraBootstrap.java");

        int lunarBranch = bootstrap.indexOf("if (isLunarClient())");
        int forgeBranch = bootstrap.indexOf("if (ForgeEnvironment.isForgeAvailable())");
        assertTrue("A Lunar client that bundles Forge compatibility classes must still select the standalone bridge",
                lunarBranch >= 0 && forgeBranch >= 0 && lunarBranch < forgeBranch);
        assertTrue("The Lunar-priority branch must retain the remapped standalone entry point",
                bootstrap.substring(lunarBranch, forgeBranch)
                        .contains("startMappedClass(\"gq.yozakura.core.StandaloneClient\", true, \"lunar\")"));
    }

    @Test
    public void renderLivingShimMatchesTheDescriptorsUsedByRenderModules() throws IOException {
        String shim = source("src/main/java/gq/yozakura/bridge/forge/RenderLivingEvent.java");
        String remapper = source("src/main/java/gq/yozakura/bridge/VanillaRemapClassLoader.java");

        assertTrue("Chams resolves entity with Forge's EntityLivingBase descriptor",
                shim.contains("import net.minecraft.entity.EntityLivingBase;"));
        assertTrue("The emulated field must keep Forge's EntityLivingBase descriptor",
                shim.contains("public final EntityLivingBase entity;"));
        assertTrue("ESP's name-tag listener needs the nested Specials event family",
                shim.contains("public static class Specials"));
        assertTrue("The remapper must redirect the nested Lunar event reference",
                remapper.contains("RenderLivingEvent$Specials$Pre"));
    }

    @Test
    public void unmodifiableLunarSkinMapStillAllowsPlayerRendererFallback() throws IOException {
        String bridge = source("src/main/java/gq/yozakura/bridge/StandaloneLivingRendererBridge.java");

        assertTrue("Lunar exposes an unmodifiable skin map; the bridge must replace it with wrapped renderers",
                bridge.contains("replacePlayerSkinMap(manager, skinMap"));
        assertTrue("The replacement map must not inherit Lunar's unmodifiable wrapper",
                bridge.contains("new HashMap"));
    }

    @Test
    public void nativeBuildAlwaysRefreshesTheEmbeddedRuntimeJar() throws IOException {
        String nativeBuild = source("build-native.bat");
        String resource = source("native/yozakura_loader.rc");

        assertTrue("The native build must refresh the remapped runtime jar before embedding it",
                nativeBuild.contains("call \"%~dp0gradlew.bat\" syncRuntimeJar"));
        assertTrue("The resource compiler must run only after the runtime jar refresh succeeds",
                nativeBuild.indexOf("syncRuntimeJar") < nativeBuild.indexOf("rc /nologo"));
        assertTrue("The loader resource must embed the refreshed runtime artifact",
                resource.contains("RCDATA \"build/libs/Yozakura.jar\""));
    }

    @Test
    public void nativeInjectorsShareOneCoreInsteadOfDuplicatingRemoteLoading() throws IOException {
        String build = source("build-injector-ui.bat");
        String core = source("native/injector_core.cpp");
        String ui = source("native/yozakura_injector_ui.cpp");
        String cli = source("native/injector.cpp");

        assertTrue("The graphical injector must use the shared native core",
                ui.contains("#include \"injector_core.h\""));
        assertTrue("The command-line injector must use the shared native core",
                cli.contains("#include \"injector_core.h\""));
        assertFalse("The graphical shell must not own CreateRemoteThread directly",
                ui.contains("CreateRemoteThread("));
        assertFalse("The command-line shell must not own CreateRemoteThread directly",
                cli.contains("CreateRemoteThread("));
        assertTrue("The shared core must own the remote-thread lifecycle",
                core.contains("CreateRemoteThread(")
                        && core.contains("DWORD waitResult = WaitForSingleObject")
                        && core.contains("releaseRemotePath = false"));
        assertTrue("The injector build must compile and link the shared core for UI and CLI",
                build.contains("native\\injector_core.cpp")
                        && build.contains("YozakuraInjectorCli.exe"));
    }

    @Test
    public void graphicalInjectorSeparatesUiStateFromRenderingAndWindowMessages() throws IOException {
        String build = source("build-injector-ui.bat");
        String uiApp = source("native/yozakura_injector_ui_entry.cpp");
        String uiRender = source("native/yozakura_injector_ui.cpp");
        String stateHeader = source("native/yozakura_injector_ui_state.h");
        String stateSource = source("native/yozakura_injector_ui_state.cpp");

        assertTrue("The UI application shell must depend on the UI application boundary",
                uiApp.contains("#include \"yozakura_injector_ui_app.h\""));
        assertTrue("The UI renderer must consume the explicit state model",
                uiRender.contains("#include \"yozakura_injector_ui_state.h\""));
        assertFalse("The UI renderer must not declare its state model inline",
                uiRender.contains("struct AppState") || uiRender.contains("enum UiState"));
        assertTrue("The window entry point must be separated from the renderer",
                uiApp.contains("wWinMain(")
                        && uiApp.contains("runInjectorApplication(instance)")
                        && !uiRender.contains("wWinMain("));
        assertTrue("The state module must define the injector lifecycle transitions",
                stateHeader.contains("beginExpansion")
                        && stateHeader.contains("beginInjection")
                        && stateHeader.contains("completeInjection")
                        && stateHeader.contains("resetToReady"));
        assertTrue("State transitions must be implemented outside the renderer",
                stateSource.contains("void beginExpansion(")
                        && stateSource.contains("void beginInjection(")
                        && stateSource.contains("void completeInjection(")
                        && stateSource.contains("void resetToReady("));
        assertTrue("The injector build must compile the UI state and entry modules",
                build.contains("native\\yozakura_injector_ui_state.cpp")
                        && build.contains("native\\yozakura_injector_ui_entry.cpp"));
    }

    @Test
    public void graphicalInjectorUsesAnAutomaticTerminalWorkflow() throws IOException {
        String build = source("build-injector-ui.bat");
        String core = source("native/injector_core.cpp");
        String app = source("native/yozakura_injector_ui.cpp");
        String design = source("native/yozakura_injector_ui_design.cpp");
        String designHeader = source("native/yozakura_injector_ui_design.h");
        String views = source("native/yozakura_injector_ui_views.cpp");

        assertTrue("Terminal geometry and motion must live in a renderer-independent design module",
                design.contains("const TerminalMetrics kMetrics")
                        && design.contains("TerminalLayout calculateTerminalLayout")
                        && design.contains("float sampleMotion(")
                        && design.contains("void redirectMotion("));
        assertTrue("Every lifecycle state must map to a terminal session phase",
                designHeader.contains("enum class TerminalPhase")
                        && designHeader.contains("Waiting")
                        && designHeader.contains("Injecting")
                        && designHeader.contains("Success")
                        && designHeader.contains("Failure")
                        && design.contains("case UiState::Ready:")
                        && design.contains("case UiState::Injecting:"));
        assertTrue("The terminal must render one continuous ASCII and log session",
                views.contains("void drawAsciiBrand(")
                        && views.contains("void drawSession(")
                        && views.contains("void drawScrollRail(")
                        && views.contains("AUTO DETECT / X64"));
        assertTrue("The application must detect a target automatically and inject the locked PID",
                core.contains("DetectedTarget findBestMinecraftTarget()")
                        && core.contains("chooseBestMinecraftTarget(")
                        && app.contains("startScan(window)")
                        && app.contains("startDetectedInjection(window, result->target)")
                        && app.contains("work->pid = detected.process.pid"));
        assertFalse("Automatic mode must not require a profile selection or Inject button",
                views.contains("Select client")
                        || views.contains("Inject Yozakura")
                        || app.contains("selectedProfile"));
        assertTrue("Closing must be blocked while the worker owns the injection lifecycle",
                app.contains("case WM_CLOSE:")
                        && app.contains("g_app.uiState == UiState::Injecting"));
        assertTrue("Closing during process discovery must wait until the scan result is reclaimed",
                app.contains("bool g_closeRequested = false;")
                        && app.contains("if (g_scanRunning)")
                        && app.contains("g_closeRequested = true;")
                        && app.contains("if (g_closeRequested) {")
                        && app.contains("DestroyWindow(window);"));
        assertTrue("The build must distribute the licensed terminal font",
                build.contains("JetBrainsMono.ttf")
                        && build.contains("LICENSE-JetBrainsMono.txt"));
    }

    @Test
    public void nativeInjectorsRejectDuplicateInjectionInsteadOfStagingAnotherLoader() throws IOException {
        String script = source("tools/InjectMod.ps1");
        String core = source("native/injector_core.cpp");
        String loader = source("native/yozakura_loader.cpp");

        assertTrue("The PowerShell injector must clearly reject an already injected target",
                script.contains("Yozakura is already injected into target PID"));
        assertTrue("The PowerShell injector must not stage another DLL copy",
                !script.contains("using staged copy") && !script.contains("Copy-Item -LiteralPath $dllPath -Destination $staged"));
        assertTrue("The shared core must clearly reject an already injected target",
                core.contains("Yozakura is already injected into this process"));
        assertFalse("The shared native core must not stage another DLL copy",
                core.contains("stagedDllCopy"));
        assertTrue("The loader itself must guard against concurrent or third-party reinjection",
                loader.contains("CreateMutexW") && loader.contains("ERROR_ALREADY_EXISTS"));
    }

    @Test
    public void nativeLoaderRetainsItsProcessGuardAfterEnteringTheClientConstructor() throws IOException {
        String loader = source("native/yozakura_loader.cpp");

        int constructorAttempt = loader.indexOf("constructorAttempted = true;");
        int newClient = loader.indexOf("env->NewObject(clientClass, ctor)", constructorAttempt);
        assertTrue("A constructor that can partially register bridge listeners must be recorded before NewObject",
                constructorAttempt >= 0 && newClient > constructorAttempt);
        assertTrue("A failed constructor attempt must retain the process-wide guard instead of allowing reinjection",
                loader.contains("bool constructorAttempted = false;")
                        && loader.contains("if (clientLoaded || constructorAttempted)")
                        && loader.contains("injectionGuard.retainForProcessLifetime();"));
    }

    @Test
    public void nativeLoaderDoesNotFormatUnboundedJavaStackTracesIntoAFixedBuffer() throws IOException {
        String loader = source("native/yozakura_loader.cpp");

        assertFalse("A long Java stack trace must not trigger MSVC's invalid-parameter fail-fast",
                loader.contains("%02u:%02u:%02u] %s\\r\\n"));
        assertTrue("The loader must write exception text directly instead of copying it into the timestamp buffer",
                loader.contains("WriteFile(file, message, messageLength"));
    }

    @Test
    public void nativeInjectorsKeepRemoteArgumentsAliveUntilLoadLibraryFinishes() throws IOException {
        String script = source("tools/InjectMod.ps1");
        String core = source("native/injector_core.cpp");

        assertTrue("The shared native core must distinguish a completed remote thread from timeout/failure",
                core.contains("DWORD waitResult = WaitForSingleObject")
                        && core.contains("waitResult != WAIT_OBJECT_0"));
        assertTrue("PowerShell must retain the remote path when LoadLibrary is still running",
                script.contains("$releaseRemotePath = $false")
                        && script.contains("$waitResult -ne $WAIT_OBJECT_0"));
    }

    @Test
    public void nativeDuplicateDetectionCoversLegacyLoadersAndEnumerationFailures() throws IOException {
        String script = source("tools/InjectMod.ps1");
        String core = source("native/injector_core.cpp");

        assertTrue("Every injector must recognize older YozakuraReobf loader names",
                script.contains("YozakuraReobf") && core.contains("YozakuraReobf"));
        assertTrue("The shared native core must reject a partial module enumeration",
                core.contains("DWORD enumerationError = GetLastError()"));
    }

    @Test
    public void reinjectionWaitsForTheOldStandaloneBridgeAndReplacesItsPacketHandler() throws IOException {
        String client = source("src/main/java/gq/yozakura/core/StandaloneClient.java");
        String bridge = source("src/main/java/gq/yozakura/bridge/StandaloneEventBridge.java");

        assertTrue("A previous pump must be interrupted and joined so its bridge can remove state cleanly",
                client.contains("thread.interrupt()") && client.contains("thread.join("));
        assertTrue("Thread.stop can strand the old Netty handler and split RotationState by classloader",
                !client.contains("thread.stop()"));
        assertTrue("A new standalone bridge must take ownership from a same-named old-loader handler",
                bridge.contains("pipeline().remove(HANDLER_NAME)"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
