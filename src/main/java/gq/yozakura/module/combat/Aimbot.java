package gq.yozakura.module.combat;

import gq.yozakura.bridge.MinecraftAccessor;
import gq.yozakura.event.bridge.TickEvent;
import gq.yozakura.event.bridge.UpdateEvent;
import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.event.bus.types.EventType;
import gq.yozakura.event.bus.types.Priority;
import gq.yozakura.module.Module;
import gq.yozakura.module.ModuleType;
import gq.yozakura.util.minecraft.RotationUtil;
import gq.yozakura.util.module.KeyBindUtil;
import gq.yozakura.util.module.TeamUtil;
import gq.yozakura.value.Mode;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class Aimbot extends Module {
    public enum AimMode {
        NORMAL,
        SILENT
    }

    public enum VapeMode {
        REGULAR,
        BLATANT
    }

    public enum SortMode {
        HEALTH,
        ANGLE,
        HURT_TIME,
        DISTANCE
    }

    private final Mode<AimMode> mode = new Mode<AimMode>("Mode", "Mode", AimMode.values(), AimMode.NORMAL);
    private final Mode<VapeMode> vapeMode =
            new Mode<VapeMode>("Vape Mode", "VapeMode", VapeMode.values(), VapeMode.REGULAR);
    private final Numbers<Double> speed = new Numbers<Double>("Horizontal Speed", "Speed", 10.0, 1.0, 180.0, 1.0);
    private final Numbers<Double> verticalSpeed =
            new Numbers<Double>("Vertical Speed", "VerticalSpeed", 5.0, 1.0, 90.0, 1.0);
    private final Numbers<Double> reactionDelay =
            new Numbers<Double>("Reaction Delay", "ReactionDelay", 30.0, 10.0, 200.0, 1.0);
    private final Numbers<Double> updateRate =
            new Numbers<Double>("Update Rate", "UpdateRate", 20.0, 0.1, 20.0, 0.1);
    private final Numbers<Double> multipointHorizontal =
            new Numbers<Double>("Multipoint horizontal", "MultipointHorizontal", 18.0, 0.0, 100.0, 1.0);
    private final Numbers<Double> multipointVertical =
            new Numbers<Double>("Multipoint vertical", "MultipointVertical", 35.0, 0.0, 100.0, 1.0);
    private final Numbers<Double> randomization =
            new Numbers<Double>("Randomization", "Randomization", 20.0, 0.0, 100.0, 1.0);
    private final Numbers<Double> fov = new Numbers<Double>("FOV", "FOV", 90.0, 15.0, 360.0, 1.0);
    private final Numbers<Double> range = new Numbers<Double>("Range", "Range", 4.5, 0.0, 6.0, 0.1);
    private final Mode<SortMode> sort = new Mode<SortMode>("Sort", "Sort", SortMode.values(), SortMode.ANGLE);
    private final Option<Boolean> ignoreBehindWalls =
            new Option<Boolean>("Ignore behind walls", "IgnoreBehindWalls", false);
    private final Option<Boolean> ignoreBehindEntities =
            new Option<Boolean>("Ignore behind entities", "IgnoreBehindEntities", false);
    private final Option<Boolean> aimInvis = new Option<Boolean>("Aim invis", "AimInvis", false);
    private final Option<Boolean> clickAim = new Option<Boolean>("Require mouse", "RequireMouse", true);
    private final Option<Boolean> requireHover =
            new Option<Boolean>("Require target", "RequireTarget", false);
    private final Option<Boolean> aimVertical =
            new Option<Boolean>("Vertical aim", "VerticalAim", true);
    private final Option<Boolean> ignoreTeammates =
            new Option<Boolean>("Ignore teammates", "IgnoreTeammates", true);
    private final Option<Boolean> botCheck = new Option<Boolean>("Bot Check", "BotCheck", true);
    private final Option<Boolean> stopWhenBreaking =
            new Option<Boolean>("Stop when breaking", "StopWhenBreaking", false);
    private final Option<Boolean> keepMoveDirection =
            new Option<Boolean>("Keep move direction", "KeepMoveDirection", true);
    private final Numbers<Double> hoverDelay =
            new Numbers<Double>("Hover delay", "HoverDelay", 100.0, 0.0, 500.0, 10.0);
    private final Option<Boolean> weaponOnly = new Option<Boolean>("Weapon only", "WeaponOnly", false);

    public EntityLivingBase target;

    private final Random random = new Random();
    private final RotationTask task = new RotationTask();
    private final MouseRotationState mouseState = new MouseRotationState();
    private int targetId = -1;
    private long miningStartTime = -1L;
    private long nextAimUpdate;
    private long taskExpireAt;
    private float assistedYaw;
    private float assistedPitch;
    private boolean rotationInitialized;

    public Aimbot() {
        super("AimAssist", Keyboard.KEY_NONE, ModuleType.Combat, "Vape-style aim assist");
        this.addValues(mode, vapeMode, speed, verticalSpeed, reactionDelay, updateRate, multipointHorizontal,
                multipointVertical, randomization, fov, range, sort, ignoreBehindWalls, ignoreBehindEntities, aimInvis, clickAim,
                requireHover, aimVertical, ignoreTeammates, botCheck, stopWhenBreaking, keepMoveDirection, hoverDelay, weaponOnly);
        Chinese = "瞄准辅助";
    }

    @Override
    public void enable() {
        this.miningStartTime = -1L;
        resetTarget();
    }

    @Override
    public void disable() {
        resetTarget();
        this.miningStartTime = -1L;
        super.disable();
    }

    @EventTarget(Priority.LOW)
    public void onBridgeTick(TickEvent event) {
        if (event.getType() != EventType.PRE) {
            return;
        }
        updateTargetOnly();
    }

    @EventTarget(Priority.LOW)
    public void onUpdate(UpdateEvent event) {
        if (event.getType() != EventType.PRE) {
            return;
        }
        AimResult result = updateAim(event.getNewYaw(), event.getNewPitch());
        if (result == null) {
            return;
        }
        if (mode.getValue() == AimMode.NORMAL) {
            mc.thePlayer.rotationYaw = result.yaw;
            mc.thePlayer.rotationPitch = result.pitch;
            RotationUtil.syncHead(mc, result.yaw);
            event.setRotation(result.yaw, result.pitch, 0);
        } else {
            event.setRotation(result.yaw, result.pitch, 0);
        }
        if (mode.getValue() == AimMode.SILENT && !Boolean.TRUE.equals(keepMoveDirection.getValue())) {
            event.setPervRotation(result.yaw, 0);
        }
    }

    private void updateTargetOnly() {
        if (!conditionsMet()) {
            resetTarget();
            return;
        }
        EntityPlayer enemy = selectTarget(mc.thePlayer.rotationYaw);
        if (enemy == null) {
            resetTarget();
            return;
        }
        target = enemy;
    }

    private AimResult updateAim(float currentYaw, float currentPitch) {
        if (!conditionsMet()) {
            resetTarget();
            return null;
        }

        EntityPlayer enemy = selectTarget(currentYaw);
        if (enemy == null) {
            resetTarget();
            return null;
        }

        prepareTarget(enemy, currentYaw, currentPitch);
        long now = System.currentTimeMillis();
        if (!task.active || now >= nextAimUpdate || now >= taskExpireAt) {
            AimPoint point = createAimPoint(enemy, currentYaw, currentPitch);
            if (point == null) {
                resetTarget();
                return null;
            }
            updateAimTask(point);
            task.active = true;
            int reaction = Math.max(0, reactionDelay.getValue().intValue());
            int updateDelay = Math.max(16, (int) (1000.0F / Math.max(0.1F, updateRate.getValue().floatValue())));
            int jitter = Math.max(1, updateDelay / 4);
            nextAimUpdate = now + updateDelay + random.nextInt(jitter + 1);
            taskExpireAt = now + Math.max(updateDelay * 3, reaction + updateDelay);
        }

        assistedYaw = advanceVapeAxis(currentYaw, task.yaw, speed.getValue().floatValue(), true);
        assistedPitch = Boolean.TRUE.equals(aimVertical.getValue())
                ? MathHelper.clamp_float(
                advanceVapeAxis(currentPitch, task.pitch, verticalSpeed.getValue().floatValue(), false),
                -90.0F,
                90.0F)
                : currentPitch;
        target = enemy;
        return new AimResult(assistedYaw, assistedPitch);
    }

    private EntityPlayer selectTarget(float viewYaw) {
        ArrayList<EntityPlayer> candidates = new ArrayList<EntityPlayer>();
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (isValidCandidate(player, viewYaw)) {
                candidates.add(player);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        candidates.sort(targetComparator(viewYaw));
        if (!Boolean.TRUE.equals(ignoreBehindWalls.getValue())
                && !Boolean.TRUE.equals(ignoreBehindEntities.getValue())) {
            return candidates.get(0);
        }
        for (EntityPlayer candidate : candidates) {
            if (findVisiblePoint(candidate) != null) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isValidCandidate(EntityPlayer player, float viewYaw) {
        if (player == null || player == mc.thePlayer || player.deathTime != 0 || player.isDead) {
            return false;
        }
        if (TeamUtil.isFriend(player)) {
            return false;
        }
        if (Boolean.TRUE.equals(ignoreTeammates.getValue()) && TeamUtil.isSameTeam(player)) {
            return false;
        }
        if (!Boolean.TRUE.equals(aimInvis.getValue()) && player.isInvisible()) {
            return false;
        }
        if (Boolean.TRUE.equals(botCheck.getValue()) && (TeamUtil.isBot(player) || AntiBot.isServerBot(player))) {
            return false;
        }
        double rangeValue = range.getValue();
        if (distanceSqFromEyeToClosestOnAABB(player) > rangeValue * rangeValue) {
            return false;
        }
        if (fov.getValue() < 360.0D && angleTo(player, viewYaw) > fov.getValue() * 0.5D) {
            return false;
        }
        return true;
    }

    private Comparator<EntityPlayer> targetComparator(final float viewYaw) {
        Comparator<EntityPlayer> primary;
        switch (sort.getValue()) {
            case HEALTH:
                primary = Comparator.comparingDouble(player -> player.getHealth() + player.getAbsorptionAmount());
                break;
            case HURT_TIME:
                primary = Comparator.comparingInt(player -> player.hurtTime);
                break;
            case DISTANCE:
                primary = Comparator.comparingDouble(player -> mc.thePlayer.getDistanceSqToEntity(player));
                break;
            case ANGLE:
            default:
                primary = Comparator.comparingDouble(player -> angleTo(player, viewYaw));
                break;
        }
        return primary.thenComparingDouble(player -> mc.thePlayer.getDistanceSqToEntity(player));
    }

    private void prepareTarget(EntityPlayer enemy, float currentYaw, float currentPitch) {
        if (enemy.getEntityId() != targetId) {
            targetId = enemy.getEntityId();
            task.active = false;
            nextAimUpdate = 0L;
            taskExpireAt = 0L;
            rotationInitialized = false;
        }
        if (!rotationInitialized) {
            assistedYaw = currentYaw;
            assistedPitch = currentPitch;
            rotationInitialized = true;
        }
    }

    private void updateAimTask(AimPoint point) {
        if (!task.active) {
            task.yaw = point.yaw;
            task.pitch = point.pitch;
            return;
        }
        float blend = vapeMode.getValue() == VapeMode.BLATANT ? 0.42F : 0.28F;
        task.yaw = task.yaw + MathHelper.wrapAngleTo180_float(point.yaw - task.yaw) * blend;
        task.pitch = MathHelper.clamp_float(
                task.pitch + MathHelper.wrapAngleTo180_float(point.pitch - task.pitch) * (blend * 0.82F),
                -90.0F,
                90.0F
        );
    }

    private AimPoint createAimPoint(EntityPlayer enemy, float currentYaw, float currentPitch) {
        Vec3 point = findVisiblePoint(enemy);
        if (point == null) {
            return null;
        }

        float[] base = RotationUtil.getRotationsTo(mc, point.xCoord, point.yCoord, point.zCoord);
        float yawRange = speed.getValue().floatValue();
        float pitchRange = verticalSpeed.getValue().floatValue();
        float currentDistance = Math.abs(MathHelper.wrapAngleTo180_float(currentYaw - base[0]))
                + Math.abs(MathHelper.wrapAngleTo180_float(currentPitch - base[1]));
        float randomRadius = (float) Math.sqrt(yawRange * yawRange + pitchRange * pitchRange);

        float yaw;
        float pitch;
        if (currentDistance >= randomRadius) {
            yaw = base[0];
            pitch = base[1];
        } else {
            yaw = base[0] + randomVapeOffset(yawRange, 0.6D);
            pitch = base[1] + randomVapeOffset(pitchRange, 0.5D);
        }
        return new AimPoint(yaw, MathHelper.clamp_float(pitch, -90.0F, 90.0F));
    }

    private Vec3 findVisiblePoint(EntityPlayer enemy) {
        AxisAlignedBB box = enemy.getEntityBoundingBox().expand(0.03D, 0.03D, 0.03D);
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 closest = closestPoint(eye, box);
        double horizontal = multipointHorizontal.getValue() / 100.0D;
        double vertical = multipointVertical.getValue() / 100.0D;
        double widthX = box.maxX - box.minX;
        double widthZ = box.maxZ - box.minZ;
        double height = box.maxY - box.minY;
        double centerX = (box.minX + box.maxX) * 0.5D;
        double centerZ = (box.minZ + box.maxZ) * 0.5D;
        double centerY = box.minY + height * 0.62D;

        double x = centerX + (closest.xCoord - centerX) * horizontal;
        double z = centerZ + (closest.zCoord - centerZ) * horizontal;
        double closestY = MathHelper.clamp_double(closest.yCoord, box.minY + height * 0.18D,
                box.minY + height * 0.92D);
        double y = centerY + (closestY - centerY) * vertical;
        Vec3 primary = new Vec3(
                MathHelper.clamp_double(x, box.minX, box.maxX),
                MathHelper.clamp_double(y, box.minY + 0.08D, box.maxY - 0.04D),
                MathHelper.clamp_double(z, box.minZ, box.maxZ)
        );
        if (isAimPointAllowed(primary, enemy)) {
            return primary;
        }
        if (!Boolean.TRUE.equals(ignoreBehindWalls.getValue())
                && !Boolean.TRUE.equals(ignoreBehindEntities.getValue())) {
            return primary;
        }

        double maxXOffset = widthX * MathHelper.clamp_double(horizontal, 0.15D, 1.0D) * 0.5D;
        double maxZOffset = widthZ * MathHelper.clamp_double(horizontal, 0.15D, 1.0D) * 0.5D;
        double minY = box.minY + height * MathHelper.clamp_double(0.28D - vertical * 0.18D, 0.12D, 0.42D);
        double maxY = box.minY + height * MathHelper.clamp_double(0.72D + vertical * 0.20D, 0.58D, 0.95D);
        double[] xOffsets = new double[]{0.0D, maxXOffset, -maxXOffset};
        double[] zOffsets = new double[]{0.0D, maxZOffset, -maxZOffset};
        double[] yValues = new double[]{y, maxY, minY};
        for (double nextY : yValues) {
            for (double xOffset : xOffsets) {
                for (double zOffset : zOffsets) {
                    Vec3 point = new Vec3(
                            MathHelper.clamp_double(centerX + xOffset, box.minX, box.maxX),
                            MathHelper.clamp_double(nextY, box.minY + 0.08D, box.maxY - 0.04D),
                            MathHelper.clamp_double(centerZ + zOffset, box.minZ, box.maxZ)
                    );
                    if (isAimPointAllowed(point, enemy)) {
                        return point;
                    }
                }
            }
        }
        return null;
    }

    private boolean isAimPointAllowed(Vec3 point, EntityPlayer enemy) {
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        double allowedRange = range.getValue() + 0.15D;
        if (eye.squareDistanceTo(point) > allowedRange * allowedRange) {
            return false;
        }
        if (Boolean.TRUE.equals(ignoreBehindWalls.getValue())
                && mc.theWorld.rayTraceBlocks(eye, point, false, true, false) != null) {
            return false;
        }
        return !Boolean.TRUE.equals(ignoreBehindEntities.getValue()) || !isBlockedByEntity(eye, point, enemy);
    }

    private boolean isBlockedByEntity(Vec3 eye, Vec3 point, EntityPlayer targetEntity) {
        double targetDistance = eye.distanceTo(point);
        for (Entity entity : mc.theWorld.getLoadedEntityList()) {
            if (entity == mc.thePlayer || entity == targetEntity || !(entity instanceof EntityLivingBase)
                    || entity instanceof EntityArmorStand || entity.isDead) {
                continue;
            }
            float border = entity.getCollisionBorderSize();
            AxisAlignedBB box = entity.getEntityBoundingBox().expand(border, border, border);
            MovingObjectPosition intercept = box.calculateIntercept(eye, point);
            if (intercept != null && intercept.hitVec != null
                    && eye.distanceTo(intercept.hitVec) < targetDistance - 0.05D) {
                return true;
            }
        }
        return false;
    }

    private boolean conditionsMet() {
        if (!isInGame() || mc.currentScreen != null || (!mc.inGameHasFocus && !isAttackHeld())) {
            return false;
        }
        // 仅当 KillAura 实际启用并有目标时才让步，避免单独使用 AimAssist 时被无故禁用
        gq.yozakura.module.runtime.Module killAuraMod = gq.yozakura.runtime.YozakuraRuntime.moduleManager.modules.get(KillAura.class);
        if (killAuraMod != null && killAuraMod.isEnabled() && KillAura.target != null) {
            return false;
        }
        if (Boolean.TRUE.equals(weaponOnly.getValue()) && !CombatUtil.isHoldingWeapon()) {
            return false;
        }
        if (Boolean.TRUE.equals(clickAim.getValue()) && !isAttackHeld()) {
            return false;
        }
        if (Boolean.TRUE.equals(requireHover.getValue()) && !isHoveringLivingTarget()) {
            return false;
        }
        if (Boolean.TRUE.equals(stopWhenBreaking.getValue()) && isMining()) {
            if (miningStartTime == -1L) {
                miningStartTime = System.currentTimeMillis();
            }
            return System.currentTimeMillis() - miningStartTime < hoverDelay.getValue().longValue();
        }
        miningStartTime = -1L;
        return true;
    }

    private boolean isMining() {
        if (MinecraftAccessor.isHittingBlock(mc.playerController)) {
            return true;
        }
        return isAttackHeld()
                && mc.objectMouseOver != null
                && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK;
    }

    private boolean isHoveringLivingTarget() {
        return mc.objectMouseOver != null
                && mc.objectMouseOver.entityHit instanceof EntityLivingBase
                && !(mc.objectMouseOver.entityHit instanceof EntityArmorStand);
    }

    private float advanceVapeAxis(float current, float target, float configuredSpeed, boolean yawAxis) {
        float diff = MathHelper.wrapAngleTo180_float(target - current);
        float quantum = getMouseQuantum();
        float absDiff = Math.abs(diff);
        float stopDistance = Math.max(quantum * 0.22F, vapeMode.getValue() == VapeMode.BLATANT ? 0.025F : 0.045F);
        if (absDiff <= stopDistance) {
            if (yawAxis) {
                mouseState.yawCounts *= 0.62F;
            } else {
                mouseState.pitchCounts *= 0.62F;
            }
            return target;
        }

        float countDiff = diff / quantum;
        float accel = getVapeAcceleration(configuredSpeed, yawAxis);
        if (!yawAxis && isYawCloserThanPitch()) {
            accel *= MathHelper.clamp_float(Math.abs(countDiff) / Math.max(1.0F, Math.abs(mouseState.yawCounts)), 0.25F, 1.0F);
        }
        float counts = yawAxis ? mouseState.yawCounts : mouseState.pitchCounts;
        float desiredCounts;
        if (vapeMode.getValue() == VapeMode.BLATANT) {
            desiredCounts = MathHelper.clamp_float(countDiff, -accel, accel);
        } else {
            desiredCounts = countDiff > 0.0F ? Math.min(accel, countDiff) : -Math.min(accel, Math.abs(countDiff));
        }

        float response = vapeMode.getValue() == VapeMode.BLATANT ? 0.52F : 0.34F;
        counts += (desiredCounts - counts) * response;
        if (Math.abs(counts) < 0.015F) {
            counts = countDiff > 0.0F ? 0.015F : -0.015F;
        }
        if (yawAxis) {
            mouseState.yawCounts = counts;
        } else {
            mouseState.pitchCounts = counts;
        }
        float maxStep = Math.min(absDiff, Math.abs(counts * quantum));
        return limitAngle(current, target, maxStep);
    }

    private float getVapeAcceleration(float configuredSpeed, boolean yawAxis) {
        float base = configuredSpeed * 0.25F;
        if (vapeMode.getValue() == VapeMode.BLATANT) {
            base *= 1.35F;
        }
        return MathHelper.clamp_float(base, 0.18F, yawAxis ? 45.0F : 22.5F);
    }

    private float getMouseQuantum() {
        float sensitivity = mc.gameSettings == null ? 0.5F : mc.gameSettings.mouseSensitivity;
        float scaled = sensitivity * 0.6F + 0.2F;
        return scaled * scaled * scaled * 8.0F * 0.15F;
    }

    private boolean isYawCloserThanPitch() {
        float yawDiff = Math.abs(MathHelper.wrapAngleTo180_float(task.yaw - assistedYaw));
        float pitchDiff = Math.abs(MathHelper.wrapAngleTo180_float(task.pitch - assistedPitch));
        return yawDiff < pitchDiff && pitchDiff > 0.0F;
    }

    private float limitAngle(float current, float target, float maxTurn) {
        float delta = MathHelper.wrapAngleTo180_float(target - current);
        return current + MathHelper.clamp_float(delta, -maxTurn, maxTurn);
    }

    private float randomVapeOffset(float max, double negativeThreshold) {
        float amount = random.nextFloat()
                * Math.max(0.0F, max)
                * (randomization.getValue().floatValue() / 100.0F);
        return random.nextDouble() > negativeThreshold ? -amount : amount;
    }

    private double angleTo(EntityPlayer player, float viewYaw) {
        Vec3 point = new Vec3(player.posX,
                player.getEntityBoundingBox().minY + (player.getEntityBoundingBox().maxY
                        - player.getEntityBoundingBox().minY) * 0.62D,
                player.posZ);
        float[] rotations = RotationUtil.getRotationsTo(mc, point.xCoord, point.yCoord, point.zCoord);
        return Math.abs(MathHelper.wrapAngleTo180_float(rotations[0] - viewYaw));
    }

    private double distanceSqFromEyeToClosestOnAABB(EntityPlayer player) {
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        return eye.squareDistanceTo(closestPoint(eye, player.getEntityBoundingBox()));
    }

    private Vec3 closestPoint(Vec3 point, AxisAlignedBB box) {
        return new Vec3(
                MathHelper.clamp_double(point.xCoord, box.minX, box.maxX),
                MathHelper.clamp_double(point.yCoord, box.minY, box.maxY),
                MathHelper.clamp_double(point.zCoord, box.minZ, box.maxZ)
        );
    }

    private boolean isAttackHeld() {
        if (mc.gameSettings == null) {
            return isMouseButtonDown(0);
        }
        return KeyBindUtil.isBindingDown(mc.gameSettings.keyBindAttack) || isMouseButtonDown(0);
    }

    private boolean isMouseButtonDown(int button) {
        try {
            return Mouse.isCreated() && Mouse.isButtonDown(button);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void resetTarget() {
        this.target = null;
        this.targetId = -1;
        this.nextAimUpdate = 0L;
        this.taskExpireAt = 0L;
        this.rotationInitialized = false;
        this.assistedYaw = 0.0F;
        this.assistedPitch = 0.0F;
        this.task.active = false;
        this.mouseState.reset();
    }

    public static void assistFaceEntity(Entity entity, float yaw, float pitch) {
        CombatUtil.faceEntity(entity, yaw, pitch, pitch <= 0.0F, 0.0F);
    }

    public static float updateRotation(float current, float target, float maxTurn) {
        return CombatUtil.updateRotation(current, target, maxTurn);
    }

    public static List<Entity> getEntityList() {
        return mc.theWorld == null ? null : mc.theWorld.getLoadedEntityList();
    }

    private static final class RotationTask {
        private float yaw;
        private float pitch;
        private boolean active;
    }

    private static final class MouseRotationState {
        private float yawCounts;
        private float pitchCounts;

        private void reset() {
            yawCounts = 0.0F;
            pitchCounts = 0.0F;
        }
    }

    private static final class AimPoint {
        private final float yaw;
        private final float pitch;

        private AimPoint(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    private static final class AimResult {
        private final float yaw;
        private final float pitch;

        private AimResult(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }
}
