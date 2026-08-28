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

        assertFalse(prePlaceController.contains("event.setCancelled(true)"));
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
        assertFalse(bridgeAssist.contains("sneakController.onPlacementPacketAccepted(event.getWriteId());"));
        assertFalse(bridgeAssist.contains("sneakController.onPlacementPacketCompleted(event.getWriteId(), event.isSuccess());"));
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
    public void acceptedPlacementsRemainScopedToTellyInsteadOfDrivingLegitSneak() throws IOException {
        String bridgeAssist = source("src/main/java/gq/yozakura/module/world/BridgeAssist.java");
        String sneakController = source("src/main/java/gq/yozakura/module/world/BridgeAssistSneakController.java");
        int acceptedBegin = bridgeAssist.indexOf("public void onPacketAccepted(PacketAcceptedEvent event)");
        int acceptedEnd = bridgeAssist.indexOf("public void onPacketWritten(PacketWriteEvent event)", acceptedBegin);
        String acceptedHandler = bridgeAssist.substring(acceptedBegin, acceptedEnd);

        assertTrue(acceptedHandler.contains("if (!getState() || !canPreservePlacementOrder())"));
        assertTrue(acceptedHandler.contains("event.requestOriginalPacketOrder();"));
        assertTrue(acceptedHandler.contains("tellyRuntime.onPacketAccepted(event);"));
        assertFalse(acceptedHandler.contains("sneakController.onPlacementPacketAccepted"));
        assertFalse(sneakController.contains("PlacementSessionTracker"));
        assertFalse(sneakController.contains("placementPending"));
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

    @Test
    public void modulePresentsTickAccurateBasicSettingsWithoutHidingActiveLegacyOptions() throws IOException {
        String bridgeAssist = source("src/main/java/gq/yozakura/module/world/BridgeAssist.java");

        assertTrue(bridgeAssist.contains("\"Edge Tolerance (blocks)\", \"EdgeOffset\""));
        assertTrue(bridgeAssist.contains("\"Edge Tolerance (blocks)\", \"EdgeOffset\", 0.0D, 0.0D, 0.3D, 0.01D"));
        assertTrue(bridgeAssist.contains("\"Release Delay (ms)\", \"UnsneakDelay\", 50.0D, 10.0D, 300.0D, 1.0D"));
        assertTrue(bridgeAssist.contains("\"Jump Sneak Hold (ms)\", \"SneakOnJump\", 0.0D, 0.0D, 500.0D, 1.0D"));
        assertTrue(bridgeAssist.contains("\"Only With Blocks\", \"HoldingBlocks\""));
        assertTrue(bridgeAssist.contains("\"Advanced Options\", \"AdvancedOptions\""));
        assertTrue(bridgeAssist.contains("visibleWhen(this::showAdvancedOptions)"));
        assertTrue(bridgeAssist.contains("Boolean.TRUE.equals(prePlace.getValue())"));
        assertTrue(bridgeAssist.contains("sneakOnJump.getValue() > 0.0D"));
    }

    @Test
    public void moduleYieldsControlDuringSpecialMovementPhysics() throws IOException {
        String bridgeAssist = source("src/main/java/gq/yozakura/module/world/BridgeAssist.java");

        assertTrue(bridgeAssist.contains("!mc.thePlayer.isInWater()"));
        assertTrue(bridgeAssist.contains("!mc.thePlayer.isInLava()"));
        assertTrue(bridgeAssist.contains("!mc.thePlayer.isOnLadder()"));
        assertTrue(bridgeAssist.contains("!mc.thePlayer.isRiding()"));
        assertTrue(bridgeAssist.contains("!isInsideWeb()"));
        assertTrue(bridgeAssist.contains("Blocks.web"));
    }

    @Test
    public void sneakControllerNeverKeepsAnInvalidConditionAliveForAPendingPlacement() throws IOException {
        String sneakController = source("src/main/java/gq/yozakura/module/world/BridgeAssistSneakController.java");

        assertTrue(sneakController.contains("if (shouldClearSneak(forward)) {"));
        assertFalse(sneakController.contains("shouldClearSneak(forward) && !activePlacementPending"));
    }

    @Test
    public void legitModeUsesTheReferenceMotionBoxProbeWithoutPlacementDrivenSneaking() throws IOException {
        String sneakController = source("src/main/java/gq/yozakura/module/world/BridgeAssistSneakController.java");

        assertTrue(sneakController.contains("forward > 0.0F"));
        assertTrue(sneakController.contains("contract(0.2D, 0.0D, 0.2D)"));
        assertTrue(sneakController.contains("offset(mc.thePlayer.motionX, -1.0D, mc.thePlayer.motionZ)"));
        assertFalse(sneakController.contains("calculateInputMotion("));
        assertFalse(sneakController.contains("placementSessions"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
