package gq.yozakura.bridge;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MovementInputMoveFixContractTest {
    @Test
    public void resolvesDiscreteInputThenUsesThePacketYawForVanillaPhysics() throws IOException {
        String source = source();
        int inputPhase = source.indexOf("private static void afterVanillaInput(HookedMovementInput input)");
        int livingUpdate = source.indexOf("EventManager.call(livingUpdate);", inputPhase);
        int moveFix = source.indexOf("applyMoveFix(input);", inputPhase);
        int physicsYaw = source.indexOf("applyRotationForPhysics(input);", inputPhase);
        int packetBoundary = source.indexOf("Runnable postInputHook = afterMoveInputHook;", inputPhase);

        assertTrue(inputPhase >= 0);
        assertTrue("MoveFix must consume the axes after every input listener has run",
                livingUpdate > inputPhase && moveFix > livingUpdate);
        assertTrue("Silent-yaw physics must run after input remapping and before the player packet boundary",
                physicsYaw > moveFix && packetBoundary > physicsYaw);
        assertTrue("MoveFix must expose only discrete Grim-simulable axes relative to the packet yaw",
                source.contains("MoveFixResolver.resolve("));
        assertTrue("Vanilla movement and sprint-jump physics must use the same yaw sent to Grim",
                source.contains("player.rotationYaw = yaw;")
                        && source.contains("player.prevRotationYaw = yaw;"));
        assertTrue("The visible yaw must be restored at the packet, render, and tick boundaries",
                source.contains("restoreAppliedRotation(player);"));
        assertFalse("Continuous arbitrary input axes are not part of Grim's 1.8 input search",
                source.contains("MoveFixResolver.rotateAxes("));
    }

    @Test
    public void moveFixDoesNotOwnJumpSneakOrSprintState() throws IOException {
        String source = source();
        String moveFix = method(source,
                "    private static void applyMoveFix(MovementInput input) {",
                "    private static void applyRotationForPhysics(MovementInput input) {");

        assertFalse(moveFix.contains(".jump"));
        assertFalse(moveFix.contains(".sneak"));
        assertFalse(moveFix.contains("setSprinting"));
        assertFalse(moveFix.contains("keyBindSprint"));
        assertFalse(moveFix.contains("MoveUtil"));
    }

    private static String method(String source, String beginMarker, String endMarker) {
        int begin = source.indexOf(beginMarker);
        int end = source.indexOf(endMarker, begin + beginMarker.length());
        assertTrue("Expected method marker: " + beginMarker, begin >= 0 && end > begin);
        return source.substring(begin, end);
    }

    private static String source() throws IOException {
        return new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/bridge/MovementInputBridge.java")), StandardCharsets.UTF_8);
    }
}
