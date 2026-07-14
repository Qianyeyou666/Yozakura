package gq.yozakura.module.world;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Guards the event boundary of BridgeAssist.
 *
 * <p>Sneak decisions must remain driven by the pre-legacy input frame,
 * while pre-place rotation remains a PRE-update concern. Neither controller
 * may reintroduce direct block placement or right-click cancellation.</p>
 */
public class BridgeAssistDelegationContractTest {
    @Test
    public void bridgeAssistDelegatesSneakAndPrePlaceToSeparateControllers() throws IOException {
        String bridgeAssist = source("src/main/java/gq/yozakura/module/world/BridgeAssist.java");
        String sneakController = source("src/main/java/gq/yozakura/module/world/BridgeAssistSneakController.java");
        String prePlaceController = source("src/main/java/gq/yozakura/module/world/BridgeAssistPrePlaceController.java");

        assertTrue(bridgeAssist.contains("BridgeAssistSneakController"));
        assertTrue(bridgeAssist.contains("BridgeAssistPrePlaceController"));
        assertTrue(bridgeAssist.contains("SneakInputEvent"));
        assertTrue(bridgeAssist.contains("sneakController.onSneakInput(event)"));
        assertTrue(bridgeAssist.contains("prePlaceController.onUpdate(event)"));
        assertTrue(sneakController.contains("SneakInputEvent event"));
        assertTrue(prePlaceController.contains("UpdateEvent event"));
    }

    @Test
    public void prePlaceStillUsesTheVanillaRightClickPath() throws IOException {
        String bridgeAssist = source("src/main/java/gq/yozakura/module/world/BridgeAssist.java");
        String prePlaceController = source("src/main/java/gq/yozakura/module/world/BridgeAssistPrePlaceController.java");

        assertFalse(bridgeAssist.contains("event.setCancelled(true)"));
        assertFalse(prePlaceController.contains("onPlayerRightClick("));
        assertFalse(prePlaceController.contains("MinecraftAccessor"));
    }

    @Test
    public void controllerSplitPreservesTheCurrentInputAndRotationBoundaries() throws IOException {
        String bridgeAssist = source("src/main/java/gq/yozakura/module/world/BridgeAssist.java");
        String sneakController = source("src/main/java/gq/yozakura/module/world/BridgeAssistSneakController.java");
        String prePlaceController = source("src/main/java/gq/yozakura/module/world/BridgeAssistPrePlaceController.java");

        assertTrue(bridgeAssist.contains("event.getType() != EventType.PRE"));
        assertTrue(bridgeAssist.contains("packet.getPlacedBlockDirection() != 255"));
        assertTrue(bridgeAssist.contains("sneakController.onPlacementPacketAccepted(event.getWriteId());"));
        assertTrue(bridgeAssist.contains("sneakController.onPlacementPacketCompleted(event.getWriteId(), event.isSuccess());"));
        assertTrue(bridgeAssist.contains("prePlaceController.onRightClick();"));
        assertTrue(sneakController.contains("event.getRawForward"));
        assertTrue(sneakController.contains("event.isJump"));
        assertFalse(sneakController.contains("keyBindForward"));
        assertFalse(sneakController.contains("keyBindJump"));
        assertFalse(sneakController.contains("KeyBinding.setKeyBindState"));
        assertFalse(sneakController.contains("Math.random()"));
        assertTrue(prePlaceController.contains("event.trySetRotation"));
        assertTrue(prePlaceController.contains("PreparedTarget"));
        assertFalse(prePlaceController.contains("Math.random()"));
        assertTrue(prePlaceController.contains("VisualRotationState.publish"));
        assertTrue(prePlaceController.contains("VisualRotationState.clearSource"));
    }

    @Test
    public void acceptedPlacementsCanBeClaimedByTheFirstSneakSession() throws IOException {
        String bridgeAssist = source("src/main/java/gq/yozakura/module/world/BridgeAssist.java");
        String sneakController = source("src/main/java/gq/yozakura/module/world/BridgeAssistSneakController.java");
        int acceptedBegin = bridgeAssist.indexOf("public void onPacketAccepted(PacketAcceptedEvent event)");
        int acceptedEnd = bridgeAssist.indexOf("public void onPacketWritten(PacketWriteEvent event)", acceptedBegin);
        String acceptedHandler = bridgeAssist.substring(acceptedBegin, acceptedEnd);

        assertTrue(acceptedHandler.contains("if (!getState() || !canAssist())"));
        assertTrue(sneakController.contains("unclaimedPlacementWriteIds"));
        assertTrue(sneakController.contains("claimUnclaimedPlacementSessions"));
        assertTrue(sneakController.contains("hasPendingPlacementForActiveSession"));
    }

    @Test
    public void moduleKeepsItsPersistedValueIdentifiers() throws IOException {
        String bridgeAssist = source("src/main/java/gq/yozakura/module/world/BridgeAssist.java");

        assertTrue(bridgeAssist.contains("\"PrePlace\""));
        assertTrue(bridgeAssist.contains("\"EdgeOffset\""));
        assertTrue(bridgeAssist.contains("\"UnsneakDelay\""));
        assertTrue(bridgeAssist.contains("\"SneakOnJump\""));
        assertTrue(bridgeAssist.contains("\"SneakKeyPressed\""));
        assertTrue(bridgeAssist.contains("\"HoldingBlocks\""));
        assertTrue(bridgeAssist.contains("\"LookingDown\""));
        assertTrue(bridgeAssist.contains("\"NotMovingForward\""));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
