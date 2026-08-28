package gq.yozakura.module.combat;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class KillAuraVapeRotationContractTest {
    @Test
    public void silentAimUsesTheStableClosestPointAndAdaptiveControllerBeforeQuantization() throws IOException {
        String source = source();
        int calculate = source.indexOf("private float[] calculateTargetRotations(UpdateEvent event)");
        int commit = source.indexOf("private void commitTargetRotations", calculate);
        String method = source.substring(calculate, commit);

        assertTrue(method.contains("KillAuraAimPoint.closest"));
        assertTrue(method.contains("rotationController.step"));
        assertTrue(method.indexOf("rotationController.step")
                < method.indexOf("KillAuraRotationQuantizer.quantizeYaw"));
    }

    @Test
    public void targetSwitchResetsTheRotationControllerState() throws IOException {
        String source = source();
        int begin = source.indexOf("private void setAttackTarget(AttackData nextTarget)");
        int end = source.indexOf("private AxisAlignedBB getLiveTargetBox()", begin);
        String method = source.substring(begin, end);

        assertTrue(method.contains("this.resetRotationSmoothing();"));
    }

    private static String source() throws IOException {
        return new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/module/combat/KillAura.java")), StandardCharsets.UTF_8);
    }
}
