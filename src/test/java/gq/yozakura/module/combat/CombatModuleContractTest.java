package gq.yozakura.module.combat;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CombatModuleContractTest {
    @Test
    public void autoClickerDoesNotMutateTheUseItemBinding() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/combat/AutoClicker.java");

        assertTrue(source.contains("isMouseButtonDown(0)"));
        assertFalse(source.contains("keyBindUseItem"));
        assertFalse(source.contains("RightClickMouseEvent"));
    }

    @Test
    public void blockHitDoesNotGloballyCancelRightClicks() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/combat/BlockHit.java")
                + source("src/main/java/gq/yozakura/module/combat/BlockHitSettings.java");

        assertTrue(source.contains(
                "\"Manual\", \"Predict\", \"Auto\", \"Lag\", \"Hypixel\", \"noprehyp\""));
        assertFalse(source.contains("RightClickMouseEvent"));
    }

    @Test
    public void autoBlockUsesAnOwnedVanillaSwordUseCycle() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/combat/AutoBlock.java");
        String manager = source("src/main/java/gq/yozakura/manager/ModuleManager.java");

        assertTrue(source.contains("new Numbers<Double>(\"Chance\""));
        assertTrue(source.contains("new Numbers<Double>(\"Distance\""));
        assertTrue(source.contains("new Numbers<Double>(\"Duration (ms)\""));
        assertTrue(source.contains("onClientTick(TickEvent.ClientTickEvent event)"));
        assertTrue(source.contains("onUpdate(UpdateEvent event)"));
        assertTrue(source.contains("mc.playerController.sendUseItem"));
        assertTrue(source.contains("if (!mc.playerController.sendUseItem"));
        assertTrue(source.contains("controller.pressFailed();"));
        assertTrue(source.contains("mc.playerController.onStoppedUsingItem"));
        assertTrue(source.contains("heldItem.getItem() instanceof ItemSword"));
        assertTrue(source.contains("KeyBindUtil.isBindingDown"));
        assertTrue(source.contains("public void onLeftClick(LeftClickMouseEvent event)"));
        assertTrue(source.contains("@EventTarget(Priority.HIGHEST)"));
        assertTrue(source.contains("public void releaseForAttack()"));
        assertFalse("AutoBlock must open the attack window without consuming the click",
                source.contains("event.setCancelled(true)"));
        assertTrue(manager.contains("addModule(\"AutoBlock\""));
        assertFalse(source.contains("KeyBinding.setKeyBindState"));
        assertFalse(source.contains("C02PacketUseEntity"));
        assertFalse(source.contains("C07PacketPlayerDigging"));
        assertFalse(source.contains("C08PacketPlayerBlockPlacement"));
    }

    @Test
    public void killAuraReleasesStandaloneAutoBlockBeforeItsBlockingGuard() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/combat/KillAura.java");
        int begin = source.indexOf("    private boolean performAttack(float yaw, float pitch) {");
        int end = source.indexOf("    private boolean startBlock()", begin);
        String attack = source.substring(begin, end);

        int release = attack.indexOf("releaseStandaloneAutoBlockForAttack()");
        int blockingGuard = attack.indexOf("if (this.autoBlockController.isBlocking()");
        assertTrue("Independent vanilla sword-use must be released before KillAura checks its own block state",
                release >= 0 && blockingGuard > release);
        assertTrue(source.contains("private void releaseStandaloneAutoBlockForAttack()"));
        assertTrue(source.contains("((AutoBlock) module).releaseForAttack();"));
    }

    @Test
    public void velocityPublishesOnlyAttackAndReduceModes() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/combat/Velocity.java");

        assertTrue(source.contains("new String[]{\"Attack\", \"Reduce\"}"));
        assertFalse(source.contains("\"Update\""));
    }

    @Test
    public void velocityAttackUsesOnlyTheExistingAttackPipeline() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/combat/Velocity.java");

        assertTrue(source.contains("public void onAttack(AttackEvent event)"));
        assertFalse(source.contains("new C02PacketUseEntity"));
        assertFalse(source.contains("new C0APacketAnimation"));
    }

    @Test
    public void velocityReducePreservesTheServerVelocityPacket() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/combat/Velocity.java");

        assertFalse(source.contains("scaleVelocityPacket"));
        assertFalse(source.contains("event.setCancelled(true)"));
        assertFalse(source.contains("runOnClientThread"));
        assertTrue(source.contains("\"Compatibility\""));
    }

    @Test
    public void realAttackSlowdownIsAppliedAtTheExistingLocalAttackHook() throws IOException {
        String source = source("src/main/java/gq/yozakura/util/module/PlayerUtil.java");

        assertTrue(source.contains("Velocity.applyAttackSlowdown(target)"));
        assertTrue(source.contains("applyAttackMotion(target, knockbackLevel > 0)"));
    }

    @Test
    public void velocityUsesOneForgeCompatibleAttackEntrypoint() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/combat/Velocity.java");

        assertTrue(source.contains("onForgeAttack"));
        assertFalse("The standalone shim reaches the Forge-compatible listener; a second listener double-counts attacks",
                source.contains("onStandaloneAttack"));
        assertTrue(source.contains("acceptExternalAttack"));
        assertTrue(source.contains("event.getType() == EventType.POST"));
        assertTrue(source.contains("private final Object attackStateLock"));
        assertTrue(source.contains("synchronized (attackStateLock)"));
    }

    @Test
    public void killAuraDoesNotSendAnOrphanSwingBeforeAttackValidation() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/combat/KillAura.java");
        int begin = source.indexOf("    private boolean performAttack(float yaw, float pitch) {");
        int end = source.indexOf("    private boolean startBlock()", begin);
        String attack = source.substring(begin, end);

        int rayTraceValidation = attack.indexOf("RotationUtil.rayTrace(");
        int attackEvent = attack.indexOf("AttackEvent event = new AttackEvent");
        int swing = attack.indexOf("mc.thePlayer.swingItem();");
        int attackPacket = attack.indexOf("new C02PacketUseEntity");

        assertTrue("A failed silent-raytrace must not leave a C0A animation without an attack",
                rayTraceValidation >= 0 && attackEvent > rayTraceValidation && swing > attackEvent);
        assertTrue("Vanilla ordering still requires the swing immediately before the accepted C02 attack",
                attackPacket > swing);
    }

    @Test
    public void killAuraAttacksInOriginalOrderOnlyAfterAConfirmedSilentRotationBoundary() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/combat/KillAura.java");
        int attackBegin = source.indexOf("    private boolean performAttack(float yaw, float pitch, boolean allowReferenceRelease) {");
        int attackEnd = source.indexOf("    private boolean startBlock()", attackBegin);
        String attack = source.substring(attackBegin, attackEnd);
        int acceptedBegin = source.indexOf("    public void onPacketAccepted(PacketAcceptedEvent event) {");
        int acceptedEnd = source.indexOf("    @EventTarget(Priority.LOWEST)\n    public void onPacketWritten", acceptedBegin);
        String accepted = source.substring(acceptedBegin, acceptedEnd);

        assertTrue("The ownership window must cover both vanilla C0A swing and the explicit C02 attack",
                attack.contains("this.submittingOwnedAttackPackets = true;")
                        && attack.contains("this.submittingOwnedAttackPackets = false;"));
        assertTrue("Owned combat actions must keep their original 1.8 position before the current movement packet",
                accepted.contains("this.submittingOwnedAttackPackets")
                        && accepted.contains("isOwnedAttackActionPacket(event.getPacket())")
                        && accepted.contains("event.requestStrictOriginalPacketOrder();")
                        && !accepted.contains("event.requestAfterCurrentRotation();"));
        assertTrue("Silent attacks need a successful canonical movement boundary before using that server rotation",
                source.contains("public void onPlayerPacketBoundary(PlayerPacketBoundaryEvent event)")
                        && source.contains("KillAuraConfirmedRotationTracker")
                        && source.contains("attackRotationTracker.acceptBoundary(")
                        && source.contains("event.isRotated()"));
        assertFalse(source.contains("ownedAttackRequiresCurrentRotation"));
    }

    @Test
    public void killAuraOwnedAutoBlockWaitsCannotPermanentlyFreezeAttackCycles() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/combat/KillAura.java");

        assertTrue("Owned block/release callbacks can be lost or bypassed, so their local gates need a deadline",
                source.contains("OWNED_PACKET_TIMEOUT_MILLIS")
                        && source.contains("ownedBlockAwaitStartedAt")
                        && source.contains("ownedReleaseAwaitStartedAt"));
        assertTrue("The timeout recovery must run before mode-specific cycles inspect the awaiting flags",
                source.contains("this.recoverOwnedPacketTimeouts(now);")
                        && source.indexOf("this.recoverOwnedPacketTimeouts(now);")
                        < source.indexOf("if (autoBlockMode == AUTOBLOCK_LEGIT)"));
        assertTrue("Timed-out ownership must clear both the boolean gate and write identity",
                source.contains("this.awaitingOwnedBlockPacket = false;")
                        && source.contains("this.ownedBlockWriteId = PacketAcceptedEvent.NO_WRITE_ID;")
                        && source.contains("this.awaitingOwnedReleasePacket = false;")
                        && source.contains("this.ownedReleaseWriteId = PacketAcceptedEvent.NO_WRITE_ID;"));
    }

    @Test
    public void killAuraNeverSynthesizesHeldItemChangesForCombatActions() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/combat/KillAura.java");
        int attackBegin = source.indexOf("    private boolean performAttack(float yaw, float pitch, boolean allowReferenceRelease) {");
        int attackEnd = source.indexOf("    private boolean startBlock()", attackBegin);
        String attack = source.substring(attackBegin, attackEnd);
        int blockBegin = source.indexOf("    private boolean startBlock() {");
        int blockEnd = source.indexOf("    private boolean stopBlock()", blockBegin);
        String block = source.substring(blockBegin, blockEnd);
        int interactBegin = source.indexOf("    private boolean interactBlockAfterAttack(");
        int interactEnd = source.indexOf("    private boolean updateLeaderLegitAutoBlockCycle(", interactBegin);
        String interact = source.substring(interactBegin, interactEnd);

        assertFalse("Attacking does not own the hotbar and must not force a C09 before C02",
                attack.contains("syncCurrentPlayItem"));
        assertFalse("PlayerController.sendUseItem owns vanilla slot synchronization",
                block.contains("syncCurrentPlayItem"));
        assertFalse("Interact plus block must not perform a second explicit hotbar sync",
                interact.contains("syncCurrentPlayItem"));
        assertFalse("AutoBlock modes must not spoof unrelated held-item slots",
                source.contains("new C09PacketHeldItemChange"));
        assertFalse(source.contains("sendSpoofHeldItemChange"));
        assertFalse(source.contains("sendHypixelLagReleaseSwapSequence"));
        assertTrue("A real player hotbar change must still terminate the owned block cycle",
                source.contains("event.getPacket() instanceof C09PacketHeldItemChange"));
    }

    @Test
    public void jumpResetDoesNotMutateVelocitysAttackWindowState() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/combat/JumpReset.java");

        assertFalse(source.contains("Velocity.hasReceivedVelocity"));
    }

    @Test
    public void modernVelocitySettingsMatchTheTwoSupportedModes() throws IOException {
        String source = source("src/main/java/gq/yozakura/ui/click/web/ModernWebClickGuiController.java");
        int begin = source.indexOf("add(\"Velocity\"");
        int end = source.indexOf("add(\"JumpReset\"", begin);
        String settings = source.substring(begin, end);

        assertTrue(settings.contains(".mode(\"Mode\", \"Reduce\", \"Attack\", \"Reduce\")"));
        assertTrue(settings.contains("server-physics-compatible"));
        assertFalse(settings.contains(".number("));
        assertFalse(settings.contains("\"Update\""));
    }

    @Test
    public void modernVelocityDoesNotInterceptInboundMotionPackets() throws IOException {
        String source = source("src/main/java/gq/yozakura/bridge/modern/ModernPacketBridge.java");

        assertFalse(source.contains("handleVelocity(packet, player)"));
        assertFalse(source.contains("scaleMotionPacket("));
        assertFalse(source.contains("VELOCITY_REDUCTION_STATE"));
    }

    @Test
    public void modernVelocityDoesNotKeepObsoleteReductionRevisionState() throws IOException {
        String source = source("src/main/java/gq/yozakura/ui/click/web/ModernWebClickGuiController.java");

        assertFalse(source.contains("VELOCITY_REVISION"));
        assertFalse(source.contains("markVelocityChanged"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
