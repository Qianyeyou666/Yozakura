package gq.yozakura.module.world;

import gq.yozakura.event.bridge.UpdateEvent;
import gq.yozakura.event.bridge.RotationResolvedEvent;
import gq.yozakura.manager.VisualRotationState;
import gq.yozakura.util.module.BlockUtil;
import gq.yozakura.util.module.ItemUtil;
import gq.yozakura.util.module.RotationUtil;
import gq.yozakura.value.Option;
import net.minecraft.client.Minecraft;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovementInput;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/** Finds and publishes a vanilla right-click target for BridgeAssist pre-place. */
final class BridgeAssistPrePlaceController {
    private static final EnumFacing[] SIDES = new EnumFacing[]{
            EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.WEST
    };
    private static final String ROTATION_SOURCE = "BridgeAssist";
    private static final int ROTATION_PRIORITY = 1;
    private static final float MIN_LOOK_DOWN_PITCH = 70.0F;
    private static final float MIN_PRE_PLACE_PITCH = 61.0F;
    private static final float MAX_PRE_PLACE_PITCH = 90.0F;
    private static final float PITCH_SAMPLE_STEP = 1.0F;
    private static final float MAX_YAW_STEP = 15.0F;
    private static final float MAX_PITCH_STEP = 20.0F;

    private final Minecraft mc;
    private final Option<Boolean> prePlace;
    private final Option<Boolean> lookingDown;
    private final Option<Boolean> notMovingForward;

    private PendingTarget pendingTarget;
    private PreparedTarget preparedTarget;
    private MovingObjectPosition injectedMouseOver;
    private MovingObjectPosition previousMouseOver;

    BridgeAssistPrePlaceController(Minecraft mc, Option<Boolean> prePlace, Option<Boolean> lookingDown,
                                   Option<Boolean> notMovingForward) {
        this.mc = mc;
        this.prePlace = prePlace;
        this.lookingDown = lookingDown;
        this.notMovingForward = notMovingForward;
    }

    void onUpdate(UpdateEvent event) {
        reset();
        if (!canPrepareTarget()) {
            return;
        }

        float baseYaw = event.getNewYaw();
        float basePitch = event.getNewPitch();
        TargetResult target = findTarget(baseYaw, basePitch, mc.playerController.getBlockReachDistance());
        if (target == null) {
            return;
        }

        float[] smoothed = smoothRotation(baseYaw, basePitch, target.yaw, target.pitch,
                MAX_YAW_STEP, MAX_PITCH_STEP);
        MovingObjectPosition hit = RotationUtil.rayTrace(smoothed[0], smoothed[1],
                mc.playerController.getBlockReachDistance(), 1.0F);
        if (!isPrePlaceHit(hit, target)) {
            return;
        }
        if (!event.trySetRotation(smoothed[0], smoothed[1], ROTATION_PRIORITY)) {
            return;
        }
        pendingTarget = new PendingTarget(mc.theWorld, mc.thePlayer.ticksExisted,
                target.support, target.face, smoothed[0], smoothed[1]);
    }

    void onRotationResolved(RotationResolvedEvent event) {
        PendingTarget target = pendingTarget;
        pendingTarget = null;
        if (target == null || !matchesResolvedRotation(target, event)) {
            reset();
            return;
        }

        MovingObjectPosition hit = resolveTargetHit(target.world, target.tick, target.support, target.face,
                target.yaw, target.pitch);
        if (hit == null) {
            reset();
            return;
        }

        VisualRotationState.publish(ROTATION_SOURCE, target.yaw, target.pitch, ROTATION_PRIORITY);
        preparedTarget = new PreparedTarget(target.world, target.tick,
                target.support, target.face, target.yaw, target.pitch);
        installMouseOver(hit);
    }

    void onRightClick() {
        PreparedTarget target = preparedTarget;
        if (target == null) {
            return;
        }
        MovingObjectPosition hit = resolveTargetHit(target.world, target.tick, target.support, target.face,
                target.yaw, target.pitch);
        if (hit == null) {
            reset();
            return;
        }
        installMouseOver(hit);
    }

    void reset() {
        pendingTarget = null;
        preparedTarget = null;
        VisualRotationState.clearSource(ROTATION_SOURCE);
        clearInjectedMouseOver();
    }

    private boolean canPrepareTarget() {
        if (!Boolean.TRUE.equals(prePlace.getValue())) {
            return false;
        }
        if (!ItemUtil.isBlock(mc.thePlayer.getHeldItem())) {
            return false;
        }
        if (Boolean.TRUE.equals(lookingDown.getValue())
                && mc.thePlayer.rotationPitch < MIN_LOOK_DOWN_PITCH) {
            return false;
        }
        MovementInput input = mc.thePlayer.movementInput;
        return !Boolean.TRUE.equals(notMovingForward.getValue())
                || input == null
                || input.moveForward <= 0.0F;
    }

    private boolean matchesResolvedRotation(PendingTarget target, RotationResolvedEvent event) {
        return event.isRotated()
                && Float.compare(target.yaw, event.getYaw()) == 0
                && Float.compare(target.pitch, event.getPitch()) == 0;
    }

    private MovingObjectPosition resolveTargetHit(World world, int tick, BlockPos support, EnumFacing face,
                                                   float yaw, float pitch) {
        if (mc.theWorld != world || mc.thePlayer.ticksExisted != tick || !canPrepareTarget()) {
            return null;
        }
        if (BlockUtil.isReplaceable(support) || !BlockUtil.isReplaceable(support.offset(face))) {
            return null;
        }
        MovingObjectPosition hit = RotationUtil.rayTrace(yaw, pitch,
                mc.playerController.getBlockReachDistance(), 1.0F);
        if (hit == null
                || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                || !support.equals(hit.getBlockPos())
                || face != hit.sideHit) {
            return null;
        }
        return hit;
    }

    private void installMouseOver(MovingObjectPosition hit) {
        if (injectedMouseOver == hit) {
            mc.objectMouseOver = hit;
            return;
        }
        clearInjectedMouseOver();
        previousMouseOver = mc.objectMouseOver;
        injectedMouseOver = hit;
        mc.objectMouseOver = hit;
    }

    private void clearInjectedMouseOver() {
        if (injectedMouseOver != null && mc.objectMouseOver == injectedMouseOver) {
            mc.objectMouseOver = previousMouseOver;
        }
        injectedMouseOver = null;
        previousMouseOver = null;
    }

    private TargetResult findTarget(float yaw, float currentPitch, double reach) {
        AxisAlignedBB box = mc.thePlayer.getEntityBoundingBox();
        int standY = MathHelper.floor_double(box.minY) - 1;
        int minX = MathHelper.floor_double(box.minX);
        int maxX = MathHelper.floor_double(box.maxX);
        int minZ = MathHelper.floor_double(box.minZ);
        int maxZ = MathHelper.floor_double(box.maxZ);

        List<FaceTarget> targets = findReplaceableFaces(minX, maxX, standY, minZ, maxZ);
        if (targets.isEmpty()) {
            return null;
        }

        float bestDelta = Float.MAX_VALUE;
        float bestPitch = Float.NaN;
        BlockPos bestSupport = null;
        EnumFacing bestFace = null;
        for (float samplePitch = MIN_PRE_PLACE_PITCH;
             samplePitch <= MAX_PRE_PLACE_PITCH;
             samplePitch += PITCH_SAMPLE_STEP) {
            MovingObjectPosition mop = RotationUtil.rayTrace(yaw, samplePitch, reach, 1.0F);
            if (mop == null || mop.sideHit == EnumFacing.UP || mop.sideHit == EnumFacing.DOWN) {
                continue;
            }
            BlockPos hitBlock = mop.getBlockPos();
            for (FaceTarget target : targets) {
                if (hitBlock.equals(target.block) && mop.sideHit == target.face) {
                    float delta = Math.abs(samplePitch - currentPitch);
                    if (delta < bestDelta) {
                        bestDelta = delta;
                        bestPitch = samplePitch;
                        bestSupport = target.block;
                        bestFace = target.face;
                    }
                    break;
                }
            }
        }

        if (bestSupport == null || bestFace == null || Float.isNaN(bestPitch)) {
            return null;
        }
        return new TargetResult(yaw, bestPitch, bestSupport, bestFace);
    }

    private List<FaceTarget> findReplaceableFaces(int minX, int maxX, int standY, int minZ, int maxZ) {
        List<FaceTarget> targets = new ArrayList<FaceTarget>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos standBlock = new BlockPos(x, standY, z);
                if (BlockUtil.isReplaceable(standBlock)) {
                    continue;
                }
                for (EnumFacing side : SIDES) {
                    BlockPos placedBlock = standBlock.offset(side);
                    if (BlockUtil.isReplaceable(placedBlock)) {
                        targets.add(new FaceTarget(standBlock, side));
                    }
                }
            }
        }
        return targets;
    }

    private boolean isPrePlaceHit(MovingObjectPosition hit, TargetResult target) {
        return hit != null
                && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && hit.getBlockPos().equals(target.support)
                && hit.sideHit == target.face;
    }

    private float[] smoothRotation(float yaw, float pitch, float targetYaw, float targetPitch,
                                   float maxYawStep, float maxPitchStep) {
        float nextYaw = stepAngle(yaw, targetYaw, maxYawStep);
        float nextPitch = stepLinear(pitch, targetPitch, maxPitchStep);
        nextYaw = RotationUtil.quantizeAngle(nextYaw);
        nextPitch = RotationUtil.quantizeAngle(MathHelper.clamp_float(nextPitch, -90.0F, 90.0F));
        return new float[]{nextYaw, nextPitch};
    }

    private float stepAngle(float current, float target, float maxStep) {
        float diff = MathHelper.wrapAngleTo180_float(target - current);
        return current + MathHelper.clamp_float(diff, -maxStep, maxStep);
    }

    private float stepLinear(float current, float target, float maxStep) {
        float diff = target - current;
        return current + MathHelper.clamp_float(diff, -maxStep, maxStep);
    }

    private static final class FaceTarget {
        final BlockPos block;
        final EnumFacing face;

        FaceTarget(BlockPos block, EnumFacing face) {
            this.block = block;
            this.face = face;
        }
    }

    private static final class TargetResult {
        final float yaw;
        final float pitch;
        final BlockPos support;
        final EnumFacing face;

        TargetResult(float yaw, float pitch, BlockPos support, EnumFacing face) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.support = support;
            this.face = face;
        }
    }

    private static final class PreparedTarget {
        final World world;
        final int tick;
        final BlockPos support;
        final EnumFacing face;
        final float yaw;
        final float pitch;

        PreparedTarget(World world, int tick, BlockPos support, EnumFacing face,
                       float yaw, float pitch) {
            this.world = world;
            this.tick = tick;
            this.support = support;
            this.face = face;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    private static final class PendingTarget {
        final World world;
        final int tick;
        final BlockPos support;
        final EnumFacing face;
        final float yaw;
        final float pitch;

        PendingTarget(World world, int tick, BlockPos support, EnumFacing face, float yaw, float pitch) {
            this.world = world;
            this.tick = tick;
            this.support = support;
            this.face = face;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }
}
