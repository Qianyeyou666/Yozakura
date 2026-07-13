package gq.yozakura.bridge;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StandaloneEventBridgeStateContractTest {
    @Test
    public void publishesAnImmutableRotationSnapshotForTheNettyThread() throws IOException {
        String bridge = source("src/main/java/gq/yozakura/bridge/StandaloneEventBridge.java");
        String publication = source("src/main/java/gq/yozakura/bridge/StandaloneRotationPublication.java");
        String write = packetBridgeWriteMethod(bridge);

        assertTrue("The main thread must publish the completed PRE rotation atomically",
                bridge.contains("rotationPublication.publish("));
        assertTrue("The Netty handler must read one immutable snapshot per C03",
                write.contains("StandaloneRotationPublication.Snapshot rotation = rotationPublication.snapshot();"));
        assertFalse("The Netty handler must not read RotationState's mutable fields directly",
                write.contains("RotationState.isActived()")
                        || write.contains("RotationState.getRotationYawHead()")
                        || write.contains("RotationState.getRotationPitch()"));
        assertTrue("The snapshot reference is the cross-thread publication barrier",
                publication.contains("private volatile Snapshot current"));
        assertTrue("Published snapshots must be immutable",
                publication.contains("private final boolean active")
                        && publication.contains("private final float yaw")
                        && publication.contains("private final float pitch"));
    }

    @Test
    public void aNewPreDispatchesThePreviousPostInsteadOfOverwritingIt() throws IOException {
        String pre = method(source("src/main/java/gq/yozakura/bridge/StandaloneEventBridge.java"),
                "    private void dispatchPreUpdate() {", "    private void dispatchPreUpdateBeforePlayerPacket() {");

        int previousPost = pre.indexOf("dispatchPendingPostUpdate();");
        int beginRotationTick = pre.indexOf("VisualRotationState.beginTick();");
        assertTrue("The previous POST must run before the next PRE begins",
                previousPost >= 0 && previousPost < beginRotationTick);
        assertFalse("PRE must never erase an undispatched POST",
                pre.contains("pendingPostUpdate = null;"));
    }

    @Test
    public void lockViewUsesTheRotationManagerSignalInsteadOfAListenerPriority() throws IOException {
        String localRotation = method(source("src/main/java/gq/yozakura/bridge/StandaloneEventBridge.java"),
                "    private void applyLocalAimAssistRotation(UpdateEvent update) {",
                "    private void queuePostUpdate(UpdateEvent preUpdate) {");

        assertTrue("KillAura Lock View publishes force=true through RotationManager",
                localRotation.contains("YozakuraRuntime.rotationManager.isRotated()"));
        assertFalse("Listener priority 1 is valid and must not suppress Lock View",
                localRotation.contains("update.isRotating() != 0"));
    }

    @Test
    public void noEventSkipsPacketEventButStillUsesTheRotationBridge() throws IOException {
        String write = packetBridgeWriteMethod(source("src/main/java/gq/yozakura/bridge/StandaloneEventBridge.java"));

        int skipFlag = write.indexOf("boolean skipPacketEvent = PacketBridgeSupport.consumeNoEvent(packet);");
        int rotationSnapshot = write.indexOf(
                "StandaloneRotationPublication.Snapshot rotation = rotationPublication.snapshot();");
        assertTrue("No-event must skip only PacketEvent and continue through C03 rewriting",
                skipFlag >= 0 && write.contains("if (!skipPacketEvent)") && rotationSnapshot > skipFlag);
        assertTrue("Blocked sprint writes must complete the caller promise",
                write.contains("SEND_BLOCKED_SPRINT") && write.contains("completeDroppedWrite(promise);"));
        assertTrue("Cancelled packet writes must complete the caller promise",
                write.contains("SEND_CANCELLED")
                        && count(write, "completeDroppedWrite(promise);") >= 2);
    }

    @Test
    public void everyC03LeavesBeforeActionsWaitingBehindIt() throws IOException {
        String write = packetBridgeWriteMethod(source("src/main/java/gq/yozakura/bridge/StandaloneEventBridge.java"));

        int silentWrite = write.indexOf("super.write(ctx, rewritten, promise);");
        int silentFlush = write.indexOf("flushDelayedPackets(ctx);", silentWrite);
        assertTrue("The rewritten C03 must precede delayed actions",
                silentWrite >= 0 && silentFlush > silentWrite);

        int normalBranch = write.indexOf("if (packet instanceof C03PacketPlayer) {", silentFlush);
        int normalWrite = write.indexOf("super.write(ctx, msg, promise);", normalBranch);
        int normalFlush = write.indexOf("flushDelayedPackets(ctx);", normalBranch);
        assertTrue("An unmodified C03 must also precede delayed actions",
                normalBranch >= 0 && normalWrite > normalBranch && normalFlush > normalWrite);
    }

    @Test
    public void actionsAfterTheCurrentSilentC03DoNotWaitForAnotherC03() throws IOException {
        String bridge = source("src/main/java/gq/yozakura/bridge/StandaloneEventBridge.java");
        String gate = method(bridge,
                "        private boolean shouldDelayUntilRotation(Packet<?> packet) {",
                "        private boolean isRotationSensitiveAction(Packet<?> packet) {");
        String write = packetBridgeWriteMethod(bridge);

        assertTrue("The delay gate must track whether the published generation is still unsent",
                gate.contains("rotationPublication.hasUnsentRotation()"));
        assertFalse("An active snapshot may already have reached the pipeline",
                gate.contains("rotationPublication.snapshot().isActive()"));
        assertTrue("Writing a silent C03 must acknowledge its exact snapshot generation",
                write.contains("rotationPublication.markSent(rotation);"));
    }

    @Test
    public void realInboundPacketsDoNotConsultTheDirectProcessNoEventList() throws IOException {
        String bridge = source("src/main/java/gq/yozakura/bridge/StandaloneEventBridge.java");

        assertFalse("receivePacketNoEvent never enters channelRead, so this list cannot match real inbound packets",
                bridge.contains("PacketUtil.skipReceiveEvent.remove"));
    }

    @Test
    public void handlerLifecycleFailsAndClearsDelayedWrites() throws IOException {
        String bridge = source("src/main/java/gq/yozakura/bridge/StandaloneEventBridge.java");

        assertTrue("Removing a handler must resolve every queued promise",
                bridge.contains("public void handlerRemoved(ChannelHandlerContext ctx)")
                        && bridge.contains("failDelayedPackets("));
        assertTrue("Closing a channel must resolve every queued promise",
                bridge.contains("public void channelInactive(ChannelHandlerContext ctx)"));
        assertTrue("Closed handlers must not retain delayed packet objects",
                bridge.contains("delayedPackets.clear();"));
    }

    @Test
    public void anEstablishedConnectionDispatchesExactlyOneDisconnectTransition() throws IOException {
        String bridge = source("src/main/java/gq/yozakura/bridge/StandaloneEventBridge.java");

        assertTrue("Startup without a world must not look like a disconnect",
                bridge.contains("private boolean wasInGame;")
                        && bridge.contains("if (wasInGame)"));
        assertTrue("The standalone event bus must receive Forge's disconnect shim",
                bridge.contains("new gq.yozakura.bridge.forge.FMLNetworkEvent.ClientDisconnectionFromServerEvent()"));
        assertTrue("Standalone has no Forge Client listener, so disconnect must persist and disable directly",
                bridge.contains("ConfigBridge.saveIfDirtyQuietly();")
                        && bridge.contains("ConfigBridge.setAutoSaveSuspended(true);")
                        && bridge.contains("ModuleManager.disableAll(false);")
                        && bridge.contains("ConfigBridge.setAutoSaveSuspended(false);"));
        assertTrue("Disconnect cleanup must include pending exit rotations",
                bridge.contains("RotationExitState.clear();"));

        String shutdown = method(bridge, "    public void shutdown() {", "    private void clearBridgeState() {");
        assertTrue("Reinjection can stop the old pump without a world-null tick",
                shutdown.contains("cleanupStandaloneModules();"));
    }

    private static String packetBridgeWriteMethod(String source) {
        return method(source,
                "        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {",
                "        @Override\n        public void channelRead");
    }

    private static String method(String source, String beginMarker, String endMarker) {
        int begin = source.indexOf(beginMarker);
        int end = source.indexOf(endMarker, begin + beginMarker.length());
        assertTrue("Expected method marker: " + beginMarker, begin >= 0);
        assertTrue("Expected end marker: " + endMarker, end > begin);
        return source.substring(begin, end);
    }

    private static int count(String source, String needle) {
        int matches = 0;
        int index = 0;
        while ((index = source.indexOf(needle, index)) >= 0) {
            matches++;
            index += needle.length();
        }
        return matches;
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }
}
