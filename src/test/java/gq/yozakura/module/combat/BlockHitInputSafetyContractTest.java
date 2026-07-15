package gq.yozakura.module.combat;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Regression contracts for keeping BlockHit inside Minecraft's real input
 * lifecycle. The module may make one bounded vanilla use-key request after an
 * accepted attack, but must not rewrite input results or hold packets for
 * later replay.
 */
public class BlockHitInputSafetyContractTest {
    @Test
    public void blockHitDoesNotTurnAnAttackIntoASyntheticUseKeyPress() throws IOException {
        String blockHit = source("src/main/java/gq/yozakura/module/combat/BlockHit.java");

        assertFalse(blockHit.contains("LeftClickMouseEvent"));
        assertTrue(blockHit.contains("controller.armUseAfterMovementBoundary()"));
        assertFalse(blockHit.contains("KeyBinding.setKeyBindState"));
        String packetHandler = method(blockHit, "public void onPacketAccepted(PacketAcceptedEvent event)",
                "public void onPacketWritten(PacketWriteEvent event)");
        assertFalse(packetHandler.contains("useAction.startUse("));
        assertFalse(packetHandler.contains("useAction.releaseUse("));
    }

    @Test
    public void blockHitDoesNotRewriteTheVanillaRayTraceResult() throws IOException {
        String blockHit = source("src/main/java/gq/yozakura/module/combat/BlockHit.java");

        assertFalse(blockHit.contains("MouseOverEvent"));
        assertFalse(blockHit.contains("mc.objectMouseOver = null"));
    }

    @Test
    public void lagModeDoesNotBufferTheVanillaCombatStream() throws IOException {
        String blockHit = source("src/main/java/gq/yozakura/module/combat/BlockHit.java");

        assertFalse(blockHit.contains("BlinkModules.BLOCK_HIT"));
        assertFalse(blockHit.contains("tryAcquire("));
        assertFalse(blockHit.contains("lagBlinking"));
    }

    @Test
    public void vanillaUseActionDoesNotOverrideThePhysicalUseBinding() throws IOException {
        String action = source("src/main/java/gq/yozakura/module/combat/BlockHitVanillaUseAction.java");

        assertTrue(action.contains("isPhysicalUseDown()"));
        assertFalse(action.contains("KeyBinding.setKeyBindState"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static String method(String source, String beginMarker, String endMarker) {
        int begin = source.indexOf(beginMarker);
        int end = source.indexOf(endMarker, begin + beginMarker.length());
        return source.substring(begin, end);
    }
}
