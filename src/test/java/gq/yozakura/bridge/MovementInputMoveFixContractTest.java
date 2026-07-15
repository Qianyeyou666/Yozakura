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
    public void resolvesTheFinalHorizontalInputImmediatelyBeforePhysics() throws IOException {
        String source = source();
        int inputPhase = source.indexOf("private static void afterVanillaInput(HookedMovementInput input)");
        int livingUpdate = source.indexOf("EventManager.call(livingUpdate);", inputPhase);
        int moveFix = source.indexOf("applyMoveFix(input);", inputPhase);
        int physics = source.indexOf("applyRotationForPhysics(input);", inputPhase);

        assertTrue(inputPhase >= 0);
        assertTrue("MoveFix must consume the axes after every input listener has run",
                livingUpdate > inputPhase && moveFix > livingUpdate);
        assertTrue("MoveFix must finish before the temporary physics yaw is applied", physics > moveFix);
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
