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
    public void referenceAutoBlockKeepsInteractionsOutsideTheAttackBoundary() throws IOException {
        String aura = source("src/main/java/gq/yozakura/module/combat/KillAura.java");
        int attackBegin = aura.indexOf(
                "    private boolean performAttack(float yaw, float pitch, boolean allowReferenceRelease) {");
        int attackEnd = aura.indexOf("    private boolean startBlock()", attackBegin);
        String attack = aura.substring(attackBegin, attackEnd);
        int interactBegin = aura.indexOf("    private boolean interactBlockAfterAttack(");
        int interactEnd = aura.indexOf(
                "    private boolean updateLeaderLegitAutoBlockCycle(", interactBegin);
        String interaction = aura.substring(interactBegin, interactEnd);

        assertTrue("The actual attack remains the one explicit C02 ATTACK action",
                attack.contains("new C02PacketUseEntity(this.attackTarget.getEntity(), Action.ATTACK)"));
        assertFalse("Reference-mode interactions must not be mixed into the normal attack submission",
                attack.contains("Action.INTERACT") || attack.contains("new Vec3("));
        assertTrue("Reference AutoBlock may keep its isolated interaction plus vanilla sword-use helper",
                interaction.contains("Action.INTERACT") && interaction.contains("this.startBlock()"));
        assertFalse(aura.contains("sendExpoInteractPackets"));
    }

    @Test
    public void vanillaActionsKeepTheirOriginalOutboundPositionUnlessASeparateModuleDelaysThem() throws IOException {
        assertNativeActionOrder(combinedStandaloneSource());
        assertNativeActionOrder(combinedForgeSource());
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

    private static void assertNativeActionOrder(String source) {
        assertTrue("The bridge must preserve vanilla action ordering by default",
                source.contains("boolean preserveOriginalPacketOrder = true;"));
        assertTrue("Only an explicit current-rotation dependency may override the default ordering guarantee",
                source.contains("afterCurrentRotation = accepted.isAfterCurrentRotationRequired();")
                        && source.contains("preserveOriginalPacketOrder = !afterCurrentRotation")
                        && source.contains("accepted.isOriginalPacketOrderRequired()"));
        assertTrue("Any retained legacy queue gate must remain behind the source-order guard",
                source.contains("if (!skipPacketEvent && !preserveOriginalPacketOrder && isPostSensitiveAction(packet))"));
    }
}
