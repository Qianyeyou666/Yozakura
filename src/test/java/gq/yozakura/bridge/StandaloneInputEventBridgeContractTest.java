package gq.yozakura.bridge;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class StandaloneInputEventBridgeContractTest {
    @Test
    public void keyInputEventsAreNotLimitedToModuleBindingKeys() throws IOException {
        String client = source("src/main/java/gq/yozakura/core/StandaloneClient.java");

        assertTrue("Standalone must scan the keyboard independently of module bindings",
                client.contains("for (int key = 0; key < KEYBOARD_SIZE; key++)"));
        assertTrue("Every physical key rising edge must dispatch the Forge key-input shim",
                client.contains("dispatchKeyPress(key);"));
        assertTrue("Module toggling remains a separate consumer of the detected key",
                client.contains("toggleModulesBoundTo(key);"));
    }

    @Test
    public void standaloneBridgeDoesNotConsumeOrResendVanillaWheelHotbarChanges() throws IOException {
        String bridge = source("src/main/java/gq/yozakura/bridge/StandaloneEventBridge.java");

        assertFalse("Minecraft.runTick owns Mouse.getEventDWheel; bridge polling duplicates the same scroll event",
                bridge.contains("Mouse.getDWheel()"));
        assertFalse("The bridge must not perform a second local hotbar change for the same wheel event",
                bridge.contains("inventory.changeCurrentItem(offset)"));
        assertFalse("PlayerControllerMP must emit the one vanilla-timed C09 instead of a bridge-side resend",
                bridge.contains("MinecraftAccessor.syncCurrentPlayItem(mc.playerController)"));
    }

    @Test
    public void standaloneBridgeDoesNotSynthesizeMouseClicksAheadOfMinecraftRunTick() throws IOException {
        String bridge = source("src/main/java/gq/yozakura/bridge/StandaloneEventBridge.java");

        assertFalse("Mouse.isButtonDown cannot consume Minecraft.runTick's pending Mouse.next event",
                bridge.contains("Mouse.isButtonDown(0)") || bridge.contains("Mouse.isButtonDown(1)"));
        assertFalse("A synthetic Forge mouse event can run combat modules before the original click",
                bridge.contains("new gq.yozakura.bridge.forge.MouseEvent"));
        assertFalse("A synthetic LeftClickMouseEvent can queue KillAura while vanilla still sends C0A/C02",
                bridge.contains("new LeftClickMouseEvent()"));
        assertFalse("A pre-runTick key-state clear cannot cancel the original mouse event",
                bridge.contains("suppressMouseKey("));
        assertFalse("No Standalone tick path may poll and dispatch its own mouse-button edge",
                bridge.contains("dispatchMouseButtons();"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
