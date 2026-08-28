package gq.yozakura.module.world;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ScaffoldLeaderMoveFixContractTest {
    @Test
    public void keepsLeaderQuantizedMoveFixAvailableForScaffold() throws IOException {
        String moveUtil = source("src/main/java/gq/yozakura/util/module/MoveUtil.java");
        int start = moveUtil.indexOf("public static void fixStrafe");
        int end = moveUtil.indexOf("    public static", start + 1);
        if (end < 0) {
            end = moveUtil.length();
        }
        String fix = moveUtil.substring(start, end);

        assertTrue(fix.contains("MathHelper.wrapAngleTo180_float"));
        assertTrue(fix.contains("adjustYaw"));
        assertTrue(fix.contains("case 7"));
        assertFalse(fix.contains("Math.sin("));
        assertFalse(fix.contains("Math.toRadians("));
    }

    @Test
    public void scaffoldOwnsOneMoveFixWithoutBridgeRemap() throws IOException {
        String scaffold = source("src/main/java/gq/yozakura/module/world/Scaffold.java");

        assertTrue(scaffold.contains("MoveUtil.fixStrafe(RotationState.getSmoothedYaw())"));
        assertTrue(scaffold.contains("RotationState.getPriority() == 3.0F"));
        assertTrue(scaffold.contains("event.setPervRotation(targetYaw, 3, false)"));
    }

    @Test
    public void tellyKeepsLeaderMoveFixSoKeysFollowThePublishedYaw() throws IOException {
        String scaffold = source("src/main/java/gq/yozakura/module/world/Scaffold.java");
        int moveInputStart = scaffold.indexOf("public void onMoveInput(MoveInputEvent event)");
        int moveInputEnd = scaffold.indexOf("private int countHotbarBlocks()", moveInputStart);
        String moveInput = scaffold.substring(moveInputStart, moveInputEnd);

        assertTrue(moveInput.contains("boolean tellyMode = this.mode.getValue() == 1"));
        assertTrue(moveInput.contains("this.moveFix.getValue() == 1 && RotationState.isActived()"));
        assertFalse(moveInput.contains("&& !tellyMode"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
