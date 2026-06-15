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

    public enum SortMode {
        HEALTH,
        ANGLE,
        HURT_TIME,
        DISTANCE
    }

    private final Mode<AimMode> mode = new Mode<AimMode>("Mode", "Mode", AimMode.values(), AimMode.NORMAL);
    private final Numbers<Double> speed = new Numbers<Double>("Speed", "Speed", 10.0, 1.0, 30.0, 1.0);
    private final Numbers<Double> multipointHorizontal =
            new Numbers<Double>("Multipoint horizontal", "MultipointHorizontal", 0.0, 0.0, 100.0, 1.0);
    private final Numbers<Double> multipointVertical =
            new Numbers<Double>("Multipoint vertical", "MultipointVertical", 0.0, 0.0, 100.0, 1.0);
    private final Numbers<Double> randomization =
            new Numbers<Double>("Randomization", "Randomization", 50.0, 0.0, 100.0, 1.0);
    private final Numbers<Double> fov = new Numbers<Double>("FOV", "FOV", 90.0, 15.0, 360.0, 1.0);
    private final Numbers<Double> range = new Numbers<Double>("Range", "Range", 4.5, 0.0, 5.0, 0.1);
    private final Mode<SortMode> sort = new Mode<SortMode>("Sort", "Sort", SortMode.values(), SortMode.ANGLE);
    private final Option<Boolean> ignoreBehindWalls =
            new Option<Boolean>("Ignore behind walls", "IgnoreBehindWalls", false);
    private final Option<Boolean> ignoreBehindEntities =
            new Option<Boolean>("Ignore behind entities", "IgnoreBehindEntities", false);
    private final Option<Boolean> aimInvis = new Option<Boolean>("Aim invis", "AimInvis", false);
    private final Option<Boolean> clickAim = new Option<Boolean>("Require mouse", "RequireMouse", true);
    private final Option<Boolean> ignoreTeammates =
            new Option<Boolean>("Ignore teammates", "IgnoreTeammates", true);
    private final Option<Boolean> stopWhenBreaking =
            new Option<Boolean>("Stop when breaking", "StopWhenBreaking", false);
    private final Option<Boolean> keepMoveDirection =
            new Option<Boolean>("Keep move direction", "KeepMoveDirection", true);
    private final Numbers<Double> hoverDelay =
            new Numbers<Double>("Hover delay", "HoverDelay", 100.0, 0.0, 500.0, 10.0);
    private final Option<Boolean> weaponOnly = new Option<Boolean>("Weapon only", "WeaponOnly", false);

    public EntityLivingBase target;

    private final Random random = new Random();
    private int targetId = -1;
    private long miningStartTime = -1L;
    private long nextRandomAt;
    private boolean rotationInitialized;
    private float assistedYaw;
    private float assistedPitch;
    private double randomOffsetX;
    private double randomOffsetY;
    private double randomOffsetZ;

    public Aimbot() {
        super("AimAssist", Keyboard.KEY_NONE, ModuleType.Combat, "Aim assist based on raven-bs");
        this.addValues(mode, speed, multipointHorizontal, multipointVertical, randomization, fov, range, sort,
                ignoreBehindWalls, ignoreBehindEntities, aimInvis, clickAim, ignoreTeammates,
                stopWhenBreaking, keepMoveDirection, hoverDelay, weaponOnly);
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
        if (event.getType() != EventType.PRE || mode.getValue() != AimMode.NORMAL) {
            return;
        }
        handleNormalAim();
    }

    private void handleNormalAim() {
        if (!conditionsMet()) {
            resetTarget();
            return;
        }

        EntityPlayer enemy = getEnemy(false);
        if (enemy == null) {
            resetTarget();
            return;
        }
        float[] rotations = getRotationsToTarget(enemy, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
        if (rotations == null) {
            resetTarget();
            return;
        }

        mc.thePlayer.rotationYaw = rotations[0];
        mc.thePlayer.rotationPitch = rotations[1];
        RotationUtil.syncHead(mc, rotations[0]);
    }

    @EventTarget(Priority.LOW)
    public void onUpdate(UpdateEvent event) {
        if (event.getType() != EventType.PRE || mode.getValue() != AimMode.SILENT) {
            return;
        }
        if (!conditionsMet()) {
            resetTarget();
            return;
        }

        EntityPlayer enemy = getEnemy(true);
        if (enemy == null) {
            resetTarget();
            return;
        }
        float[] rotations = getRotationsToTarget(enemy, event.getNewYaw(), event.getNewPitch());
        if (rotations == null) {
            resetTarget();
            return;
        }

        event.setRotation(rotations[0], rotations[1], 0);
        if (!Boolean.TRUE.equals(keepMoveDirection.getValue())) {
            event.setPervRotation(rotations[0], 0);
        }
    }

    private EntityPlayer getEnemy(boolean silentMode) {
        float viewYaw = silentMode && rotationInitialized ? assistedYaw : mc.thePlayer.rotationYaw;
        ArrayList<EntityPlayer> candidates = new ArrayList<EntityPlayer>();
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (!isValidCandidate(player, viewYaw)) {
                continue;
            }
            candidates.add(player);
        }
        if (candidates.isEmpty()) {
            return null;
        }

        candidates.sort(targetComparator());
        if (Boolean.TRUE.equals(ignoreBehindWalls.getValue())
                || Boolean.TRUE.equals(ignoreBehindEntities.getValue())) {
            for (EntityPlayer candidate : candidates) {
                if (findAimPoint(candidate, false) != null) {
                    return candidate;
                }
            }
            return null;
        }
        return candidates.get(0);
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
        if (TeamUtil.isBot(player) || AntiBot.isServerBot(player)) {
            return false;
        }
        double rangeValue = range.getValue();
        if (distanceSqFromEyeToClosestOnAABB(player) > rangeValue * rangeValue) {
            return false;
        }
        float fovValue = fov.getValue().floatValue();
        if (fovValue < 360.0F) {
            float yaw = yawTo(player.posX, player.posZ);
            if (Math.abs(MathHelper.wrapAngleTo180_float(yaw - viewYaw)) > fovValue * 0.5F) {
                return false;
            }
        }
        return true;
    }

    private Comparator<EntityPlayer> targetComparator() {
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
                primary = Comparator.comparingDouble(this::angleScore);
                break;
        }
        return primary.thenComparingDouble(player -> mc.thePlayer.getDistanceSqToEntity(player));
    }

    private float[] getRotationsToTarget(EntityPlayer enemy, float currentYaw, float currentPitch) {
        prepareTarget(enemy, currentYaw, currentPitch);
        Vec3 aimPoint = findAimPoint(enemy, true);
        if (aimPoint == null) {
            return null;
        }

        float[] targetRotations = RotationUtil.getRotationsTo(mc, aimPoint.xCoord, aimPoint.yCoord, aimPoint.zCoord);
        float baseYaw = rotationInitialized ? assistedYaw : currentYaw;
        float basePitch = rotationInitialized ? assistedPitch : currentPitch;
        float yawDiff = MathHelper.wrapAngleTo180_float(targetRotations[0] - baseYaw);
        float pitchDiff = MathHelper.wrapAngleTo180_float(targetRotations[1] - basePitch);
        float speedValue = speed.getValue().floatValue();
        float randomScale = 1.0F - random.nextFloat() * randomization.getValue().floatValue() * 0.0015F;
        float yawScale = MathHelper.clamp_float(Math.abs(yawDiff) / 60.0F, 0.25F, 1.0F);
        float pitchScale = MathHelper.clamp_float(Math.abs(pitchDiff) / 42.0F, 0.25F, 1.0F);
        float maxYaw = (0.35F + speedValue * 0.58F) * yawScale * randomScale;
        float maxPitch = (0.20F + speedValue * 0.38F) * pitchScale * randomScale;

        assistedYaw = baseYaw + MathHelper.clamp_float(yawDiff, -maxYaw, maxYaw);
        assistedPitch = MathHelper.clamp_float(
                basePitch + MathHelper.clamp_float(pitchDiff, -maxPitch, maxPitch),
                -90.0F,
                90.0F
        );
        rotationInitialized = true;
        target = enemy;
        return new float[]{assistedYaw, assistedPitch};
    }

    private void prepareTarget(EntityPlayer enemy, float currentYaw, float currentPitch) {
        if (enemy.getEntityId() != targetId) {
            targetId = enemy.getEntityId();
            rotationInitialized = false;
            nextRandomAt = 0L;
            randomOffsetX = 0.0D;
            randomOffsetY = 0.0D;
            randomOffsetZ = 0.0D;
        }
        if (!rotationInitialized) {
            assistedYaw = currentYaw;
            assistedPitch = currentPitch;
            rotationInitialized = true;
        }
        updateRandomOffsets(enemy);
    }

    private Vec3 findAimPoint(EntityPlayer enemy, boolean includeRandomness) {
        AxisAlignedBB box = enemy.getEntityBoundingBox().expand(0.03D, 0.03D, 0.03D);
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 closest = closestPoint(eye, box);
        double widthX = box.maxX - box.minX;
        double widthZ = box.maxZ - box.minZ;
        double height = box.maxY - box.minY;
        double horizontal = multipointHorizontal.getValue() / 100.0D;
        double vertical = multipointVertical.getValue() / 100.0D;
        double centerX = (box.minX + box.maxX) * 0.5D;
        double centerZ = (box.minZ + box.maxZ) * 0.5D;
        double centerY = box.minY + height * 0.62D;
        double targetX = centerX + (closest.xCoord - centerX) * horizontal;
        double targetZ = centerZ + (closest.zCoord - centerZ) * horizontal;
        double closestY = MathHelper.clamp_double(closest.yCoord, box.minY + height * 0.18D,
                box.minY + height * 0.92D);
        double targetY = centerY + (closestY - centerY) * vertical;

        Vec3 primary = new Vec3(
                MathHelper.clamp_double(targetX + (includeRandomness ? randomOffsetX : 0.0D),
                        box.minX, box.maxX),
                MathHelper.clamp_double(targetY + (includeRandomness ? randomOffsetY : 0.0D),
                        box.minY + 0.08D, box.maxY - 0.04D),
                MathHelper.clamp_double(targetZ + (includeRandomness ? randomOffsetZ : 0.0D),
                        box.minZ, box.maxZ)
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
        double[] yValues = new double[]{targetY, maxY, minY};
        for (double y : yValues) {
            for (double xOffset : xOffsets) {
                for (double zOffset : zOffsets) {
                    Vec3 point = new Vec3(
                            MathHelper.clamp_double(centerX + xOffset, box.minX, box.maxX),
                            MathHelper.clamp_double(y, box.minY + 0.08D, box.maxY - 0.04D),
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
        if (KillAura.target != null) {
            return false;
        }
        if (Boolean.TRUE.equals(weaponOnly.getValue()) && !CombatUtil.isHoldingWeapon()) {
            return false;
        }
        if (Boolean.TRUE.equals(clickAim.getValue()) && !isAttackHeld()) {
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

    private double angleScore(EntityPlayer player) {
        Vec3 point = new Vec3(player.posX,
                player.getEntityBoundingBox().minY + (player.getEntityBoundingBox().maxY
                        - player.getEntityBoundingBox().minY) * 0.62D,
                player.posZ);
        float[] rotations = RotationUtil.getRotationsTo(mc, point.xCoord, point.yCoord, point.zCoord);
        double yawDelta = Math.abs(MathHelper.wrapAngleTo180_float(rotations[0] - mc.thePlayer.rotationYaw));
        double pitchDelta = Math.abs(MathHelper.wrapAngleTo180_float(rotations[1] - mc.thePlayer.rotationPitch));
        return yawDelta + pitchDelta;
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

    private void updateRandomOffsets(EntityPlayer enemy) {
        double amount = randomization.getValue() / 100.0D;
        if (amount <= 0.0D) {
            randomOffsetX = 0.0D;
            randomOffsetY = 0.0D;
            randomOffsetZ = 0.0D;
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextRandomAt) {
            return;
        }
        AxisAlignedBB box = enemy.getEntityBoundingBox();
        double width = Math.max(box.maxX - box.minX, box.maxZ - box.minZ);
        double height = box.maxY - box.minY;
        randomOffsetX = (random.nextDouble() - 0.5D) * width * 0.22D * amount;
        randomOffsetZ = (random.nextDouble() - 0.5D) * width * 0.22D * amount;
        randomOffsetY = (random.nextDouble() - 0.5D) * height * 0.12D * amount;
        nextRandomAt = now + 260L + random.nextInt(421);
    }

    private float yawTo(double x, double z) {
        double diffX = x - mc.thePlayer.posX;
        double diffZ = z - mc.thePlayer.posZ;
        return (float) (Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0D);
    }

    private boolean isAttackHeld() {
        int key = mc.gameSettings.keyBindAttack.getKeyCode();
        try {
            return key < 0 ? Mouse.isCreated() && Mouse.isButtonDown(key + 100) : Keyboard.isKeyDown(key);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void resetTarget() {
        this.target = null;
        this.targetId = -1;
        this.nextRandomAt = 0L;
        this.rotationInitialized = false;
        this.assistedYaw = 0.0F;
        this.assistedPitch = 0.0F;
        this.randomOffsetX = 0.0D;
        this.randomOffsetY = 0.0D;
        this.randomOffsetZ = 0.0D;
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
}
