package gq.vapulite.Vapu.modules.world;

import gq.vapulite.Vapu.ModuleType;
import gq.vapulite.Vapu.modules.Module;
import gq.vapulite.Vapu.utils.RotationUtil;
import gq.vapulite.Vapu.value.Mode;
import gq.vapulite.Vapu.value.Numbers;
import gq.vapulite.Vapu.value.Option;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.block.BlockFalling;
import net.minecraft.block.BlockLiquid;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

public class Scaffold extends Module {
    public enum AimMode {
        OFF,
        SMOOTH,
        LOCK
    }

    private final Mode<AimMode> aimMode = new Mode<AimMode>("Aim", "Aim", AimMode.values(), AimMode.SMOOTH);
    private final Numbers<Double> aimSpeed = new Numbers<Double>("Aim Speed", "AimSpeed", 28.0, 5.0, 90.0, 1.0);
    private final Numbers<Double> aimFov = new Numbers<Double>("Aim FOV", "AimFOV", 110.0, 25.0, 180.0, 5.0);
    private final Numbers<Double> aimLockTicks = new Numbers<Double>("Aim Lock", "AimLock", 5.0, 0.0, 12.0, 1.0);
    private final Numbers<Double> switchMargin = new Numbers<Double>("Switch Margin", "SwitchMargin", 18.0, 4.0, 45.0, 1.0);
    private final Numbers<Double> prediction = new Numbers<Double>("Prediction", "Prediction", 0.65, 0.0, 1.5, 0.05);
    private final Numbers<Double> edgeDistance = new Numbers<Double>("Edge Guard", "EdgeGuard", 0.22, 0.05, 0.45, 0.01);
    private final Option<Boolean> rightClickOnly = new Option<Boolean>("Right Click Only", "RightClickOnly", true);
    private final Option<Boolean> smartAim = new Option<Boolean>("Smart Aim", "SmartAim", true);
    private final Option<Boolean> assistAim = new Option<Boolean>("Assist Aim", "AssistAim", true);
    private final Option<Boolean> aimRightOnly = new Option<Boolean>("Aim Right Only", "AimRightOnly", true);
    private final Option<Boolean> syncHead = new Option<Boolean>("Sync Head", "SyncHead", true);
    private final Option<Boolean> edgeSneak = new Option<Boolean>("Edge Sneak", "EdgeSneak", true);
    private final Option<Boolean> motionGuard = new Option<Boolean>("Motion Guard", "MotionGuard", true);
    private final Option<Boolean> autoSwap = new Option<Boolean>("Auto Swap", "AutoSwap", true);
    private final Option<Boolean> fullBlocks = new Option<Boolean>("Full Blocks", "FullBlocks", true);
    private final Option<Boolean> keepY = new Option<Boolean>("Keep Y", "KeepY", false);
    private final Option<Boolean> towerSafety = new Option<Boolean>("Tower Safety", "TowerSafety", true);

    private int scaffoldY;
    private boolean holdingSneak;
    private PlaceTarget lastTarget;
    private int targetTicks;
    private boolean hasSmoothedRotation;
    private float smoothedYaw;
    private float smoothedPitch;

    public Scaffold() {
        super("Scaffold", Keyboard.KEY_NONE, ModuleType.World, "Safe bridge assist that still requires manual right click");
        this.addValues(aimMode, aimSpeed, aimFov, aimLockTicks, switchMargin, prediction, edgeDistance, rightClickOnly,
                smartAim, assistAim, aimRightOnly, syncHead, edgeSneak, motionGuard, autoSwap, fullBlocks, keepY,
                towerSafety);
        Chinese = "安全搭路";
    }

    @Override
    public void enable() {
        if (isInGame()) {
            scaffoldY = MathHelper.floor_double(mc.thePlayer.posY - 1.0D);
        }
        lastTarget = null;
        targetTicks = 0;
        hasSmoothedRotation = false;
        releaseSneak();
    }

    @Override
    public void disable() {
        lastTarget = null;
        targetTicks = 0;
        hasSmoothedRotation = false;
        releaseSneak();
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!isInGame() || mc.currentScreen != null) {
            resetAimState();
            releaseSneak();
            return;
        }

        boolean useHeld = mc.gameSettings.keyBindUseItem.isKeyDown();
        if (Boolean.TRUE.equals(rightClickOnly.getValue()) && !useHeld) {
            resetAimState();
            releaseSneak();
            return;
        }

        if (!isHoldingPlaceableBlock()) {
            if (!Boolean.TRUE.equals(autoSwap.getValue()) || !selectBestBlock()) {
                resetAimState();
                releaseSneak();
                return;
            }
        }

        PlaceTarget target = findPlaceTarget();
        boolean unsafeEdge = isNearUnsafeEdge();
        boolean shouldSneak = Boolean.TRUE.equals(edgeSneak.getValue()) && unsafeEdge && mc.thePlayer.onGround;
        setSneak(shouldSneak);

        if (Boolean.TRUE.equals(motionGuard.getValue()) && unsafeEdge) {
            mc.thePlayer.motionX *= 0.32D;
            mc.thePlayer.motionZ *= 0.32D;
        }

        if (Boolean.TRUE.equals(towerSafety.getValue()) && useHeld && mc.gameSettings.keyBindJump.isKeyDown()) {
            mc.thePlayer.motionX *= 0.55D;
            mc.thePlayer.motionZ *= 0.55D;
        }

        if (target != null && shouldAssistAim(useHeld)) {
            aimAt(target);
        } else if (target == null) {
            resetAimState();
        }
    }

    private void resetAimState() {
        lastTarget = null;
        targetTicks = 0;
        hasSmoothedRotation = false;
    }

    private boolean shouldAssistAim(boolean useHeld) {
        if (!Boolean.TRUE.equals(assistAim.getValue()) || aimMode.getValue() == AimMode.OFF) {
            return false;
        }
        return !Boolean.TRUE.equals(aimRightOnly.getValue()) || useHeld;
    }

    private boolean isHoldingPlaceableBlock() {
        return isPlaceableStack(mc.thePlayer.getHeldItem());
    }

    private boolean selectBestBlock() {
        int bestSlot = -1;
        int bestCount = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.mainInventory[i];
            if (!isPlaceableStack(stack)) {
                continue;
            }
            if (stack.stackSize > bestCount) {
                bestCount = stack.stackSize;
                bestSlot = i;
            }
        }
        if (bestSlot == -1) {
            return false;
        }
        mc.thePlayer.inventory.currentItem = bestSlot;
        return true;
    }

    private boolean isPlaceableStack(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0 || !(stack.getItem() instanceof ItemBlock)) {
            return false;
        }
        Block block = ((ItemBlock) stack.getItem()).getBlock();
        if (block == null || block == Blocks.air || block instanceof BlockAir || block instanceof BlockLiquid) {
            return false;
        }
        if (block instanceof BlockFalling) {
            return false;
        }
        return !Boolean.TRUE.equals(fullBlocks.getValue()) || block.isFullBlock();
    }

    private PlaceTarget findPlaceTarget() {
        List<BlockPos> candidates = getCandidatePositions();
        if (!Boolean.TRUE.equals(smartAim.getValue())) {
            for (BlockPos candidate : candidates) {
                if (!isReplaceable(candidate)) {
                    continue;
                }
                PlaceTarget target = findFirstSupport(candidate);
                if (target != null) {
                    rememberTarget(target);
                    return target;
                }
            }
            rememberTarget(null);
            return null;
        }

        Vec3 predicted = getPredictedPlayerPosition();
        PlaceTarget best = null;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < candidates.size(); i++) {
            BlockPos candidate = candidates.get(i);
            if (!isReplaceable(candidate)) {
                continue;
            }
            PlaceTarget target = findBestSupport(candidate, predicted, i);
            if (target != null) {
                if (target.score < bestScore) {
                    bestScore = target.score;
                    best = target;
                }
            }
        }

        PlaceTarget locked = refreshLockedTarget(predicted);
        if (locked != null && shouldKeepLockedTarget(locked, best)) {
            best = locked;
        }
        rememberTarget(best);
        return best;
    }

    private List<BlockPos> getCandidatePositions() {
        ArrayList<BlockPos> positions = new ArrayList<BlockPos>();
        int y = Boolean.TRUE.equals(keepY.getValue()) ? scaffoldY : MathHelper.floor_double(mc.thePlayer.posY - 1.0D);
        double motionScale = Math.max(0.35D, edgeDistance.getValue() * 2.4D + prediction.getValue() * 0.25D);
        addUnique(positions, new BlockPos(mc.thePlayer.posX, y, mc.thePlayer.posZ));
        addUnique(positions, new BlockPos(mc.thePlayer.posX + mc.thePlayer.motionX * motionScale, y,
                mc.thePlayer.posZ + mc.thePlayer.motionZ * motionScale));
        addUnique(positions, new BlockPos(mc.thePlayer.posX + mc.thePlayer.motionX * (motionScale + 0.45D), y,
                mc.thePlayer.posZ + mc.thePlayer.motionZ * (motionScale + 0.45D)));

        Vec3 move = getMovementDirection();
        if (move != null) {
            addUnique(positions, new BlockPos(mc.thePlayer.posX + move.xCoord * 0.45D, y,
                    mc.thePlayer.posZ + move.zCoord * 0.45D));
            addUnique(positions, new BlockPos(mc.thePlayer.posX + move.xCoord * 0.85D, y,
                    mc.thePlayer.posZ + move.zCoord * 0.85D));
        }

        BlockPos center = new BlockPos(mc.thePlayer.posX, y, mc.thePlayer.posZ);
        for (EnumFacing facing : EnumFacing.HORIZONTALS) {
            addUnique(positions, center.offset(facing));
        }
        return positions;
    }

    private void addUnique(List<BlockPos> positions, BlockPos pos) {
        if (!positions.contains(pos)) {
            positions.add(pos);
        }
    }

    private PlaceTarget findFirstSupport(BlockPos target) {
        for (EnumFacing side : getPlaceSides()) {
            BlockPos support = target.offset(side.getOpposite());
            if (isSolidSupport(support)) {
                return new PlaceTarget(target, support, side, getHitVec(support, side, getPredictedPlayerPosition()), 0.0D);
            }
        }
        return null;
    }

    private PlaceTarget findBestSupport(BlockPos target, Vec3 predicted, int priority) {
        PlaceTarget best = null;
        double bestScore = Double.MAX_VALUE;
        for (EnumFacing side : getPlaceSides()) {
            BlockPos support = target.offset(side.getOpposite());
            if (!isSolidSupport(support)) {
                continue;
            }
            Vec3 hitVec = getHitVec(support, side, predicted);
            PlaceTarget placeTarget = new PlaceTarget(target, support, side, hitVec, 0.0D);
            double score = scoreTarget(placeTarget, predicted, priority);
            if (score < bestScore) {
                bestScore = score;
                best = new PlaceTarget(target, support, side, hitVec, score);
            }
        }
        return best;
    }

    private PlaceTarget refreshLockedTarget(Vec3 predicted) {
        if (lastTarget == null || !isReplaceable(lastTarget.target) || !isSolidSupport(lastTarget.support)) {
            return null;
        }
        Vec3 hitVec = getHitVec(lastTarget.support, lastTarget.side, predicted);
        PlaceTarget refreshed = new PlaceTarget(lastTarget.target, lastTarget.support, lastTarget.side, hitVec, 0.0D);
        double score = scoreTarget(refreshed, predicted, 0);
        return new PlaceTarget(lastTarget.target, lastTarget.support, lastTarget.side, hitVec, score);
    }

    private boolean shouldKeepLockedTarget(PlaceTarget locked, PlaceTarget best) {
        if (best == null) {
            return true;
        }
        if (targetTicks < aimLockTicks.getValue().intValue()) {
            return true;
        }
        return locked.score <= best.score + switchMargin.getValue();
    }

    private EnumFacing[] getPlaceSides() {
        EnumFacing[] sides = new EnumFacing[]{
                EnumFacing.UP,
                EnumFacing.NORTH,
                EnumFacing.SOUTH,
                EnumFacing.WEST,
                EnumFacing.EAST
        };
        return sides;
    }

    private Vec3 getPredictedPlayerPosition() {
        double factor = prediction.getValue();
        return new Vec3(mc.thePlayer.posX + mc.thePlayer.motionX * factor,
                mc.thePlayer.posY,
                mc.thePlayer.posZ + mc.thePlayer.motionZ * factor);
    }

    private Vec3 getMovementDirection() {
        double x = mc.thePlayer.posX - mc.thePlayer.prevPosX;
        double z = mc.thePlayer.posZ - mc.thePlayer.prevPosZ;
        if (x * x + z * z < 0.0009D) {
            x = mc.thePlayer.motionX;
            z = mc.thePlayer.motionZ;
        }
        double length = Math.sqrt(x * x + z * z);
        if (length < 0.03D) {
            return null;
        }
        return new Vec3(x / length, 0.0D, z / length);
    }

    private Vec3 getHitVec(BlockPos support, EnumFacing side, Vec3 preferred) {
        double x = support.getX() + 0.5D;
        double y = support.getY() + 0.5D;
        double z = support.getZ() + 0.5D;
        double minX = support.getX() + 0.22D;
        double maxX = support.getX() + 0.78D;
        double minZ = support.getZ() + 0.22D;
        double maxZ = support.getZ() + 0.78D;

        if (side == EnumFacing.UP) {
            x = clamp(preferred.xCoord, minX, maxX);
            y = support.getY() + 1.0D;
            z = clamp(preferred.zCoord, minZ, maxZ);
        } else if (side == EnumFacing.NORTH) {
            x = clamp(preferred.xCoord, minX, maxX);
            y = support.getY() + 0.58D;
            z = support.getZ();
        } else if (side == EnumFacing.SOUTH) {
            x = clamp(preferred.xCoord, minX, maxX);
            y = support.getY() + 0.58D;
            z = support.getZ() + 1.0D;
        } else if (side == EnumFacing.WEST) {
            x = support.getX();
            y = support.getY() + 0.58D;
            z = clamp(preferred.zCoord, minZ, maxZ);
        } else if (side == EnumFacing.EAST) {
            x = support.getX() + 1.0D;
            y = support.getY() + 0.58D;
            z = clamp(preferred.zCoord, minZ, maxZ);
        }
        return new Vec3(x, y, z);
    }

    private double scoreTarget(PlaceTarget target, Vec3 predicted, int priority) {
        float[] rotations = rotationsTo(target);
        float yawDiff = Math.abs(MathHelper.wrapAngleTo180_float(rotations[0] - mc.thePlayer.rotationYaw));
        float pitchDiff = Math.abs(MathHelper.wrapAngleTo180_float(rotations[1] - mc.thePlayer.rotationPitch));
        double angleScore = yawDiff + pitchDiff * 0.62D;
        double fovPenalty = angleScore > aimFov.getValue() ? (angleScore - aimFov.getValue()) * 3.0D : 0.0D;
        double distanceScore = horizontalDistance(predicted, target.target) * 11.0D;
        double sidePenalty = target.side == EnumFacing.UP ? 0.0D : 8.5D;
        double stabilityBonus = isSameTarget(lastTarget, target) ? -18.0D : 0.0D;
        double crosshairBonus = isCrosshairNear(target) ? -10.0D : 0.0D;
        return priority * 2.2D + angleScore * 0.78D + fovPenalty + distanceScore + sidePenalty + stabilityBonus + crosshairBonus;
    }

    private double horizontalDistance(Vec3 predicted, BlockPos target) {
        double x = predicted.xCoord - (target.getX() + 0.5D);
        double z = predicted.zCoord - (target.getZ() + 0.5D);
        return Math.sqrt(x * x + z * z);
    }

    private boolean isCrosshairNear(PlaceTarget target) {
        if (mc.objectMouseOver == null || mc.objectMouseOver.getBlockPos() == null) {
            return false;
        }
        BlockPos pos = mc.objectMouseOver.getBlockPos();
        return pos.equals(target.support) || pos.equals(target.target);
    }

    private boolean isSameTarget(PlaceTarget first, PlaceTarget second) {
        return first != null
                && second != null
                && first.support.equals(second.support)
                && first.side == second.side;
    }

    private void rememberTarget(PlaceTarget target) {
        if (target == null) {
            lastTarget = null;
            targetTicks = 0;
            return;
        }
        if (isSameTarget(lastTarget, target)) {
            targetTicks++;
        } else {
            targetTicks = 0;
        }
        lastTarget = target;
    }

    private boolean isNearUnsafeEdge() {
        if (!mc.thePlayer.onGround || mc.thePlayer.capabilities.isFlying) {
            return false;
        }
        AxisAlignedBB box = mc.thePlayer.getEntityBoundingBox();
        double guard = edgeDistance.getValue();
        double xOffset = clamp(mc.thePlayer.motionX * 2.0D, -guard, guard);
        double zOffset = clamp(mc.thePlayer.motionZ * 2.0D, -guard, guard);
        double y = box.minY - 0.05D;

        return !hasSupportAt(box.minX + xOffset, y, box.minZ + zOffset)
                || !hasSupportAt(box.minX + xOffset, y, box.maxZ + zOffset)
                || !hasSupportAt(box.maxX + xOffset, y, box.minZ + zOffset)
                || !hasSupportAt(box.maxX + xOffset, y, box.maxZ + zOffset);
    }

    private boolean hasSupportAt(double x, double y, double z) {
        return isSolidSupport(new BlockPos(x, y, z));
    }

    private boolean isReplaceable(BlockPos pos) {
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        return block == null || block.isReplaceable(mc.theWorld, pos);
    }

    private boolean isSolidSupport(BlockPos pos) {
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        return block != null
                && !(block instanceof BlockAir)
                && !(block instanceof BlockLiquid)
                && !block.isReplaceable(mc.theWorld, pos)
                && block.getCollisionBoundingBox(mc.theWorld, pos, mc.theWorld.getBlockState(pos)) != null;
    }

    private void aimAt(PlaceTarget target) {
        float[] rotations = rotationsTo(target);
        if (aimMode.getValue() == AimMode.LOCK) {
            mc.thePlayer.rotationYaw = rotations[0];
            mc.thePlayer.rotationPitch = rotations[1];
            smoothedYaw = rotations[0];
            smoothedPitch = rotations[1];
            hasSmoothedRotation = true;
            syncPlayerHead(rotations[0]);
            return;
        }
        if (!hasSmoothedRotation) {
            smoothedYaw = mc.thePlayer.rotationYaw;
            smoothedPitch = mc.thePlayer.rotationPitch;
            hasSmoothedRotation = true;
        }
        float yawDiff = Math.abs(MathHelper.wrapAngleTo180_float(rotations[0] - mc.thePlayer.rotationYaw));
        float pitchDiff = Math.abs(MathHelper.wrapAngleTo180_float(rotations[1] - mc.thePlayer.rotationPitch));
        float speed = getAdaptiveAimSpeed(yawDiff, pitchDiff);
        float targetBlend = getTargetBlendSpeed(speed, yawDiff, pitchDiff);
        smoothedYaw = updateRotation(smoothedYaw, rotations[0], targetBlend);
        smoothedPitch = updateRotation(smoothedPitch, rotations[1], Math.max(2.5F, targetBlend * 0.72F));
        mc.thePlayer.rotationYaw = updateRotation(mc.thePlayer.rotationYaw, smoothedYaw, speed);
        mc.thePlayer.rotationPitch = updateRotation(mc.thePlayer.rotationPitch, smoothedPitch, Math.max(3.0F, speed * 0.78F));
        mc.thePlayer.rotationPitch = MathHelper.clamp_float(mc.thePlayer.rotationPitch, -90.0F, 90.0F);
        syncPlayerHead(mc.thePlayer.rotationYaw);
    }

    private float getAdaptiveAimSpeed(float yawDiff, float pitchDiff) {
        float base = aimSpeed.getValue().floatValue();
        float error = MathHelper.sqrt_float(yawDiff * yawDiff + pitchDiff * pitchDiff);
        float scale = 0.42F + Math.min(1.0F, error / 70.0F) * 0.85F;
        if (targetTicks > 2 && error < 35.0F) {
            scale *= 0.78F;
        }
        if (isNearUnsafeEdge()) {
            scale *= 1.12F;
        }
        return MathHelper.clamp_float(base * scale, 3.5F, 90.0F);
    }

    private float getTargetBlendSpeed(float speed, float yawDiff, float pitchDiff) {
        float error = MathHelper.sqrt_float(yawDiff * yawDiff + pitchDiff * pitchDiff);
        float blend = Math.max(4.0F, speed * 0.58F);
        if (error > 65.0F) {
            blend = Math.max(blend, speed * 0.85F);
        }
        if (targetTicks > 2 && error < 28.0F) {
            blend *= 0.55F;
        }
        return MathHelper.clamp_float(blend, 2.5F, 42.0F);
    }

    private void syncPlayerHead(float yaw) {
        if (!Boolean.TRUE.equals(syncHead.getValue())) {
            return;
        }
        RotationUtil.syncHead(mc, yaw);
    }

    private float[] rotationsTo(PlaceTarget target) {
        Vec3 hitVec = target.hitVec;
        double x = hitVec.xCoord - mc.thePlayer.posX;
        double y = hitVec.yCoord - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        double z = hitVec.zCoord - mc.thePlayer.posZ;
        double distance = MathHelper.sqrt_double(x * x + z * z);
        float yaw = (float) (Math.atan2(z, x) * 180.0D / Math.PI) - 90.0F;
        float pitch = (float) (-(Math.atan2(y, distance) * 180.0D / Math.PI));
        float minPitch = target.side == EnumFacing.UP ? 57.0F : 42.0F;
        return new float[]{yaw, MathHelper.clamp_float(pitch, minPitch, 88.0F)};
    }

    private float updateRotation(float current, float target, float speed) {
        float difference = MathHelper.wrapAngleTo180_float(target - current);
        return RotationUtil.limitAngleChange(current, target, speed);
    }

    private void setSneak(boolean sneak) {
        if (sneak) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), true);
            holdingSneak = true;
        } else {
            releaseSneak();
        }
    }

    private void releaseSneak() {
        if (!holdingSneak) {
            return;
        }
        int key = mc.gameSettings.keyBindSneak.getKeyCode();
        KeyBinding.setKeyBindState(key, key > 0 && Keyboard.isKeyDown(key));
        holdingSneak = false;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class PlaceTarget {
        final BlockPos target;
        final BlockPos pos;
        final BlockPos support;
        final EnumFacing side;
        final Vec3 hitVec;
        final double score;

        PlaceTarget(BlockPos target, BlockPos support, EnumFacing side, Vec3 hitVec, double score) {
            this.target = target;
            this.pos = support;
            this.support = support;
            this.side = side;
            this.hitVec = hitVec;
            this.score = score;
        }
    }
}
