package gq.yozakura.bridge;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PacketWriteLifecycleContractTest {
    @Test
    public void reportsTheOutboundWriteResultAfterThePromiseCompletes() throws IOException {
        assertWriteObservation(combinedForgeSource());
        assertWriteObservation(combinedStandaloneSource());
    }

    @Test
    public void bridgeAssistUsesCommittedPlacementSignalsWithIsolatedTellyRuntimeHooks() throws IOException {
        String bridgeAssist = source("src/main/java/gq/yozakura/module/world/BridgeAssist.java");

        assertTrue(bridgeAssist.contains("PacketWriteEvent"));
        assertTrue(bridgeAssist.contains("packet.getPlacedBlockDirection() != 255"));
        assertTrue(bridgeAssist.contains("sneakController.onPlacementPacketAccepted(event.getWriteId())"));
        assertTrue(bridgeAssist.contains("sneakController.onPlacementPacketCompleted(event.getWriteId(), event.isSuccess())"));
        assertFalse(bridgeAssist.contains("BridgeAssistTellyController"));
        assertTrue(bridgeAssist.contains("TellyBridgeRuntime"));
    }

    @Test
    public void correlatesAcceptedPacketsWithTheSneakSessionBeforeCountingTheirCompletion() throws IOException {
        String bridgeAssist = source("src/main/java/gq/yozakura/module/world/BridgeAssist.java");
        String controller = source("src/main/java/gq/yozakura/module/world/BridgeAssistSneakController.java");
        String accepted = source("src/main/java/gq/yozakura/event/bridge/PacketAcceptedEvent.java");
        String completed = source("src/main/java/gq/yozakura/event/bridge/PacketWriteEvent.java");

        assertTrue(bridgeAssist.contains("PacketAcceptedEvent"));
        assertTrue(bridgeAssist.contains("sneakController.onPlacementPacketAccepted"));
        assertTrue(bridgeAssist.contains("sneakController.onPlacementPacketCompleted"));
        assertTrue(bridgeAssist.contains("event.isSuccess()"));
        assertTrue(controller.contains("activePlacementSession"));
        assertTrue(controller.contains("pendingPlacementSessions"));
        assertTrue(controller.contains("completedPlacementSessions"));
        assertTrue(controller.contains("Map<Long, Long> pendingPlacementSessions"));
        assertFalse(controller.contains("session.longValue() == activePlacementSession.get()"));
        assertTrue(accepted.contains("long getWriteId()"));
        assertTrue(completed.contains("long getWriteId()"));
    }

    @Test
    public void keepsAcceptedWriteIdsThroughPlayerPacketAndBlinkBoundaries() throws IOException {
        String forge = combinedForgeSource();
        String standalone = combinedStandaloneSource();

        assertPlayerPacketWriteLifecycle(forge);
        assertPlayerPacketWriteLifecycle(standalone);
        assertTrue("Forge PRE-delayed C03 packets must retain their original accepted write id",
                forge.contains("rotation.getGeneration(), writeId")
                        || forge.contains("snapshot.getGeneration(), writeId")
                        || forge.contains("getRotationGeneration(snapshot), writeId"));
        assertTrue("A delayed Forge C03 must write with its stored id",
                forge.contains("delayed.promise, published, delayed.writeId")
                        || forge.contains("delayed.writeId"));
        assertTrue("Disconnect must fail the original delayed-C03 promise instead of synthesizing a second result",
                forge.contains("delayedPlayer.promise.tryFailure(cause);")
                        || forge.contains("delayed.promise.tryFailure(cause);")
                        || forge.contains("promise.tryFailure(cause);"));
    }

    @Test
    public void blinkBuffersDoNotCompleteAcceptedPacketsBeforeTheirRealReplay() throws IOException {
        String forge = combinedForgeSource();
        String standalone = combinedStandaloneSource();

        assertBlinkKeepsWriteId(forge);
        assertBlinkKeepsWriteId(standalone);
    }

    @Test
    public void readyPlacementsDoNotBypassTheNativeSprintAndSneakTransitions() throws IOException {
        assertNativeMovementStateOrder(combinedForgeSource());
        assertNativeMovementStateOrder(combinedStandaloneSource());
    }

    @Test
    public void bridgeAssistPlacementsKeepOriginalSourceOrderAcrossRuntimePaths() throws IOException {
        String accepted = source("src/main/java/gq/yozakura/event/bridge/PacketAcceptedEvent.java");
        String bridgeAssist = source("src/main/java/gq/yozakura/module/world/BridgeAssist.java");
        String base = baseSource();

        assertTrue("The packet lifecycle contract must expose source-order requests",
                accepted.contains("requestOriginalPacketOrder()"));

        int acceptedHandler = bridgeAssist.indexOf("public void onPacketAccepted(PacketAcceptedEvent event)");
        int writtenHandler = bridgeAssist.indexOf("public void onPacketWritten(PacketWriteEvent event)",
                acceptedHandler);
        String placementHandler = bridgeAssist.substring(acceptedHandler, writtenHandler);
        int originalOrderRequest = placementHandler.indexOf("event.requestOriginalPacketOrder();");
        int sneakClaim = placementHandler.indexOf("sneakController.onPlacementPacketAccepted");
        assertTrue("Original source order must be requested before the sneak session consumes the placement",
                originalOrderRequest >= 0 && sneakClaim > originalOrderRequest);
        assertFalse(placementHandler.contains("BridgeAssistTellyController"));
        assertTrue(placementHandler.contains("tellyRuntime.onPacketAccepted(event);"));
        assertFalse(placementHandler.contains("requestAfterCurrentRotation()"));
        assertTrue("The bridge may retain the generic queue for other callers",
                base.contains("accepted.isAfterCurrentRotationRequired()")
                        && base.contains("queueAfterCurrentRotationPacket(packet, promise, writeId);"));
    }

    private static void assertWriteObservation(String source) {
        assertTrue(source.contains("promise.addListener"));
        assertTrue(source.contains("PacketWriteDisposition.isServerVisibleSuccess(future)"));
        assertTrue(source.contains("new PacketAcceptedEvent(packet)"));
        assertTrue(source.contains("reportPacketWrite(packet, writeId,"));
        assertTrue(source.contains("new PacketWriteEvent(packet, writeId, success)"));
        assertTrue(source.contains("observePacketWrite(ctx, packet, promise, writeId);"));
        assertTrue(source.contains("promise.tryFailure(cause);") || source.contains("delayed.promise.tryFailure(cause);"));
        assertFalse(source.contains("observePacketWrite(ctx, delayed.packet, delayed.promise, delayed.writeId)"));
    }

    private static void assertPlayerPacketWriteLifecycle(String source) {
        assertTrue(source.contains("writePlayerPacketInternal(ctx, (C03PacketPlayer) packet, promise, rotation")
                || source.contains("writePlayerPacket(ctx, (C03PacketPlayer) packet, promise, rotation"));
        assertTrue(source.contains("writeId"));
        assertTrue(source.contains("observePacketWrite(ctx, packet, promise, writeId)"));
    }

    private static void assertBlinkKeepsWriteId(String source) {
        assertTrue(source.contains("offerPacket(packet, promise, writeId)"));
        assertTrue(source.contains("offerPacket(delayed.packet, delayed.promise,")
                && source.contains("delayed.writeId))")
                || (source.contains("offerPacket(getDelayedPacketPacket(delayed), getDelayedPacketPromise(delayed),")
                        && source.contains("getDelayedPacketWriteId(delayed))"))
                || source.contains("writeQueuedActionPacket"));
        assertFalse(source.contains("reportPacketWrite(packet, writeId, false);"));
    }

    private static void assertNativeMovementStateOrder(String source) {
        assertFalse("A queued placement must not be flushed before vanilla C0B state transitions",
                source.contains("if (isMovementStateAction(packet))"));
        assertFalse("Sprint and sneak must retain their native C0B -> C03 boundary",
                source.contains("private boolean isMovementStateAction(Packet<?> packet)"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static String baseSource() throws IOException {
        return new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/bridge/BasePacketBridgeHandler.java")), StandardCharsets.UTF_8);
    }

    private static String forgeSource() throws IOException {
        return new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/bridge/YozakuraEventBridge.java")), StandardCharsets.UTF_8);
    }

    private static String standaloneSource() throws IOException {
        return new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/bridge/StandaloneEventBridge.java")), StandardCharsets.UTF_8);
    }

    private static String combinedForgeSource() throws IOException {
        return baseSource() + "\n" + forgeSource();
    }

    private static String combinedStandaloneSource() throws IOException {
        return baseSource() + "\n" + standaloneSource();
    }
}
