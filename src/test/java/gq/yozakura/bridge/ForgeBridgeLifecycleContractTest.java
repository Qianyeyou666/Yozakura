package gq.yozakura.bridge;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ForgeBridgeLifecycleContractTest {
    @Test
    public void bridgeRegistrationPublishesRunningStateOnlyAfterBothEventBusesSucceed() throws IOException {
        String bridge = source("src/main/java/gq/yozakura/bridge/YozakuraEventBridge.java");
        String init = method(bridge, "    public static void init() {", "    public static void markNoEvent(");

        int forgeRegistration = init.indexOf("MinecraftForge.EVENT_BUS.register(INSTANCE);");
        int fmlRegistration = init.indexOf("FMLCommonHandler.instance().bus().register(INSTANCE);");
        int publishedState = init.lastIndexOf("registered = true;");
        assertTrue("Both Forge buses must register before the bridge is published as active",
                forgeRegistration >= 0 && fmlRegistration > forgeRegistration && publishedState > fmlRegistration);
        assertTrue("A failure registering the second bus must undo the first registration",
                init.contains("rollbackFailedRegistration(")
                        && bridge.contains("MinecraftForge.EVENT_BUS.unregister(INSTANCE);")
                        && bridge.contains("FMLCommonHandler.instance().bus().unregister(INSTANCE);"));
    }

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
    public void serverDisconnectPreservesModulesUntilTheClientIsUnloaded() throws IOException {
        String client = source("src/main/java/gq/yozakura/core/Client.java");
        String disconnect = method(client,
                "    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {",
                "    public static void unInject() {");
        String uninject = method(client,
                "    public static void unInject() {",
                "    private static void unregisterClient(Client client) {");

        assertTrue("Disconnect must persist the enabled module configuration",
                disconnect.contains("fileManager.saveIfDirtyQuietly();"));
        assertFalse("Switching servers must not disable enabled modules",
                disconnect.contains("ModuleManager.disableAll(false);"));
        assertTrue("Explicit client unload must still disable modules and unregister their listeners",
                uninject.contains("ModuleManager.disableAll(false);"));
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

    @Test
    public void forgeBridgeDoesNotReinstallBesideAnActiveStandaloneBridge() throws IOException {
        String bridge = source("src/main/java/gq/yozakura/bridge/YozakuraEventBridge.java");
        String inject = method(bridge, "    private void injectPacketHandler() {", "    private void removePacketHandler() {");

        assertTrue("A stale Forge listener must yield instead of reintroducing a second packet bridge in Lunar",
                inject.contains("PacketPipelineAnchors.STANDALONE_BRIDGE_HANDLER_NAME")
                        && inject.contains("next.pipeline().remove(HANDLER_NAME);")
                        && inject.contains("return;"));
    }

    @Test
    public void forgeBridgeReplacesAForeignSameNamedPacketHandlerInsteadOfLeavingSilentRotationDetached()
            throws IOException {
        String bridge = source("src/main/java/gq/yozakura/bridge/YozakuraEventBridge.java");
        String inject = method(bridge, "    private void injectPacketHandler() {", "    private void removePacketHandler() {");

        int knownHandler = inject.indexOf("else if (existing instanceof PacketBridgeHandler)");
        int channelAssigned = inject.indexOf("channel = next;", knownHandler);
        String foreignHandlerRecovery = inject.substring(knownHandler, channelAssigned);
        assertTrue("A handler from another injection classloader must be removed before this bridge claims the channel",
                foreignHandlerRecovery.contains("else {")
                        && foreignHandlerRecovery.contains("next.pipeline().remove(HANDLER_NAME);")
                        && foreignHandlerRecovery.contains("new PacketBridgeHandler()")
                        && foreignHandlerRecovery.contains("next.pipeline().addBefore(\"packet_handler\", HANDLER_NAME, handler)"));
    }

    @Test
    public void forgeTickYieldsBeforeOverwritingAnActiveStandaloneMovementBridge() throws IOException {
        String bridge = source("src/main/java/gq/yozakura/bridge/YozakuraEventBridge.java");
        String tick = method(bridge,
                "    public void onClientTick(TickEvent.ClientTickEvent event) {",
                "    @SubscribeEvent(priority = EventPriority.LOWEST)");

        int standaloneYield = tick.indexOf("if (yieldToStandaloneBridge())");
        int movementInstall = tick.indexOf("MovementInputBridge.install();");
        assertTrue("Forge ticks must detect the standalone handler before mutating shared movement hooks",
                standaloneYield >= 0 && movementInstall > standaloneYield);
        assertTrue("A stale Forge listener must return instead of dispatching duplicate tick/update events",
                tick.substring(standaloneYield, movementInstall).contains("return;"));
        assertTrue("The detection helper must inspect the canonical standalone packet-handler name",
                bridge.contains("private boolean yieldToStandaloneBridge()")
                        && bridge.contains("PacketPipelineAnchors.STANDALONE_BRIDGE_HANDLER_NAME"));
    }

    @Test
    public void standaloneOwnershipClaimYieldsBeforeThePacketPipelineIsInstalled() throws IOException {
        String bridge = source("src/main/java/gq/yozakura/bridge/YozakuraEventBridge.java");
        String client = source("src/main/java/gq/yozakura/core/StandaloneClient.java");
        String ownership = method(bridge, "    private boolean yieldToStandaloneBridge() {",
                "    private void abandonStaleForgeBridge(");
        String pump = method(client, "    private void startMainThreadPump() {", "    private void stopExistingStandalonePumps()");
        int standaloneClaim = ownership.indexOf("StandaloneClient.isBridgeOwnerActive()");
        int channelLookup = ownership.indexOf("mc.getNetHandler()");

        assertTrue("Lunar ownership must win even while its packet handler is between remove and install",
                standaloneClaim >= 0 && channelLookup > standaloneClaim);
        assertTrue("The standalone owner marker must be process-wide so remapped classloaders agree on it",
                client.contains("public static boolean isBridgeOwnerActive()")
                        && client.contains("System.getProperty(ACTIVE_INSTANCE_PROPERTY)"));
        assertTrue("A completed standalone shutdown must release its ownership claim for a later Forge session",
                pump.contains("completeSuccessfulShutdown();")
                        && client.contains("private void completeSuccessfulShutdown()")
                        && client.contains("clearActiveInstanceIfOwner();"));
    }

    @Test
    public void everyForgeCallbackYieldsAndConvergesToTheStandalonePacketOwner() throws IOException {
        String bridge = source("src/main/java/gq/yozakura/bridge/YozakuraEventBridge.java");
        String playerTick = method(bridge,
                "    public void onPlayerTick(TickEvent.PlayerTickEvent event) {",
                "    public static boolean hasRenderedOverlayThisFrame()");
        String render2d = method(bridge,
                "    public void onRender2D(RenderGameOverlayEvent.Text event) {",
                "    @SubscribeEvent\n    public void onRender3D");
        String render3d = method(bridge,
                "    public void onRender3D(RenderWorldLastEvent event) {",
                "    @SubscribeEvent\n    public void onMouse");
        String mouse = method(bridge,
                "    public void onMouse(MouseEvent event) {",
                "    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)");
        String playerPre = method(bridge,
                "    public void onRenderPlayerPre(RenderPlayerEvent.Pre event) {",
                "    @SubscribeEvent\n    public void onRenderPlayerPost");
        String playerPost = method(bridge,
                "    public void onRenderPlayerPost(RenderPlayerEvent.Post event) {",
                "    private void restoreLatestPlayerRenderRotation()");

        assertTrue("A stale Forge player-tick callback must not restore Standalone's movement rotation",
                yieldsBefore(playerTick, "MovementInputBridge.restoreRotation();"));
        assertTrue("A stale Forge overlay callback must not render a duplicate HUD event",
                yieldsBefore(render2d, "EventManager.call(new Render2DEvent"));
        assertTrue("A stale Forge world-render callback must not render a duplicate world event",
                yieldsBefore(render3d, "EventManager.call(new Render3DEvent"));
        assertTrue("A stale Forge mouse callback must not synthesize a second click or wheel bridge",
                yieldsBefore(mouse, "EventManager.call(new LeftClickMouseEvent())"));
        assertTrue("A stale Forge render PRE callback must not mutate the local-player visual rotation",
                yieldsBefore(playerPre, "playerRenderRotationSnapshots.push("));
        assertTrue("A stale Forge render POST callback must not restore Standalone's visual rotation snapshot",
                yieldsBefore(playerPost, "restoreLatestPlayerRenderRotation();"));

        String ownership = method(bridge, "    private boolean yieldToStandaloneBridge() {",
                "    private void removePacketHandler() {");
        assertTrue("An existing Forge packet handler must be removed when Standalone takes ownership",
                ownership.contains("next.pipeline().remove(HANDLER_NAME);"));
        assertTrue("The stale Forge bridge must release its cached Netty handler reference",
                ownership.contains("packetBridgeHandler = null;"));
        assertTrue("Standalone ownership must release the old Forge safe-walk request before discarding its state",
                ownership.contains("MovementInputBridge.setSafeWalkRequested(false);"));
        assertFalse("Forge ownership handoff must not write raw key state directly",
                ownership.contains("KeyBinding.setKeyBindState"));
    }

    @Test
    public void standaloneOwnershipDetectionFailsClosedWhenItsCleanupRacesThePipeline() throws IOException {
        String bridge = source("src/main/java/gq/yozakura/bridge/YozakuraEventBridge.java");
        String ownership = method(bridge, "    private boolean yieldToStandaloneBridge() {",
                "    private void abandonStaleForgeBridge(");
        int standaloneCheck = ownership.indexOf("PacketPipelineAnchors.STANDALONE_BRIDGE_HANDLER_NAME");
        int cleanupCatch = ownership.indexOf("catch (Throwable ignored)", standaloneCheck);
        String afterStandaloneCheck = ownership.substring(standaloneCheck);

        assertTrue("A pipeline failure after Standalone ownership is observed must yield instead of running Forge callbacks",
                cleanupCatch > standaloneCheck && afterStandaloneCheck.contains("return true;"));
        assertFalse("A cleanup race must not be interpreted as proof that Standalone is absent",
                afterStandaloneCheck.contains("catch (Throwable ignored) {\n            return false;"));
    }

    @Test
    public void failedStandalonePipelineInspectionClearsTheSharedSafeWalkRequest() throws IOException {
        String bridge = source("src/main/java/gq/yozakura/bridge/YozakuraEventBridge.java");
        String ownership = method(bridge, "    private boolean yieldToStandaloneBridge() {",
                "    private void abandonStaleForgeBridge(");
        String failureCleanup = "catch (Throwable ignored) {\n"
                + "            MovementInputBridge.setSafeWalkRequested(false);\n"
                + "            return true;\n"
                + "        }";

        assertEquals("Every fail-closed standalone handoff path must release SafeWalk",
                2, occurrences(ownership, failureCleanup));
    }

    private static boolean yieldsBefore(String callback, String sideEffect) {
        int yield = callback.indexOf("if (yieldToStandaloneBridge())");
        int effect = callback.indexOf(sideEffect);
        return yield >= 0 && effect > yield && callback.substring(yield, effect).contains("return;");
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
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
