package gq.yozakura.module.combat.aim;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The injected Lunar loader must be able to resolve the complete Silent target
 * refresh path without relying on a separately loaded helper class.
 */
public class AimAssistLunarBridgeContractTest {
    @Test
    public void silentTargetSelectionDoesNotLinkToAnOptionalHelperClass() throws IOException {
        String selector = source("src/main/java/gq/yozakura/module/combat/aim/AimAssistTargetSelector.java");

        assertTrue("Target selection must keep the lock rule in the selector's own class",
                selector.contains("static boolean shouldSwitch("));
        assertFalse("Lunar must not fail target refresh when the helper class is absent from an old loader",
                selector.contains("AimAssistTargetLock.shouldSwitch("));
    }

    @Test
    public void silentModeStillPublishesRotationThroughTheStandalonePacketBoundary() throws IOException {
        String aimbot = source("src/main/java/gq/yozakura/module/combat/Aimbot.java");
        String bridge = source("src/main/java/gq/yozakura/bridge/StandaloneEventBridge.java");

        int refresh = aimbot.indexOf("if (!refreshTargetForCurrentInput())");
        int publish = aimbot.indexOf("event.setRotation(", refresh);
        int pre = bridge.indexOf("dispatchPreUpdateBeforePlayerPacket();");
        int c03 = bridge.indexOf("writePlayerPacket(ctx, (C03PacketPlayer) packet", pre);

        assertTrue("Silent aim must refresh a target before publishing its server rotation",
                refresh >= 0 && publish > refresh);
        assertTrue("Lunar must dispatch PRE before the real C03 packet reaches the writer",
                pre >= 0 && c03 > pre);
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
