package gq.yozakura.module.world;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ScaffoldLeaderTellyContractTest {
    private static final String SCAFFOLD =
            "src/main/java/gq/yozakura/module/world/Scaffold.java";

    @Test
    public void tellyUsesLeaderSettingsAndTimingState() throws IOException {
        String source = source();

        assertTrue(source.contains("\"jump-delay\""));
        assertTrue(source.contains("\"place-delay\""));
        assertTrue(source.contains("\"start-rotate-speed\""));
        assertTrue(source.contains("\"normal-rotate-speed\""));
        assertTrue(source.contains("tellyJumpDelayTimer"));
        assertTrue(source.contains("jumpDelayOverride"));
        assertTrue(source.contains("wasInAir"));
        assertTrue(source.contains("placeDelayCounter"));
    }

    @Test
    public void tellyUsesLeaderSearchAndSixteenPointSampling() throws IOException {
        String source = source();

        assertTrue(source.contains("for (int x = -4; x <= 4; x++)"));
        assertTrue(source.contains("for (int y = -4; y <= 0; y++)"));
        assertTrue(source.contains("for (int z = -4; z <= 4; z++)"));
        assertTrue(source.contains("BlockUtil.isInteractable(pos)"));
        assertTrue(source.contains("Comparator.comparingDouble"));
        assertTrue(source.contains("0.03125D"));
        assertTrue(source.contains("0.96875D"));
        assertTrue(source.contains("RotationUtil.rayTrace"));
    }

    @Test
    public void scaffoldUsesLeaderMoveFixAndPlacesWithSameTickRotation() throws IOException {
        String source = source();

        assertTrue(source.contains("event.setRotation(targetYaw, targetPitch, 3)"));
        assertTrue(source.contains("event.setPervRotation(targetYaw, 3, false)"));
        assertTrue(source.contains("MoveUtil.fixStrafe(RotationState.getSmoothedYaw())"));
        assertTrue(source.contains("RotationState.getPriority() == 3.0F"));
        assertFalse(source.contains("event.trySetRotation"));
        assertFalse(source.contains("event.setPervRotation(targetYaw, 3);"));
        assertTrue(source.contains("this.yaw = targetYaw;"));
        assertTrue(source.contains("this.pitch = targetPitch;"));
        assertTrue(source.contains("MovingObjectPosition finalCheck = RotationUtil.rayTrace("));
        assertTrue(source.contains("this.isPlacementRayTrace(finalCheck, blockData)"));
        assertTrue(source.contains("this.place(blockData.blockPos(), blockData.facing(), finalCheck.hitVec)"));
        assertFalse(source.contains("pendingPlacementData"));
        assertFalse(source.contains("onRotationPublished"));
        assertFalse(source.contains("attemptPreviousPublishedPlacement()"));
        assertFalse(source.contains("onPacketAccepted(PacketAcceptedEvent event)"));
        assertFalse(source.contains("requestAfterCurrentRotation()"));
        assertTrue(source.contains("mc.playerController.onPlayerRightClick("));
        assertTrue(source.indexOf("MinecraftAccessor.syncCurrentPlayItem(mc.playerController)")
                == source.lastIndexOf("MinecraftAccessor.syncCurrentPlayItem(mc.playerController)"));
    }

    @Test
    public void placesSameTickAndKeepsLeaderFallbackPlacement() throws IOException {
        String source = source();
        int updateStart = source.indexOf("public void onUpdate(UpdateEvent event)");
        int moveInputStart = source.indexOf("public void onMoveInput(MoveInputEvent event)", updateStart);

        assertTrue(updateStart >= 0 && moveInputStart > updateStart);
        String update = source.substring(updateStart, moveInputStart);
        assertTrue(update.contains("MovingObjectPosition finalCheck = RotationUtil.rayTrace("));
        assertTrue(update.contains("this.isPlacementRayTrace(finalCheck, blockData)"));
        assertTrue(update.contains("this.place(blockData.blockPos(), blockData.facing(), finalCheck.hitVec)"));
        assertTrue(update.contains("else if (this.canRotate)"));
        assertTrue(update.contains("this.place(blockData.blockPos(), blockData.facing(), hitVec)"));
        assertFalse(update.contains("pendingPlacementData"));
        assertFalse(update.contains("attemptPreviousPublishedPlacement()"));
        assertFalse(source.contains("data.hitVec()"));
        assertFalse(source.contains("new BlockData(blockData.blockPos(), blockData.facing(), hitVec)"));
    }

    @Test
    public void replacementRemovesOldScaffoldFeaturesAndBridgeTellyDependencies() throws IOException {
        String source = source();

        assertFalse(source.contains("drawSakuraBlockCounter"));
        assertFalse(source.contains("ScaffoldSprintPolicy"));
        assertFalse(source.contains("keep-y"));
        assertFalse(source.contains("multiplace"));
        assertFalse(source.contains("TellyBridgeRuntime"));
        assertFalse(source.contains("TellyBridgeProgram"));
        assertFalse(source.contains("TellyBridgePlacementSearch"));
        assertFalse(source.contains("TellyBridgeRotation"));
    }

    @Test
    public void replacementKeepsOnlyNormalTellyAndSnap() throws IOException {
        String source = source();

        assertTrue(source.contains("new String[]{\"Normal\", \"Telly\", \"Snap\"}"));
        assertFalse(source.contains("Legit"));
        assertFalse(source.contains("legit"));
    }

    private static String source() throws IOException {
        return new String(Files.readAllBytes(Paths.get(SCAFFOLD)), StandardCharsets.UTF_8);
    }
}
