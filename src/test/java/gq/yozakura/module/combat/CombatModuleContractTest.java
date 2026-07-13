package gq.yozakura.module.combat;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CombatModuleContractTest {
    @Test
    public void autoClickerDoesNotMutateTheUseItemBinding() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/combat/AutoClicker.java");

        assertTrue(source.contains("isMouseButtonDown(0)"));
        assertFalse(source.contains("keyBindUseItem"));
        assertFalse(source.contains("RightClickMouseEvent"));
    }

    @Test
    public void blockHitDoesNotGloballyCancelRightClicks() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/combat/BlockHit.java");

        assertTrue(source.contains("\"Helper\", \"Auto\", \"Lag\""));
        assertFalse(source.contains("RightClickMouseEvent"));
    }

    @Test
    public void velocityPublishesOnlyAttackAndReduceModes() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/combat/Velocity.java");

        assertTrue(source.contains("new String[]{\"Attack\", \"Reduce\"}"));
        assertFalse(source.contains("\"Update\""));
    }

    @Test
    public void velocityAttackUsesOnlyTheExistingAttackPipeline() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/combat/Velocity.java");

        assertTrue(source.contains("public void onAttack(AttackEvent event)"));
        assertFalse(source.contains("new C02PacketUseEntity"));
        assertFalse(source.contains("new C0APacketAnimation"));
    }

    @Test
    public void velocityReduceLetsVanillaApplyTheScaledPacket() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/combat/Velocity.java");

        assertTrue(source.contains("scaleVelocityPacket"));
        assertFalse(source.contains("event.setCancelled(true)"));
        assertFalse(source.contains("runOnClientThread"));
    }

    @Test
    public void realAttackSlowdownIsAppliedAtTheExistingLocalAttackHook() throws IOException {
        String source = source("src/main/java/gq/yozakura/util/module/PlayerUtil.java");

        assertTrue(source.contains("Velocity.applyAttackSlowdown(target)"));
        assertTrue(source.contains("applyAttackMotion(target, knockbackLevel > 0)"));
    }

    @Test
    public void velocityObservesBothCustomAndForgeAttackEntrypoints() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/combat/Velocity.java");

        assertTrue(source.contains("onStandaloneAttack"));
        assertTrue(source.contains("onForgeAttack"));
        assertTrue(source.contains("acceptExternalAttack"));
        assertTrue(source.contains("event.getType() == EventType.POST"));
        assertTrue(source.contains("private final Object attackStateLock"));
        assertTrue(source.contains("synchronized (attackStateLock)"));
    }

    @Test
    public void jumpResetDoesNotMutateVelocitysAttackWindowState() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/combat/JumpReset.java");

        assertFalse(source.contains("Velocity.hasReceivedVelocity"));
    }

    @Test
    public void modernVelocitySettingsMatchTheTwoSupportedModes() throws IOException {
        String source = source("src/main/java/gq/yozakura/ui/click/web/ModernWebClickGuiController.java");

        assertTrue(source.contains(".mode(\"Mode\", \"Reduce\", \"Attack\", \"Reduce\")"));
        assertTrue(source.contains(".number(\"Horizontal\", 60.0D"));
        assertTrue(source.contains(".number(\"Vertical\", 100.0D"));
        assertFalse(source.contains("\"Update\""));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
