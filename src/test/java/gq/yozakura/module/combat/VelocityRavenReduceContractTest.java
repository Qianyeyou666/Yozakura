package gq.yozakura.module.combat;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Contracts for the Raven-Lunar16 Reduce behavior adapted to VapuLite. */
public class VelocityRavenReduceContractTest {
    @Test
    public void reduceArmsFromLocalS12WithoutMutatingTheServerPacket() throws IOException {
        String source = source();
        String packetHandler = methodBody(source,
                "    public void onPacket(PacketEvent event) {");
        assertTrue("Reduce must arm a one-shot knockback action after local S12",
                packetHandler.contains("knockback = true"));
        assertFalse("Reduce must not cancel the accepted S12",
                packetHandler.contains("event.setCancelled(true)"));
        assertFalse("Reduce must not rewrite server-owned S12 motion",
                packetHandler.contains("scaleVelocityPacket("));
        assertFalse("Reduce must not rewrite server-owned S12 motion",
                packetHandler.contains("setMotion"));
    }

    @Test
    public void reduceConsumesTheOneShotOnlyOnPreTickWithRavenGuards() throws IOException {
        String source = source();
        String tickHandler = methodBody(source, "    public void onTick(TickEvent event) {");
        String reduceHandler = methodBody(source, "    private void performReduce() {");
        assertTrue("Reduce must consume the armed state on PRE, not POST",
                tickHandler.contains("event.getType() == EventType.PRE"));
        assertTrue("Raven Reduce requires the forward key",
                reduceHandler.contains("keyBindForward"));
        assertTrue("Raven Reduce requires sprinting",
                reduceHandler.contains("isSprinting()"));
        assertTrue("Reduce must clear its one-shot state before executing",
                reduceHandler.contains("knockback = false"));
    }

    @Test
    public void reduceUsesKillAuraThenCrosshairPlayerAndSendsNoEventAttack() throws IOException {
        String source = source();
        assertTrue("Reduce must prefer the enabled KillAura target",
                source.contains("ModuleManager.getModule(\"KillAura\")")
                        && source.contains("getTarget()"));
        assertTrue("Reduce must fall back to the crosshair player",
                source.contains("objectMouseOver") && source.contains("EntityPlayer"));
        assertTrue("Reduce must exclude friends through the shared friend policy",
                source.contains("TeamUtil.isFriend"));
        assertTrue("Raven sends an animation without re-entering packet events",
                source.contains("sendPacketNoEvent") && source.contains("C0APacketAnimation"));
        assertTrue("Raven sends the extra attack without re-entering packet events",
                source.contains("sendPacketNoEvent") && source.contains("C02PacketUseEntity")
                        && source.contains("Action.ATTACK"));
    }

    @Test
    public void reduceAppliesVanillaSprintSlowdownLocally() throws IOException {
        String source = source();
        assertTrue("Reduce keeps the original horizontal motion behavior",
                source.contains("motionX *= 0.6D") || source.contains("motionX =")
                        && source.contains("0.6D"));
        assertTrue("Reduce keeps the original horizontal motion behavior",
                source.contains("motionZ *= 0.6D") || source.contains("motionZ =")
                        && source.contains("0.6D"));
        assertTrue("Reduce stops sprinting after the extra attack",
                source.contains("setSprinting(false)"));
    }

    @Test
    public void reduceKeepsAttackModeStateMachineSeparate() throws IOException {
        String source = source();
        assertTrue("Attack mode must remain present",
                source.contains("MODE_ATTACK") && source.contains("controller.armAttackWindow"));
        assertTrue("Reduce must have an explicit implementation path",
                source.contains("MODE_REDUCE") && source.contains("performReduce"));
        assertTrue("Reduce suffix must describe the real mode",
                source.contains("new String[]{\"Reduce\"}"));
    }

    private static String source() throws IOException {
        return new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/module/combat/Velocity.java")), StandardCharsets.UTF_8);
    }

    private static String methodBody(String source, String beginMarker) {
        int methodStart = source.indexOf(beginMarker);
        if (methodStart < 0) {
            throw new AssertionError("Missing method: " + beginMarker);
        }
        int bodyStart = source.indexOf('{', methodStart);
        int depth = 0;
        for (int index = bodyStart; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character == '{') {
                depth++;
            } else if (character == '}' && --depth == 0) {
                return source.substring(methodStart, index + 1);
            }
        }
        throw new AssertionError("Unclosed method body: " + beginMarker);
    }
}
