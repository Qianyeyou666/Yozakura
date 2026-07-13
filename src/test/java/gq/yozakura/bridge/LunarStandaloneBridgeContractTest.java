package gq.yozakura.bridge;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

/**
 * Contracts for the Forge event surface emulated by the Lunar standalone bridge.
 */
public class LunarStandaloneBridgeContractTest {
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
    public void nativeInjectorsRejectDuplicateInjectionInsteadOfStagingAnotherLoader() throws IOException {
        String script = source("tools/InjectMod.ps1");
        String ui = source("native/yozakura_injector_ui.cpp");
        String cli = source("native/injector.cpp");
        String loader = source("native/yozakura_loader.cpp");

        assertTrue("The PowerShell injector must clearly reject an already injected target",
                script.contains("Yozakura is already injected into target PID"));
        assertTrue("The PowerShell injector must not stage another DLL copy",
                !script.contains("using staged copy") && !script.contains("Copy-Item -LiteralPath $dllPath -Destination $staged"));
        assertTrue("The graphical injector must clearly reject an already injected target",
                ui.contains("Yozakura is already injected into this process"));
        assertTrue("The graphical injector must not stage another DLL copy",
                !ui.contains("stagedDllCopy"));
        assertTrue("The command-line injector must reject an already loaded Yozakura module",
                cli.contains("Yozakura is already injected into pid"));
        assertTrue("The loader itself must guard against concurrent or third-party reinjection",
                loader.contains("CreateMutexW") && loader.contains("ERROR_ALREADY_EXISTS"));
    }

    @Test
    public void nativeInjectorsKeepRemoteArgumentsAliveUntilLoadLibraryFinishes() throws IOException {
        String script = source("tools/InjectMod.ps1");
        String ui = source("native/yozakura_injector_ui.cpp");
        String cli = source("native/injector.cpp");

        assertTrue("The CLI must distinguish a completed remote thread from timeout/failure",
                cli.contains("DWORD waitResult = WaitForSingleObject")
                        && cli.contains("waitResult != WAIT_OBJECT_0"));
        assertTrue("The UI must distinguish a completed remote thread from timeout/failure",
                ui.contains("DWORD waitResult = WaitForSingleObject")
                        && ui.contains("waitResult != WAIT_OBJECT_0"));
        assertTrue("PowerShell must retain the remote path when LoadLibrary is still running",
                script.contains("$releaseRemotePath = $false")
                        && script.contains("$waitResult -ne $WAIT_OBJECT_0"));
    }

    @Test
    public void nativeDuplicateDetectionCoversLegacyLoadersAndEnumerationFailures() throws IOException {
        String script = source("tools/InjectMod.ps1");
        String ui = source("native/yozakura_injector_ui.cpp");
        String cli = source("native/injector.cpp");

        assertTrue("Every injector must recognize older YozakuraReobf loader names",
                script.contains("YozakuraReobf")
                        && ui.contains("YozakuraReobf")
                        && cli.contains("YozakuraReobf"));
        assertTrue("The CLI must reject a partial module enumeration",
                cli.contains("DWORD enumerationError = GetLastError()"));
        assertTrue("The UI must reject a partial module enumeration",
                ui.contains("DWORD enumerationError = GetLastError()"));
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
