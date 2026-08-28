package gq.yozakura.module.combat;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KillAuraRewriteContractTest {
    @Test
    public void killAuraDelegatesAutoBlockLifecycleToOneController() throws IOException {
        String aura = source("src/main/java/gq/yozakura/module/combat/KillAura.java");

        assertTrue(aura.contains("private final KillAuraAutoBlockController autoBlockController"));
        assertTrue(aura.contains("autoBlockController.update("));
        assertTrue(aura.contains("autoBlockController.onBlockStarted()"));
        assertTrue(aura.contains("autoBlockController.onBlockStopped()"));
        assertTrue(aura.contains("autoBlockController.onAttackResult("));
        assertFalse(aura.contains("private boolean blockingState"));
        assertFalse(aura.contains("private boolean isBlocking ="));
        assertFalse(aura.contains("private boolean fakeBlockState"));
        assertFalse(aura.contains("private int autoBlockState"));
        assertFalse(aura.contains("KillAuraLagAutoBlockController"));
    }

    @Test
    public void killAuraOwnsARealVanillaSwordUseLifecycle() throws IOException {
        String aura = source("src/main/java/gq/yozakura/module/combat/KillAura.java");

        assertTrue(aura.contains("mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, itemStack)"));
        assertTrue(aura.contains("mc.playerController.onStoppedUsingItem(mc.thePlayer)"));
        assertFalse("the rewrite must not hand-construct sword use packets",
                aura.contains("new C08PacketPlayerBlockPlacement(itemStack)"));
        assertFalse("the rewrite must not hand-construct release packets",
                aura.contains("new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM"));
    }

    @Test
    public void ownedBlockPacketsDriveTheLifecycleOnlyAfterBridgeAcceptance() throws IOException {
        String aura = source("src/main/java/gq/yozakura/module/combat/KillAura.java");
        String packet = method(aura, "    public void onPacket(PacketEvent event) {",
                "    public void onPacketAccepted(PacketAcceptedEvent event) {");
        String accepted = method(aura, "    public void onPacketAccepted(PacketAcceptedEvent event) {",
                "    public void onPacketWritten(PacketWriteEvent event) {");
        String written = method(aura, "    public void onPacketWritten(PacketWriteEvent event) {",
                "    private static boolean isUseItemPacket");

        assertTrue(packet.contains("event.isCancelled()"));
        assertTrue(packet.contains("this.autoBlockController.onBlockStartFailed()"));
        assertTrue(packet.contains("this.autoBlockController.onBlockStopFailed()"));
        assertTrue(accepted.contains("this.autoBlockController.onBlockStarted()"));
        assertTrue(accepted.contains("this.autoBlockController.onBlockStopped()"));
        assertTrue(written.contains("this.autoBlockController.onBlockWriteFailed()"));
        assertTrue(written.contains("this.autoBlockController.onReleaseWriteFailed()"));
        assertTrue("mode switches must release any synthetic reference-mode block owner",
                aura.contains("this.releaseAutoBlock(true)"));
    }

    @Test
    public void blockRangeAndAttackRangeAreIndependent() throws IOException {
        String aura = source("src/main/java/gq/yozakura/module/combat/KillAura.java");
        String update = method(aura, "    public void onUpdate(UpdateEvent event) {",
                "    private float[] calculateTargetRotations");

        assertTrue(update.contains("boolean targetPresent = this.attackTarget != null && this.hasValidTarget();"));
        assertTrue(update.contains("boolean shouldBlock = targetPresent && this.canAutoBlock()"));
        assertTrue(update.contains("this.isBoxInBlockRange(this.attackTarget.getBox())"));
        assertTrue(update.contains("boolean canAttackTarget = targetPresent && this.canAttack()"));
        assertFalse(update.contains("boolean block = attack && this.canAutoBlock()"));
    }

    @Test
    public void transientPacketStateClassifiesOnlyActualDigAndPlacementActions() throws IOException {
        String state = source("src/main/java/gq/yozakura/manager/PlayerStateManager.java");

        assertTrue(state.contains("digging.getStatus() != C07PacketPlayerDigging.Action.RELEASE_USE_ITEM"));
        assertTrue(state.contains("placement.getPlacedBlockDirection() != 255"));
    }

    @Test
    public void standaloneClearsPriorPacketActionsBeforeMovementPreListenersRun() throws IOException {
        String bridge = source("src/main/java/gq/yozakura/bridge/StandaloneEventBridge.java");
        String pre = method(bridge, "    private void dispatchPreUpdate() {",
                "    private void dispatchPreUpdateBeforePlayerPacket() {");

        int reset = pre.indexOf("resetTransientPacketState();");
        int dispatch = pre.indexOf("EventManager.call(update);");
        assertTrue(reset >= 0 && dispatch > reset);
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }

    private static String method(String source, String start, String end) {
        int begin = source.indexOf(start);
        int finish = source.indexOf(end, begin + start.length());
        assertTrue(begin >= 0 && finish > begin);
        return source.substring(begin, finish);
    }
}
