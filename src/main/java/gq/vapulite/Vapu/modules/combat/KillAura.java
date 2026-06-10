package gq.vapulite.Vapu.modules.combat;

import gq.vapulite.Vapu.ModuleType;
import gq.vapulite.Vapu.modules.Module;
import gq.vapulite.Vapu.utils.TimerUtil;
import gq.vapulite.Vapu.utils.RotationUtil;
import gq.vapulite.Vapu.value.Mode;
import gq.vapulite.Vapu.value.Numbers;
import gq.vapulite.Vapu.value.Option;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
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
    private long nextAimUpdateAt;
    private double aimOffsetX;
    private double aimOffsetZ;
    private double randomHeightRatio = 0.62D;
    private boolean blocking;
    private boolean serverBlocking;
    private float silentYaw;
    private float silentPitch;
    private boolean silentRotationReady;
    private final RotationUtil.State rotationState = new RotationUtil.State();

    public KillAura() {
        super("KillAura", Keyboard.KEY_NONE, ModuleType.Combat, "Auto attack nearby targets");
        this.addValues(rangeValue, minCps, cps, fov, yawSpeed, pitchSpeed, hurtTime, switchDelay, maxTargets,
                rayExpand, auraMode, mode, priority, aimPoint, autoBlockMode, autoblock, rayCast, randomAim,
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
        nextAimUpdateAt = 0L;
        blocking = false;
        serverBlocking = false;
        resetSilentRotation();
        delayMs = CombatUtil.nextDelay(minCps.getValue(), cps.getValue());
    }

    @Override
    public void disable() {
        releaseBlock();
        target = null;
        targets.clear();
        targetId = -1;
        rotationState.reset();
        resetSilentRotation();
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!isInGame() || CombatUtil.shouldPauseForScreen()) {
            clearTargetState();
            return;
        }
        if (Boolean.TRUE.equals(weaponOnly.getValue()) && !CombatUtil.isHoldingWeapon()) {
            clearTargetState();
            return;
        }

        List<EntityLivingBase> foundTargets = CombatUtil.collectTargets(rangeValue.getValue(), fov.getValue(),
                players.getValue(), mobs.getValue(), animals.getValue(), throughWalls.getValue());
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
            nextAimUpdateAt = 0L;
        }

        if (target != null) {
            updateAuraRotation(target);
        }
        if (!Boolean.TRUE.equals(autoblock.getValue())) {
            releaseBlock();
        }

        if (!timer.delay(delayMs)) {
            return;
        }

        boolean attacked = false;
        if (mode.getValue() == AttackMode.MULTI) {
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
            delayMs = CombatUtil.nextDelay(minCps.getValue(), cps.getValue());
            timer.reset();
        } else if (Boolean.TRUE.equals(autoblock.getValue()) && target == null) {
            releaseBlock();
        }
    }

    private EntityLivingBase selectTarget(List<EntityLivingBase> foundTargets) {
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
        if (auraMode.getValue() == AuraMode.RAGE) {
            updateSilentRotation(entity);
        } else {
            faceTarget(entity);
        }
    }

    private void faceTarget(EntityLivingBase entity) {
        float[] rotations = getAimRotations(entity);
        RotationUtil.applyToPlayer(mc, rotations[0], rotations[1], yawSpeed.getValue().floatValue(),
                pitchSpeed.getValue().floatValue(), Boolean.TRUE.equals(onlyYaw.getValue()), 0.16f,
                rotationState, 0.34f, 0.16f, true);
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
        float yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - silentYaw);
        float pitchDiff = MathHelper.wrapAngleTo180_float(targetPitch - silentPitch);
        float yawStep = RotationUtil.adaptiveStep(yawDiff, yawSpeed.getValue().floatValue() * 1.35f, 0.35f);
        float pitchStep = RotationUtil.adaptiveStep(pitchDiff, pitchSpeed.getValue().floatValue() * 1.25f, 0.30f);

        silentYaw = RotationUtil.limitAngleChange(silentYaw, targetYaw, yawStep);
        silentPitch = RotationUtil.limitAngleChange(silentPitch, targetPitch, pitchStep);
        silentPitch = MathHelper.clamp_float(silentPitch, -90.0f, 90.0f);
    }

    private void resetSilentRotation() {
        silentYaw = 0.0f;
        silentPitch = 0.0f;
        silentRotationReady = false;
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
        randomHeightRatio = random.nextDouble(0.35D, 0.88D);
        aimOffsetX = random.nextDouble(-0.20D, 0.21D);
        aimOffsetZ = random.nextDouble(-0.20D, 0.21D);
        double distance = mc.thePlayer.getDistanceToEntity(entity);
        long baseDelay = distance < 2.7D ? 120L : 180L;
        nextAimUpdateAt = now + baseDelay + random.nextLong(110L);
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
        Criticals.tryCritical();
        mc.thePlayer.swingItem();
        mc.playerController.attackEntity(mc.thePlayer, entity);
        HitSelect.onAttack(entity);
        BlockHit.onAttack(entity);
        WTap.onAttack(entity);
        if (Boolean.TRUE.equals(autoblock.getValue())) {
            blockWithSword();
        }
        return true;
    }

    private boolean prepareAttackRotation(EntityLivingBase entity) {
        boolean shouldRotate = Boolean.TRUE.equals(rotate.getValue());
        boolean rage = auraMode.getValue() == AuraMode.RAGE;
        boolean requireRay = Boolean.TRUE.equals(rayCast.getValue()) || auraMode.getValue() == AuraMode.SAFE;

        if (shouldRotate && rage) {
            updateSilentRotation(entity);
        }
        if (requireRay && !isRaycastReady(entity)) {
            return false;
        }
        if (shouldRotate && rage) {
            sendSilentLook();
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

    private void sendSilentLook() {
        if (!silentRotationReady || mc.thePlayer == null || mc.thePlayer.sendQueue == null) {
            return;
        }
        mc.thePlayer.sendQueue.addToSendQueue(new C03PacketPlayer.C05PacketPlayerLook(
                silentYaw, silentPitch, mc.thePlayer.onGround));
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
        if (!Boolean.TRUE.equals(autoblock.getValue()) || autoBlockMode.getValue() != AutoBlockMode.PACKET) {
            return;
        }
        releaseBlock();
    }

    private void blockWithSword() {
        ItemStack stack = mc.thePlayer.getCurrentEquippedItem();
        if (stack == null || !(stack.getItem() instanceof ItemSword)) {
            releaseBlock();
            return;
        }
        AutoBlockMode currentMode = autoBlockMode.getValue();
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
        nextAimUpdateAt = 0L;
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
}
