package gq.yozakura.module.combat;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Locks BlockHit to the bridge's one real player-tick boundary rather than
 * every C03 packet written by combat modules.
 */
public class BlockHitCanonicalBoundaryContractTest {
    @Test
    public void onlyTheCanonicalPlayerBoundaryMayAdvanceTheUseCycle() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/combat/BlockHit.java");
        String writeHandler = method(source, "public void onPacketWritten(PacketWriteEvent event)",
                "public void onPlayerPacketBoundary(PlayerPacketBoundaryEvent event)");
        String boundaryHandler = method(source,
                "public void onPlayerPacketBoundary(PlayerPacketBoundaryEvent event)",
                "public static boolean isBlockingActive()");

        assertTrue(source.contains("PlayerPacketBoundaryEvent"));
        assertTrue(writeHandler.contains("event.isPacketAccepted()"));
        assertTrue(writeHandler.contains("packet instanceof C03PacketPlayer"));
        assertTrue(writeHandler.contains("clearAcceptedMovementWrite(event.getWriteId())"));
        assertFalse(writeHandler.contains("controller.confirmMovementBoundary()"));
        assertTrue(boundaryHandler.contains("event.isPacketAccepted()"));
        assertTrue(boundaryHandler.contains("successfulMovementBoundaries.offer("));
        assertFalse(boundaryHandler.contains("controller.confirmMovementBoundary()"));
    }

    @Test
    public void nettyWriteCompletionOnlyQueuesAnAttackForTheClientThread() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/combat/BlockHit.java");
        String updateHandler = method(source, "public void onUpdate(UpdateEvent event)",
                "public void onPacketAccepted(PacketAcceptedEvent event)");
        String attackDrain = method(source, "private void drainSuccessfulAttacks()",
                "private void drainSuccessfulUseWrites()");
        String writeHandler = method(source, "public void onPacketWritten(PacketWriteEvent event)",
                "public void onPlayerPacketBoundary(PlayerPacketBoundaryEvent event)");

        assertTrue(updateHandler.contains("drainSuccessfulAttacks()"));
        assertTrue(updateHandler.contains("drainSuccessfulMovementBoundaries()"));
        assertTrue(attackDrain.contains("armUseForAttack("));
        assertTrue(writeHandler.contains("successfulAttacks.offer("));
        assertFalse(writeHandler.contains("armUseForAttack("));
        assertFalse(writeHandler.contains("matchesAutomaticTarget("));
        assertFalse(writeHandler.contains("isGameplayReady()"));
    }

    @Test
    public void disabledBlockHitDoesNotAccumulateBridgeThreadPackets() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/combat/BlockHit.java");
        String enabled = method(source, "public void onEnabled()", "public void onDisabled()");
        String disabled = method(source, "public void onDisabled()", "public void onUpdate(UpdateEvent event)");
        String accepted = method(source, "public void onPacketAccepted(PacketAcceptedEvent event)",
                "public void onPacketWritten(PacketWriteEvent event)");
        String written = method(source, "public void onPacketWritten(PacketWriteEvent event)",
                "public void onPlayerPacketBoundary(PlayerPacketBoundaryEvent event)");
        String boundary = method(source,
                "public void onPlayerPacketBoundary(PlayerPacketBoundaryEvent event)",
                "public static boolean isBlockingActive()");

        assertTrue(source.contains("private volatile boolean acceptingBridgeEvents;"));
        assertTrue(enabled.contains("acceptingBridgeEvents = true;"));
        assertTrue(disabled.contains("acceptingBridgeEvents = false;"));
        assertTrue(accepted.contains("!acceptingBridgeEvents"));
        assertTrue(written.contains("!acceptingBridgeEvents"));
        assertTrue(boundary.contains("!acceptingBridgeEvents"));
    }

    @Test
    public void acceptedWriteGenerationSurvivesUntilItsCompletionOrCanonicalBoundary() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/combat/BlockHit.java");
        String accepted = method(source, "public void onPacketAccepted(PacketAcceptedEvent event)",
                "public void onPacketWritten(PacketWriteEvent event)");
        String written = method(source, "public void onPacketWritten(PacketWriteEvent event)",
                "public void onPlayerPacketBoundary(PlayerPacketBoundaryEvent event)");
        String boundary = method(source,
                "public void onPlayerPacketBoundary(PlayerPacketBoundaryEvent event)",
                "public static boolean isBlockingActive()");

        assertTrue(source.contains("acceptedWriteGenerations"));
        assertTrue(source.contains("latestAcceptedMovementWrite"));
        assertTrue(accepted.contains("rememberAcceptedWrite(event.getWriteId(), generation)"));
        assertTrue(accepted.contains("packet instanceof C03PacketPlayer"));
        assertTrue(accepted.contains("rememberAcceptedMovementWrite(event.getWriteId(), generation)"));
        assertTrue(written.contains("consumeAcceptedWriteGeneration(event.getWriteId())"));
        assertTrue(written.contains("clearAcceptedMovementWrite(event.getWriteId())"));
        assertTrue(boundary.contains("consumeAcceptedMovementWrite(event.getWriteId())"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static String method(String source, String beginMarker, String endMarker) {
        int begin = source.indexOf(beginMarker);
        int end = source.indexOf(endMarker, begin + beginMarker.length());
        assertTrue("Expected method marker: " + beginMarker, begin >= 0);
        assertTrue("Expected end marker: " + endMarker, end > begin);
        return source.substring(begin, end);
    }
}
