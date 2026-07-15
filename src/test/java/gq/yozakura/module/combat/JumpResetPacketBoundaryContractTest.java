package gq.yozakura.module.combat;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JumpResetPacketBoundaryContractTest {
    @Test
    public void receiveHandlerQueuesPacketsAndDefersClientStateToTickPre() throws IOException {
        String source = new String(Files.readAllBytes(
                Paths.get("src/main/java/gq/yozakura/module/combat/JumpReset.java")), StandardCharsets.UTF_8);
        int packetBegin = source.indexOf("    public void onPacket(PacketEvent event) {");
        int tickBegin = source.indexOf("    public void onTick(TickEvent event) {");
        int moveBegin = source.indexOf("    public void onMove(MoveInputEvent event) {");

        String packetHandler = source.substring(packetBegin, tickBegin);
        String tickHandler = source.substring(tickBegin, moveBegin);

        assertTrue(packetHandler.contains("pendingPackets.offer(new QueuedPacket"));
        assertFalse(packetHandler.contains("mc.thePlayer"));
        assertFalse(packetHandler.contains("mc.theWorld"));
        assertFalse(packetHandler.contains("jumpKeyGuard"));
        assertTrue(tickHandler.contains("drainPendingPackets();"));
        assertTrue(source.contains("new ConcurrentLinkedQueue<QueuedPacket>()"));
        assertTrue(source.contains("new AtomicLong()"));
    }

    @Test
    public void resetOnlyUsesLocalHurtAndVelocitySignalsAndCleansEveryLifecycleExit() throws IOException {
        String source = new String(Files.readAllBytes(
                Paths.get("src/main/java/gq/yozakura/module/combat/JumpReset.java")), StandardCharsets.UTF_8);
        int statusBegin = source.indexOf("    private void handleEntityStatus(S19PacketEntityStatus packet) {");
        int velocityBegin = source.indexOf("    private void handleVelocity(S12PacketEntityVelocity packet) {");
        int blockedBegin = source.indexOf("    private boolean isResetBlocked() {");
        int moveBegin = source.indexOf("    public void onMove(MoveInputEvent event) {");
        int enabledBegin = source.indexOf("    public void onEnabled() {");

        String statusHandler = source.substring(statusBegin, velocityBegin);
        String velocityHandler = source.substring(velocityBegin, blockedBegin);
        String moveHandler = source.substring(moveBegin, enabledBegin);

        assertTrue(statusHandler.contains("entity == mc.thePlayer"));
        assertTrue(statusHandler.contains("packet.getOpCode() == 2"));
        assertTrue(velocityHandler.contains("packet.getEntityID() == mc.thePlayer.getEntityId()"));
        assertTrue(moveHandler.contains("if (isResetBlocked())"));
        assertTrue(moveHandler.contains("resetTransientState();"));
        assertTrue(source.contains("public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event)"));
    }

    @Test
    public void chanceIsPersistedAndExposedInTheModernCombatSettings() throws IOException {
        String moduleSource = new String(Files.readAllBytes(
                Paths.get("src/main/java/gq/yozakura/module/combat/JumpReset.java")), StandardCharsets.UTF_8);
        String webSource = new String(Files.readAllBytes(
                Paths.get("src/main/java/gq/yozakura/ui/click/web/ModernWebClickGuiController.java")),
                StandardCharsets.UTF_8);
        int jumpResetBegin = webSource.indexOf("add(\"JumpReset\", \"Combat\"");
        int nextModuleBegin = webSource.indexOf("add(\"BowAimBot\", \"Combat\"", jumpResetBegin);
        String jumpResetSettings = webSource.substring(jumpResetBegin, nextModuleBegin);

        assertTrue(moduleSource.contains("new PercentProperty(\"Chance\", 100)"));
        assertTrue(moduleSource.contains(
                "controller.acceptVelocity(Boolean.TRUE.equals(fakeCheck.getValue()), chance.getValue())"));
        assertTrue(jumpResetSettings.contains(".number(\"Chance\", 100.0D, 0.0D, 100.0D, 1.0D);"));
    }
}
