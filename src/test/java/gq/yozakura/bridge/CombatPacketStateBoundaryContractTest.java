package gq.yozakura.bridge;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CombatPacketStateBoundaryContractTest {
    @Test
    public void bridgeNeverSuppressesVanillaSprintStatePacketsForSilentRotation() throws IOException {
        String movement = source("src/main/java/gq/yozakura/bridge/MovementInputBridge.java");
        String standalone = source("src/main/java/gq/yozakura/bridge/StandaloneEventBridge.java");
        String forge = source("src/main/java/gq/yozakura/bridge/YozakuraEventBridge.java");

        assertFalse("Movement correction must not mutate the vanilla sprint state machine",
                movement.contains("shouldBlockSprintPacket")
                        || movement.contains("suppressSprintKey")
                        || movement.contains("stopSprint("));
        assertFalse("Lunar must forward a real START_SPRINTING packet in its original lifecycle",
                standalone.contains("SEND_BLOCKED_SPRINT") || standalone.contains("shouldBlockSprintPacket"));
        assertFalse("Forge must follow the same unmodified sprint-state contract",
                forge.contains("SEND_BLOCKED_SPRINT") || forge.contains("shouldBlockSprintPacket"));
    }

    @Test
    public void autoBlockDoesNotInjectInteractPacketsOnAnAttackBoundary() throws IOException {
        String aura = source("src/main/java/gq/yozakura/module/combat/KillAura.java");

        assertTrue("The actual attack remains the one explicit C02 ATTACK action",
                aura.contains("new C02PacketUseEntity(this.attackTarget.getEntity(), Action.ATTACK)"));
        assertFalse("AutoBlock must use the normal use-item path instead of synthetic entity interaction",
                aura.contains("sendExpoInteractPackets") || aura.contains("Action.INTERACT"));
    }

    @Test
    public void vanillaActionsKeepTheirOriginalOutboundPositionUnlessASeparateModuleDelaysThem() throws IOException {
        assertNativeActionOrder(source("src/main/java/gq/yozakura/bridge/StandaloneEventBridge.java"));
        assertNativeActionOrder(source("src/main/java/gq/yozakura/bridge/YozakuraEventBridge.java"));
    }

    @Test
    public void lunarBacktrackUsesTheRealMouseOverBoundaryBeforeVanillaClickHandling() throws IOException {
        String backtrack = source("src/main/java/gq/yozakura/module/combat/Backtrack.java");
        String standalone = source("src/main/java/gq/yozakura/bridge/StandaloneEventBridge.java");

        assertTrue("Lunar Backtrack must update its historical hit after vanilla ray tracing and before click handling",
                backtrack.contains("onMouseOver(MouseOverEvent event)")
                        && backtrack.contains("applyHistoricalHit(event.getPartialTicks())"));
        assertFalse("The standalone bridge must not synthesize a duplicate click event to make Backtrack work",
                standalone.contains("new LeftClickMouseEvent()"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static void assertNativeActionOrder(String source) {
        assertTrue("The bridge must preserve vanilla action ordering by default",
                source.contains("boolean preserveOriginalPacketOrder = true;"));
        assertTrue("Packet listeners may strengthen but never clear the default ordering guarantee",
                source.contains("preserveOriginalPacketOrder = preserveOriginalPacketOrder")
                        && source.contains("|| accepted.isOriginalPacketOrderRequired();"));
        assertTrue("Any retained legacy queue gate must remain behind the source-order guard",
                source.contains("if (!skipPacketEvent && !preserveOriginalPacketOrder && isPostSensitiveAction(packet))"));
    }
}
