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
    }
}
