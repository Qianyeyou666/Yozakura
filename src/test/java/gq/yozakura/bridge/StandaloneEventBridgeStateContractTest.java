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
        String bridge = standaloneSource();
        String publication = source("src/main/java/gq/yozakura/bridge/StandaloneRotationPublication.java");
        String combined = combinedSource();
        String write = packetBridgeWriteMethod(combined);

        assertTrue("The main thread must publish the completed PRE rotation atomically",
                bridge.contains("rotationPublication.publish("));
        assertTrue("The Netty handler must read one immutable snapshot per C03",
                write.contains("StandaloneRotationPublication.Snapshot rotation = rotationPublication.snapshot();")
                        || bridge.contains("return rotationPublication.snapshot();"));
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
        String pre = method(standaloneSource(),
                "    private void dispatchPreUpdate() {", "    private void dispatchPreUpdateBeforePlayerPacket() {");

        int previousPost = pre.indexOf("dispatchPendingPostUpdate();");
        int beginRotationTick = pre.indexOf("VisualRotationState.beginTick();");
        assertTrue("The previous POST must run before the next PRE begins",
                previousPost >= 0 && previousPost < beginRotationTick);
        assertFalse("PRE must never erase an undispatched POST",
                pre.contains("pendingPostUpdate = null;"));
    }

    @Test
    public void aNewPreRestoresThePreviousTemporaryPhysicsYawBeforeCapturingCameraRotation() throws IOException {
        String pre = method(standaloneSource(),
                "    private void dispatchPreUpdate() {", "    private void dispatchPreUpdateBeforePlayerPacket() {");
        int restorePreviousPhysicsYaw = pre.indexOf("MovementInputBridge.restoreRotation();");
        int captureUpdateRotation = pre.indexOf("UpdateEvent update = new UpdateEvent");

        assertTrue("A stale physics yaw must not become the next silent rotation's camera baseline",
                restorePreviousPhysicsYaw >= 0 && captureUpdateRotation > restorePreviousPhysicsYaw);
    }

    @Test
    public void lockViewUsesTheRotationManagerSignalInsteadOfAListenerPriority() throws IOException {
        String localRotation = method(standaloneSource(),
                "    private void applyLocalAimAssistRotation(UpdateEvent update) {",
                "    private void queuePostUpdate(UpdateEvent preUpdate) {");

        assertTrue("KillAura Lock View publishes force=true through RotationManager",
                localRotation.contains("YozakuraRuntime.rotationManager.isRotated()"));
        assertFalse("Listener priority 1 is valid and must not suppress Lock View",
                localRotation.contains("update.isRotating() != 0"));
    }

    @Test
    public void noEventSkipsPacketEventButStillUsesTheRotationBridge() throws IOException {
        String combined = combinedSource();
        String write = packetBridgeWriteMethod(combined);

        int marker = write.indexOf("PacketBridgeSupport.consumeNoEventMarker(packet);");
        int skipFlag = write.indexOf("boolean skipPacketEvent = noEventMarker.isMarked();", marker);
        int rotationSnapshot = write.indexOf(
                "StandaloneRotationPublication.Snapshot rotation = rotationPublication.snapshot();");
        assertTrue("No-event must skip only PacketEvent and continue through C03 rewriting",
                marker >= 0 && skipFlag > marker && write.contains("if (!skipPacketEvent)")
                        && (rotationSnapshot > skipFlag || write.contains("S rotation = getRotationSnapshot();")));
        assertTrue("Only replay packets already processed by the bridge may bypass its lifecycle",
                write.contains("if (noEventMarker.isAlreadyBridgeProcessed())"));
        assertFalse("Sprint state packets must retain their native C0B lifecycle",
                write.contains("SEND_BLOCKED_SPRINT") || write.contains("shouldBlockSprintPacket"));
        assertTrue("Cancelled packet writes must complete the caller promise",
                write.contains("SEND_CANCELLED") && write.contains("completeDroppedWrite(promise);"));
    }

    @Test
    public void normalPlayerPacketsFlushTheCurrentActionBatchBeforeC03() throws IOException {
        String combined = combinedSource();
        String standalone = standaloneSource();
        String write = packetBridgeWriteMethod(combined);
        String playerPacket = standaloneWritePlayerPacketMethod();

        int currentActionFlush = playerPacket.indexOf("flushCurrentActionPackets(ctx);");
        int normalC03Write = playerPacket.indexOf("super.write(ctx, packet, promise);");
        if (normalC03Write < 0) {
            normalC03Write = playerPacket.indexOf("super.write(ctx, packet, promise)");
        }
        assertTrue("When no silent rotation is active, the native C0A/C02 batch must leave before C03",
                currentActionFlush >= 0 && normalC03Write > currentActionFlush);
        assertTrue("A second C03 in the same tick must not flush actions after the first one",
                playerPacket.contains(
                                "boolean playerTickAdvanced = playerPacketTickGate.consumeNextCanonicalPlayerPacket(")
                        && (playerPacket.contains(
                                "if (playerTickAdvanced && !preUpdatePending && !rotation.isActive())")
                                || playerPacket.contains("writePlayerPacketCommon(")));
        assertTrue("C03 handling must be centralized so both rewritten and normal paths use the same batch boundary",
                write.contains("writePlayerPacketInternal(ctx, (C03PacketPlayer) packet, promise, rotation")
                        || write.contains("writePlayerPacket(ctx, (C03PacketPlayer) packet, promise, rotation,"));
    }

    @Test
    public void silentPlayerPacketsPublishRotationThenDeferOnlyTheCurrentActionBatch() throws IOException {
        String playerPacket = standaloneWritePlayerPacketMethod();
        String combined = combinedSource();

        int readyFlush = playerPacket.indexOf("flushReadyActionPackets(ctx);");
        int silentC03 = playerPacket.indexOf("super.write(ctx, rewritten, promise);");
        int markRotationSent = playerPacket.indexOf("rotationPublication.markSent(rotation);");
        int deferCurrentActions = playerPacket.indexOf("promoteCurrentActionPackets();");

        if (readyFlush < 0 || silentC03 < 0 || markRotationSent < 0 || deferCurrentActions < 0) {
            playerPacket = combined;
            readyFlush = playerPacket.indexOf("flushReadyActionPackets(ctx);");
            silentC03 = playerPacket.indexOf("super.write(ctx, rewritten, promise);");
            markRotationSent = playerPacket.indexOf("markRotationSent(snapshot);");
            if (markRotationSent < 0) {
                markRotationSent = playerPacket.indexOf("rotationPublication.markSent(rotation);");
            }
            deferCurrentActions = playerPacket.indexOf("promoteCurrentActionPackets();");
        }

        assertTrue("The prior action batch must be sent before the C03 that starts the next silent tick",
                readyFlush >= 0 && silentC03 > readyFlush);
        assertTrue("The new yaw must reach the server before the current batch is marked ready for the next tick",
                markRotationSent > silentC03 && deferCurrentActions > markRotationSent);
    }

    @Test
    public void realInboundPacketsDoNotConsultTheDirectProcessNoEventList() throws IOException {
        String bridge = standaloneSource();

        assertFalse("receivePacketNoEvent never enters channelRead, so this list cannot match real inbound packets",
                bridge.contains("PacketUtil.skipReceiveEvent.remove"));
    }

    @Test
    public void handlerLifecycleFailsAndClearsDelayedWrites() throws IOException {
        String combined = combinedSource();

        assertTrue("Removing a handler must resolve every queued promise",
                combined.contains("public void handlerRemoved(ChannelHandlerContext ctx)")
                        && combined.contains("failDelayedPackets("));
        assertTrue("Closing a channel must resolve every queued promise",
                combined.contains("public void channelInactive(ChannelHandlerContext ctx)"));
        assertTrue("Closed handlers must not retain delayed packet objects",
                combined.contains("delayedPackets.clear();"));
    }

    @Test
    public void anEstablishedConnectionDispatchesExactlyOneDisconnectTransition() throws IOException {
        String bridge = standaloneSource();
        String disconnect = method(bridge,
                "    private void dispatchDisconnected() {",
                "    private void cleanupStandaloneModules() {");

        assertTrue("Startup without a world must not look like a disconnect",
                bridge.contains("private boolean wasInGame;")
                        && bridge.contains("if (wasInGame)"));
        assertTrue("The standalone event bus must receive Forge's disconnect shim",
                bridge.contains("new gq.yozakura.bridge.forge.FMLNetworkEvent.ClientDisconnectionFromServerEvent()"));
        assertTrue("Standalone has no Forge Client listener, so disconnect must persist directly",
                disconnect.contains("ConfigBridge.saveIfDirtyQuietly();"));
        assertFalse("Switching servers must not disable standalone modules",
                disconnect.contains("cleanupStandaloneModules();")
                        || disconnect.contains("ModuleManager.disableAll(false);"));
        assertTrue("Disconnect cleanup must include pending exit rotations",
                bridge.contains("RotationExitState.clear();"));

        String shutdown = method(bridge, "    public void shutdown() {", "    private void clearBridgeState() {");
        assertTrue("Reinjection must still disable modules without a world-null tick",
                shutdown.contains("cleanupStandaloneModules();"));
    }

    private static String packetBridgeWriteMethod(String source) {
        int begin = source.indexOf("    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {");
        if (begin < 0) {
            begin = source.indexOf("        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {");
        }
        int end = source.indexOf("    protected void observePacketWrite(", begin);
        if (end < 0) {
            end = source.indexOf("    @Override\n    public void channelRead", begin);
        }
        if (end < 0) {
            end = source.indexOf("        @Override\n        public void channelRead", begin);
        }
        if (begin < 0 || end <= begin) {
            return "";
        }
        return source.substring(begin, end);
    }

    private static String standaloneWritePlayerPacketMethod() {
        try {
            String combined = combinedSource();
            return findWritePlayerPacketCommonMethod(combined);
        } catch (IOException e) {
            return "";
        }
    }

    private static String findWritePlayerPacketCommonMethod(String source) {
        int begin = source.indexOf(
                "    protected void writePlayerPacketCommon(ChannelHandlerContext ctx, C03PacketPlayer packet,");
        if (begin < 0) {
            begin = source.indexOf(
                    "        protected void writePlayerPacketInternal(ChannelHandlerContext ctx, C03PacketPlayer packet,");
        }
        if (begin < 0) {
            begin = source.indexOf(
                    "        private void writePlayerPacket(ChannelHandlerContext ctx, C03PacketPlayer packet,");
        }
        if (begin < 0) {
            return source;
        }
        int end = source.indexOf(
                "    void markNextPlayerPacketTick(", begin);
        if (end < 0) {
            end = source.indexOf(
                    "        protected void logActionQueue(", begin);
        }
        if (end < 0) {
            end = source.indexOf(
                    "        @Override", begin);
        }
        if (end < 0 || end <= begin) {
            end = Math.min(begin + 600, source.length());
        }
        return source.substring(begin, end);
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

    private static String standaloneSource() throws IOException {
        return new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/bridge/StandaloneEventBridge.java")), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }

    private static String baseSource() throws IOException {
        return new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/bridge/BasePacketBridgeHandler.java")), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }

    private static String combinedSource() throws IOException {
        return baseSource() + "\n" + standaloneSource();
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }
}
