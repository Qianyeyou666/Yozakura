package gq.vapulite.Vapu.modules.combat;

import gq.vapulite.Vapu.ModuleType;
import gq.vapulite.Vapu.modules.Module;
import gq.vapulite.Vapu.utils.TimerUtil;
import gq.vapulite.Vapu.utils.RotationUtil;
import gq.vapulite.Vapu.value.Mode;
import gq.vapulite.Vapu.value.Numbers;
import gq.vapulite.Vapu.value.Option;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;

public class KillAura extends Module {
    public enum AuraMode {
        SAFE,
        RAGE
    }

    public enum AttackMode {
        SINGLE,
        SWITCH,
        MULTI
    }

    public enum AimPoint {
        SMART,
        HEAD,
        CHEST,
        LEGS,
        RANDOM
    }

    public enum AutoBlockMode {
        LEGIT,
        PACKET,
        FAKE
    }

    private final TimerUtil timer = new TimerUtil();
    public static EntityLivingBase target;
    public static final List<EntityLivingBase> targets = new ArrayList<EntityLivingBase>();

    private final Numbers<Double> rangeValue = new Numbers<Double>("Range", "Range", 4.2, 1.0, 6.0, 0.1);
    private final Numbers<Double> minCps = new Numbers<Double>("Min CPS", "MinCPS", 8.0, 1.0, 20.0, 1.0);
    private final Numbers<Double> cps = new Numbers<Double>("Max CPS", "Cps", 12.0, 1.0, 20.0, 1.0);
    private final Numbers<Double> fov = new Numbers<Double>("FOV", "FOV", 180.0, 10.0, 180.0, 5.0);
    private final Numbers<Double> yawSpeed = new Numbers<Double>("Yaw Speed", "YawSpeed", 32.0, 1.0, 90.0, 1.0);
    private final Numbers<Double> pitchSpeed = new Numbers<Double>("Pitch Speed", "PitchSpeed", 24.0, 1.0, 90.0, 1.0);
    private final Numbers<Double> hurtTime = new Numbers<Double>("Hurt Time", "HurtTime", 10.0, 0.0, 10.0, 1.0);
    private final Numbers<Double> switchDelay = new Numbers<Double>("Switch Delay", "SwitchDelay", 320.0, 80.0, 1200.0, 10.0);
    private final Numbers<Double> maxTargets = new Numbers<Double>("Max Targets", "MaxTargets", 3.0, 1.0, 8.0, 1.0);
    private final Numbers<Double> rayExpand = new Numbers<Double>("Ray Expand", "RayExpand", 0.12, 0.0, 0.6, 0.01);
    private final Mode<AuraMode> auraMode = new Mode<AuraMode>("Aura Mode", "AuraMode", AuraMode.values(), AuraMode.SAFE);
    private final Mode<AttackMode> mode = new Mode<AttackMode>("Mode", "Mode", AttackMode.values(), AttackMode.SINGLE);
    private final Mode<CombatUtil.TargetPriority> priority =
            new Mode<CombatUtil.TargetPriority>("Priority", "Priority", CombatUtil.TargetPriority.values(), CombatUtil.TargetPriority.DISTANCE);
    private final Mode<AimPoint> aimPoint = new Mode<AimPoint>("Aim Point", "AimPoint", AimPoint.values(), AimPoint.SMART);
    private final Mode<AutoBlockMode> autoBlockMode =
            new Mode<AutoBlockMode>("AutoBlock Mode", "AutoBlockMode", AutoBlockMode.values(), AutoBlockMode.LEGIT);
    private final Option<Boolean> autoblock = new Option<Boolean>("AutoBlock", "AutoBlock", true);
    private final Option<Boolean> legitRequireAttack = new Option<Boolean>("Require Attack", "LegitRequireAttack", true);
    private final Option<Boolean> rayCast = new Option<Boolean>("Ray Cast", "RayCast", false);
    private final Option<Boolean> randomAim = new Option<Boolean>("Random Aim", "RandomAim", true);
    private final Option<Boolean> weaponOnly = new Option<Boolean>("Only Weapon", "OnlyWeapon", false);
    private final Option<Boolean> players = new Option<Boolean>("Players", "Players", true);
    private final Option<Boolean> mobs = new Option<Boolean>("Mobs", "Mobs", true);
    private final Option<Boolean> animals = new Option<Boolean>("Animals", "Animals", true);
    private final Option<Boolean> throughWalls = new Option<Boolean>("Through Walls", "ThroughWalls", false);
    private final Option<Boolean> rotate = new Option<Boolean>("Rotate", "Rotate", true);
    private final Option<Boolean> onlyYaw = new Option<Boolean>("Only Yaw", "OnlyYaw", false);

    private int switchIndex;
    private int delayMs;
    private int targetId = -1;
    private long lastSwitchAt;
    private long targetAcquiredAt;
    private long legitReactionMs;
    private long nextLegitClickAt;
    private long nextAimUpdateAt;
    private double aimOffsetX;
    private double aimOffsetZ;
    private double targetAimOffsetX;
    private double targetAimOffsetZ;
    private double randomHeightRatio = 0.62D;
    private double targetRandomHeightRatio = 0.62D;
    private boolean blocking;
    private boolean serverBlocking;
    private float silentYaw;
    private float silentPitch;
    private boolean silentRotationReady;
    private float rageCurveProgress;
    private static final String ROTATION_HANDLER_NAME = "vapulite_killaura_rotation";
    private static final long ATTACK_WAIT_TIMEOUT_MS = 70L;
    private Channel rotationChannel;
    private PendingPacket pendingAttackPacket;
    private long lastSilentPacketAt;
    private final RotationUtil.State rotationState = new RotationUtil.State();

    public KillAura() {
        super("KillAura", Keyboard.KEY_NONE, ModuleType.Combat, "Auto attack nearby targets");
        mode.visibleWhen(() -> !isLegitMode());
        switchDelay.visibleWhen(() -> !isLegitMode() && mode.getValue() == AttackMode.SWITCH);
        maxTargets.visibleWhen(() -> !isLegitMode() && mode.getValue() == AttackMode.MULTI);
        rayCast.visibleWhen(() -> !isLegitMode());
        rayExpand.visibleWhen(() -> !isLegitMode() && Boolean.TRUE.equals(rayCast.getValue()));
        autoblock.visibleWhen(() -> !isLegitMode());
        autoBlockMode.visibleWhen(() -> !isLegitMode() && Boolean.TRUE.equals(autoblock.getValue()));
        throughWalls.visibleWhen(() -> !isLegitMode());
        legitRequireAttack.visibleWhen(() -> isLegitMode());
        this.addValues(rangeValue, minCps, cps, fov, yawSpeed, pitchSpeed, hurtTime, switchDelay, maxTargets,
                rayExpand, auraMode, mode, priority, aimPoint, autoBlockMode, autoblock, legitRequireAttack, rayCast, randomAim,
                weaponOnly, players, mobs, animals, throughWalls, rotate, onlyYaw);
        Chinese = "杀戮光环";
    }

    @Override
    public void enable() {
        target = null;
        targets.clear();
        targetId = -1;
        rotationState.reset();
        switchIndex = 0;
        lastSwitchAt = 0L;
        targetAcquiredAt = 0L;
        legitReactionMs = 0L;
        nextLegitClickAt = 0L;
        nextAimUpdateAt = 0L;
        aimOffsetX = 0.0D;
        aimOffsetZ = 0.0D;
        targetAimOffsetX = 0.0D;
        targetAimOffsetZ = 0.0D;
        randomHeightRatio = 0.62D;
        targetRandomHeightRatio = 0.62D;
        blocking = false;
        serverBlocking = false;
        pendingAttackPacket = null;
        lastSilentPacketAt = 0L;
        resetSilentRotation();
        delayMs = nextAttackDelay();
    }

    @Override
    public void disable() {
        releaseBlock();
        removeRotationHandler();
        target = null;
        targets.clear();
        targetId = -1;
        rotationState.reset();
        resetSilentRotation();
        pendingAttackPacket = null;
        lastSilentPacketAt = 0L;
        targetAcquiredAt = 0L;
        legitReactionMs = 0L;
        nextLegitClickAt = 0L;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        if (!isInGame() || CombatUtil.shouldPauseForScreen()) {
            removeRotationHandler();
            clearTargetState();
            return;
        }
        injectRotationHandler();
        if (Boolean.TRUE.equals(weaponOnly.getValue()) && !CombatUtil.isHoldingWeapon()) {
            clearTargetState();
            return;
        }
        if (shouldRequireAttackKey() && !mc.gameSettings.keyBindAttack.isKeyDown()) {
            clearTargetState();
            return;
        }

        double searchRange = getSearchRange();
        double searchFov = getSearchFov();
        boolean allowThroughWalls = !isLegitMode() && Boolean.TRUE.equals(throughWalls.getValue());
        List<EntityLivingBase> foundTargets = CombatUtil.collectTargets(searchRange, searchFov,
                players.getValue(), mobs.getValue(), animals.getValue(), allowThroughWalls);
        CombatUtil.sortTargets(foundTargets, priority.getValue());
        targets.clear();
        targets.addAll(foundTargets);

        if (foundTargets.isEmpty()) {
            clearTargetState();
            return;
        }

        target = selectTarget(foundTargets);

        if (target != null && target.getEntityId() != targetId) {
            targetId = target.getEntityId();
            rotationState.reset();
            resetSilentRotation();
            targetAcquiredAt = System.currentTimeMillis();
            legitReactionMs = randomRange(130L, 260L);
            nextLegitClickAt = 0L;
            nextAimUpdateAt = 0L;
            aimOffsetX = 0.0D;
            aimOffsetZ = 0.0D;
            targetAimOffsetX = 0.0D;
            targetAimOffsetZ = 0.0D;
            randomHeightRatio = 0.62D;
            targetRandomHeightRatio = 0.62D;
        }

        if (target != null) {
            updateAuraRotation(target);
        }
        if (isLegitMode() || !Boolean.TRUE.equals(autoblock.getValue())) {
            releaseBlock();
        }

        if (!timer.delay(delayMs)) {
            return;
        }

        boolean attacked = false;
        if (!isLegitMode() && mode.getValue() == AttackMode.MULTI) {
            int attackedTargets = 0;
            int limit = Math.max(1, maxTargets.getValue().intValue());
            for (EntityLivingBase entity : foundTargets) {
                if (attackedTargets >= limit) {
                    break;
                }
                if (attack(entity, true)) {
                    attackedTargets++;
                    attacked = true;
                }
            }
        } else {
            attacked = attack(target, false);
        }

        if (attacked) {
            delayMs = nextAttackDelay();
            timer.reset();
        } else if (!isLegitMode() && Boolean.TRUE.equals(autoblock.getValue()) && target == null) {
            releaseBlock();
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !isLegitMode() || target == null) {
            return;
        }
        if (!isInGame() || CombatUtil.shouldPauseForScreen() || !Boolean.TRUE.equals(rotate.getValue())) {
            return;
        }
        if (shouldRequireAttackKey() && !mc.gameSettings.keyBindAttack.isKeyDown()) {
            return;
        }
        if (CombatUtil.isValidTarget(target, getSearchRange() + 0.25D, getSearchFov() + 8.0D,
                players.getValue(), mobs.getValue(), animals.getValue(), false)) {
            faceTarget(target);
        }
    }

    private EntityLivingBase selectTarget(List<EntityLivingBase> foundTargets) {
        if (isLegitMode()) {
            if (target != null && foundTargets.contains(target)) {
                return target;
            }
            return foundTargets.get(0);
        }
        AttackMode currentMode = mode.getValue();
        if (currentMode == AttackMode.SWITCH) {
            return selectSwitchTarget(foundTargets);
        }
        if (currentMode == AttackMode.SINGLE && target != null && foundTargets.contains(target)) {
            return target;
        }
        return foundTargets.get(0);
    }

    private EntityLivingBase selectSwitchTarget(List<EntityLivingBase> foundTargets) {
        long now = System.currentTimeMillis();
        if (switchIndex >= foundTargets.size()) {
            switchIndex = 0;
        }
        if (target == null || !foundTargets.contains(target)) {
            switchIndex = 0;
            lastSwitchAt = now;
            return foundTargets.get(switchIndex);
        }
        if (foundTargets.size() > 1 && now - lastSwitchAt >= switchDelay.getValue().longValue()) {
            switchIndex = foundTargets.indexOf(target) + 1;
            if (switchIndex >= foundTargets.size()) {
                switchIndex = 0;
            }
            lastSwitchAt = now;
        } else {
            switchIndex = foundTargets.indexOf(target);
        }
        return foundTargets.get(switchIndex);
    }

    private void updateAuraRotation(EntityLivingBase entity) {
        if (!Boolean.TRUE.equals(rotate.getValue())) {
            resetSilentRotation();
            return;
        }
        if (shouldRequireAttackKey() && !mc.gameSettings.keyBindAttack.isKeyDown()) {
            rotationState.reset();
            return;
        }
        if (auraMode.getValue() == AuraMode.RAGE) {
            updateSilentRotation(entity);
        } else {
            faceTarget(entity);
        }
    }

    private void faceTarget(EntityLivingBase entity) {
        float[] rotations = getAimRotations(entity);
        if (isLegitMode()) {
            float mouseStep = getMouseStep();
            float yawDiff = MathHelper.wrapAngleTo180_float(rotations[0] - mc.thePlayer.rotationYaw);
            float edge = Math.abs(yawDiff) / Math.max(1.0f, (float) getSearchFov());
            float edgeScale = MathHelper.clamp_float(1.0f
                            - RotationUtil.smoothStep(edge) * 0.38f,
                    0.45f, 1.0f);
            float yawStep = MathHelper.clamp_float(yawSpeed.getValue().floatValue(), 2.0f, 11.0f)
                    * getSensitivityScale(mouseStep) * edgeScale;
            float pitchStep = MathHelper.clamp_float(pitchSpeed.getValue().floatValue(), 1.0f, 7.0f)
                    * getSensitivityScale(mouseStep) * edgeScale;
            boolean yawOnlyLegit = Math.abs(yawDiff) > 12.0f || Boolean.TRUE.equals(onlyYaw.getValue());
            RotationUtil.applyToPlayer(mc, rotations[0], rotations[1], yawStep, pitchStep,
                    yawOnlyLegit, 0.55f, rotationState, 0.48f, 0.006f, true);
            return;
        }
        RotationUtil.applyToPlayer(mc, rotations[0], rotations[1], yawSpeed.getValue().floatValue(),
                pitchSpeed.getValue().floatValue(), Boolean.TRUE.equals(onlyYaw.getValue()), 0.16f,
                rotationState, 0.48f, 0.08f, true);
    }

    private void updateSilentRotation(EntityLivingBase entity) {
        float[] rotations = getAimRotations(entity);
        if (!silentRotationReady) {
            silentYaw = mc.thePlayer.rotationYaw;
            silentPitch = mc.thePlayer.rotationPitch;
            silentRotationReady = true;
        }

        float targetYaw = rotations[0];
        float targetPitch = Boolean.TRUE.equals(onlyYaw.getValue()) ? mc.thePlayer.rotationPitch : rotations[1];
        rageCurveProgress = MathHelper.clamp_float(rageCurveProgress + 0.22f, 0.0f, 1.0f);
        float yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - silentYaw);
        float pitchDiff = MathHelper.wrapAngleTo180_float(targetPitch - silentPitch);
        float yawStep = RotationUtil.curvedStep(yawDiff, yawSpeed.getValue().floatValue() * 1.45f,
                0.18f, rageCurveProgress);
        float pitchStep = RotationUtil.curvedStep(pitchDiff, pitchSpeed.getValue().floatValue() * 1.30f,
                0.14f, rageCurveProgress);

        silentYaw = RotationUtil.limitAngleChange(silentYaw, targetYaw, yawStep);
        silentPitch = RotationUtil.limitAngleChange(silentPitch, targetPitch, pitchStep);
        silentPitch = MathHelper.clamp_float(silentPitch, -90.0f, 90.0f);
    }

    private void resetSilentRotation() {
        silentYaw = 0.0f;
        silentPitch = 0.0f;
        silentRotationReady = false;
        rageCurveProgress = 0.0f;
    }

    private void injectRotationHandler() {
        if (!isInGame() || mc.getNetHandler() == null) {
            return;
        }
        try {
            NetworkManager manager = mc.getNetHandler().getNetworkManager();
            Channel current = getChannel(manager);
            if (current == null || !current.isOpen()) {
                return;
            }
            if (rotationChannel != null && rotationChannel != current) {
                removeRotationHandler();
            }
            if (current.pipeline().get(ROTATION_HANDLER_NAME) == null) {
                current.pipeline().addBefore("packet_handler", ROTATION_HANDLER_NAME, new SilentRotationPacketHandler(this));
            }
            rotationChannel = current;
        } catch (Throwable ignored) {
            rotationChannel = null;
        }
    }

    private void removeRotationHandler() {
        Channel current = rotationChannel;
        rotationChannel = null;
        releasePendingAttackPacket();
        if (current == null) {
            return;
        }
        try {
            if (current.isOpen() && current.pipeline().get(ROTATION_HANDLER_NAME) != null) {
                current.pipeline().remove(ROTATION_HANDLER_NAME);
            }
        } catch (Throwable ignored) {
        }
    }

    private Channel getChannel(NetworkManager manager) {
        String[] names = new String[]{"channel", "field_150746_k"};
        for (String name : names) {
            try {
                Field field = NetworkManager.class.getDeclaredField(name);
                field.setAccessible(true);
                Object value = field.get(manager);
                if (value instanceof Channel) {
                    return (Channel) value;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private boolean shouldRewriteRotationPacket() {
        return getState()
                && isInGame()
                && auraMode.getValue() == AuraMode.RAGE
                && Boolean.TRUE.equals(rotate.getValue())
                && silentRotationReady
                && target != null;
    }

    private C03PacketPlayer rewriteRotationPacket(C03PacketPlayer packet) {
        if (!shouldRewriteRotationPacket() || packet == null) {
            return null;
        }
        lastSilentPacketAt = System.currentTimeMillis();
        boolean onGround = packet.isOnGround();
        float yaw = silentYaw;
        float pitch = silentPitch;
        if (packet instanceof C03PacketPlayer.C06PacketPlayerPosLook
                || packet instanceof C03PacketPlayer.C04PacketPlayerPosition) {
            return new C03PacketPlayer.C06PacketPlayerPosLook(packet.getPositionX(), packet.getPositionY(),
                    packet.getPositionZ(), yaw, pitch, onGround);
        }
        return new C03PacketPlayer.C05PacketPlayerLook(yaw, pitch, onGround);
    }

    private boolean shouldHoldAttackPacket(Object packet) {
        if (!shouldRewriteRotationPacket() || !(packet instanceof C02PacketUseEntity)) {
            return false;
        }
        C02PacketUseEntity useEntity = (C02PacketUseEntity) packet;
        if (useEntity.getAction() != C02PacketUseEntity.Action.ATTACK) {
            return false;
        }
        return System.currentTimeMillis() - lastSilentPacketAt > 45L;
    }

    private boolean holdAttackPacket(ChannelHandlerContext ctx, Object packet, ChannelPromise promise) {
        releasePendingAttackPacket();
        pendingAttackPacket = new PendingPacket(ctx, packet, promise, System.currentTimeMillis() + ATTACK_WAIT_TIMEOUT_MS);
        ctx.executor().schedule(new Runnable() {
            @Override
            public void run() {
                releasePendingAttackIfDue();
            }
        }, ATTACK_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        return true;
    }

    private void releasePendingAttackPacket() {
        PendingPacket pending = pendingAttackPacket;
        pendingAttackPacket = null;
        if (pending == null || pending.ctx == null || pending.packet == null || pending.promise == null) {
            return;
        }
        pending.ctx.writeAndFlush(pending.packet, pending.promise);
    }

    private void releasePendingAttackIfDue() {
        PendingPacket pending = pendingAttackPacket;
        if (pending != null && System.currentTimeMillis() >= pending.releaseAt) {
            releasePendingAttackPacket();
        }
    }

    private float[] getAimRotations(EntityLivingBase entity) {
        AimProfile profile = getAimProfile(entity);
        AxisAlignedBB box = entity.getEntityBoundingBox();
        double width = Math.max(0.1D, Math.min(box.maxX - box.minX, box.maxZ - box.minZ));
        double prediction = 0.16D;
        double targetX = entity.posX + (entity.posX - entity.lastTickPosX) * prediction + profile.offsetX * width;
        double targetZ = entity.posZ + (entity.posZ - entity.lastTickPosZ) * prediction + profile.offsetZ * width;
        double targetY = box.minY + (box.maxY - box.minY) * profile.heightRatio
                + (entity.posY - entity.lastTickPosY) * Math.min(0.7D, prediction);
        return RotationUtil.getRotationsTo(mc, targetX, targetY, targetZ);
    }

    private AimProfile getAimProfile(EntityLivingBase entity) {
        long now = System.currentTimeMillis();
        if (now >= nextAimUpdateAt || entity.getEntityId() != targetId) {
            retargetAimPoint(entity, now);
        }
        updateAimPointDrift();
        double height = getBaseHeightRatio(entity);
        if (aimPoint.getValue() == AimPoint.RANDOM) {
            height = randomHeightRatio;
        }
        if (Boolean.TRUE.equals(randomAim.getValue())) {
            height = MathHelper.clamp_double(height + ThreadLocalRandom.current().nextDouble(-0.018D, 0.019D),
                    0.24D, 0.92D);
            return new AimProfile(height, aimOffsetX, aimOffsetZ);
        }
        return new AimProfile(height, 0.0D, 0.0D);
    }

    private void retargetAimPoint(EntityLivingBase entity, long now) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        targetRandomHeightRatio = random.nextDouble(0.38D, 0.84D);
        targetAimOffsetX = random.nextDouble(-0.15D, 0.16D);
        targetAimOffsetZ = random.nextDouble(-0.15D, 0.16D);
        double distance = mc.thePlayer.getDistanceToEntity(entity);
        long baseDelay = distance < 2.7D ? 220L : 300L;
        nextAimUpdateAt = now + baseDelay + random.nextLong(180L);
    }

    private void updateAimPointDrift() {
        aimOffsetX += (targetAimOffsetX - aimOffsetX) * 0.065D;
        aimOffsetZ += (targetAimOffsetZ - aimOffsetZ) * 0.065D;
        randomHeightRatio += (targetRandomHeightRatio - randomHeightRatio) * 0.055D;
    }

    private double getBaseHeightRatio(EntityLivingBase entity) {
        AimPoint point = aimPoint.getValue();
        if (point == AimPoint.HEAD) {
            return 0.86D;
        }
        if (point == AimPoint.CHEST) {
            return 0.62D;
        }
        if (point == AimPoint.LEGS) {
            return 0.32D;
        }
        if (point == AimPoint.RANDOM) {
            return randomHeightRatio;
        }
        AxisAlignedBB box = entity.getEntityBoundingBox();
        double height = Math.max(0.1D, box.maxY - box.minY);
        double playerEye = mc.thePlayer.posY + mc.thePlayer.getEyeHeight();
        double eyeRelative = (playerEye - box.minY) / height;
        double smart = MathHelper.clamp_double(eyeRelative, 0.44D, 0.84D);
        if (entity.hurtTime > 4) {
            smart = Math.min(smart, 0.64D);
        }
        if (entity instanceof EntityLiving && ((EntityLiving) entity).getAttackTarget() == mc.thePlayer) {
            smart = Math.max(0.55D, smart - 0.04D);
        }
        return smart;
    }

    private boolean attack(EntityLivingBase entity, boolean multiAttack) {
        if (entity == null || entity.isDead || entity.getHealth() <= 0.0f) {
            return false;
        }
        if (entity.hurtTime > hurtTime.getValue().intValue()) {
            return false;
        }
        if (!HitSelect.shouldAttack(entity, multiAttack) || !KnockbackDelay.shouldAttack(entity)) {
            return false;
        }
        if (!prepareAttackRotation(entity)) {
            return false;
        }
        preAttackBlock();
        if (!isLegitMode()) {
            Criticals.tryCritical(false);
        }
        mc.thePlayer.swingItem();
        mc.playerController.attackEntity(mc.thePlayer, entity);
        HitSelect.onAttack(entity);
        BlockHit.onAttack(entity);
        WTap.onAttack(entity);
        if (!isLegitMode() && Boolean.TRUE.equals(autoblock.getValue())) {
            blockWithSword();
        }
        if (isLegitMode()) {
            nextLegitClickAt = System.currentTimeMillis() + randomRange(35L, 95L);
        }
        return true;
    }

    private boolean prepareAttackRotation(EntityLivingBase entity) {
        boolean shouldRotate = Boolean.TRUE.equals(rotate.getValue());
        boolean rage = auraMode.getValue() == AuraMode.RAGE;
        boolean requireRay = Boolean.TRUE.equals(rayCast.getValue()) || auraMode.getValue() == AuraMode.SAFE;

        if (isLegitMode()) {
            return isLegitAttackReady(entity);
        }
        if (shouldRotate && rage) {
            snapSilentRotation(entity);
        }
        if (requireRay && !isRaycastReady(entity)) {
            return false;
        }
        return true;
    }

    private boolean isRaycastReady(EntityLivingBase expected) {
        MovingObjectPosition hit = rayTraceEntity(rangeValue.getValue() + 0.2D, rayExpand.getValue(),
                getAttackYaw(), getAttackPitch());
        if (hit == null || hit.entityHit != expected) {
            return false;
        }
        mc.objectMouseOver = hit;
        mc.pointedEntity = expected;
        return true;
    }

    private void snapSilentRotation(EntityLivingBase entity) {
        float[] rotations = getAimRotations(entity);
        silentYaw = rotations[0];
        silentPitch = Boolean.TRUE.equals(onlyYaw.getValue()) ? mc.thePlayer.rotationPitch : rotations[1];
        silentPitch = MathHelper.clamp_float(silentPitch, -90.0f, 90.0f);
        silentRotationReady = true;
        rageCurveProgress = 1.0f;
    }

    private boolean isLegitAttackReady(EntityLivingBase expected) {
        long now = System.currentTimeMillis();
        if ((shouldRequireAttackKey() && !mc.gameSettings.keyBindAttack.isKeyDown()) || now < nextLegitClickAt) {
            return false;
        }
        if (targetAcquiredAt <= 0L || now - targetAcquiredAt < legitReactionMs) {
            return false;
        }
        if (expected == null || !mc.thePlayer.canEntityBeSeen(expected)) {
            return false;
        }
        if (mc.thePlayer.getDistanceToEntity(expected) > getSearchRange() + 0.05D) {
            return false;
        }
        MovingObjectPosition hit = rayTraceEntity(getSearchRange() + 0.05D, 0.015D,
                mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
        if (hit == null || hit.entityHit != expected) {
            return false;
        }
        float[] rotations = getAimRotations(expected);
        float yawError = Math.abs(MathHelper.wrapAngleTo180_float(rotations[0] - mc.thePlayer.rotationYaw));
        float pitchError = Math.abs(MathHelper.wrapAngleTo180_float(rotations[1] - mc.thePlayer.rotationPitch));
        if (yawError > 4.8f || pitchError > 6.2f) {
            return false;
        }
        mc.objectMouseOver = hit;
        mc.pointedEntity = expected;
        return true;
    }

    private float getAttackYaw() {
        if (auraMode.getValue() == AuraMode.RAGE && Boolean.TRUE.equals(rotate.getValue()) && silentRotationReady) {
            return silentYaw;
        }
        return mc.thePlayer.rotationYaw;
    }

    private float getAttackPitch() {
        if (auraMode.getValue() == AuraMode.RAGE && Boolean.TRUE.equals(rotate.getValue()) && silentRotationReady) {
            return silentPitch;
        }
        return mc.thePlayer.rotationPitch;
    }

    private MovingObjectPosition rayTraceEntity(double distance, double hitboxExpand, float yaw, float pitch) {
        if (!isInGame()) {
            return null;
        }
        Entity view = mc.getRenderViewEntity();
        if (view == null) {
            return null;
        }
        Vec3 eyes = view.getPositionEyes(1.0f);
        Vec3 look = getVectorForRotation(pitch, yaw);
        Vec3 end = eyes.addVector(look.xCoord * distance, look.yCoord * distance, look.zCoord * distance);
        Entity pointed = null;
        Vec3 hitVec = null;
        double bestDistance = distance;
        List<Entity> entities = mc.theWorld.getEntitiesWithinAABBExcludingEntity(view,
                view.getEntityBoundingBox().addCoord(look.xCoord * distance, look.yCoord * distance,
                        look.zCoord * distance).expand(1.0D, 1.0D, 1.0D));

        for (Entity entity : entities) {
            if (!(entity instanceof EntityLivingBase)) {
                continue;
            }
            EntityLivingBase living = (EntityLivingBase) entity;
            if (!CombatUtil.isValidTarget(living, distance + hitboxExpand, 180.0D, players.getValue(),
                    mobs.getValue(), animals.getValue(), throughWalls.getValue())) {
                continue;
            }
            double border = entity.getCollisionBorderSize() + hitboxExpand;
            AxisAlignedBB box = entity.getEntityBoundingBox().expand(border, border, border);
            MovingObjectPosition intercept = box.calculateIntercept(eyes, end);
            if (box.isVecInside(eyes)) {
                if (bestDistance >= 0.0D) {
                    pointed = entity;
                    hitVec = intercept == null ? eyes : intercept.hitVec;
                    bestDistance = 0.0D;
                }
            } else if (intercept != null) {
                double currentDistance = eyes.distanceTo(intercept.hitVec);
                if (currentDistance < bestDistance || bestDistance == 0.0D) {
                    if (entity == view.ridingEntity && !entity.canRiderInteract()) {
                        continue;
                    }
                    pointed = entity;
                    hitVec = intercept.hitVec;
                    bestDistance = currentDistance;
                }
            }
        }
        return pointed == null || hitVec == null ? null : new MovingObjectPosition(pointed, hitVec);
    }

    private Vec3 getVectorForRotation(float pitch, float yaw) {
        float yawCos = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
        float yawSin = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        float pitchCos = -MathHelper.cos(-pitch * 0.017453292F);
        float pitchSin = MathHelper.sin(-pitch * 0.017453292F);
        return new Vec3(yawSin * pitchCos, pitchSin, yawCos * pitchCos);
    }

    private void preAttackBlock() {
        if (isLegitMode() || !Boolean.TRUE.equals(autoblock.getValue())
                || getEffectiveAutoBlockMode() != AutoBlockMode.PACKET) {
            return;
        }
        releaseBlock();
    }

    private void blockWithSword() {
        if (isLegitMode() || auraMode.getValue() == AuraMode.RAGE) {
            releaseBlock();
            return;
        }
        ItemStack stack = mc.thePlayer.getCurrentEquippedItem();
        if (stack == null || !(stack.getItem() instanceof ItemSword)) {
            releaseBlock();
            return;
        }
        AutoBlockMode currentMode = getEffectiveAutoBlockMode();
        if (currentMode == AutoBlockMode.FAKE) {
            blocking = true;
            serverBlocking = false;
            return;
        }
        if (currentMode == AutoBlockMode.PACKET) {
            mc.thePlayer.sendQueue.addToSendQueue(new C08PacketPlayerBlockPlacement(stack));
        } else {
            mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, stack);
        }
        blocking = true;
        serverBlocking = true;
    }

    private void releaseBlock() {
        if (!blocking || !isInGame() || mc.thePlayer.sendQueue == null) {
            blocking = false;
            serverBlocking = false;
            return;
        }
        if (serverBlocking) {
            mc.thePlayer.sendQueue.addToSendQueue(new C07PacketPlayerDigging(
                    C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
        }
        blocking = false;
        serverBlocking = false;
    }

    private void clearTargetState() {
        releaseBlock();
        target = null;
        targets.clear();
        targetId = -1;
        rotationState.reset();
        resetSilentRotation();
        targetAcquiredAt = 0L;
        legitReactionMs = 0L;
        nextLegitClickAt = 0L;
        nextAimUpdateAt = 0L;
        aimOffsetX = 0.0D;
        aimOffsetZ = 0.0D;
        targetAimOffsetX = 0.0D;
        targetAimOffsetZ = 0.0D;
        randomHeightRatio = 0.62D;
        targetRandomHeightRatio = 0.62D;
    }

    private boolean isLegitMode() {
        return auraMode.getValue() == AuraMode.SAFE;
    }

    private boolean shouldRequireAttackKey() {
        return isLegitMode() && Boolean.TRUE.equals(legitRequireAttack.getValue());
    }

    private double getSearchRange() {
        return isLegitMode() ? Math.min(rangeValue.getValue(), 4.05D) : rangeValue.getValue();
    }

    private double getSearchFov() {
        return isLegitMode() ? Math.min(fov.getValue(), 90.0D) : fov.getValue();
    }

    private int nextAttackDelay() {
        if (!isLegitMode()) {
            return CombatUtil.nextDelay(minCps.getValue(), cps.getValue());
        }
        double min = Math.min(minCps.getValue(), 7.0D);
        double max = Math.min(cps.getValue(), 10.5D);
        if (max < min) {
            min = Math.max(1.0D, max - 1.0D);
        }
        return CombatUtil.nextDelay(min, max) + (int) randomRange(12L, 48L);
    }

    private AutoBlockMode getEffectiveAutoBlockMode() {
        AutoBlockMode current = autoBlockMode.getValue();
        if (isLegitMode() && current != AutoBlockMode.LEGIT) {
            return AutoBlockMode.LEGIT;
        }
        return current;
    }

    private float getMouseStep() {
        float sensitivity = MathHelper.clamp_float(mc.gameSettings.mouseSensitivity, 0.0f, 1.0f);
        float base = sensitivity * 0.6f + 0.2f;
        return base * base * base * 1.2f;
    }

    private float getSensitivityScale(float mouseStep) {
        return MathHelper.clamp_float((float) Math.sqrt(mouseStep / 0.15f), 0.55f, 1.55f);
    }

    private long randomRange(long first, long second) {
        long min = Math.max(0L, Math.min(first, second));
        long max = Math.max(min, Math.max(first, second));
        if (max <= min) {
            return min;
        }
        return ThreadLocalRandom.current().nextLong(max - min + 1L) + min;
    }

    private static final class AimProfile {
        final double heightRatio;
        final double offsetX;
        final double offsetZ;

        AimProfile(double heightRatio, double offsetX, double offsetZ) {
            this.heightRatio = heightRatio;
            this.offsetX = offsetX;
            this.offsetZ = offsetZ;
        }
    }

    public static void assistFaceEntity(Entity entity, float yaw, float pitch) {
        CombatUtil.faceEntity(entity, yaw, pitch, pitch <= 0.0f, 0.0f);
    }

    public static float updateRotation(float current, float targetYaw, float maxTurn) {
        return CombatUtil.updateRotation(current, targetYaw, maxTurn);
    }

    private static final class PendingPacket {
        final ChannelHandlerContext ctx;
        final Object packet;
        final ChannelPromise promise;
        final long releaseAt;

        PendingPacket(ChannelHandlerContext ctx, Object packet, ChannelPromise promise, long releaseAt) {
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
                    module.releasePendingAttackPacket();
                    return;
                }
            }
            if (module.shouldHoldAttackPacket(msg) && module.holdAttackPacket(ctx, msg, promise)) {
                return;
            }
            module.releasePendingAttackIfDue();
            super.write(ctx, msg, promise);
        }
    }
}
