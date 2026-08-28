package gq.yozakura.module.combat.aim;

import gq.yozakura.util.minecraft.RotationUtil;
import gq.yozakura.module.combat.AntiBot;
import gq.yozakura.util.module.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.passive.EntityAmbientCreature;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.passive.EntityWaterMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class AimAssistTargetSelector {
    public enum Sort {
        HEALTH,
        ANGLE,
        HURT_TIME,
        DISTANCE
    }

    public enum AimPointMode {
        ADAPTIVE,
        LOCK_ON_HEAD
    }

    private static final long MINIMUM_LOCK_MILLIS = 150L;

    private int lockedTargetId = -1;
    private long lockedAtMillis;

    public Selection select(Minecraft minecraft, float viewYaw, Settings settings, long nowMillis) {
        if (minecraft == null || minecraft.thePlayer == null || minecraft.theWorld == null) {
            clear();
            return null;
        }
        if (settings == null) {
            throw new IllegalArgumentException("settings cannot be null");
        }

        List<Blocker> blockers = settings.ignoreBehindEntities
                ? collectBlockers(minecraft)
                : Collections.<Blocker>emptyList();
        List<Candidate> candidates = new ArrayList<Candidate>();
        for (Entity entity : minecraft.theWorld.getLoadedEntityList()) {
            if (!(entity instanceof EntityLivingBase)) {
                continue;
            }
            EntityLivingBase living = (EntityLivingBase) entity;
            if (!isValidCandidate(minecraft, living, viewYaw, settings)) {
                continue;
            }
            Vec3 point = findAimPoint(minecraft, living, settings, blockers);
            if (point == null) {
                continue;
            }
            candidates.add(new Candidate(living, point, score(minecraft, living, point, viewYaw, settings.sort),
                    distanceToTarget(minecraft, living)));
        }
        if (candidates.isEmpty()) {
            clear();
            return null;
        }

        Collections.sort(candidates, new Comparator<Candidate>() {
            @Override
            public int compare(Candidate first, Candidate second) {
                int scoreOrder = Double.compare(first.score, second.score);
                return scoreOrder != 0 ? scoreOrder : Double.compare(first.distance, second.distance);
            }
        });

        Candidate best = candidates.get(0);
        Candidate locked = findLockedCandidate(candidates);
        Candidate selected = best;
        if (locked != null && locked.target.getEntityId() != best.target.getEntityId()) {
            long lockedFor = Math.max(0L, nowMillis - lockedAtMillis);
            if (!shouldSwitch(locked.score, best.score, switchMargin(settings.sort), lockedFor,
                    MINIMUM_LOCK_MILLIS)) {
                selected = locked;
            }
        }

        int selectedId = selected.target.getEntityId();
        if (selectedId != lockedTargetId) {
            lockedTargetId = selectedId;
            lockedAtMillis = nowMillis;
        }
        return new Selection(selected.target, selected.point);
    }

    public void clear() {
        lockedTargetId = -1;
        lockedAtMillis = 0L;
    }

    public int getLockedTargetId() {
        return lockedTargetId;
    }

    /**
     * Keep the lock decision next to the target-selection state machine. The
     * standalone Lunar loader can then resolve target refresh even when an old
     * injected class path is missing the optional test/helper facade.
     */
    static boolean shouldSwitch(double currentScore, double challengerScore, double margin,
                                long lockedForMillis, long minimumLockMillis) {
        return lockedForMillis >= minimumLockMillis && challengerScore + margin < currentScore;
    }

    public boolean isStillEligible(Minecraft minecraft, EntityLivingBase target, float viewYaw, Settings settings) {
        if (minecraft == null || minecraft.thePlayer == null || minecraft.theWorld == null || target == null
                || settings == null || minecraft.theWorld.getEntityByID(target.getEntityId()) != target) {
            return false;
        }
        return isValidCandidate(minecraft, target, viewYaw, settings);
    }

    private Candidate findLockedCandidate(List<Candidate> candidates) {
        if (lockedTargetId < 0) {
            return null;
        }
        for (Candidate candidate : candidates) {
            if (candidate.target.getEntityId() == lockedTargetId) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isValidCandidate(Minecraft minecraft, EntityLivingBase target, float viewYaw, Settings settings) {
        if (target == null || target == minecraft.thePlayer || target.isDead || target.deathTime != 0
                || target.getHealth() <= 0.0F || target instanceof EntityArmorStand) {
            return false;
        }
        if (!isTargetTypeAllowed(target, settings)) {
            return false;
        }
        if (target instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) target;
            if (TeamUtil.isFriend(player)) {
                return false;
            }
            if (settings.ignoreTeammates && TeamUtil.isSameTeam(player)) {
                return false;
            }
            if (settings.botCheck && AntiBot.isServerBot(player)) {
                return false;
            }
        }
        if (!settings.aimInvisible && target.isInvisible()) {
            return false;
        }
        if (distanceSqFromEyeToClosestOnAabb(minecraft, target) > settings.range * settings.range) {
            return false;
        }
        if (settings.fov < 360.0D) {
            Vec3 center = centerPoint(target.getEntityBoundingBox());
            if (angleTo(minecraft, center, viewYaw) > settings.fov * 0.5D) {
                return false;
            }
        }
        return true;
    }

    private boolean isTargetTypeAllowed(EntityLivingBase target, Settings settings) {
        if (settings.aimPointMode == AimPointMode.LOCK_ON_HEAD) {
            return target instanceof EntityPlayer && settings.targetPlayers;
        }
        if (target instanceof EntityPlayer) {
            return settings.targetPlayers;
        }
        if (target instanceof EntityAnimal || target instanceof EntityWaterMob
                || target instanceof EntityAmbientCreature || target instanceof EntityVillager) {
            return settings.targetAnimals;
        }
        return settings.targetMobs;
    }

    private Vec3 findAimPoint(Minecraft minecraft, EntityLivingBase target, Settings settings, List<Blocker> blockers) {
        if (settings.aimPointMode == AimPointMode.LOCK_ON_HEAD) {
            return findLockOnHeadPoint(minecraft, (EntityPlayer) target, settings, blockers);
        }
        AxisAlignedBB box = target.getEntityBoundingBox().expand(0.03D, 0.03D, 0.03D);
        Vec3 eye = minecraft.thePlayer.getPositionEyes(1.0F);
        Vec3 closest = closestPoint(eye, box);
        double horizontal = settings.multipointHorizontal;
        double vertical = settings.multipointVertical;
        double widthX = box.maxX - box.minX;
        double widthZ = box.maxZ - box.minZ;
        double height = box.maxY - box.minY;
        double centerX = (box.minX + box.maxX) * 0.5D;
        double centerZ = (box.minZ + box.maxZ) * 0.5D;
        double aimX = centerX + (closest.xCoord - centerX) * horizontal;
        double aimZ = centerZ + (closest.zCoord - centerZ) * horizontal;
        double levelY = preferredLevelHeight(eye.yCoord, box);
        Vec3 preferredLevelPoint = createPoint(box, aimX, levelY, aimZ);
        if (isAimPointAllowed(minecraft, preferredLevelPoint, target, settings, blockers)) {
            return preferredLevelPoint;
        }

        double centerY = box.minY + height * 0.62D;
        double closestY = MathHelper.clamp_double(closest.yCoord, box.minY + height * 0.18D,
                box.minY + height * 0.92D);
        Vec3 primary = createPoint(box,
                aimX,
                centerY + (closestY - centerY) * vertical,
                aimZ);
        if (isAimPointAllowed(minecraft, primary, target, settings, blockers)) {
            return primary;
        }
        if (!settings.ignoreBehindWalls && !settings.ignoreBehindEntities) {
            Vec3 nearest = createPoint(box, closest.xCoord, closest.yCoord, closest.zCoord);
            return isAimPointAllowed(minecraft, nearest, target, settings, blockers) ? nearest : null;
        }

        double xOffset = widthX * MathHelper.clamp_double(horizontal, 0.15D, 1.0D) * 0.5D;
        double zOffset = widthZ * MathHelper.clamp_double(horizontal, 0.15D, 1.0D) * 0.5D;
        double minY = box.minY + height * MathHelper.clamp_double(0.28D - vertical * 0.18D, 0.12D, 0.42D);
        double maxY = box.minY + height * MathHelper.clamp_double(0.72D + vertical * 0.20D, 0.58D, 0.95D);
        List<Vec3> alternatives = new ArrayList<Vec3>(10);
        alternatives.add(createPoint(box, centerX + xOffset, primary.yCoord, centerZ));
        alternatives.add(createPoint(box, centerX - xOffset, primary.yCoord, centerZ));
        alternatives.add(createPoint(box, centerX, primary.yCoord, centerZ + zOffset));
        alternatives.add(createPoint(box, centerX, primary.yCoord, centerZ - zOffset));
        alternatives.add(createPoint(box, centerX + xOffset, primary.yCoord, centerZ + zOffset));
        alternatives.add(createPoint(box, centerX + xOffset, primary.yCoord, centerZ - zOffset));
        alternatives.add(createPoint(box, centerX - xOffset, primary.yCoord, centerZ + zOffset));
        alternatives.add(createPoint(box, centerX - xOffset, primary.yCoord, centerZ - zOffset));
        alternatives.add(createPoint(box, centerX, maxY, centerZ));
        alternatives.add(createPoint(box, centerX, minY, centerZ));

        Vec3 best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Vec3 alternative : alternatives) {
            if (!isAimPointAllowed(minecraft, alternative, target, settings, blockers)) {
                continue;
            }
            double distance = alternative.squareDistanceTo(primary);
            if (distance < bestDistance) {
                best = alternative;
                bestDistance = distance;
            }
        }
        return best;
    }

    private Vec3 findLockOnHeadPoint(Minecraft minecraft, EntityPlayer target,
                                     Settings settings, List<Blocker> blockers) {
        AxisAlignedBB box = target.getEntityBoundingBox();
        AimAssistLockOnGeometry.Frame frame = AimAssistLockOnGeometry.create(
                box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
                target.renderYawOffset, settings.multipointHorizontal, settings.multipointVertical);
        Vec3 eye = minecraft.thePlayer.getPositionEyes(1.0F);
        AimAssistLockOnGeometry.Point nearest = frame.nearestHeadPoint(
                eye.xCoord, eye.yCoord, eye.zCoord);
        Vec3 preferred = new Vec3(nearest.getX(), nearest.getY(), nearest.getZ());
        if (isAimPointAllowed(minecraft, preferred, target, settings, blockers)) {
            return preferred;
        }

        AimAssistLockOnGeometry.Region head = frame.get(AimAssistLockOnGeometry.Zone.HEAD);
        Vec3 best = null;
        double bestAngleDistance = Double.MAX_VALUE;
        float[] preferredRotation = RotationUtil.getRotationsTo(
                minecraft, preferred.xCoord, preferred.yCoord, preferred.zCoord);
        for (int yIndex = 0; yIndex < 3; yIndex++) {
            double y = head.getMinimumY() + (head.getMaximumY() - head.getMinimumY())
                    * (0.2D + yIndex * 0.3D);
            for (int xIndex = 0; xIndex < 5; xIndex++) {
                double x = box.minX + (box.maxX - box.minX) * (0.1D + xIndex * 0.2D);
                for (int zIndex = 0; zIndex < 5; zIndex++) {
                    double z = box.minZ + (box.maxZ - box.minZ) * (0.1D + zIndex * 0.2D);
                    if (!head.contains(x, y, z)) {
                        continue;
                    }
                    Vec3 candidate = new Vec3(x, y, z);
                    if (!isAimPointAllowed(minecraft, candidate, target, settings, blockers)) {
                        continue;
                    }
                    float[] rotation = RotationUtil.getRotationsTo(minecraft, x, y, z);
                    double yawDistance = Math.abs(MathHelper.wrapAngleTo180_float(
                            rotation[0] - preferredRotation[0]));
                    double pitchDistance = Math.abs(rotation[1] - preferredRotation[1]);
                    double angleDistance = yawDistance * yawDistance + pitchDistance * pitchDistance;
                    if (angleDistance < bestAngleDistance) {
                        best = candidate;
                        bestAngleDistance = angleDistance;
                    }
                }
            }
        }
        return best;
    }

    static double preferredLevelHeight(double eyeY, AxisAlignedBB box) {
        return preferredLevelHeight(eyeY, box.minY, box.maxY);
    }

    static double preferredLevelHeight(double eyeY, double minY, double maxY) {
        return Math.max(minY + 0.08D, Math.min(maxY - 0.04D, eyeY));
    }

    private Vec3 createPoint(AxisAlignedBB box, double x, double y, double z) {
        return new Vec3(
                MathHelper.clamp_double(x, box.minX, box.maxX),
                MathHelper.clamp_double(y, box.minY + 0.08D, box.maxY - 0.04D),
                MathHelper.clamp_double(z, box.minZ, box.maxZ)
        );
    }

    public boolean isLineAllowed(Minecraft minecraft, Vec3 eye, Vec3 point,
                                 EntityLivingBase target, Settings settings) {
        if (minecraft == null || minecraft.theWorld == null || eye == null || point == null
                || target == null || settings == null) {
            return false;
        }
        double allowedRange = settings.range + 0.15D;
        if (eye.squareDistanceTo(point) > allowedRange * allowedRange) {
            return false;
        }
        if (settings.ignoreBehindWalls
                && minecraft.theWorld.rayTraceBlocks(eye, point, false, true, false) != null) {
            return false;
        }
        if (!settings.ignoreBehindEntities) {
            return true;
        }
        double targetDistance = eye.distanceTo(point);
        for (Entity entity : minecraft.theWorld.getLoadedEntityList()) {
            if (entity == minecraft.thePlayer || entity == target || !(entity instanceof EntityLivingBase)
                    || entity instanceof EntityArmorStand || entity.isDead) {
                continue;
            }
            float border = entity.getCollisionBorderSize();
            MovingObjectPosition intercept = entity.getEntityBoundingBox().expand(border, border, border)
                    .calculateIntercept(eye, point);
            if (intercept != null && intercept.hitVec != null
                    && eye.distanceTo(intercept.hitVec) < targetDistance - 0.05D) {
                return false;
            }
        }
        return true;
    }

    private boolean isAimPointAllowed(Minecraft minecraft, Vec3 point, EntityLivingBase target, Settings settings,
                                      List<Blocker> blockers) {
        Vec3 eye = minecraft.thePlayer.getPositionEyes(1.0F);
        double allowedRange = settings.range + 0.15D;
        if (eye.squareDistanceTo(point) > allowedRange * allowedRange) {
            return false;
        }
        if (settings.ignoreBehindWalls
                && minecraft.theWorld.rayTraceBlocks(eye, point, false, true, false) != null) {
            return false;
        }
        return !settings.ignoreBehindEntities || !isBlockedByEntity(eye, point, target, blockers);
    }

    private boolean isBlockedByEntity(Vec3 eye, Vec3 point, EntityLivingBase target, List<Blocker> blockers) {
        double targetDistance = eye.distanceTo(point);
        for (Blocker blocker : blockers) {
            if (blocker.entity == target) {
                continue;
            }
            MovingObjectPosition intercept = blocker.box.calculateIntercept(eye, point);
            if (intercept != null && intercept.hitVec != null
                    && eye.distanceTo(intercept.hitVec) < targetDistance - 0.05D) {
                return true;
            }
        }
        return false;
    }

    private List<Blocker> collectBlockers(Minecraft minecraft) {
        List<Blocker> blockers = new ArrayList<Blocker>();
        for (Entity entity : minecraft.theWorld.getLoadedEntityList()) {
            if (entity == minecraft.thePlayer || !(entity instanceof EntityLivingBase)
                    || entity instanceof EntityArmorStand || entity.isDead) {
                continue;
            }
            float border = entity.getCollisionBorderSize();
            blockers.add(new Blocker(entity, entity.getEntityBoundingBox().expand(border, border, border)));
        }
        return blockers;
    }

    private double score(Minecraft minecraft, EntityLivingBase target, Vec3 point, float viewYaw, Sort sort) {
        switch (sort) {
            case HEALTH:
                return target.getHealth() + target.getAbsorptionAmount();
            case HURT_TIME:
                return target.hurtTime;
            case DISTANCE:
                return distanceToTarget(minecraft, target);
            case ANGLE:
            default:
                return angleTo(minecraft, point, viewYaw);
        }
    }

    private double distanceToTarget(Minecraft minecraft, EntityLivingBase target) {
        return Math.sqrt(distanceSqFromEyeToClosestOnAabb(minecraft, target));
    }

    private double angleTo(Minecraft minecraft, Vec3 point, float viewYaw) {
        float[] rotations = RotationUtil.getRotationsTo(minecraft, point.xCoord, point.yCoord, point.zCoord);
        return Math.abs(MathHelper.wrapAngleTo180_float(rotations[0] - viewYaw));
    }

    private double distanceSqFromEyeToClosestOnAabb(Minecraft minecraft, EntityLivingBase target) {
        Vec3 eye = minecraft.thePlayer.getPositionEyes(1.0F);
        return eye.squareDistanceTo(closestPoint(eye, target.getEntityBoundingBox()));
    }

    private static Vec3 centerPoint(AxisAlignedBB box) {
        return new Vec3((box.minX + box.maxX) * 0.5D,
                box.minY + (box.maxY - box.minY) * 0.62D,
                (box.minZ + box.maxZ) * 0.5D);
    }

    private static Vec3 closestPoint(Vec3 point, AxisAlignedBB box) {
        return new Vec3(
                MathHelper.clamp_double(point.xCoord, box.minX, box.maxX),
                MathHelper.clamp_double(point.yCoord, box.minY, box.maxY),
                MathHelper.clamp_double(point.zCoord, box.minZ, box.maxZ)
        );
    }

    private static double switchMargin(Sort sort) {
        switch (sort) {
            case HEALTH:
                return 1.0D;
            case HURT_TIME:
                return 1.0D;
            case DISTANCE:
                return 0.25D;
            case ANGLE:
            default:
                return 2.25D;
        }
    }

    public static final class Settings {
        private final double range;
        private final double fov;
        private final Sort sort;
        private final boolean ignoreBehindWalls;
        private final boolean ignoreBehindEntities;
        private final boolean aimInvisible;
        private final boolean ignoreTeammates;
        private final boolean botCheck;
        private final boolean targetPlayers;
        private final boolean targetAnimals;
        private final boolean targetMobs;
        private final double multipointHorizontal;
        private final double multipointVertical;
        private final AimPointMode aimPointMode;

        public Settings(double range, double fov, Sort sort, boolean ignoreBehindWalls,
                        boolean ignoreBehindEntities, boolean aimInvisible, boolean ignoreTeammates,
                        boolean botCheck, boolean targetPlayers, boolean targetAnimals, boolean targetMobs,
                        double multipointHorizontal, double multipointVertical) {
            this(range, fov, sort, ignoreBehindWalls, ignoreBehindEntities, aimInvisible,
                    ignoreTeammates, botCheck, targetPlayers, targetAnimals, targetMobs,
                    multipointHorizontal, multipointVertical, AimPointMode.ADAPTIVE);
        }

        public Settings(double range, double fov, Sort sort, boolean ignoreBehindWalls,
                        boolean ignoreBehindEntities, boolean aimInvisible, boolean ignoreTeammates,
                        boolean botCheck, boolean targetPlayers, boolean targetAnimals, boolean targetMobs,
                        double multipointHorizontal, double multipointVertical, AimPointMode aimPointMode) {
            if (!isFinite(range) || range < 0.0D || !isFinite(fov) || fov < 0.0D || sort == null) {
                throw new IllegalArgumentException("Invalid target selector settings");
            }
            this.range = range;
            this.fov = fov;
            this.sort = sort;
            this.ignoreBehindWalls = ignoreBehindWalls;
            this.ignoreBehindEntities = ignoreBehindEntities;
            this.aimInvisible = aimInvisible;
            this.ignoreTeammates = ignoreTeammates;
            this.botCheck = botCheck;
            this.targetPlayers = targetPlayers;
            this.targetAnimals = targetAnimals;
            this.targetMobs = targetMobs;
            this.multipointHorizontal = MathHelper.clamp_double(multipointHorizontal, 0.0D, 1.0D);
            this.multipointVertical = MathHelper.clamp_double(multipointVertical, 0.0D, 1.0D);
            this.aimPointMode = aimPointMode == null ? AimPointMode.ADAPTIVE : aimPointMode;
        }
    }

    public static final class Selection {
        private final EntityLivingBase target;
        private final Vec3 point;

        private Selection(EntityLivingBase target, Vec3 point) {
            this.target = target;
            this.point = point;
        }

        public EntityLivingBase getTarget() {
            return target;
        }

        public Vec3 getPoint() {
            return point;
        }
    }

    private static final class Candidate {
        private final EntityLivingBase target;
        private final Vec3 point;
        private final double score;
        private final double distance;

        private Candidate(EntityLivingBase target, Vec3 point, double score, double distance) {
            this.target = target;
            this.point = point;
            this.score = score;
            this.distance = distance;
        }
    }

    private static final class Blocker {
        private final Entity entity;
        private final AxisAlignedBB box;

        private Blocker(Entity entity, AxisAlignedBB box) {
            this.entity = entity;
            this.box = box;
        }
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
