package gq.yozakura.module.combat;

import gq.yozakura.value.properties.ModeProperty;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KillAuraAutoBlockLifecycleContractTest {
    @Test
    public void fullAbIsRestoredWhileLegitRemainsConfigCompatibleAndHidden() throws IOException {
        String aura = source();
        String modern = source("src/main/java/gq/yozakura/ui/click/web/ModernWebClickGuiController.java");

        assertTrue(aura.contains("private static final int AUTOBLOCK_LEGIT = 5;"));
        assertTrue(aura.contains("private static final int AUTOBLOCK_FULL_AB = 6;"));
        assertTrue(aura.contains("private static final int AUTOBLOCK_BYPASS_ALL = 7;"));
        assertTrue(aura.contains("private static final int AUTOBLOCK_HYPIXEL = 8;"));
        assertTrue(aura.contains("\"BLINK\", \"LEGIT\", \"FullAB\", \"BypassAll\", \"Hypixel\""));
        assertTrue(aura.contains("new String[]{\"None\", \"RELEASE\", \"INTERACT\", \"SWITCH\", \"BLINK\", \"FullAB\", \"BypassAll\", \"Hypixel\"}"));
        assertTrue(aura.contains(".addStoredAlias(\"Hypixel(Without NoSlow)\", AUTOBLOCK_FULL_AB)"));
        assertTrue(aura.contains(".addStoredAlias(\"HypixelLag\", AUTOBLOCK_HYPIXEL)"));
        assertTrue(modern.contains(".mode(\"AutoBlock\", \"None\", \"None\", \"RELEASE\", \"INTERACT\", \"SWITCH\", \"BLINK\", \"FullAB\", \"BypassAll\", \"Hypixel\")"));
        assertFalse(modern.contains(".mode(\"AutoBlock\", \"None\", \"None\", \"RELEASE\", \"INTERACT\", \"SWITCH\", \"BLINK\", \"LEGIT\""));
        assertFalse(modern.contains(".mode(\"AutoBlock\", \"None\", \"None\", \"RELEASE\", \"INTERACT\", \"SWITCH\", \"BLINK\", \"Hypixel(Without NoSlow)\""));

        String modeProperty = source("src/main/java/gq/yozakura/value/properties/ModeProperty.java");
        String fileManager = source("src/main/java/gq/yozakura/manager/FileManager.java");
        assertTrue(modeProperty.contains("private final String[] selectableModes;"));
        assertTrue(modeProperty.contains("public void setStoredMode(String mode)"));
        assertTrue(modeProperty.contains("public void setStoredNumberValue(double value)"));
        assertTrue(fileManager.contains("setStoredMode(element.getAsString())"));
        assertTrue(fileManager.contains("setStoredNumberValue(element.getAsDouble())"));
    }

    @Test
    public void fullAbIsSelectableAndLegacyReferenceNamesRemainLoadable() {
        ModeProperty autoBlock = new ModeProperty(
                "AutoBlock", 0,
                new String[]{"None", "RELEASE", "INTERACT", "SWITCH", "BLINK", "LEGIT", "FullAB", "BypassAll", "Hypixel"},
                new String[]{"None", "RELEASE", "INTERACT", "SWITCH", "BLINK", "FullAB", "BypassAll", "Hypixel"})
                .addStoredAlias("Hypixel(Without NoSlow)", 6)
                .addStoredAlias("HypixelLag", 8);

        assertEquals(8, autoBlock.getModes().length);
        autoBlock.setMode("LEGIT");
        assertEquals(Integer.valueOf(0), autoBlock.getValue());
        autoBlock.setNumberValue(8.0D);
        assertEquals(Integer.valueOf(0), autoBlock.getValue());

        autoBlock.setStoredMode("LEGIT");
        assertEquals(Integer.valueOf(5), autoBlock.getValue());
        assertEquals("LEGIT", autoBlock.getModeString());
        autoBlock.setMode("FullAB");
        assertEquals(Integer.valueOf(6), autoBlock.getValue());
        assertEquals("FullAB", autoBlock.getModeString());
        autoBlock.setStoredMode("Hypixel(Without NoSlow)");
        assertEquals(Integer.valueOf(6), autoBlock.getValue());
        assertEquals("FullAB", autoBlock.getModeString());
        autoBlock.setStoredNumberValue(6.0D);
        assertEquals(Integer.valueOf(6), autoBlock.getValue());
        assertEquals("FullAB", autoBlock.getModeString());

        autoBlock.setMode("BypassAll");
        assertEquals(Integer.valueOf(7), autoBlock.getValue());
        assertEquals("BypassAll", autoBlock.getModeString());
        autoBlock.setMode("Hypixel");
        assertEquals(Integer.valueOf(8), autoBlock.getValue());
        assertEquals("Hypixel", autoBlock.getModeString());
        autoBlock.setStoredMode("HypixelLag");
        assertEquals(Integer.valueOf(8), autoBlock.getValue());
        assertEquals("Hypixel", autoBlock.getModeString());
    }

    @Test
    public void hypixelAnimationModeOnlyForcesLocalPoseAndKeepsNormalAttackPath() throws IOException {
        String aura = source();
        String update = method(aura, "    public void onUpdate(UpdateEvent event) {",
                "    private float[] calculateTargetRotations");
        String animationUpdate = method(aura, "    private boolean updateHypixelAnimationMode(",
                "    private boolean applyAutoBlockAction(");
        String isBlocking = method(aura, "    public boolean isBlocking() {",
                "    public boolean isPlayerBlocking()");
        String isPlayerBlocking = method(aura, "    public boolean isPlayerBlocking() {",
                "    private boolean shouldCancelBlockInput()");
        String renderStart = method(aura, "    public void onRenderTickStart(RenderTickStartEvent event) {",
                "    @EventTarget(Priority.LOWEST)\n    public void onRenderTickEnd");
        String renderEnd = method(aura, "    public void onRenderTickEnd(RenderTickEndEvent event) {",
                "    @EventTarget(Priority.LOW)\n    public void onUpdate");

        assertTrue(update.contains("autoBlockMode == AUTOBLOCK_BYPASS_ALL"));
        assertTrue(update.contains("this.updateHypixelAnimationMode(shouldBlock, attackReady, yaw, pitch)"));
        assertTrue(animationUpdate.contains("this.hypixelAnimationBlockPose = shouldBlock;"));
        assertTrue(animationUpdate.contains("return attackReady && this.performAttack(yaw, pitch);"));
        assertFalse(animationUpdate.contains("this.startBlock()"));
        assertFalse(animationUpdate.contains("this.stopBlock()"));
        assertFalse(animationUpdate.contains("this.interactBlockAfterAttack("));
        assertFalse(animationUpdate.contains("this.startReferenceBlink()"));
        assertFalse(animationUpdate.contains("this.spoofSlot("));
        assertFalse(animationUpdate.contains("this.autoBlockController.update("));
        assertTrue(isBlocking.contains("this.hypixelAnimationBlockPose"));
        assertFalse(isPlayerBlocking.contains("this.hypixelAnimationBlockPose"));
        assertTrue(renderStart.contains("this.hypixelAnimationBlockPose"));
        assertTrue(renderStart.contains("this.hypixelAnimationRenderPose.begin(mc.thePlayer);"));
        assertTrue(renderEnd.contains("this.hypixelAnimationRenderPose.end();"));
        assertTrue(aura.contains("private final BlockHitRenderPose hypixelAnimationRenderPose ="));
        assertTrue(aura.contains("this.hypixelAnimationBlockPose = false;"));
        assertFalse(renderStart.contains("this.startBlock()"));
        assertFalse(renderStart.contains("PacketUtil.sendPacket"));
    }

    @Test
    public void leaderLegitUsesSyntheticAttackInteractBlockAndReleaseCycle() throws IOException {
        String aura = source();
        String update = method(aura, "    public void onUpdate(UpdateEvent event) {",
                "    private float[] calculateTargetRotations");
        String legitCycle = method(aura, "    private boolean updateLeaderLegitAutoBlockCycle(",
                "    private boolean updateHypixelWithoutNoSlowCycle(");
        String interact = method(aura, "    private boolean interactBlockAfterAttack(",
                "    private boolean updateLeaderLegitAutoBlockCycle(");

        assertTrue(update.contains("this.updateLeaderLegitAutoBlockCycle("));
        assertFalse(update.contains("this.autoBlockController.updateLegit("));
        assertTrue(legitCycle.contains("KillAuraLeaderAutoBlockCycle.LegitStep"));
        assertTrue(legitCycle.contains("case ATTACK_AND_BLOCK:"));
        assertTrue(legitCycle.contains("this.performAttack(yaw, pitch)"));
        assertTrue(legitCycle.contains("this.interactBlockAfterAttack(yaw, pitch)"));
        assertTrue(legitCycle.contains("case RELEASE_AND_WAIT:"));
        assertTrue(legitCycle.contains("this.stopBlock()"));
        assertTrue(interact.contains("new C02PacketUseEntity("));
        assertTrue(interact.contains("new Vec3("));
        assertTrue(interact.contains("Action.INTERACT"));
        assertTrue(interact.contains("this.startBlock()"));
        assertFalse(aura.contains("physicalBlockActive"));
    }

    @Test
    public void hypixelWithoutNoSlowUsesReferenceThreePhaseBlinkCycle() throws IOException {
        String aura = source();
        String cycle = method(aura, "    private boolean updateHypixelWithoutNoSlowCycle(",
                "    private boolean applyAutoBlockAction(");

        assertTrue(cycle.contains("KillAuraLeaderAutoBlockCycle.HypixelStep"));
        assertTrue(cycle.contains("this.hypixelAnimationBlockPose = shouldBlock;"));
        assertTrue(cycle.contains("case FLUSH_ATTACK_AND_BLOCK:"));
        assertTrue(cycle.contains("this.stopReferenceBlink();"));
        assertTrue(cycle.contains("this.interactBlockAfterAttack(yaw, pitch)"));
        assertTrue(cycle.contains("case WAIT:"));
        assertTrue(cycle.contains("case BLINK_RELEASE_AND_ATTACK:"));
        assertTrue(cycle.indexOf("this.startReferenceBlink();")
                < cycle.indexOf("this.stopBlock();"));
        assertTrue(cycle.contains("this.performAttack(yaw, pitch, true)"));
        assertFalse("Hypixel(Without NoSlow) must not depend on a NoSlow module",
                aura.contains("module.modules.movement.NoSlow") || aura.contains("getModule(NoSlow.class)"));
        assertFalse(aura.contains("lagReleaseBoundary"));
    }

    @Test
    public void hypixelLagMirrorsLeaderLiteBlockReleaseAndBlinkBoundaries() throws IOException {
        String aura = source();
        String cycle = method(aura, "    private boolean updateHypixelLagCycle(",
                "    private boolean updateHypixelAnimationMode(");
        String blink = source("src/main/java/gq/yozakura/manager/BlinkManager.java");

        assertTrue(aura.contains("private static final int AUTOBLOCK_HYPIXEL = 8;"));
        assertTrue(cycle.contains("KillAuraLeaderAutoBlockCycle.HypixelLagStep"));
        assertTrue(cycle.contains("this.hypixelAnimationBlockPose = shouldBlock;"));
        assertTrue(cycle.contains("case ATTACK_AND_BLOCK:"));
        assertTrue(cycle.contains("this.performAttack(yaw, pitch)"));
        assertTrue(cycle.contains("this.interactBlockAfterAttack(yaw, pitch)"));
        assertTrue(cycle.indexOf("this.stopReferenceBlink();") < cycle.indexOf("this.startReferenceBlink();"));
        assertTrue(cycle.contains("case RELEASE_AND_SUPPRESS_ATTACK:"));
        assertFalse("The release phase must not inject held-item changes unrelated to the local hotbar",
                cycle.contains("sendHypixelLagReleaseSwapSequence"));
        assertTrue(cycle.contains("this.stopBlock();"));
        assertTrue(cycle.contains("case FLUSH_AND_WAIT:"));
        assertTrue(cycle.contains("this.remainingAttackDelay(now)"));
        assertTrue(cycle.contains("case RESET:"));
        assertTrue(cycle.contains("this.stopReferenceBlink();"));

        assertTrue(blink.contains("private boolean slowReleasing"));
        assertTrue(blink.contains("private void processSlowRelease()"));
        assertTrue(blink.contains("private void flushRemaining()"));
        assertTrue(blink.contains("packet instanceof C00PacketKeepAlive || packet instanceof C01PacketChatMessage"));
        assertTrue(blink.contains("this.blinkedPackets.isEmpty() && packet instanceof C0FPacketConfirmTransaction"));
        assertTrue("slow release must keep its current module ownership",
                blink.contains("if ((blinking || slowReleasing) && blinkModule != module)"));
        assertTrue("death cleanup must bypass configurable slow release",
                blink.contains("this.forceStopAndFlush(this.getBlinkingModule());"));
    }

    @Test
    public void referenceModesUseVanillaControllerForSyntheticUseAndRelease() throws IOException {
        String aura = source();
        String startBlock = method(aura, "    private boolean startBlock() {",
                "    private boolean stopBlock()");
        String stopBlock = method(aura, "    private boolean stopBlock() {",
                "    private void releaseAutoBlock");

        assertTrue(startBlock.contains("mc.playerController.sendUseItem("));
        assertFalse(startBlock.contains("isLegitAutoBlockMode()"));
        assertFalse(startBlock.contains("syncCurrentPlayItem"));
        assertTrue(stopBlock.contains("mc.playerController.onStoppedUsingItem(mc.thePlayer);"));
        assertFalse(stopBlock.contains("activeAutoBlockMode == AUTOBLOCK_LEGIT"));
    }

    @Test
    public void combatTimersUseOneMonotonicClock() throws IOException {
        String aura = source();

        assertTrue(aura.contains("private static long monotonicTimeMillis()"));
        assertTrue(aura.contains("private long remainingAttackDelay(long now)"));
        assertFalse(aura.contains("System.currentTimeMillis()"));
    }

    private static String source() throws IOException {
        return source("src/main/java/gq/yozakura/module/combat/KillAura.java");
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }

    private static String method(String source, String beginMarker, String endMarker) {
        int begin = source.indexOf(beginMarker);
        int end = source.indexOf(endMarker, begin + beginMarker.length());
        assertTrue("missing begin marker: " + beginMarker, begin >= 0);
        assertTrue("missing end marker: " + endMarker, end > begin);
        return source.substring(begin, end);
    }
}
