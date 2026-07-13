package gq.yozakura.module.combat;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Regression contracts for the vanilla-input boundary of BlockHit.
 *
 * <p>Lunar/Grim rejects a release that shares an input tick with an item-use
 * packet, and rejects an item-use packet that is emitted after the movement
 * packet. BlockHit must therefore drive Minecraft's normal use-key path rather
 * than constructing, replaying, or cancelling interaction packets itself.</p>
 */
public class BlockHitRewriteContractTest {
    @Test
    public void blockHitDoesNotConstructOrReplayInteractionPackets() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/combat/BlockHit.java");

        assertTrue(source.contains("C02PacketUseEntity.Action.ATTACK"));
        assertTrue(source.contains("C07PacketPlayerDigging"));
        assertTrue(source.contains("C03PacketPlayer"));
        assertTrue(source.contains("controller.advanceMovementEpoch()"));
        assertFalse(source.contains("C08PacketPlayerBlockPlacement"));
        assertFalse(source.contains("new C07PacketPlayerDigging"));
        assertFalse(source.contains("new C08PacketPlayerBlockPlacement"));
        assertFalse(source.contains("PacketUtil.sendPacket("));
        assertFalse(source.contains("event.setCancelled(true)"));
    }

    @Test
    public void manualModeUsesTheNormalUseKeyWithoutObservingRightClicks() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/combat/BlockHit.java");
        String guard = source("src/main/java/gq/yozakura/module/combat/BlockHitUseKeyGuard.java");

        assertTrue(source.contains("onAttackInput(LeftClickMouseEvent event)"));
        assertTrue(source.contains("useKeyGuard.holdUse()"));
        assertTrue(guard.contains("KeyBinding.setKeyBindState"));
        assertTrue(guard.contains("isPhysicalUseDown"));
        assertFalse(source.contains("RightClickMouseEvent"));
        assertFalse(guard.contains("suppressPackets"));
    }

    @Test
    public void automaticModeArmsFromTheRealAttackPacketWithoutReplayingIt() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/combat/BlockHit.java");
        String autoClicker = source("src/main/java/gq/yozakura/module/combat/AutoClicker.java");

        assertTrue(source.contains("instanceof C02PacketUseEntity"));
        assertTrue(source.contains("C02PacketUseEntity.Action.ATTACK"));
        assertTrue(source.contains("controller.armAuto()"));
        assertTrue(source.contains("controller.isAutoReadyAfterMovement()"));
        assertFalse(autoClicker.contains("BlockHit.onAttack"));
    }

    @Test
    public void vapeModeSurfaceAndSettingsAreBoundToTheModule() throws IOException {
        String blockHit = source("src/main/java/gq/yozakura/module/combat/BlockHit.java");
        String settings = source("src/main/java/gq/yozakura/module/combat/BlockHitSettings.java");

        assertTrue(settings.contains("\"Manual\", \"Predict\", \"Auto\", \"Lag\""));
        assertTrue(settings.contains("Require Mouse Down"));
        assertTrue(settings.contains("Ignore Manual Block"));
        assertTrue(settings.contains("Angle"));
        assertTrue(settings.contains("Distance"));
        assertTrue(blockHit.contains("addValues(settings.values())"));
    }

    @Test
    public void lagBuffersOnlyAnExistingVanillaRelease() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/combat/BlockHit.java");

        assertTrue(source.contains("tryAcquire(BlinkModules.BLOCK_HIT)"));
        assertTrue(source.contains("lagReleaseAt"));
        assertTrue(source.contains("RELEASE_USE_ITEM"));
        assertFalse(source.contains("startSyntheticBlock"));
        assertFalse(source.contains("requestOwnedRelease"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
