package gq.yozakura.module.world;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ScaffoldLeaderFullPortContractTest {
    private static final String SCAFFOLD =
            "src/main/java/gq/yozakura/module/world/Scaffold.java";

    @Test
    public void exposesSelectedLeaderScaffoldModesAndCoreSettings() throws IOException {
        String source = source();

        assertTrue(source.contains("new String[]{\"Normal\", \"Telly\", \"Snap\"}"));
        assertFalse(source.contains("Legit"));
        assertFalse(source.contains("legit"));
        assertTrue(source.contains("new String[]{\"None\", \"Vanilla\", \"Backwards\", \"Prediction\"}"));
        assertTrue(source.contains("\"jump-delay\""));
        assertTrue(source.contains("\"place-delay\""));
        assertTrue(source.contains("\"start-rotate-speed\""));
        assertTrue(source.contains("\"normal-rotate-speed\""));
        assertFalse(source.contains("\"clutch\""));
        assertFalse(source.contains("\"only-void\""));
        assertFalse(source.contains("clutchActive"));
        assertFalse(source.contains("clutchTickCounter"));
        assertTrue(source.contains("\"edge-threshold\""));
        assertTrue(source.contains("\"ticks-limit\""));
        assertTrue(source.contains("\"speed-limit\""));
    }

    @Test
    public void includesLeaderSnapAndTellyStateMachinesOnly() throws IOException {
        String source = source();

        assertTrue(source.contains("snapForwardTimer"));
        assertTrue(source.contains("snapLocked"));
        assertTrue(source.contains("pendingSpeedLimitRot"));
        assertTrue(source.contains("forwardRotateTicksLeft"));
        assertFalse(source.contains("legitEdgeState"));
        assertFalse(source.contains("legitTellyPhase"));
        assertFalse(source.contains("updateLegitTelly"));
    }

    @Test
    public void portsLeaderSearchPlacementTimingAndScaffoldOwnedMoveFix() throws IOException {
        String source = source();

        assertTrue(source.contains("for (int x = -4; x <= 4; x++)"));
        assertTrue(source.contains("for (int y = -4; y <= 0; y++)"));
        assertTrue(source.contains("for (int z = -4; z <= 4; z++)"));
        assertTrue(source.contains("placeOffsets"));
        assertTrue(source.contains("RotationUtil.rayTrace"));
        assertTrue(source.contains("this.placeDelayCounter = this.placeDelay.getValue()"));
        assertTrue(source.contains("event.setPervRotation(targetYaw, 3, false)"));
        assertTrue(source.contains("MoveUtil.fixStrafe(RotationState.getSmoothedYaw())"));
    }

    @Test
    public void keepsPredictionSamplingAndPlacesWithThePublishedRotation() throws IOException {
        String source = source();
        String update = source.substring(
                source.indexOf("public void onUpdate(UpdateEvent event)"),
                source.indexOf("public void onMoveInput(MoveInputEvent event)"));
        String bridgeAssist = source("src/main/java/gq/yozakura/module/world/BridgeAssist.java");

        assertTrue(source.contains("this.rotationMode.getValue() == 3"));
        assertTrue(source.contains("RotationUtil.getRotations(targetX, targetY, targetZ)"));
        assertTrue(source.contains("Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff)"));
        assertTrue(source.contains("RandomUtil.nextFloat(-0.5F, 0.5F)"));
        assertTrue(source.contains("this.yaw = targetYaw;"));
        assertTrue(source.contains("this.pitch = targetPitch;"));
        assertTrue(update.contains("MovingObjectPosition finalCheck = RotationUtil.rayTrace("));
        assertTrue(update.contains("this.isPlacementRayTrace(finalCheck, blockData)"));
        assertTrue(update.contains("this.place(blockData.blockPos(), blockData.facing(), finalCheck.hitVec)"));
        assertFalse(source.contains("pendingPlacementData"));
        assertFalse(source.contains("onRotationPublished"));
        assertFalse(source.contains("attemptPreviousPublishedPlacement()"));
        assertTrue(bridgeAssist.contains("event.requestOriginalPacketOrder()"));
        assertFalse(source.contains("requestAfterCurrentRotation()"));
        assertFalse(source.contains("isOwnedPlacementActionPacket"));
    }

    @Test
    public void replacesBpsOverlayWithRetainedAnimatedBlockCounter() throws IOException {
        String source = source();

        assertFalse(source.contains("render-bps"));
        assertFalse(source.contains("currentBps"));
        assertFalse(source.contains("5.92"));
        assertTrue(source.contains("countHotbarBlocks"));
        assertTrue(source.contains("blockCounterClock.tick(System.nanoTime())"));
        assertTrue(source.contains("ScaffoldBlockCounterMotion"));
        assertTrue(source.contains("blockCounterMotion.setVisible"));
        assertTrue(source.contains("blockCounterPulse.updateSpring"));
        assertTrue(source.contains("renderItemAndEffectIntoGUI"));
        assertTrue(source.contains("displayedBlockStack"));
        assertTrue(source.contains("GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F)"));
        assertTrue(source.contains("GlStateManager.depthMask(true)"));
        assertTrue(source.contains("FontLoaders.circular(14)"));
        assertTrue(source.contains("float panelScale = motion.getScale()"));
        assertTrue(source.contains("blockCounterBackgroundAlpha"));
        assertTrue(source.contains("RenderServices.blur().glass"));
        assertFalse(source.contains("drawSoftShadowOffset"));
        assertFalse(source.contains("FontLoaders.ICON_CUBE"));
        assertFalse(source.contains("\"BLOCKS\""));
    }

    @Test
    public void keepsRenderingAfterDisableUntilTheExitAnimationSettles() throws IOException {
        String source = source();

        assertTrue(source.contains("BlockCounterExitRenderer"));
        assertTrue(source.contains("EventManager.register(this.blockCounterExitRenderer)"));
        assertTrue(source.contains("EventManager.unregister(this.blockCounterExitRenderer)"));
        assertTrue(source.contains("this.blockCounterMotion.setVisible(false, System.currentTimeMillis())"));
        assertTrue(source.contains("private void clearBlockCounterRetainedState()"));
        assertFalse(source.contains("if (!this.isEnabled() || mc.thePlayer == null)"));
        assertFalse(source.contains("onDisabled() {\n        this.blockCounterMotion.reset()"));
    }

    @Test
    public void keepsOnlyRequiredVapuRuntimeAdapters() throws IOException {
        String source = source();

        assertTrue(source.contains("event.setRotation(targetYaw, targetPitch, 3)"));
        assertFalse(source.contains("event.trySetRotation"));
        assertFalse(source.contains("VisualRotationState.publish(\"Scaffold\""));
        assertFalse(source.contains("event.requestAfterCurrentRotation()"));
        assertFalse(source.contains("PacketAcceptedEvent"));
        assertTrue(source.contains("MinecraftAccessor.syncCurrentPlayItem(mc.playerController)"));
        assertTrue(source.indexOf("MinecraftAccessor.syncCurrentPlayItem(mc.playerController)")
                == source.lastIndexOf("MinecraftAccessor.syncCurrentPlayItem(mc.playerController)"));
        assertTrue(source.indexOf("MinecraftAccessor.syncCurrentPlayItem(mc.playerController)")
                > source.indexOf("public void onDisabled()"));
        assertFalse(source.substring(source.indexOf("private boolean place("),
                source.indexOf("private float getCurrentYaw()")).contains("syncCurrentPlayItem"));
        assertFalse(source.substring(source.indexOf("private void selectScaffoldBlock()"),
                source.indexOf("private boolean isUsableScaffoldStack")).contains("syncCurrentPlayItem"));
        assertFalse(source.contains("ScaffoldSprintPolicy"));
        assertFalse(source.contains("drawSakuraBlockCounter"));
        assertFalse(source.contains("keep-y"));
        assertFalse(source.contains("multiplace"));
    }

    @Test
    public void fullPortDoesNotDependOnBridgeTelly() throws IOException {
        String source = source();

        assertFalse(source.contains("TellyBridgeRuntime"));
        assertFalse(source.contains("TellyBridgeProgram"));
        assertFalse(source.contains("TellyBridgePlacementSearch"));
        assertFalse(source.contains("TellyBridgeRotation"));
    }

    private static String source() throws IOException {
        return new String(Files.readAllBytes(Paths.get(SCAFFOLD)), StandardCharsets.UTF_8);
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
