package gq.yozakura.module.world;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class ClutchRotationMoveFixContractTest {
    @Test
    public void startsAimingFromTheRotationAlreadyResolvedForThisTick() throws IOException {
        String source = source();
        String resolveAim = method(source,
                "    private AimData resolveAim(PlaceTarget target, UpdateEvent event, boolean upward) {",
                "    private AimData findExactAim(PlaceTarget target, UpdateEvent event, boolean upward) {");
        String findExactAim = method(source,
                "    private AimData findExactAim(PlaceTarget target, UpdateEvent event, boolean upward) {",
                "    private float stepRotation(float current, float target, float maxSpeed) {");

        assertTrue(resolveAim.contains("float baseYaw = hasRotation ? yaw : event.getNewYaw();"));
        assertTrue(resolveAim.contains("float basePitch = hasRotation ? pitch : event.getNewPitch();"));
        assertTrue(findExactAim.contains("float baseYaw = hasRotation ? yaw : event.getNewYaw();"));
        assertTrue(findExactAim.contains("float basePitch = hasRotation ? pitch : event.getNewPitch();"));
    }

    @Test
    public void publishesMoveFixOnlyAfterClutchWinsTheRotationClaim() throws IOException {
        String source = source();
        String applyRotation = method(source,
                "    private boolean applySilentRotation(UpdateEvent event, AimData aim) {",
                "    private boolean canPlaceNow(PlaceTarget target, boolean upward, boolean exactHit) {");

        int claim = applyRotation.indexOf("event.trySetRotation(nextYaw, nextPitch, ROTATION_PRIORITY)");
        int moveFix = applyRotation.indexOf("event.setPervRotation(nextYaw, ROTATION_PRIORITY)");
        int publish = applyRotation.indexOf("VisualRotationState.publish(\"Clutch\", nextYaw, nextPitch, ROTATION_PRIORITY)");

        assertTrue(claim >= 0);
        assertTrue(moveFix > claim);
        assertTrue(publish > moveFix);
        assertTrue(applyRotation.contains("if (!event.trySetRotation(nextYaw, nextPitch, ROTATION_PRIORITY))"));
        assertTrue(applyRotation.contains("if (Boolean.TRUE.equals(moveFix.getValue()))"));
    }

    @Test
    public void skipsPlacementWhenItsRotationClaimIsRejected() throws IOException {
        String source = source();
        String update = method(source,
                "    public void onUpdate(UpdateEvent event) {",
                "    private boolean canRun() {");

        int claim = update.indexOf("if (!applySilentRotation(event, aim))");
        int restore = update.indexOf("restoreSlot(originalSlot);", claim);
        int reset = update.indexOf("resetTarget();", restore);
        int returnAfterReset = update.indexOf("return;", reset);
        int slowMotion = update.indexOf("slowHorizontalMotion(upward);");
        int place = update.indexOf("if (place(target))");

        assertTrue(claim >= 0);
        assertTrue(restore > claim);
        assertTrue(reset > restore);
        assertTrue(returnAfterReset > reset);
        assertTrue(slowMotion > returnAfterReset);
        assertTrue(place > slowMotion);
    }

    private static String method(String source, String beginMarker, String endMarker) {
        int begin = source.indexOf(beginMarker);
        int end = source.indexOf(endMarker, begin + beginMarker.length());
        assertTrue("Expected method marker: " + beginMarker, begin >= 0 && end > begin);
        return source.substring(begin, end);
    }

    private static String source() throws IOException {
        return new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/module/world/Clutch.java")), StandardCharsets.UTF_8);
    }
}
