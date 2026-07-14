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
        assertWriteObservation(source("src/main/java/gq/yozakura/bridge/YozakuraEventBridge.java"));
        assertWriteObservation(source("src/main/java/gq/yozakura/bridge/StandaloneEventBridge.java"));
    }

    @Test
    public void bridgeAssistUsesTheCommittedPlacementSignalInsteadOfThePreQueuePacketEvent() throws IOException {
        String bridgeAssist = source("src/main/java/gq/yozakura/module/world/BridgeAssist.java");

        assertTrue(bridgeAssist.contains("PacketWriteEvent"));
        assertTrue(bridgeAssist.contains("packet.getPlacedBlockDirection() != 255"));
        assertFalse(bridgeAssist.contains("PacketEvent"));
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
        String forge = source("src/main/java/gq/yozakura/bridge/YozakuraEventBridge.java");
        String standalone = source("src/main/java/gq/yozakura/bridge/StandaloneEventBridge.java");

        assertPlayerPacketWriteLifecycle(forge);
        assertPlayerPacketWriteLifecycle(standalone);
        assertTrue("Forge PRE-delayed C03 packets must retain their original accepted write id",
                forge.contains("rotation.getGeneration(), writeId"));
        assertTrue("A delayed Forge C03 must write with its stored id",
                forge.contains("delayed.promise, published, delayed.writeId"));
        assertTrue("Disconnect must fail the original delayed-C03 promise instead of synthesizing a second result",
                forge.contains("delayedPlayer.promise.tryFailure(cause);"));
    }

    @Test
    public void blinkBuffersDoNotCompleteAcceptedPacketsBeforeTheirRealReplay() throws IOException {
        String forge = source("src/main/java/gq/yozakura/bridge/YozakuraEventBridge.java");
        String standalone = source("src/main/java/gq/yozakura/bridge/StandaloneEventBridge.java");

        assertBlinkKeepsWriteId(forge);
        assertBlinkKeepsWriteId(standalone);
    }

    @Test
    public void readyPlacementsDoNotBypassTheNativeSprintAndSneakTransitions() throws IOException {
        assertNativeMovementStateOrder(source("src/main/java/gq/yozakura/bridge/YozakuraEventBridge.java"));
        assertNativeMovementStateOrder(source("src/main/java/gq/yozakura/bridge/StandaloneEventBridge.java"));
    }

    @Test
    public void bridgeAssistC08PacketsKeepTheirVanillaOutboundPosition() throws IOException {
        String accepted = source("src/main/java/gq/yozakura/event/bridge/PacketAcceptedEvent.java");
        String bridgeAssist = source("src/main/java/gq/yozakura/module/world/BridgeAssist.java");
        String forge = source("src/main/java/gq/yozakura/bridge/YozakuraEventBridge.java");
        String standalone = source("src/main/java/gq/yozakura/bridge/StandaloneEventBridge.java");

        assertTrue("The packet lifecycle contract must expose an explicit source-order request",
                accepted.contains("requestOriginalPacketOrder()")
                        && accepted.contains("isOriginalPacketOrderRequired()"));

        int acceptedHandler = bridgeAssist.indexOf("public void onPacketAccepted(PacketAcceptedEvent event)");
        int writtenHandler = bridgeAssist.indexOf("public void onPacketWritten(PacketWriteEvent event)",
                acceptedHandler);
        String placementHandler = bridgeAssist.substring(acceptedHandler, writtenHandler);
        int originalOrderRequest = placementHandler.indexOf("event.requestOriginalPacketOrder();");
        int blockPlacementGuard = placementHandler.indexOf("if (packet.getPlacedBlockDirection() != 255)");
        int placementClaim = placementHandler.indexOf("sneakController.onPlacementPacketAccepted");
        assertTrue("Every BridgeAssist C08 must preserve vanilla order before a sneak/sprint state packet can pass it",
                originalOrderRequest >= 0 && originalOrderRequest < blockPlacementGuard);
        assertTrue("Only a real block placement may enter the BridgeAssist sneak-placement session",
                blockPlacementGuard >= 0 && placementClaim > blockPlacementGuard);

        assertOriginalOrderRequestBypassesOnlyTheRotationActionQueue(forge);
        assertOriginalOrderRequestBypassesOnlyTheRotationActionQueue(standalone);
    }

    private static void assertWriteObservation(String source) {
        assertTrue(source.contains("promise.addListener"));
        assertTrue(source.contains("future.isSuccess()"));
        assertTrue(source.contains("new PacketAcceptedEvent(packet)"));
        assertTrue(source.contains("reportPacketWrite(packet, writeId, future.isSuccess())"));
        assertTrue(source.contains("new PacketWriteEvent(packet, writeId, success)"));
        assertTrue(source.contains("observePacketWrite(ctx, packet, promise, writeId);"));
        assertTrue(source.contains("delayed.promise.tryFailure(cause);"));
        assertFalse(source.contains("observePacketWrite(ctx, delayed.packet, delayed.promise, delayed.writeId)"));
    }

    private static void assertPlayerPacketWriteLifecycle(String source) {
        assertTrue(source.contains("writePlayerPacket(ctx, (C03PacketPlayer) packet, promise, rotation"));
        assertTrue(source.contains("writeId"));
        assertTrue(source.contains("observePacketWrite(ctx, packet, promise, writeId)"));
    }

    private static void assertBlinkKeepsWriteId(String source) {
        assertTrue(source.contains("offerPacket(packet, promise, writeId)"));
        assertTrue(source.contains("offerPacket(delayed.packet, delayed.promise,")
                && source.contains("delayed.writeId))"));
        assertFalse(source.contains("reportPacketWrite(packet, writeId, false);"));
    }

    private static void assertNativeMovementStateOrder(String source) {
        assertFalse("A queued placement must not be flushed before vanilla C0B state transitions",
                source.contains("if (isMovementStateAction(packet))"));
        assertFalse("Sprint and sneak must retain their native C0B -> C03 boundary",
                source.contains("private boolean isMovementStateAction(Packet<?> packet)"));
    }

    private static void assertOriginalOrderRequestBypassesOnlyTheRotationActionQueue(String source) {
        assertTrue("The bridge must retain the accepted packet's explicit source-order request",
                source.contains("accepted.isOriginalPacketOrderRequired()"));
        assertTrue("Only an explicit request may keep a post-sensitive action out of the rotation queue",
                source.contains("!preserveOriginalPacketOrder && isPostSensitiveAction(packet)"));
        assertFalse("The bridge must not special-case or construct a movement-state C0B packet",
                source.contains("new C0BPacketEntityAction"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
