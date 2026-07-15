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

        assertTrue(source.contains("\"Manual\", \"Predict\", \"Auto\", \"Lag\""));
        assertFalse(source.contains("RightClickMouseEvent"));
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
        int end = source.indexOf("    private boolean sendUseItem()", begin);
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
    public void silentAimRefreshesItsTargetInTheMovementPrePhase() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/combat/Aimbot.java");
        int updateBegin = source.indexOf("    public void onUpdate(UpdateEvent event) {");
        int updateEnd = source.indexOf("    private void handleModeChange()", updateBegin);
        String update = source.substring(updateBegin, updateEnd);

        assertTrue("Lunar dispatches Update PRE before its delayed bridge Tick PRE, so silent aim needs its target here",
                source.contains("private boolean refreshTargetForCurrentInput()"));
        int silentMode = update.indexOf("if (!mode.getValue().isSilent()");
        int targetRefresh = update.indexOf("if (!refreshTargetForCurrentInput())");
        int rotation = update.indexOf("event.setRotation(");
        assertTrue("The target refresh must happen after confirming silent mode and before publishing the C03 rotation",
                silentMode >= 0 && targetRefresh > silentMode && rotation > targetRefresh);
    }

    @Test
    public void silentAimClearsThePreviousScanDeadlineWhenAttackInputIsReleased() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/combat/Aimbot.java");
        int begin = source.indexOf("    private boolean refreshTargetForCurrentInput() {");
        int end = source.indexOf("    private void refreshTarget(long now) {", begin);
        String refresh = source.substring(begin, end);

        int clear = refresh.indexOf("clearTargetState(mode.getValue().isSilent());");
        int resetDeadline = refresh.indexOf("nextTargetScanAt = 0L;", clear);
        int returnFalse = refresh.indexOf("return false;", resetDeadline);
        assertTrue("Releasing RequireMouse input must not leave the old UpdateRate deadline blocking the next press",
                clear >= 0 && resetDeadline > clear && returnFalse > resetDeadline);
    }

    @Test
    public void silentAimRepressReacquiresBeforeContinuingItsReturnRotation() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/combat/Aimbot.java");
        int begin = source.indexOf("    public void onUpdate(UpdateEvent event) {");
        int end = source.indexOf("    private void handleModeChange()", begin);
        String update = source.substring(begin, end);

        int returning = update.indexOf("if (silentReturning) {");
        int silentMode = update.indexOf("if (!mode.getValue().isSilent()", returning);
        String returnBlock = update.substring(returning, silentMode);
        assertTrue("A valid re-press must refresh/reacquire a silent target before cancelling the return path",
                returnBlock.contains("mode.getValue().isSilent() && refreshTargetForCurrentInput(true)")
                        && returnBlock.contains("finishSilentReturn();"));
        assertTrue("No valid target must retain the existing return packet instead of emitting a stale normal rotation",
                returnBlock.contains("publishSilentReturn(event);") && returnBlock.contains("return;"));
    }

    @Test
    public void silentAimRechecksRangeImmediatelyWhileReturningToTheCamera() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/combat/Aimbot.java");
        int begin = source.indexOf("    private void refreshTarget(long now");
        int end = source.indexOf("    private void updateTickTarget(long now)", begin);
        String refresh = source.substring(begin, end);

        assertTrue("A target that re-enters range during the short silent return must bypass UpdateRate cooldown",
                source.contains("private boolean refreshTargetForCurrentInput(boolean forceScan)")
                        && refresh.contains("if (!forceScan && now < nextTargetScanAt)"));
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
        String source = source("src/main/java/gq/yozakura/core/modern/ModernPacketBridge.java");

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
