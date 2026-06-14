package gq.yozakura.module.combat;

import gq.yozakura.module.Module;
import gq.yozakura.module.ModuleType;
import gq.yozakura.util.minecraft.RotationUtil;
import gq.yozakura.util.time.TimerUtil;
import gq.yozakura.value.Mode;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ThreadLocalRandom;

public class KillAura extends Module {
    public enum AttackMode {
        SINGLE,
        SWITCH,
        MULTI
    }

    public enum RotationMode {
        NONE,
        SILENT,
        LOCK_VIEW,
        LIQUID_BOUNCE
    }

    public enum AutoBlockMode {
        NONE,
        VANILLA,
        SPOOF,
        HYPIXEL,
        BLINK,
        INTERACT,
        FAKE
    }

    public static EntityLivingBase target;
    public static final List<EntityLivingBase> targets = new ArrayList<EntityLivingBase>();

    private static final String ROTATION_HANDLER_NAME = "vapulite_killaura_rotation";

    private final TimerUtil attackTimer = new TimerUtil();
    private final Mode<AttackMode> mode = new Mode<AttackMode>("Mode", "Mode", AttackMode.values(), AttackMode.SINGLE);
    private final Mode<CombatUtil.TargetPriority> priority =
            new Mode<CombatUtil.TargetPriority>("Priority", "Priority", CombatUtil.TargetPriority.values(), CombatUtil.TargetPriority.DISTANCE);
    private final Mode<RotationMode> rotations =
            new Mode<RotationMode>("Rotations", "Rotations", RotationMode.values(), RotationMode.SILENT);
    private final Mode<AutoBlockMode> autoBlock =
            new Mode<AutoBlockMode>("AutoBlock", "AutoBlock", AutoBlockMode.values(), AutoBlockMode.HYPIXEL);
    private final Numbers<Double> rangeValue = new Numbers<Double>("Range", "Range", 4.2D, 1.0D, 6.0D, 0.1D);
    private final Numbers<Double> swingRange = new Numbers<Double>("Swing Range", "SwingRange", 4.2D, 1.0D, 6.0D, 0.1D);
    private final Numbers<Double> blockRange = new Numbers<Double>("Block Range", "BlockRange", 6.0D, 1.0D, 8.0D, 0.1D);
    private final Numbers<Double> minCps = new Numbers<Double>("Min CPS", "MinCPS", 10.0D, 1.0D, 20.0D, 1.0D);
    private final Numbers<Double> maxCps = new Numbers<Double>("Max CPS", "MaxCPS", 14.0D, 1.0D, 20.0D, 1.0D);
    private final Numbers<Double> fov = new Numbers<Double>("FOV", "FOV", 180.0D, 10.0D, 180.0D, 5.0D);
    private final Numbers<Double> turnSpeed = new Numbers<Double>("Turn Speed", "TurnSpeed", 90.0D, 5.0D, 180.0D, 1.0D);
    private final Numbers<Double> hurtTime = new Numbers<Double>("Hurt Time", "HurtTime", 10.0D, 0.0D, 10.0D, 1.0D);
    private final Numbers<Double> switchDelay = new Numbers<Double>("Switch Delay", "SwitchDelay", 150.0D, 0.0D, 1000.0D, 25.0D);
    private final Option<Boolean> throughWalls = new Option<Boolean>("Through Walls", "ThroughWalls", true);
    private final Option<Boolean> requirePress = new Option<Boolean>("Require Press", "RequirePress", false);
    private final Option<Boolean> weaponsOnly = new Option<Boolean>("Weapons Only", "WeaponsOnly", true);
    private final Option<Boolean> players = new Option<Boolean>("Players", "Players", true);
    private final Option<Boolean> mobs = new Option<Boolean>("Mobs", "Mobs", false);
    private final Option<Boolean> animals = new Option<Boolean>("Animals", "Animals", false);
    private final Option<Boolean> moveFix = new Option<Boolean>("Move Fix", "MoveFix", true);

    private Channel rotationChannel;
    private Field channelField;
    private float serverYaw;
    private float serverPitch;
    private boolean rotated;
    private boolean blockingState;
    private boolean fakeBlockState;
    private int blockTick;
    private int switchIndex;
    private long lastSwitchAt;
    private int attackDelay;
    private final Queue<QueuedPacket> blinkPackets = new ArrayDeque<QueuedPacket>();

    public KillAura() {
        super("KillAura", Keyboard.KEY_NONE, ModuleType.Combat, "Leader-style silent aura");
        addValues(mode, priority, rotations, autoBlock, rangeValue, swingRange, blockRange, minCps, maxCps,
                fov, turnSpeed, hurtTime, switchDelay, throughWalls, requirePress, weaponsOnly,
                players, mobs, animals, moveFix);
        Chinese = "杀戮光环";
    }

    @Override
    public void enable() {
        target = null;
        targets.clear();
        serverYaw = isInGame() ? mc.thePlayer.rotationYaw : 0.0F;
        serverPitch = isInGame() ? mc.thePlayer.rotationPitch : 0.0F;
        rotated = false;
        blockingState = false;
        fakeBlockState = false;
        blockTick = 0;
        switchIndex = 0;
        lastSwitchAt = 0L;
        attackDelay = nextAttackDelay();
        attackTimer.reset();
        injectRotationHandler();
    }

    @Override
    public void disable() {
        stopBlock();
        target = null;
        targets.clear();
        rotated = false;
        fakeBlockState = false;
        releaseBlinkPackets();
        removeRotationHandler();
        resetVisibleRotation();
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        if (!isInGame() || CombatUtil.shouldPauseForScreen()) {
            clearState();
            return;
        }

        injectRotationHandler();
        releaseDueBlinkPackets();
        collectTargets();
        target = selectTarget();
        boolean attack = target != null && canAttack();
        boolean block = target != null && canAutoBlock();

        if (target != null && rotations.getValue() != RotationMode.NONE) {
            updateServerRotation(target);
            rotated = true;
            maintainVisibleRotation();
        } else {
            rotated = false;
        }

        updateAutoBlock(block && attack);

        if (attack && attackTimer.delay(attackDelay)) {
            performAttack(target);
            attackDelay = nextAttackDelay();
            attackTimer.reset();
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START || event.phase == TickEvent.Phase.END) {
            maintainVisibleRotation();
            maintainBlockVisual();
        }
    }

    private void collectTargets() {
        targets.clear();
        targets.addAll(CombatUtil.collectTargets(swingRange.getValue(), fov.getValue(), players.getValue(),
                mobs.getValue(), animals.getValue(), throughWalls.getValue()));
        CombatUtil.sortTargets(targets, priority.getValue());
    }

    private EntityLivingBase selectTarget() {
        if (targets.isEmpty()) {
            return null;
        }
        if (mode.getValue() == AttackMode.SWITCH && targets.size() > 1) {
            long now = System.currentTimeMillis();
            if (now - lastSwitchAt >= switchDelay.getValue().longValue()) {
                switchIndex = (switchIndex + 1) % targets.size();
                lastSwitchAt = now;
            }
            if (switchIndex >= targets.size()) {
                switchIndex = 0;
            }
            return targets.get(switchIndex);
        }
        return targets.get(0);
    }

    private boolean canAttack() {
        if (target == null || target.hurtTime > hurtTime.getValue().intValue()) {
            return false;
        }
        if (Boolean.TRUE.equals(requirePress.getValue()) && !mc.gameSettings.keyBindAttack.isKeyDown()) {
            return false;
        }
        if (Boolean.TRUE.equals(weaponsOnly.getValue()) && !CombatUtil.isHoldingWeapon()) {
            return false;
        }
        return mc.thePlayer.getDistanceToEntity(target) <= rangeValue.getValue();
    }

    private boolean canAutoBlock() {
        AutoBlockMode current = autoBlock.getValue() == null ? AutoBlockMode.NONE : autoBlock.getValue();
        if (current == AutoBlockMode.NONE || target == null || !isHoldingSword()) {
            return false;
        }
        return mc.thePlayer.getDistanceToEntity(target) <= blockRange.getValue();
    }

    private void updateServerRotation(EntityLivingBase entity) {
        float[] rotations = rotationsToEntity(entity);
        RotationMode current = this.rotations.getValue() == null ? RotationMode.SILENT : this.rotations.getValue();
        if (!rotated || current == RotationMode.SILENT || current == RotationMode.LOCK_VIEW) {
            serverYaw = quantize(rotations[0]);
            serverPitch = quantize(MathHelper.clamp_float(rotations[1], -90.0F, 90.0F));
            return;
        }
        float speed = turnSpeed.getValue().floatValue();
        serverYaw = quantize(RotationUtil.limitAngleChange(serverYaw, rotations[0], speed));
        serverPitch = quantize(RotationUtil.limitAngleChange(serverPitch, rotations[1], speed));
        serverPitch = MathHelper.clamp_float(serverPitch, -90.0F, 90.0F);
    }

    private float[] rotationsToEntity(EntityLivingBase entity) {
        AxisAlignedBB box = entity.getEntityBoundingBox().expand(entity.getCollisionBorderSize(),
                entity.getCollisionBorderSize(), entity.getCollisionBorderSize());
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 point = clampVecToBox(eyes, box);
        double dx = point.xCoord - eyes.xCoord;
        double dy = point.yCoord - eyes.yCoord;
        double dz = point.zCoord - eyes.zCoord;
        double dist = MathHelper.sqrt_double(dx * dx + dz * dz);
        float yaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        float pitch = (float) (-(Math.atan2(dy, dist) * 180.0D / Math.PI));
        return new float[]{yaw, pitch};
    }

    private Vec3 clampVecToBox(Vec3 point, AxisAlignedBB box) {
        return new Vec3(MathHelper.clamp_double(point.xCoord, box.minX, box.maxX),
                MathHelper.clamp_double(point.yCoord, box.minY, box.maxY),
                MathHelper.clamp_double(point.zCoord, box.minZ, box.maxZ));
    }

    private void performAttack(EntityLivingBase entity) {
        if (entity == null) {
            return;
        }
        if (mode.getValue() == AttackMode.MULTI) {
            for (EntityLivingBase living : new ArrayList<EntityLivingBase>(targets)) {
                if (mc.thePlayer.getDistanceToEntity(living) <= rangeValue.getValue()) {
                    sendAttack(living);
                }
            }
            return;
        }
        sendAttack(entity);
    }

    private void sendAttack(EntityLivingBase entity) {
        mc.thePlayer.swingItem();
        mc.thePlayer.sendQueue.addToSendQueue(new C02PacketUseEntity(entity, C02PacketUseEntity.Action.ATTACK));
    }

    private void updateAutoBlock(boolean shouldBlock) {
        if (!shouldBlock) {
            stopBlock();
            blockTick = 0;
            fakeBlockState = false;
            return;
        }
        AutoBlockMode current = autoBlock.getValue() == null ? AutoBlockMode.NONE : autoBlock.getValue();
        if (current == AutoBlockMode.FAKE) {
            fakeBlockState = true;
            maintainBlockVisual();
            return;
        }
        if (current == AutoBlockMode.SPOOF && blockTick == 0) {
            int slot = emptyHotbarSlot();
            mc.thePlayer.sendQueue.addToSendQueue(new C09PacketHeldItemChange(slot));
            mc.thePlayer.sendQueue.addToSendQueue(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
        }
        if ((current == AutoBlockMode.HYPIXEL || current == AutoBlockMode.BLINK) && blockingState && blockTick % 2 == 1) {
            stopBlock();
            blockTick++;
            return;
        }
        if (!blockingState) {
            startBlock();
        }
        fakeBlockState = true;
        blockTick++;
        maintainBlockVisual();
    }

    private void startBlock() {
        ItemStack stack = mc.thePlayer.getHeldItem();
        if (stack == null || !(stack.getItem() instanceof ItemSword)) {
            return;
        }
        mc.thePlayer.sendQueue.addToSendQueue(new C08PacketPlayerBlockPlacement(stack));
        mc.thePlayer.setItemInUse(stack, stack.getMaxItemUseDuration());
        blockingState = true;
    }

    private void stopBlock() {
        if (blockingState && isInGame()) {
            mc.thePlayer.sendQueue.addToSendQueue(new C07PacketPlayerDigging(
                    C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
        }
        if (isInGame()) {
            mc.thePlayer.stopUsingItem();
        }
        blockingState = false;
    }

    private void maintainBlockVisual() {
        if (!isInGame() || (!blockingState && !fakeBlockState)) {
            return;
        }
        ItemStack stack = mc.thePlayer.getHeldItem();
        if (stack != null && stack.getItem() instanceof ItemSword) {
            mc.thePlayer.setItemInUse(stack, stack.getMaxItemUseDuration());
        }
    }

    private boolean isHoldingSword() {
        ItemStack stack = mc.thePlayer.getHeldItem();
        return stack != null && stack.getItem() instanceof ItemSword;
    }

    private int emptyHotbarSlot() {
        for (int slot = 0; slot < 9; slot++) {
            if (slot != mc.thePlayer.inventory.currentItem && mc.thePlayer.inventory.getStackInSlot(slot) == null) {
                return slot;
            }
        }
        return (mc.thePlayer.inventory.currentItem + 1) % 9;
    }

    private int nextAttackDelay() {
        return CombatUtil.nextDelay(minCps.getValue(), maxCps.getValue());
    }

    private float quantize(float angle) {
        float f = mc.gameSettings.mouseSensitivity * 0.6F + 0.2F;
        float gcd = f * f * f * 1.2F;
        return Math.round(angle / gcd) * gcd;
    }

    private void syncVisibleRotation(float yaw) {
        if (mc.thePlayer == null) {
            return;
        }
        mc.thePlayer.rotationYawHead = yaw;
        mc.thePlayer.prevRotationYawHead = yaw;
        mc.thePlayer.renderYawOffset = yaw;
        mc.thePlayer.prevRenderYawOffset = yaw;
    }

    private void maintainVisibleRotation() {
        if (getState() && isInGame() && rotated && target != null) {
            syncVisibleRotation(serverYaw);
        }
    }

    private void resetVisibleRotation() {
        if (mc.thePlayer != null) {
            syncVisibleRotation(mc.thePlayer.rotationYaw);
        }
    }

    private void clearState() {
        target = null;
        targets.clear();
        rotated = false;
        fakeBlockState = false;
        stopBlock();
        releaseBlinkPackets();
        resetVisibleRotation();
    }

    public boolean isBlocking() {
        return isHoldingSword() && (blockingState || fakeBlockState);
    }

    public EntityLivingBase getTarget() {
        return target;
    }

    public static void assistFaceEntity(Entity entity, float yaw, float pitch) {
        CombatUtil.faceEntity(entity, yaw, pitch, pitch <= 0.0F, 0.0F);
    }

    public static float updateRotation(float current, float targetYaw, float maxTurn) {
        return CombatUtil.updateRotation(current, targetYaw, maxTurn);
    }

    private boolean shouldRewriteRotationPacket() {
        return getState() && isInGame() && rotated && target != null && rotations.getValue() != RotationMode.NONE;
    }

    private C03PacketPlayer rewriteRotationPacket(C03PacketPlayer packet) {
        if (!shouldRewriteRotationPacket() || packet == null) {
            return null;
        }
        boolean onGround = packet.isOnGround();
        if (packet instanceof C03PacketPlayer.C06PacketPlayerPosLook
                || packet instanceof C03PacketPlayer.C04PacketPlayerPosition) {
            return new C03PacketPlayer.C06PacketPlayerPosLook(packet.getPositionX(), packet.getPositionY(),
                    packet.getPositionZ(), serverYaw, serverPitch, onGround);
        }
        return new C03PacketPlayer.C05PacketPlayerLook(serverYaw, serverPitch, onGround);
    }

    private boolean shouldBlinkPacket(Object packet) {
        return getState() && autoBlock.getValue() == AutoBlockMode.BLINK
                && (packet instanceof C08PacketPlayerBlockPlacement || packet instanceof C07PacketPlayerDigging);
    }

    private boolean queueBlinkPacket(ChannelHandlerContext ctx, Object packet, ChannelPromise promise) {
        if (blinkPackets.size() > 10) {
            releaseBlinkPackets();
            return false;
        }
        blinkPackets.offer(new QueuedPacket(ctx, packet, promise, System.currentTimeMillis()
                + ThreadLocalRandom.current().nextLong(75L, 130L)));
        return true;
    }

    private void releaseDueBlinkPackets() {
        long now = System.currentTimeMillis();
        while (!blinkPackets.isEmpty() && blinkPackets.peek().releaseAt <= now) {
            writeQueued(blinkPackets.poll());
        }
    }

    private void releaseBlinkPackets() {
        while (!blinkPackets.isEmpty()) {
            writeQueued(blinkPackets.poll());
        }
    }

    private void writeQueued(QueuedPacket packet) {
        if (packet != null && packet.ctx != null && packet.packet != null && packet.promise != null) {
            packet.ctx.writeAndFlush(packet.packet, packet.promise);
        }
    }

    private void injectRotationHandler() {
        if (!isInGame() || mc.getNetHandler() == null) {
            return;
        }
        try {
            NetworkManager manager = mc.getNetHandler().getNetworkManager();
            Channel channel = getChannel(manager);
            if (channel == null || !channel.isOpen()) {
                return;
            }
            if (rotationChannel != null && rotationChannel != channel) {
                removeRotationHandler();
            }
            if (channel.pipeline().get(ROTATION_HANDLER_NAME) == null) {
                channel.pipeline().addBefore("packet_handler", ROTATION_HANDLER_NAME, new SilentRotationPacketHandler(this));
            }
            rotationChannel = channel;
        } catch (Throwable ignored) {
            rotationChannel = null;
        }
    }

    private void removeRotationHandler() {
        Channel channel = rotationChannel;
        rotationChannel = null;
        if (channel == null) {
            return;
        }
        try {
            if (channel.isOpen() && channel.pipeline().get(ROTATION_HANDLER_NAME) != null) {
                channel.pipeline().remove(ROTATION_HANDLER_NAME);
            }
        } catch (Throwable ignored) {
        }
    }

    private Channel getChannel(NetworkManager manager) {
        try {
            if (channelField == null) {
                for (String name : new String[]{"channel", "field_150746_k"}) {
                    try {
                        channelField = NetworkManager.class.getDeclaredField(name);
                        channelField.setAccessible(true);
                        break;
                    } catch (Throwable ignored) {
                    }
                }
            }
            Object value = channelField == null ? null : channelField.get(manager);
            return value instanceof Channel ? (Channel) value : null;
        } catch (Throwable ignored) {
            channelField = null;
            return null;
        }
    }

    private static final class QueuedPacket {
        final ChannelHandlerContext ctx;
        final Object packet;
        final ChannelPromise promise;
        final long releaseAt;

        QueuedPacket(ChannelHandlerContext ctx, Object packet, ChannelPromise promise, long releaseAt) {
            this.ctx = ctx;
            this.packet = packet;
            this.promise = promise;
            this.releaseAt = releaseAt;
        }
    }

    private static final class SilentRotationPacketHandler extends ChannelDuplexHandler {
        private final KillAura module;

        SilentRotationPacketHandler(KillAura module) {
            this.module = module;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (msg instanceof C03PacketPlayer) {
                C03PacketPlayer replacement = module.rewriteRotationPacket((C03PacketPlayer) msg);
                if (replacement != null) {
                    super.write(ctx, replacement, promise);
                    return;
                }
            }
            if (module.shouldBlinkPacket(msg) && module.queueBlinkPacket(ctx, msg, promise)) {
                return;
            }
            module.releaseDueBlinkPackets();
            super.write(ctx, msg, promise);
        }
    }
}
