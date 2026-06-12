package gq.vapulite.module.world;

import gq.vapulite.module.ModuleType;
import gq.vapulite.module.Module;
import gq.vapulite.util.minecraft.RotationUtil;
import gq.vapulite.value.Mode;
import gq.vapulite.value.Option;
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
    public enum ScaffoldMode {
        SAFE,
        SMART,
        FAST
    }

    private final Mode<ScaffoldMode> mode =
            new Mode<ScaffoldMode>("Mode", "Mode", ScaffoldMode.values(), ScaffoldMode.SMART);
    private final Option<Boolean> rightClickOnly = new Option<Boolean>("Right Click Only", "RightClickOnly", true);
    private final Option<Boolean> autoSwap = new Option<Boolean>("Auto Swap", "AutoSwap", true);
    private final Option<Boolean> keepY = new Option<Boolean>("Keep Y", "KeepY", false);

    private int scaffoldY;
    private boolean holdingSneak;
    private PlaceTarget lastTarget;
    private int targetTicks;
    private boolean hasSmoothedRotation;
    private float smoothedYaw;
    private float smoothedPitch;
    private boolean towering;

    public Scaffold() {
        super("Scaffold", Keyboard.KEY_NONE, ModuleType.World, "Safe bridge assist that still requires manual right click");
        this.addValues(mode, rightClickOnly, autoSwap, keepY);
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
        towering = false;
        releaseSneak();
    }

    @Override
    public void disable() {
        lastTarget = null;
        targetTicks = 0;
        hasSmoothedRotation = false;
        towering = false;
        releaseSneak();
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!isInGame() || mc.currentScreen != null) {
            resetAimState();
            towering = false;
            releaseSneak();
            return;
        }

        boolean useHeld = mc.gameSettings.keyBindUseItem.isKeyDown();
        towering = isTowering(useHeld);
        if (Boolean.TRUE.equals(rightClickOnly.getValue()) && !useHeld) {
            resetAimState();
            towering = false;
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
        setSneak(shouldSneak(unsafeEdge));

        if (unsafeEdge) {
            double scale = getEdgeMotionScale();
            mc.thePlayer.motionX *= scale;
            mc.thePlayer.motionZ *= scale;
        }

        if (useHeld && mc.gameSettings.keyBindJump.isKeyDown()) {
            double scale = getTowerMotionScale();
            mc.thePlayer.motionX *= scale;
            mc.thePlayer.motionZ *= scale;
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

    private boolean isTowering(boolean useHeld) {
        return useHeld && mc.gameSettings.keyBindJump.isKeyDown() && !mc.gameSettings.keyBindSneak.isKeyDown();
    }

    private boolean shouldAssistAim(boolean useHeld) {
        return !Boolean.TRUE.equals(rightClickOnly.getValue()) || useHeld;
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
        return block.isFullBlock();
    }

    private PlaceTarget findPlaceTarget() {
        List<BlockPos> candidates = getCandidatePositions();

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
        if (towering) {
            int towerY = MathHelper.floor_double(mc.thePlayer.posY - 0.10D);
            addCandidateLayer(positions, towerY, true);
        }
        addCandidateLayer(positions, y, true);
        return positions;
    }

    private void addCandidateLayer(List<BlockPos> positions, int y, boolean includeMotion) {
        double motionScale = getMotionPredictionScale();
        addUnique(positions, new BlockPos(mc.thePlayer.posX, y, mc.thePlayer.posZ));
        if (includeMotion) {
            addUnique(positions, new BlockPos(mc.thePlayer.posX + mc.thePlayer.motionX * motionScale, y,
                    mc.thePlayer.posZ + mc.thePlayer.motionZ * motionScale));
            addUnique(positions, new BlockPos(mc.thePlayer.posX + mc.thePlayer.motionX * (motionScale + 0.45D), y,
                    mc.thePlayer.posZ + mc.thePlayer.motionZ * (motionScale + 0.45D)));
        }

        Vec3 move = getMovementDirection();
        if (includeMotion && move != null) {
            addUnique(positions, new BlockPos(mc.thePlayer.posX + move.xCoord * 0.45D, y,
                    mc.thePlayer.posZ + move.zCoord * 0.45D));
            addUnique(positions, new BlockPos(mc.thePlayer.posX + move.xCoord * 0.85D, y,
                    mc.thePlayer.posZ + move.zCoord * 0.85D));
        }

        BlockPos center = new BlockPos(mc.thePlayer.posX, y, mc.thePlayer.posZ);
        for (EnumFacing facing : EnumFacing.HORIZONTALS) {
            addUnique(positions, center.offset(facing));
        }
    }

    private void addUnique(List<BlockPos> positions, BlockPos pos) {
        if (!positions.contains(pos)) {
            positions.add(pos);
        }
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
        if (targetTicks < getAimLockTicks()) {
            return true;
        }
        return locked.score <= best.score + getSwitchMargin();
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
        double factor = getPredictionFactor();
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
        double fovPenalty = angleScore > getAimFov() ? (angleScore - getAimFov()) * 3.0D : 0.0D;
        double distanceScore = horizontalDistance(predicted, target.target) * 11.0D;
        double sidePenalty = target.side == EnumFacing.UP ? 0.0D : (towering ? 16.0D : 8.5D);
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

    private ScaffoldMode currentMode() {
        return mode.getValue() == null ? ScaffoldMode.SMART : mode.getValue();
    }

    private double getHorizontalSpeed() {
        if (mc.thePlayer == null) {
            return 0.0D;
        }
        return Math.sqrt(mc.thePlayer.motionX * mc.thePlayer.motionX + mc.thePlayer.motionZ * mc.thePlayer.motionZ);
    }

    private double getPredictionFactor() {
        double speed = getHorizontalSpeed();
        double prediction = towering ? 0.28D + speed * 0.85D : 0.52D + speed * 1.9D;
        if (currentMode() == ScaffoldMode.SAFE) {
            prediction *= 0.82D;
        } else if (currentMode() == ScaffoldMode.FAST) {
            prediction *= 1.25D;
        }
        if (!mc.thePlayer.onGround) {
            prediction += Math.min(0.45D, Math.abs(mc.thePlayer.motionY) * 0.75D);
        }
        return clamp(prediction, 0.35D, 1.65D);
    }

    private double getMotionPredictionScale() {
        double scale = getEdgeGuardDistance() * 2.4D + getPredictionFactor() * 0.25D;
        if (currentMode() == ScaffoldMode.FAST) {
            scale += 0.22D;
        }
        return clamp(scale, 0.35D, 1.25D);
    }

    private double getEdgeGuardDistance() {
        if (currentMode() == ScaffoldMode.SAFE) {
            return 0.30D;
        }
        if (currentMode() == ScaffoldMode.FAST) {
            return 0.18D;
        }
        if (towering) {
            return 0.20D;
        }
        return 0.23D;
    }

    private double getEdgeMotionScale() {
        if (currentMode() == ScaffoldMode.SAFE) {
            return 0.28D;
        }
        if (currentMode() == ScaffoldMode.FAST) {
            return 0.52D;
        }
        return 0.36D;
    }

    private double getTowerMotionScale() {
        if (currentMode() == ScaffoldMode.SAFE) {
            return 0.48D;
        }
        if (currentMode() == ScaffoldMode.FAST) {
            return 0.68D;
        }
        return 0.56D;
    }

    private boolean shouldSneak(boolean unsafeEdge) {
        if (!unsafeEdge || !mc.thePlayer.onGround) {
            return false;
        }
        return currentMode() != ScaffoldMode.FAST || getHorizontalSpeed() > 0.08D;
    }

    private double getAimFov() {
        double fov = 132.0D + getHorizontalSpeed() * 85.0D;
        if (currentMode() == ScaffoldMode.SAFE) {
            fov = 105.0D + getHorizontalSpeed() * 55.0D;
        } else if (currentMode() == ScaffoldMode.FAST) {
            fov = 180.0D;
        }
        return clamp(fov, 92.0D, 180.0D);
    }

    private int getAimLockTicks() {
        if (currentMode() == ScaffoldMode.SAFE) {
            return 6;
        }
        if (currentMode() == ScaffoldMode.FAST) {
            return 2;
        }
        return 4;
    }

    private double getSwitchMargin() {
        if (currentMode() == ScaffoldMode.SAFE) {
            return 22.0D;
        }
        if (currentMode() == ScaffoldMode.FAST) {
            return 30.0D;
        }
        return 18.0D;
    }

    private float getBaseAimSpeed() {
        float speed = (float) (24.0D + getHorizontalSpeed() * 85.0D);
        if (currentMode() == ScaffoldMode.SAFE) {
            speed *= 0.72F;
        } else if (currentMode() == ScaffoldMode.FAST) {
            speed = 90.0F;
        }
        if (towering) {
            speed = Math.max(speed, 56.0F);
        }
        if (!mc.thePlayer.onGround) {
            speed *= 1.12F;
        }
        return MathHelper.clamp_float(speed, 8.0F, 90.0F);
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
        double guard = getEdgeGuardDistance();
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
        if (currentMode() == ScaffoldMode.FAST) {
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
        smoothedPitch = updateRotation(smoothedPitch, rotations[1], getPitchBlendSpeed(targetBlend));
        mc.thePlayer.rotationYaw = updateRotation(mc.thePlayer.rotationYaw, smoothedYaw, speed);
        mc.thePlayer.rotationPitch = updateRotation(mc.thePlayer.rotationPitch, smoothedPitch, getPitchAimSpeed(speed));
        mc.thePlayer.rotationPitch = MathHelper.clamp_float(mc.thePlayer.rotationPitch, -90.0F, 90.0F);
        syncPlayerHead(mc.thePlayer.rotationYaw);
    }

    private float getAdaptiveAimSpeed(float yawDiff, float pitchDiff) {
        float base = getBaseAimSpeed();
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

    private float getPitchBlendSpeed(float targetBlend) {
        if (towering) {
            return MathHelper.clamp_float(Math.max(8.0F, targetBlend * 1.05F), 6.0F, 58.0F);
        }
        return Math.max(2.5F, targetBlend * 0.72F);
    }

    private float getPitchAimSpeed(float speed) {
        if (towering) {
            return MathHelper.clamp_float(Math.max(16.0F, speed * 1.08F), 10.0F, 90.0F);
        }
        return Math.max(3.0F, speed * 0.78F);
    }

    private void syncPlayerHead(float yaw) {
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
        float maxPitch = 88.0F;
        if (towering) {
            minPitch = target.side == EnumFacing.UP ? 76.0F : 58.0F;
            maxPitch = 89.2F;
        }
        return new float[]{yaw, MathHelper.clamp_float(pitch, minPitch, maxPitch)};
    }

    private float updateRotation(float current, float target, float speed) {
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
