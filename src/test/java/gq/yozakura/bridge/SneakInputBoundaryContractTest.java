package gq.yozakura.bridge;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SneakInputBoundaryContractTest {
    @Test
    public void resolvesSneakBeforeLegacyMovementListenersObserveTheInput() throws IOException {
        String source = source("src/main/java/gq/yozakura/bridge/MovementInputBridge.java");

        int inputPhase = source.indexOf("private static void afterVanillaInput(HookedMovementInput input)");
        int sneakInput = source.indexOf("resolveSneakInput(input);", inputPhase);
        int legacyInput = source.indexOf("EventManager.call(new MoveInputEvent());", inputPhase);
        assertTrue(inputPhase >= 0);
        assertTrue(sneakInput > inputPhase);
        assertTrue(legacyInput > sneakInput);
        assertTrue(source.contains("SneakInputEvent event = new SneakInputEvent"));
        assertTrue(source.contains("EventManager.call(event);"));
        assertTrue(source.contains("input.sneak = resolved.isSneaking();"));
        assertTrue(source.contains("input.moveForward = resolved.getForward();"));
        assertTrue(source.contains("input.moveStrafe = resolved.getStrafe();"));
        assertTrue(source.contains("input.jump = event.isResolvedJump();"));
    }

    @Test
    public void safeWalkAndBridgeAssistDoNotWriteTheGlobalSneakKey() throws IOException {
        String base = baseSource();
        String bridgeAssist = source("src/main/java/gq/yozakura/module/world/BridgeAssistSneakController.java");
        String movementInput = source("src/main/java/gq/yozakura/bridge/MovementInputBridge.java");
        String yozakura = source("src/main/java/gq/yozakura/bridge/YozakuraEventBridge.java");
        String standalone = source("src/main/java/gq/yozakura/bridge/StandaloneEventBridge.java");

        assertTrue(yozakura.contains("MovementInputBridge.setSafeWalkRequested"));
        assertTrue(standalone.contains("MovementInputBridge.setSafeWalkRequested"));
        assertFalse(base.contains("KeyBinding.setKeyBindState"));
        assertFalse(movementInput.contains("KeyBinding.setKeyBindState"));
        assertFalse(bridgeAssist.contains("KeyBinding.setKeyBindState"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static String baseSource() throws IOException {
        return new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/bridge/BasePacketBridgeHandler.java")), StandardCharsets.UTF_8);
    }

    private static String forgeSource() throws IOException {
        return new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/bridge/YozakuraEventBridge.java")), StandardCharsets.UTF_8);
    }

    private static String standaloneSource() throws IOException {
        return new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/bridge/StandaloneEventBridge.java")), StandardCharsets.UTF_8);
    }

    private static String combinedForgeSource() throws IOException {
        return baseSource() + "\n" + forgeSource();
    }

    private static String combinedStandaloneSource() throws IOException {
        return baseSource() + "\n" + standaloneSource();
    }
}
