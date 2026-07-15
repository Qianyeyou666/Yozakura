package gq.yozakura.module.combat;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Regression contracts for the vanilla-input boundary of BlockHit.
 *
 * <p>Lunar/Grim rejects a release that shares an input tick with an item-use
 * packet, and rejects an item-use packet that is emitted after the movement
 * packet. BlockHit must therefore drive Minecraft's normal use-key path rather
 * than constructing, replaying, or cancelling interaction packets itself.</p>
 */
public class BlockHitRewriteContractTest {
    @Test
    public void blockHitUsesTheVanillaInputPathWithoutConstructingPackets() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/combat/BlockHit.java");

        assertTrue(source.contains("C02PacketUseEntity.Action.ATTACK"));
        assertTrue(source.contains("C07PacketPlayerDigging"));
        assertTrue(source.contains("C08PacketPlayerBlockPlacement"));
        assertTrue(source.contains("PlayerPacketBoundaryEvent"));
        assertTrue(source.contains("controller.observe("));
        assertTrue(source.contains("useAction.startUse(useCycle)"));
        assertTrue(source.contains("useAction.releaseUse(releaseCycle)"));
        assertFalse(source.contains("new C02PacketUseEntity"));
        assertFalse(source.contains("new C07PacketPlayerDigging"));
        assertFalse(source.contains("new C08PacketPlayerBlockPlacement"));
        assertFalse(source.contains("PacketUtil.sendPacket("));
        assertFalse(source.contains("event.setCancelled(true)"));
    }

    @Test
    public void blockHitLeavesMouseInputAndRayTracingToMinecraft() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/combat/BlockHit.java");

        assertFalse(source.contains("RightClickMouseEvent"));
        assertFalse(source.contains("LeftClickMouseEvent"));
        assertFalse(source.contains("MouseOverEvent"));
        assertFalse(source.contains("KeyBinding.setKeyBindState"));
        assertFalse(source.contains("mc.objectMouseOver ="));
    }

    @Test
    public void packetObservationArmsButDoesNotWriteTheUseKey() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/combat/BlockHit.java");

        assertTrue(source.contains("instanceof C02PacketUseEntity"));
        assertTrue(source.contains("C02PacketUseEntity.Action.ATTACK"));
        assertTrue(source.contains("C07PacketPlayerDigging.Action.RELEASE_USE_ITEM"));
        assertTrue(source.contains("controller.armUseAfterMovementBoundary()"));
        assertTrue(source.contains("controller.consumeUseRequest()"));
        assertTrue(source.contains("controller.consumeReleaseRequest()"));
        assertTrue(source.contains("onPacketWritten(PacketWriteEvent event)"));
        assertTrue(source.contains("onPlayerPacketBoundary(PlayerPacketBoundaryEvent event)"));
        assertTrue(source.contains("event.isPacketAccepted()"));
        assertTrue(source.contains("event.isSuccess()"));
        String packetHandler = method(source, "public void onPacketAccepted(PacketAcceptedEvent event)",
                "public void onPacketWritten(PacketWriteEvent event)");
        assertFalse(packetHandler.contains("useAction.startUse("));
        assertFalse(packetHandler.contains("useAction.releaseUse("));
        assertFalse(packetHandler.contains("armUseForAttack("));
        String writeHandler = method(source, "public void onPacketWritten(PacketWriteEvent event)",
                "public void onPlayerPacketBoundary(PlayerPacketBoundaryEvent event)");
        String boundaryHandler = method(source,
                "public void onPlayerPacketBoundary(PlayerPacketBoundaryEvent event)",
                "public static boolean isBlockingActive()");
        assertTrue(writeHandler.contains("successfulAttacks.offer("));
        assertFalse(writeHandler.contains("controller.confirmMovementBoundary()"));
        assertFalse(writeHandler.contains("armUseForAttack("));
        assertTrue(boundaryHandler.contains("successfulMovementBoundaries.offer("));
        assertFalse(boundaryHandler.contains("controller.confirmMovementBoundary()"));
        String boundaryDrain = method(source,
                "private void drainSuccessfulMovementBoundaries()",
                "private boolean matchesAutomaticTarget(C02PacketUseEntity packet)");
        assertTrue(boundaryDrain.contains("controller.confirmMovementBoundary()"));
        assertFalse(source.contains("armUseForPrediction("));
    }

    @Test
    public void vapeModeSurfaceAndSettingsAreBoundToTheModule() throws IOException {
        String blockHit = source("src/main/java/gq/yozakura/module/combat/BlockHit.java");
        String settings = source("src/main/java/gq/yozakura/module/combat/BlockHitSettings.java");

        assertTrue(settings.contains("\"Manual\", \"Predict\", \"Auto\", \"Lag\""));
        assertTrue(settings.contains("Require Mouse Down"));
        assertTrue(settings.contains("Ignore Manual Block"));
        assertTrue(settings.contains("Angle"));
        assertTrue(settings.contains("Distance"));
        assertTrue(blockHit.contains("addValues(settings.values())"));
    }

    @Test
    public void lagModeNeverBuffersTheVanillaCombatStream() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/combat/BlockHit.java");

        assertFalse(source.contains("BlinkModules.BLOCK_HIT"));
        assertFalse(source.contains("BlinkManager"));
        assertFalse(source.contains("tryAcquire("));
        assertFalse(source.contains("lagBlinking"));
        assertFalse(source.contains("lagReleaseAt"));
    }

    @Test
    public void externalUseOwnersAreVisibleInsteadOfSilentlySharingInput() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/combat/BlockHit.java");

        assertTrue(source.contains("hasExternalUseOwner()"));
        assertTrue(source.contains("\"Paused\""));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static String method(String source, String beginMarker, String endMarker) {
        int begin = source.indexOf(beginMarker);
        int end = source.indexOf(endMarker, begin + beginMarker.length());
        return source.substring(begin, end);
    }
}
