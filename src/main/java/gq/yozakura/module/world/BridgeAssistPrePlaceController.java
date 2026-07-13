package gq.yozakura.module.world;

import gq.yozakura.event.bridge.UpdateEvent;
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
    private static final float MIN_PRE_PLACE_PITCH = 60.0F;
    private static final float MAX_PRE_PLACE_PITCH = 90.0F;
    private static final float MAX_YAW_STEP = 15.0F;
    private static final float MAX_PITCH_STEP = 20.0F;

    private final Minecraft mc;
    private final Option<Boolean> prePlace;
    private final Option<Boolean> lookingDown;
    private final Option<Boolean> notMovingForward;

    private MovingObjectPosition prePlaceMouseOver;

    BridgeAssistPrePlaceController(Minecraft mc, Option<Boolean> prePlace, Option<Boolean> lookingDown,
                                   Option<Boolean> notMovingForward) {
        this.mc = mc;
        this.prePlace = prePlace;
        this.lookingDown = lookingDown;
        this.notMovingForward = notMovingForward;
    }

    void onUpdate(UpdateEvent event) {
        if (!canPrepareTarget()) {
            reset();
            return;
        }

        float baseYaw = event.getNewYaw();
        float basePitch = event.getNewPitch();
        TargetResult target = findTarget(baseYaw, basePitch, mc.playerController.getBlockReachDistance());
        if (target == null) {
            reset();
            return;
        }

        float[] smoothed = smoothRotation(baseYaw, basePitch, target.yaw, target.pitch,
                MAX_YAW_STEP, MAX_PITCH_STEP);
        event.setRotation(smoothed[0], smoothed[1], ROTATION_PRIORITY);
        VisualRotationState.publish(ROTATION_SOURCE, smoothed[0], smoothed[1], ROTATION_PRIORITY);

        MovingObjectPosition hit = RotationUtil.rayTrace(smoothed[0], smoothed[1],
                mc.playerController.getBlockReachDistance(), 1.0F);
        prePlaceMouseOver = isPrePlaceHit(hit, target) ? hit : null;
        if (prePlaceMouseOver != null) {
            mc.objectMouseOver = prePlaceMouseOver;
        }
    }

    void onRightClick() {
        if (ItemUtil.isBlock(mc.thePlayer.getHeldItem()) && prePlaceMouseOver != null) {
            mc.objectMouseOver = prePlaceMouseOver;
        }
    }

    void reset() {
        prePlaceMouseOver = null;
        VisualRotationState.clearSource(ROTATION_SOURCE);
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
        for (float pitch = MIN_PRE_PLACE_PITCH; pitch <= MAX_PRE_PLACE_PITCH; ) {
            float step = 1.0F + (float) (Math.random() * 2.0D - 1.0D) * 0.38F;
            pitch += MathHelper.clamp_float(step, 0.4F, 1.8F);
            float samplePitch = Math.min(pitch, MAX_PRE_PLACE_PITCH);
            MovingObjectPosition mop = RotationUtil.rayTrace(yaw, samplePitch, reach, 1.0F);
            if (mop == null || mop.sideHit == EnumFacing.UP || mop.sideHit == EnumFacing.DOWN) {
                if (pitch >= MAX_PRE_PLACE_PITCH) {
                    break;
                }
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
            if (pitch >= MAX_PRE_PLACE_PITCH) {
                break;
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
}
