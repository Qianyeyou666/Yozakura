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
 * <p>Sneak decisions must remain driven by the real movement input event,
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
        assertTrue(bridgeAssist.contains("sneakController.onMoveInput()"));
        assertTrue(bridgeAssist.contains("prePlaceController.onUpdate(event)"));
        assertTrue(sneakController.contains("MovementInput input"));
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
        assertTrue(bridgeAssist.contains("sneakController.onPlacementPacket();"));
        assertTrue(bridgeAssist.contains("prePlaceController.onRightClick();"));
        assertTrue(sneakController.contains("input.moveForward"));
        assertTrue(sneakController.contains("input.jump"));
        assertFalse(sneakController.contains("keyBindForward"));
        assertFalse(sneakController.contains("keyBindJump"));
        assertTrue(prePlaceController.contains("VisualRotationState.publish"));
        assertTrue(prePlaceController.contains("VisualRotationState.clearSource"));
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
