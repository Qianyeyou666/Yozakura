package gq.yozakura.module.combat;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BlockHitHelperIntegrationContractTest {
    @Test
    public void helperIsAppendedAfterHypixelWithoutChangingExistingModeIndexes() throws IOException {
        String settings = source("src/main/java/gq/yozakura/module/combat/BlockHitSettings.java");
        String blockHit = source("src/main/java/gq/yozakura/module/combat/BlockHit.java");

        assertTrue(settings.contains(
                "\"Manual\", \"Predict\", \"Auto\", \"Lag\", \"Hypixel\", \"noprehyp\""));
        assertTrue(settings.contains(
                "\"Stop Ticks\", 2, 1, 5, () -> mode.getValue() == 4 || mode.getValue() == 5"));
        assertTrue(settings.contains(
                "\"Threat Range\", 3.6F, 2.0F, 6.0F, () -> mode.getValue() == 4"));
        assertTrue(settings.contains(
                "\"Threat Angle\", 65, 15, 180, () -> mode.getValue() == 4"));
        assertTrue(blockHit.contains("private static final int MODE_LAG = 3;"));
        assertTrue(blockHit.contains("private static final int MODE_HYPIXEL = 4;"));
        assertTrue(blockHit.contains("private static final int MODE_NO_PRE_HYP = 5;"));
    }

    @Test
    public void helperOwnsItsNarrowInputMutationBoundary() throws IOException {
        String blockHit = source("src/main/java/gq/yozakura/module/combat/BlockHit.java");
        String helperInput = source("src/main/java/gq/yozakura/module/combat/BlockHitHelperInput.java");
        String vanillaUse = source("src/main/java/gq/yozakura/module/combat/BlockHitVanillaUseAction.java");

        assertTrue(blockHit.contains("helperInput.holdUse()"));
        assertTrue(blockHit.contains("helperInput.suppressUse()"));
        assertTrue(blockHit.contains("helperInput.pressAttackOnce()"));
        assertTrue(blockHit.contains("helperInput.releaseOwnedUse()"));
        assertTrue(blockHit.contains("armHelperFirstAttackWarmUp()"));
        assertTrue(blockHit.contains("helperController.armFirstAttackWarmUp()"));
        assertFalse(blockHit.contains("KeyBinding.setKeyBindState"));
        assertTrue(helperInput.contains("KeyBindUtil.setKeyBindState"));
        String holdUse = method(helperInput, "void holdUse()", "void suppressUse()");
        assertTrue(holdUse.contains("startHeldItemUse()"));
        assertFalse(holdUse.contains("KeyBindUtil.pressKeyOnce"));
        assertTrue(helperInput.contains("mc.playerController.sendUseItem("));
        String pressAttack = method(helperInput, "void pressAttackOnce()", "void releaseOwnedUse()");
        assertTrue(pressAttack.contains("KeyBindUtil.pressKeyOnce(binding.getKeyCode())"));
        assertTrue(helperInput.contains("KeyBindUtil.updateKeyState"));
        assertFalse(helperInput.contains("onStoppedUsingItem"));
        String hypixelHandler = method(
                blockHit, "private void handleHypixelTick()", "private void handleHelperTick()");
        String helperHandler = method(
                blockHit, "private void handleHelperTick()", "private void handleHelperTick(boolean");
        String sharedHandler = method(
                blockHit, "private void handleHelperTick(boolean", "private void applyHelperAction(");
        assertTrue(hypixelHandler.contains("helperThreatScanner.hasThreat("));
        assertTrue(hypixelHandler.contains("handleHelperTick(threatPredicted)"));
        assertTrue(helperHandler.contains("handleHelperTick(isPhysicalAttackDown())"));
        assertFalse(helperHandler.contains("helperThreatScanner"));
        assertFalse(sharedHandler.contains("isPhysicalUseDown()"));
        assertTrue(sharedHandler.contains("hasExternalUseOwner() || !activationAllowed"));
        assertTrue(sharedHandler.contains("stopHelper(true)"));
        assertTrue(sharedHandler.contains("attackDown, activationAllowed"));
        assertFalse(blockHit.contains("BlockHitNoPreHypGate"));
        assertFalse(blockHit.contains("noPreHypGate"));
        assertFalse(vanillaUse.contains("KeyBinding.setKeyBindState"));
    }

    @Test
    public void forcedPoseIsBoundedToTheSharedWorldRenderFrame() throws IOException {
        String blockHit = source("src/main/java/gq/yozakura/module/combat/BlockHit.java");
        String pose = source("src/main/java/gq/yozakura/module/combat/BlockHitRenderPose.java");
        String forgeBridge = source("src/main/java/gq/yozakura/bridge/YozakuraEventBridge.java");
        String standalone = source("src/main/java/gq/yozakura/bridge/StandaloneEntityRenderer.java");

        assertTrue(blockHit.contains("onRenderTickStart(RenderTickStartEvent event)"));
        assertTrue(blockHit.contains("onRenderTickEnd(RenderTickEndEvent event)"));
        assertTrue(blockHit.contains("renderPose.begin(mc.thePlayer)"));
        assertTrue(blockHit.contains("renderPose.end()"));
        assertTrue(pose.contains("ITEM_IN_USE.set(currentPlayer, heldItem)"));
        assertTrue(pose.contains("ITEM_IN_USE_COUNT.setInt(currentPlayer, heldItem.getMaxItemUseDuration())"));
        assertTrue(pose.contains("ITEM_IN_USE.set(posePlayer, savedItem)"));
        assertTrue(pose.contains("ITEM_IN_USE_COUNT.setInt(posePlayer, savedCount)"));
        assertTrue(pose.contains("originalItemInUse"));
        assertTrue(pose.contains("originalItemInUseCount"));
        assertFalse(pose.contains("sendUseItem"));
        assertFalse(pose.contains("onStoppedUsingItem"));
        assertFalse(pose.contains("setItemInUse("));
        assertFalse(pose.contains("clearItemInUse("));
        assertTrue(forgeBridge.contains("new RenderTickEndEvent(event.renderTickTime)"));
        assertTrue(standalone.contains("EventManager.call(new RenderTickEndEvent(partialTicks))"));
        assertTrue(standalone.contains("abortRuntimeRenderWorld()"));
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
