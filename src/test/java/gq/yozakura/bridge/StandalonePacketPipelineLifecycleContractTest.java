package gq.yozakura.bridge;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StandalonePacketPipelineLifecycleContractTest {
    @Test
    public void standaloneInstallationReplacesAStaleForgeBridge() throws IOException {
        String anchors = source("src/main/java/gq/yozakura/bridge/PacketPipelineAnchors.java");
        int standaloneInstall = anchors.indexOf("public static void installStandaloneBridge");
        int delayInstall = anchors.indexOf("public static void installDelayHandler", standaloneInstall);
        String install = anchors.substring(standaloneInstall, delayInstall);

        assertTrue("A Lunar packet must not traverse both a stale Forge and standalone packet bridge",
                install.contains("pipeline.remove(FORGE_BRIDGE_HANDLER_NAME)"));
        assertTrue("The standalone bridge must still become the sole packet bridge after conflict removal",
                install.contains("pipeline.addBefore(PACKET_HANDLER_NAME, STANDALONE_BRIDGE_HANDLER_NAME, handler)"));
    }

    @Test
    public void standaloneBridgeAnchorsEveryDelayHandlerInOneDeterministicOrder() throws IOException {
        String bridge = source("src/main/java/gq/yozakura/bridge/StandaloneEventBridge.java");
        String fakeLag = source("src/main/java/gq/yozakura/module/combat/FakeLag.java");
        String lagRange = source("src/main/java/gq/yozakura/module/combat/LagRange.java");
        String backtrack = source("src/main/java/gq/yozakura/module/combat/Backtrack.java");

        assertTrue("The standalone bridge must install through the shared named anchor",
                bridge.contains("PacketPipelineAnchors.installStandaloneBridge("));
        assertTrue("FakeLag must remain before either Yozakura bridge when it buffers outbound writes",
                fakeLag.contains("PacketPipelineAnchors.installDelayHandler("));
        assertTrue("LagRange must remain before either Yozakura bridge when it buffers outbound writes",
                lagRange.contains("PacketPipelineAnchors.installDelayHandler("));
        assertTrue("Backtrack must remain before the bridge so released inbound packets are observed once",
                backtrack.contains("PacketPipelineAnchors.installDelayHandler("));
        assertFalse("Delay handlers must not compete to insert directly before packet_handler",
                fakeLag.contains("addBefore(\"packet_handler\"")
                        || lagRange.contains("addBefore(\"packet_handler\"")
                        || backtrack.contains("addBefore(\"packet_handler\""));
    }

    @Test
    public void delayHandlersCanUseEitherForgeOrStandaloneBridgeAsTheirAnchor() throws IOException {
        String anchors = source("src/main/java/gq/yozakura/bridge/PacketPipelineAnchors.java");

        assertTrue("The helper must recognize Forge's bridge for modules shared with Forge",
                anchors.contains("yozakura_event_bridge"));
        assertTrue("The helper must recognize the standalone bridge",
                anchors.contains("yozakura_standalone_event_bridge"));
        assertTrue("New delay handlers must use the next canonical handler instead of enable order",
                anchors.contains("findDelaySuccessor("));
        assertFalse("Queued packets retain their handler context, so installed delay handlers must not be moved",
                anchors.contains("pipeline.remove(handlerName);"));
    }

    @Test
    public void actionBatchesReachFakeLagBeforeTheFollowingPlayerPacketWithoutReenteringTheBridge() throws IOException {
        String bridge = source("src/main/java/gq/yozakura/bridge/StandaloneEventBridge.java");
        String fakeLag = source("src/main/java/gq/yozakura/module/combat/FakeLag.java");
        String anchors = source("src/main/java/gq/yozakura/bridge/PacketPipelineAnchors.java");
        int sensitiveBegin = bridge.indexOf("        private boolean isPostSensitiveAction(Packet<?> packet) {");
        int sensitiveEnd = bridge.indexOf("        private void queueCurrentActionPacket", sensitiveBegin);
        String sensitive = sensitiveBegin >= 0 && sensitiveEnd > sensitiveBegin
                ? bridge.substring(sensitiveBegin, sensitiveEnd) : "";

        assertTrue("C0A and its companion packets must stay in a Post-sensitive FIFO batch",
                sensitive.contains("C0APacketAnimation")
                        && sensitive.contains("C02PacketUseEntity")
                        && sensitive.contains("C09PacketHeldItemChange"));
        assertTrue("The batch gate must run before any action can leave the bridge",
                bridge.contains("if (!skipPacketEvent && !preserveOriginalPacketOrder && isPostSensitiveAction(packet))")
                        && bridge.contains("queueCurrentActionPacket(packet, promise, packetPendingPost, writeId);"));
        assertTrue("The bridge must flush ready actions to preceding delay handlers before C03",
                bridge.contains("super.write(ctx, delayed.packet, delayed.promise);"));
        assertTrue("A bridge installed after FakeLag must be placed at packet_handler's tail-side boundary",
                anchors.contains("pipeline.addBefore(PACKET_HANDLER_NAME, STANDALONE_BRIDGE_HANDLER_NAME, handler);"));
        assertTrue("A FakeLag enabled after the bridge must be placed before that bridge",
                anchors.contains("anchor = findBridgeAnchor(pipeline);")
                        && anchors.contains("pipeline.addBefore(anchor, handlerName, handler);"));
        assertTrue("FakeLag releases from its own handler context, which excludes later bridge handlers",
                fakeLag.contains("queued.ctx.writeAndFlush(queued.packet, queued.promise);"));
    }

    @Test
    public void aClosedOrRemovedChannelStopsStandaloneTicksAndWritesBeforeTheNextWorldTick() throws IOException {
        String bridge = source("src/main/java/gq/yozakura/bridge/StandaloneEventBridge.java");

        assertTrue("Netty lifecycle callbacks must publish transport loss to the main bridge",
                bridge.contains("onPacketBridgeTerminated(ctx.channel());"));
        assertTrue("Tick dispatch must stop before world events when its channel has terminated",
                bridge.contains("if (stopForTerminatedPacketBridge())"));
        assertTrue("Writes in the disconnect window must fail instead of traversing the old pipeline",
                bridge.contains("if (packetBridgeTerminated || !ctx.channel().isActive())"));
        assertTrue("A channel loss must be published across the Netty/main-thread boundary",
                bridge.contains("private volatile boolean packetBridgeTerminated;"));
        assertTrue("A replacement channel cannot skip the one required termination cleanup pass",
                bridge.contains("private volatile boolean terminatedPacketBridgeCleaned;"));
        int stopBegin = bridge.indexOf("    private boolean stopForTerminatedPacketBridge() {");
        int stopEnd = bridge.indexOf("    private boolean hasReplacementPacketChannel() {", stopBegin);
        String stop = stopBegin >= 0 && stopEnd > stopBegin ? bridge.substring(stopBegin, stopEnd) : "";
        int cleanup = stop.indexOf("if (!terminatedPacketBridgeCleaned)");
        int replacement = stop.indexOf("hasReplacementPacketChannel()");
        assertTrue("The first terminated tick must clean up before a replacement can resume dispatch",
                cleanup >= 0 && replacement > cleanup);
        assertTrue("Disconnect cleanup must restore the original renderer hooks before returning",
                stop.contains("uninstallRendererHooks();"));
        int tickBegin = bridge.indexOf("    public void tick(boolean playerTick) {");
        int tickEnd = bridge.indexOf("    public void shutdown() {", tickBegin);
        String tick = tickBegin >= 0 && tickEnd > tickBegin ? bridge.substring(tickBegin, tickEnd) : "";
        int inject = tick.indexOf("injectPacketHandler();");
        int postInjectStop = tick.indexOf("if (stopForTerminatedPacketBridge())", inject);
        assertTrue("A close racing packet-handler installation must stop this same main-thread tick",
                inject >= 0 && postInjectStop > inject);
        assertTrue("The movement-input PRE hook must reject a terminated bridge before dispatching events",
                bridge.contains("if (packetBridgeTerminated || dispatchingPlayerPacketPreUpdate"));
        int injectBegin = bridge.indexOf("    private void injectPacketHandler() {");
        int injectEnd = bridge.indexOf("    private void removePacketHandler() {", injectBegin);
        String injection = injectBegin >= 0 && injectEnd > injectBegin ? bridge.substring(injectBegin, injectEnd) : "";
        assertTrue("Installation must not revive a bridge that terminated during this same tick",
                injection.contains("if (packetBridgeTerminated) {"));
        assertFalse("Only the next main-thread termination pass may clear the termination signal",
                injection.contains("packetBridgeTerminated = false;"));
        int channelAssigned = injection.indexOf("channel = next;");
        int activeCheck = injection.indexOf("if (!next.isActive())", channelAssigned);
        assertTrue("A channel that closes while its handler is being added must still publish termination",
                channelAssigned >= 0 && activeCheck > channelAssigned
                        && injection.substring(activeCheck).contains("onPacketBridgeTerminated(next);"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }
}
