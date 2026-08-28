package gq.yozakura.module.combat;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KillAuraRotationClaimContractTest {
    @Test
    public void onlyTheWinningRotationClaimPublishesMoveFixAndAttackYaw() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/module/combat/KillAura.java")), StandardCharsets.UTF_8);
        int updateBegin = source.indexOf("    public void onUpdate(UpdateEvent event) {");
        int updateEnd = source.indexOf("    private float[] calculateTargetRotations", updateBegin);
        String update = source.substring(updateBegin, updateEnd);
        int claim = update.indexOf("if (event.trySetRotation(rotations[0], rotations[1], 1))");

        assertTrue("KillAura must observe rotation arbitration before using a target yaw", claim >= 0);
        assertTrue(update.indexOf("event.setPervRotation(rotations[0], 1);", claim) > claim);
        assertTrue(update.indexOf("VisualRotationState.publish(\"KillAura\"", claim) > claim);
        assertTrue(update.indexOf("yaw = rotations[0];", claim) > claim);
        assertFalse(update.contains("event.setRotation(rotations[0], rotations[1], 1);"));
        assertTrue("Silent move-fix must preserve the camera-relative world vector",
                update.contains("if (this.moveFix.getValue() == 1)"));
        assertTrue("Strict move-fix must keep movement relative to the server yaw",
                update.contains("event.setPervRotation(rotations[0], 1, false);"));
    }

    @Test
    public void attackUsesThePreviousConfirmedBoundaryInOriginalPacketOrder() throws IOException {
        String aura = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/module/combat/KillAura.java")), StandardCharsets.UTF_8);

        assertTrue("silent attacks must consume a successful server-visible KillAura boundary",
                aura.contains("KillAuraConfirmedRotationTracker.Rotation confirmedRotation")
                        && aura.contains("confirmedRotation != null"));
        assertTrue("the confirmed yaw and pitch must drive the final live-box attack check",
                aura.contains("yaw = confirmedRotation.yaw;")
                        && aura.contains("pitch = confirmedRotation.pitch;"));
        assertTrue("1.8 combat actions must remain before the current movement packet",
                aura.contains("event.requestStrictOriginalPacketOrder();"));
        assertFalse("combat actions must not be flushed after the current C03",
                aura.contains("event.requestAfterCurrentRotation();"));
        assertTrue("a target switch must discard a previous target's confirmed rotation",
                aura.contains("this.attackRotationTracker.clear();"));
        assertTrue("the attack gate must re-check the live entity box",
                aura.contains("getLiveTargetBox()"));
    }

    @Test
    public void everyOwnedAttackSendsAnimationBeforeInteractEvenWhenKeepingBlockPose() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/module/combat/KillAura.java")), StandardCharsets.UTF_8);
        String attack = method(source,
                "    private boolean performAttack(float yaw, float pitch, boolean allowReferenceRelease) {",
                "    private boolean startBlock() {");
        int pose = attack.indexOf("if (this.shouldKeepReferenceBlockPose())");
        int animation = attack.indexOf("PacketUtil.sendPacket(new C0APacketAnimation());", pose);
        int interact = attack.indexOf("PacketUtil.sendPacket(new C02PacketUseEntity", pose);

        assertTrue("block-pose attacks still need a network animation before ATTACK",
                pose >= 0 && animation > pose && interact > animation);
    }

    @Test
    public void silentSmoothingStartsAtThePublishedBoundaryAndOnlyCommitsAfterWinning() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/module/combat/KillAura.java")), StandardCharsets.UTF_8);
        int updateBegin = source.indexOf("    public void onUpdate(UpdateEvent event) {");
        int updateEnd = source.indexOf("    private float[] calculateTargetRotations", updateBegin);
        String update = source.substring(updateBegin, updateEnd);
        int claim = update.indexOf("if (event.trySetRotation(rotations[0], rotations[1], 1))");
        int commit = update.indexOf("this.commitTargetRotations(rotations);", claim);

        assertTrue("silent smoothing may only advance after KillAura wins rotation arbitration",
                claim >= 0 && commit > claim);
        assertTrue("a new silent turn must start at the last server-visible rotation",
                source.contains("float sourceYaw = this.rotationSmoothActive ? this.smoothYaw : event.getYaw();")
                        && source.contains("float sourcePitch = this.rotationSmoothActive ? this.smoothPitch : event.getPitch();"));
        assertFalse("calculating a candidate must not mutate the committed smoothing state",
                method(source,
                        "    private float[] calculateTargetRotations(UpdateEvent event) {",
                        "    private void commitTargetRotations(float[] rotations) {")
                        .contains("this.smoothYaw ="));
        assertTrue("the final silent delta must use the configured vanilla mouse quantum",
                source.contains("float sensitivity = mc.gameSettings.mouseSensitivity;")
                        && source.contains("KillAuraRotationQuantizer.quantizeYaw(")
                        && source.contains("KillAuraRotationQuantizer.quantizePitch("));
    }

    private static String method(String source, String beginMarker, String endMarker) {
        int begin = source.indexOf(beginMarker);
        int end = source.indexOf(endMarker, begin + beginMarker.length());
        assertTrue("Expected method marker: " + beginMarker, begin >= 0 && end > begin);
        return source.substring(begin, end);
    }
}
