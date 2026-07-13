package gq.yozakura.bridge;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ForgeBridgeLifecycleContractTest {
    @Test
    public void forgeShutdownUnregistersTheBridgeAndClearsAllBridgeState() throws IOException {
        String bridge = source("src/main/java/gq/yozakura/bridge/YozakuraEventBridge.java");
        String shutdown = method(bridge, "    private void shutdownInternal() {", "    private void clearBridgeState()");
        String client = source("src/main/java/gq/yozakura/core/Client.java");
        String uninject = method(client, "    public static void unInject() {", "    private void loadConfigOnStartup()");

        assertTrue("Forge unload must explicitly stop the independent event bridge",
                uninject.contains("YozakuraEventBridge.shutdown();"));
        assertTrue("Bridge shutdown must detach from both Forge event buses",
                shutdown.contains("MinecraftForge.EVENT_BUS.unregister(INSTANCE);")
                        && shutdown.contains("FMLCommonHandler.instance().bus().unregister(INSTANCE);"));
        assertTrue("Bridge shutdown must remove the Netty handler before returning",
                shutdown.contains("removePacketHandler();"));
        assertTrue("Bridge shutdown must clear packet, rotation, and movement bridge state as one operation",
                shutdown.contains("clearBridgeState();"));

        String clearState = method(bridge, "    private void clearBridgeState() {", "    private void dispatchPreUpdate()");
        assertTrue("No-event packet markers cannot survive unload",
                clearState.contains("PacketBridgeSupport.clearNoEventPackets();"));
        assertTrue("Every rotation publication and visible/packet rotation state must be reset",
                clearState.contains("PacketRotationState.clear();")
                        && clearState.contains("RotationExitState.clear();")
                        && clearState.contains("RotationState.clear();")
                        && clearState.contains("VisualRotationState.clear();")
                        && clearState.contains("rotationPublication.clear();"));
        assertTrue("Movement input must be detached rather than left wrapped after unload",
                shutdown.contains("MovementInputBridge.uninstall();"));
    }

    @Test
    public void playerRenderRotationUsesBalancedSnapshotsAndCleansUpCancelledPairs() throws IOException {
        String bridge = source("src/main/java/gq/yozakura/bridge/YozakuraEventBridge.java");
        String pre = method(bridge,
                "    public void onRenderPlayerPre(RenderPlayerEvent.Pre event) {",
                "    @SubscribeEvent");
        String post = method(bridge,
                "    public void onRenderPlayerPost(RenderPlayerEvent.Post event) {",
                "    private void dispatchPreUpdate()");

        assertFalse("A single boolean cannot represent nested local-player renders",
                bridge.contains("private boolean renderingPlayer;"));
        assertTrue("Each PRE must capture an independent local-player snapshot",
                bridge.contains("ArrayDeque<PlayerRenderRotationSnapshot>")
                        && pre.contains("playerRenderRotationSnapshots.push("));
        assertTrue("Already-cancelled PRE events must not change player rotation",
                pre.contains("event.isCanceled()"));
        assertTrue("POST must restore the matching top-of-stack snapshot",
                post.contains("restoreLatestPlayerRenderRotation();"));
        assertTrue("An unpaired PRE must be restored at a frame boundary and on shutdown",
                bridge.contains("restoreDanglingPlayerRenderRotations();")
                        && bridge.contains("public static void shutdown()"));
    }

    @Test
    public void standaloneInitializationRollsBackPublishedRunningStateOnAnyFailure() throws IOException {
        String client = source("src/main/java/gq/yozakura/core/StandaloneClient.java");
        String constructor = method(client, "    public StandaloneClient() {", "    public static boolean isState()");

        assertTrue("Initialization must be guarded by a committed transaction",
                constructor.contains("boolean initialized = false;")
                        && constructor.contains("finally")
                        && constructor.contains("rollbackFailedInitialization();"));
        assertTrue("Rollback must revoke all externally observable running state",
                client.contains("private void rollbackFailedInitialization()")
                        && client.contains("state = false;")
                        && client.contains("activeClient = null;")
                        && client.contains("System.clearProperty(ACTIVE_INSTANCE_PROPERTY);"));
        assertTrue("The pump is started only after active state has been published",
                constructor.indexOf("state = true;") < constructor.indexOf("startMainThreadPump();"));
    }

    private static String method(String source, String beginMarker, String endMarker) {
        int begin = source.indexOf(beginMarker);
        int end = source.indexOf(endMarker, begin + beginMarker.length());
        assertTrue("Expected method marker: " + beginMarker, begin >= 0);
        assertTrue("Expected end marker: " + endMarker, end > begin);
        return source.substring(begin, end);
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }
}
