package gq.yozakura.module.combat;

import gq.yozakura.bridge.PacketBridgeSupport;
import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.event.bus.types.EventType;
import gq.yozakura.event.bus.types.Priority;
import gq.yozakura.event.bridge.AttackEvent;
import gq.yozakura.event.bridge.PacketAcceptedEvent;
import gq.yozakura.event.bridge.PacketEvent;
import gq.yozakura.event.bridge.PacketWriteEvent;
import gq.yozakura.manager.RotationState;
import gq.yozakura.module.Module;
import gq.yozakura.module.ModuleType;
import gq.yozakura.util.module.PacketUtil;
import gq.yozakura.value.Mode;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;

import java.util.concurrent.ThreadLocalRandom;

public final class Displace extends Module {
    private enum Direction {
        LEFT,
        RIGHT,
        RANDOM
    }

    private final Mode<Direction> direction = new Mode<Direction>(
            "Direction", "Direction", Direction.values(), Direction.RANDOM);
    private final Numbers<Double> angle = new Numbers<Double>(
            "Angle", "Angle", 90.0D, 1.0D, 180.0D, 1.0D);
    private final Numbers<Double> cooldown = new Numbers<Double>(
            "Cooldown", "Cooldown", 100.0D, 0.0D, 1000.0D, 10.0D);
    private final Option<Boolean> onlyPlayers = new Option<Boolean>(
            "Only Players", "OnlyPlayers", true);
    private final Option<Boolean> requireSprint = new Option<Boolean>(
            "Require Sprint", "RequireSprint", true);
    private final Option<Boolean> ensureSprint = new Option<Boolean>(
            "Ensure Sprint", "EnsureSprint", true);

    private Entity pendingTarget;
    private int pendingAttacks;
    private long lastActivationAt;
    private boolean awaitingAttackAcceptance;
    private long displacedAttackWriteId = PacketAcceptedEvent.NO_WRITE_ID;
    private float restoreYaw;
    private float restorePitch;

    public Displace() {
        super("Displace", Keyboard.KEY_NONE, ModuleType.Combat,
                "Flick server rotation around sprint attacks to redirect target knockback");
        addValues(direction, angle, cooldown, onlyPlayers, requireSprint, ensureSprint);
        Chinese = "击退位移";
    }

    @Override
    public void enable() {
        resetPending();
        lastActivationAt = 0L;
    }

    @Override
    public void disable() {
        resetPending();
        awaitingAttackAcceptance = false;
        displacedAttackWriteId = PacketAcceptedEvent.NO_WRITE_ID;
    }

    @EventTarget(Priority.HIGHEST)
    public void onAttack(AttackEvent event) {
        if (event == null || event.isCancelled()) {
            return;
        }
        arm(event.getTarget());
    }

    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        if (event == null || event.button != 0 || !event.buttonstate
                || !isInGame() || mc.objectMouseOver == null) {
            return;
        }
        arm(mc.objectMouseOver.entityHit);
    }

    @EventTarget(Priority.HIGHEST)
    public void onPacket(PacketEvent event) {
        if (event == null || event.getType() != EventType.SEND
                || !isAttackPacket(event.getPacket()) || !isInGame()) {
            return;
        }
        C02PacketUseEntity attack = (C02PacketUseEntity) event.getPacket();
        Entity target = attack.getEntityFromWorld(mc.theWorld);
        consumeArmedAttack(target);
        if (!canActivate(target)) {
            return;
        }

        float baseYaw = RotationState.isActived()
                ? RotationState.getRotationYawHead()
                : mc.thePlayer.rotationYaw;
        float basePitch = RotationState.isActived()
                ? RotationState.getRotationPitch()
                : mc.thePlayer.rotationPitch;
        boolean right = chooseRight();
        float displacedYaw = DisplacePlan.displacedYaw(
                baseYaw, angle.getValue().floatValue(), right);

        restoreYaw = baseYaw;
        restorePitch = basePitch;
        if (Boolean.TRUE.equals(ensureSprint.getValue())) {
            C0BPacketEntityAction sprintPacket = new C0BPacketEntityAction(
                    mc.thePlayer, C0BPacketEntityAction.Action.START_SPRINTING);
            PacketUtil.sendPacketNoEvent(sprintPacket);
        }
        C03PacketPlayer spoofLook = new C03PacketPlayer.C05PacketPlayerLook(
                displacedYaw, basePitch, mc.thePlayer.onGround);
        PacketBridgeSupport.markNonCanonicalPlayerPacket(spoofLook);
        PacketBridgeSupport.markPreservePlayerLook(spoofLook);
        PacketUtil.sendPacketNoEvent(spoofLook);

        awaitingAttackAcceptance = true;
        lastActivationAt = monotonicTimeMillis();
    }

    @EventTarget(Priority.HIGHEST)
    public void onPacketAccepted(PacketAcceptedEvent event) {
        if (event == null) {
            return;
        }
        Packet<?> packet = event.getPacket();
        if (awaitingAttackAcceptance && isAttackPacket(packet)) {
            event.requestStrictOriginalPacketOrder();
            displacedAttackWriteId = event.getWriteId();
            awaitingAttackAcceptance = false;
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onPacketWrite(PacketWriteEvent event) {
        if (event == null || event.getWriteId() != displacedAttackWriteId) {
            return;
        }
        displacedAttackWriteId = PacketAcceptedEvent.NO_WRITE_ID;
        if (event.isSuccess() && isInGame()) {
            C03PacketPlayer restoreLook = new C03PacketPlayer.C05PacketPlayerLook(
                    restoreYaw, restorePitch, mc.thePlayer.onGround);
            PacketBridgeSupport.markNonCanonicalPlayerPacket(restoreLook);
            PacketBridgeSupport.markPreservePlayerLook(restoreLook);
            PacketUtil.sendPacketNoEvent(restoreLook);
        }
    }

    private void arm(Entity target) {
        if (!canActivate(target)) {
            return;
        }
        pendingTarget = target;
        pendingAttacks = 1;
    }

    private boolean consumeArmedAttack(Entity target) {
        if (pendingAttacks <= 0 || pendingTarget != null && pendingTarget != target) {
            return false;
        }
        pendingAttacks--;
        if (pendingAttacks <= 0) {
            pendingTarget = null;
        }
        return true;
    }

    private boolean canActivate(Entity target) {
        if (!isInGame() || target == null || target == mc.thePlayer) {
            return false;
        }
        if (Boolean.TRUE.equals(onlyPlayers.getValue()) && !(target instanceof EntityPlayer)) {
            return false;
        }
        if (Boolean.TRUE.equals(requireSprint.getValue()) && !mc.thePlayer.isSprinting()) {
            return false;
        }
        long now = monotonicTimeMillis();
        return now - lastActivationAt >= cooldown.getValue().longValue();
    }

    private boolean chooseRight() {
        Direction value = direction.getValue();
        if (value == Direction.RIGHT) {
            return true;
        }
        if (value == Direction.LEFT) {
            return false;
        }
        return ThreadLocalRandom.current().nextBoolean();
    }

    private void resetPending() {
        pendingTarget = null;
        pendingAttacks = 0;
    }

    private static boolean isAttackPacket(Packet<?> packet) {
        return packet instanceof C02PacketUseEntity
                && ((C02PacketUseEntity) packet).getAction() == C02PacketUseEntity.Action.ATTACK;
    }

    private static long monotonicTimeMillis() {
        return System.nanoTime() / 1000000L;
    }
}
