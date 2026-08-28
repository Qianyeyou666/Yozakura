package gq.yozakura.module.combat;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class DisplaceContractTest {
    @Test
    public void attackEventArmsOneShotDisplacementAndRequiresSprintHit() throws Exception {
        String source = read("src/main/java/gq/yozakura/module/combat/Displace.java");

        assertTrue(source.contains("onAttack(AttackEvent event)"));
        assertTrue(source.contains("mc.thePlayer.isSprinting()"));
        assertTrue(source.contains("pendingTarget"));
        assertTrue(source.contains("pendingAttacks = 1"));
    }

    @Test
    public void ownedAttackIsWrappedBySpoofAndRestoreLookPackets() throws Exception {
        String source = read("src/main/java/gq/yozakura/module/combat/Displace.java");

        assertTrue(source.contains("new C03PacketPlayer.C05PacketPlayerLook"));
        assertTrue(source.contains("PacketUtil.sendPacketNoEvent"));
        assertTrue(!source.contains("PacketUtil.sendPacket(new C03PacketPlayer.C05PacketPlayerLook"));
        assertTrue(source.contains("event.requestStrictOriginalPacketOrder()"));
        assertTrue(source.contains("C02PacketUseEntity.Action.ATTACK"));
        String accepted = read("src/main/java/gq/yozakura/event/bridge/PacketAcceptedEvent.java");
        assertTrue(accepted.contains("originalPacketOrderRequired = true;\n        afterCurrentRotationRequired = false;"));
    }

    @Test
    public void spoofLookBypassesCanonicalRotationRewrite() throws Exception {
        String source = read("src/main/java/gq/yozakura/module/combat/Displace.java");
        String support = read("src/main/java/gq/yozakura/bridge/PacketBridgeSupport.java");
        String bridge = read("src/main/java/gq/yozakura/bridge/BasePacketBridgeHandler.java");

        assertTrue(source.contains("PacketBridgeSupport.markPreservePlayerLook"));
        assertTrue(source.contains("PacketUtil.sendPacketNoEvent"));
        assertTrue(support.contains("consumePreservePlayerLook"));
        assertTrue(bridge.contains("boolean preservePlayerLook"));
        assertTrue(bridge.contains("if (preservePlayerLook)"));
        assertTrue(bridge.contains("super.write(ctx, packet, promise);"));
    }

    @Test
    public void displacedAttackOrderCannotBeOverriddenByLaterRotationListeners() throws Exception {
        String source = read("src/main/java/gq/yozakura/module/combat/Displace.java");
        String accepted = read("src/main/java/gq/yozakura/event/bridge/PacketAcceptedEvent.java");

        assertTrue(source.contains("event.requestStrictOriginalPacketOrder()"));
        assertTrue(accepted.contains("strictOriginalPacketOrderRequired"));
        assertTrue(accepted.contains("if (strictOriginalPacketOrderRequired)"));
    }

    @Test
    public void actualAttackPacketCanActivateWithoutForgeMouseArming() throws Exception {
        String source = read("src/main/java/gq/yozakura/module/combat/Displace.java");

        assertTrue(source.contains("Entity target = attack.getEntityFromWorld(mc.theWorld);"));
        assertTrue(source.contains("consumeArmedAttack(target);"));
        assertTrue(source.contains("if (!canActivate(target))"));
        assertTrue(source.contains("onMouse(MouseEvent event)"));
    }

    @Test
    public void displacementIsRelativeToCurrentSilentServerAim() throws Exception {
        String source = read("src/main/java/gq/yozakura/module/combat/Displace.java");

        assertTrue(source.contains("RotationState.isActived()"));
        assertTrue(source.contains("RotationState.getRotationYawHead()"));
        assertTrue(source.contains("RotationState.getRotationPitch()"));
    }

    @Test
    public void serverSprintIsEstablishedBeforeDisplacedLookAndAttack() throws Exception {
        String source = read("src/main/java/gq/yozakura/module/combat/Displace.java");

        int sprint = source.indexOf("C0BPacketEntityAction.Action.START_SPRINTING");
        int look = source.indexOf("new C03PacketPlayer.C05PacketPlayerLook");
        assertTrue(sprint >= 0);
        assertTrue(look > sprint);
        assertTrue(source.contains("PacketUtil.sendPacketNoEvent(sprintPacket)"));
        assertTrue(source.contains("\"Ensure Sprint\""));
    }

    @Test
    public void moduleSupportsDirectionAngleCooldownAndManualAttacks() throws Exception {
        String source = read("src/main/java/gq/yozakura/module/combat/Displace.java");

        assertTrue(source.contains("Direction"));
        assertTrue(source.contains("Angle"));
        assertTrue(source.contains("Cooldown"));
        assertTrue(source.contains("onMouse(MouseEvent event)"));
    }

    @Test
    public void moduleIsRegistered() throws Exception {
        String source = read("src/main/java/gq/yozakura/manager/ModuleManager.java");
        assertTrue(source.contains("addModule(\"Displace\""));
    }

    private static String read(String relativePath) throws Exception {
        return new String(Files.readAllBytes(Paths.get(relativePath)), StandardCharsets.UTF_8);
    }
}
