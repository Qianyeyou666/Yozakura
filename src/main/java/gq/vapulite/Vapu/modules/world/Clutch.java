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
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
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

public class Clutch extends Module {
    public enum ClutchMode {
        LEGIT,
        SMART,
        PANIC
    }

    private final Mode<ClutchMode> mode = new Mode<ClutchMode>("Mode", "Mode", ClutchMode.values(), ClutchMode.SMART);
    private final Numbers<Double> fallDistance = new Numbers<Double>("Fall Distance", "FallDistance", 2.2, 0.2, 8.0, 0.1);
    private final Option<Boolean> autoPlace = new Option<Boolean>("Auto Place", "AutoPlace", true);
    private final Option<Boolean> autoSwap = new Option<Boolean>("Auto Swap", "AutoSwap", true);
    private final Option<Boolean> restoreSlot = new Option<Boolean>("Restore Slot", "RestoreSlot", false);

    private final RotationUtil.State rotationState = new RotationUtil.State();
    private PlaceTarget lastTarget;
    private int targetTicks;
    private long lastPlaceAt;

    public Clutch() {
        super("Clutch", Keyboard.KEY_NONE, ModuleType.World, "Place a block under you while falling");
        this.addValues(mode, fallDistance, autoPlace, autoSwap, restoreSlot);
        Chinese = "落地救方块";
    }

    @Override
    public void enable() {
        rotationState.reset();
        lastTarget = null;
        targetTicks = 0;
        lastPlaceAt = 0L;
    }

    @Override
    public void disable() {
        rotationState.reset();
        lastTarget = null;
        targetTicks = 0;
        lastPlaceAt = 0L;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!isInGame() || mc.currentScreen != null || mc.thePlayer.capabilities.isFlying
                || mc.thePlayer.capabilities.isCreativeMode) {
            rotationState.reset();
            return;
        }
        if (!shouldClutch()) {
            rotationState.reset();
            lastTarget = null;
            targetTicks = 0;
            return;
        }
        if (System.currentTimeMillis() - lastPlaceAt < getPlaceDelay()) {
            return;
        }

        int oldSlot = mc.thePlayer.inventory.currentItem;
        if (!isHoldingPlaceableBlock()) {
            if (!Boolean.TRUE.equals(autoSwap.getValue()) || !selectBestBlock()) {
                return;
            }
            syncHeldItem();
        }

        PlaceTarget target = findPlaceTarget();
        if (target == null) {
            restoreSlot(oldSlot);
            return;
        }

        aimAt(target);
        double scale = getMotionScale();
        if (scale < 1.0D) {
            mc.thePlayer.motionX *= scale;
            mc.thePlayer.motionZ *= scale;
        }
        if (shouldWaitForAim() && !isAimReady(target) && targetTicks < getMaxAimWaitTicks()) {
            if (Boolean.TRUE.equals(autoPlace.getValue()) && Boolean.TRUE.equals(restoreSlot.getValue())) {
                restoreSlot(oldSlot);
            }
            return;
        }
        if (!Boolean.TRUE.equals(autoPlace.getValue())) {
            return;
        }
        if (place(target)) {
            lastPlaceAt = System.currentTimeMillis();
            lastTarget = null;
            targetTicks = 0;
        }
        if (Boolean.TRUE.equals(restoreSlot.getValue())) {
            restoreSlot(oldSlot);
        }
    }

    private boolean shouldClutch() {
        if (mc.thePlayer.onGround || mc.thePlayer.isInWater() || mc.thePlayer.isInLava()) {
            return false;
        }
        if (isUpwardBridgeAttempt()) {
            return isFeetUnsafe();
        }
        if (mc.thePlayer.motionY > -0.03D) {
            return false;
        }
        return mc.thePlayer.fallDistance >= getTriggerFallDistance() || isFeetUnsafe();
    }

    private boolean isUpwardBridgeAttempt() {
        if (mc.thePlayer == null || mc.gameSettings == null) {
            return false;
        }
        return mc.thePlayer.motionY > 0.0D
                && mc.gameSettings.keyBindJump.isKeyDown()
                && !mc.gameSettings.keyBindSneak.isKeyDown()
                && (mc.gameSettings.keyBindUseItem.isKeyDown()
                || Boolean.TRUE.equals(autoPlace.getValue()) && hasPlaceableBlockAvailable());
    }

    private boolean isFeetUnsafe() {
        AxisAlignedBB box = mc.thePlayer.getEntityBoundingBox();
        double prediction = getPredictionFactor();
        double predictedX = mc.thePlayer.posX + mc.thePlayer.motionX * prediction;
        double predictedZ = mc.thePlayer.posZ + mc.thePlayer.motionZ * prediction;
        double y = box.minY - 0.18D + mc.thePlayer.motionY * Math.min(1.0D, prediction);
        double halfX = Math.max(0.18D, (box.maxX - box.minX) * 0.38D);
        double halfZ = Math.max(0.18D, (box.maxZ - box.minZ) * 0.38D);

        return !hasSupportAt(predictedX - halfX, y, predictedZ - halfZ)
                || !hasSupportAt(predictedX - halfX, y, predictedZ + halfZ)
                || !hasSupportAt(predictedX + halfX, y, predictedZ - halfZ)
                || !hasSupportAt(predictedX + halfX, y, predictedZ + halfZ);
    }

    private boolean isHoldingPlaceableBlock() {
        return isPlaceableStack(mc.thePlayer.getHeldItem());
    }

    private boolean hasPlaceableBlockAvailable() {
        if (isHoldingPlaceableBlock()) {
            return true;
        }
        if (!Boolean.TRUE.equals(autoSwap.getValue())) {
            return false;
        }
        for (int i = 0; i < 9; i++) {
            if (isPlaceableStack(mc.thePlayer.inventory.mainInventory[i])) {
                return true;
            }
        }
        return false;
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
        syncHeldItem();
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
        Vec3 predicted = getPredictedFeet();
        List<BlockPos> candidates = getCandidatePositions(predicted);
        PlaceTarget best = null;
        double bestScore = Double.MAX_VALUE;

        for (int i = 0; i < candidates.size(); i++) {
            BlockPos target = candidates.get(i);
            if (!isReplaceable(target)) {
                continue;
            }
            PlaceTarget placeTarget = findSupport(target, predicted);
            if (placeTarget == null) {
                continue;
            }
            double score = score(placeTarget, predicted, i);
            if (score < bestScore) {
                bestScore = score;
                best = new PlaceTarget(target, placeTarget.support, placeTarget.side, placeTarget.hitVec, score);
            }
        }

        PlaceTarget locked = refreshLockedTarget(predicted);
        if (locked != null && shouldKeepLockedTarget(locked, best)) {
            best = locked;
        }
        rememberTarget(best);
        return best;
    }

    private Vec3 getPredictedFeet() {
        double factor = getPredictionFactor();
        if (isUpwardBridgeAttempt()) {
            double vertical = clamp(mc.thePlayer.motionY * 0.65D, 0.0D, 0.75D);
            return new Vec3(mc.thePlayer.posX + mc.thePlayer.motionX * factor,
                    mc.thePlayer.getEntityBoundingBox().minY + vertical,
                    mc.thePlayer.posZ + mc.thePlayer.motionZ * factor);
        }
        return new Vec3(mc.thePlayer.posX + mc.thePlayer.motionX * factor,
                mc.thePlayer.getEntityBoundingBox().minY + mc.thePlayer.motionY * Math.min(1.0D, factor),
                mc.thePlayer.posZ + mc.thePlayer.motionZ * factor);
    }

    private List<BlockPos> getCandidatePositions(Vec3 predicted) {
        ArrayList<BlockPos> positions = new ArrayList<BlockPos>();
        int radius = getSearchRadius();
        int vertical = getVerticalSearch();
        int baseY = MathHelper.floor_double(predicted.yCoord - 1.0D);
        if (isUpwardBridgeAttempt()) {
            addUnique(positions, new BlockPos(mc.thePlayer.posX, MathHelper.floor_double(mc.thePlayer.posY - 0.10D), mc.thePlayer.posZ));
        }
        for (int dy = 0; dy <= vertical; dy++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    addUnique(positions, new BlockPos(predicted.xCoord + x, baseY - dy, predicted.zCoord + z));
                }
            }
        }
        Vec3 move = getMovementDirection();
        if (move != null) {
            addUnique(positions, new BlockPos(predicted.xCoord + move.xCoord * 0.45D, baseY, predicted.zCoord + move.zCoord * 0.45D));
            addUnique(positions, new BlockPos(predicted.xCoord + move.xCoord * 0.85D, baseY, predicted.zCoord + move.zCoord * 0.85D));
        }
        addUnique(positions, new BlockPos(mc.thePlayer.posX, MathHelper.floor_double(mc.thePlayer.getEntityBoundingBox().minY - 1.0D), mc.thePlayer.posZ));
        return positions;
    }

    private void addUnique(List<BlockPos> positions, BlockPos pos) {
        if (!positions.contains(pos)) {
            positions.add(pos);
        }
    }

    private PlaceTarget findSupport(BlockPos target, Vec3 predicted) {
        for (EnumFacing side : getPlaceSides()) {
            BlockPos support = target.offset(side.getOpposite());
            if (!isSolidSupport(support)) {
                continue;
            }
            Vec3 hitVec = getHitVec(support, side, predicted);
            double distance = mc.thePlayer.getPositionEyes(1.0f).distanceTo(hitVec);
            if (distance <= getPlaceRange()) {
                return new PlaceTarget(target, support, side, hitVec, distance);
            }
        }
        return null;
    }

    private PlaceTarget refreshLockedTarget(Vec3 predicted) {
        if (lastTarget == null || !isReplaceable(lastTarget.target) || !isSolidSupport(lastTarget.support)) {
            return null;
        }
        Vec3 hitVec = getHitVec(lastTarget.support, lastTarget.side, predicted);
        if (mc.thePlayer.getPositionEyes(1.0f).distanceTo(hitVec) > getPlaceRange()) {
            return null;
        }
        PlaceTarget refreshed = new PlaceTarget(lastTarget.target, lastTarget.support, lastTarget.side, hitVec, 0.0D);
        return new PlaceTarget(refreshed.target, refreshed.support, refreshed.side, hitVec, score(refreshed, predicted, 0));
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
        return new EnumFacing[]{
                EnumFacing.UP,
                EnumFacing.NORTH,
                EnumFacing.SOUTH,
                EnumFacing.WEST,
                EnumFacing.EAST
        };
    }

    private double score(PlaceTarget target, Vec3 predicted, int priority) {
        double x = target.target.getX() + 0.5D - predicted.xCoord;
        double y = target.target.getY() + 0.5D - predicted.yCoord;
        double z = target.target.getZ() + 0.5D - predicted.zCoord;
        float[] rotations = rotationsTo(target);
        float yawDiff = Math.abs(MathHelper.wrapAngleTo180_float(rotations[0] - mc.thePlayer.rotationYaw));
        float pitchDiff = Math.abs(MathHelper.wrapAngleTo180_float(rotations[1] - mc.thePlayer.rotationPitch));
        double angleScore = yawDiff + pitchDiff * 0.65D;
        double fovPenalty = angleScore > getAimFov() ? (angleScore - getAimFov()) * 3.2D : 0.0D;
        double sidePenalty = target.side == EnumFacing.UP ? 0.0D : (isUpwardBridgeAttempt() ? 9.0D : 2.2D);
        double stabilityBonus = isSameTarget(lastTarget, target) ? -18.0D : 0.0D;
        double crosshairBonus = isCrosshairNear(target) ? -9.0D : 0.0D;
        return priority * 1.35D + (x * x + z * z) * 9.0D + Math.abs(y) * 0.5D
                + angleScore * 0.72D + fovPenalty + sidePenalty + target.score * 0.22D
                + stabilityBonus + crosshairBonus;
    }

    private boolean place(PlaceTarget target) {
        ItemStack stack = mc.thePlayer.getHeldItem();
        if (!isPlaceableStack(stack)) {
            return false;
        }
        syncHeldItem();
        boolean placed = mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, stack,
                target.support, target.side, target.hitVec);
        if (placed) {
            mc.thePlayer.swingItem();
        }
        return placed;
    }

    private boolean isAimReady(PlaceTarget target) {
        float[] rotations = rotationsTo(target);
        float yawDiff = Math.abs(MathHelper.wrapAngleTo180_float(rotations[0] - mc.thePlayer.rotationYaw));
        float pitchDiff = Math.abs(MathHelper.wrapAngleTo180_float(rotations[1] - mc.thePlayer.rotationPitch));
        float limit = isUpwardBridgeAttempt() ? 8.0F : Math.max(10.0F, (float) getAimFov() * 0.16F);
        return yawDiff + pitchDiff * 0.65F <= limit;
    }

    private void aimAt(PlaceTarget target) {
        float[] rotations = rotationsTo(target);
        if (currentMode() == ClutchMode.PANIC) {
            mc.thePlayer.rotationYaw = rotations[0];
            mc.thePlayer.rotationPitch = rotations[1];
            RotationUtil.syncHead(mc, rotations[0]);
            rotationState.reset();
            return;
        }
        float speed = getAimSpeed();
        RotationUtil.applyToPlayer(mc, rotations[0], rotations[1], speed,
                getPitchAimSpeed(speed), false, 0.0F,
                rotationState, getAimInertia(), getAimMinStep(), true);
    }

    private float[] rotationsTo(PlaceTarget target) {
        Vec3 hitVec = target.hitVec;
        float[] rotations = RotationUtil.getRotationsTo(mc, hitVec.xCoord, hitVec.yCoord, hitVec.zCoord);
        float minPitch = target.side == EnumFacing.UP ? 60.0F : 38.0F;
        float maxPitch = 89.0F;
        if (isUpwardBridgeAttempt()) {
            minPitch = target.side == EnumFacing.UP ? 76.0F : 58.0F;
            maxPitch = 89.4F;
        }
        return new float[]{rotations[0], MathHelper.clamp_float(rotations[1], minPitch, maxPitch)};
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
            y = clamp(preferred.yCoord, support.getY() + 0.22D, support.getY() + 0.82D);
            z = support.getZ();
        } else if (side == EnumFacing.SOUTH) {
            x = clamp(preferred.xCoord, minX, maxX);
            y = clamp(preferred.yCoord, support.getY() + 0.22D, support.getY() + 0.82D);
            z = support.getZ() + 1.0D;
        } else if (side == EnumFacing.WEST) {
            x = support.getX();
            y = clamp(preferred.yCoord, support.getY() + 0.22D, support.getY() + 0.82D);
            z = clamp(preferred.zCoord, minZ, maxZ);
        } else if (side == EnumFacing.EAST) {
            x = support.getX() + 1.0D;
            y = clamp(preferred.yCoord, support.getY() + 0.22D, support.getY() + 0.82D);
            z = clamp(preferred.zCoord, minZ, maxZ);
        }
        return new Vec3(x, y, z);
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

    private boolean isCrosshairNear(PlaceTarget target) {
        return mc.objectMouseOver != null
                && mc.objectMouseOver.getBlockPos() != null
                && (mc.objectMouseOver.getBlockPos().equals(target.support)
                || mc.objectMouseOver.getBlockPos().equals(target.target));
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

    private boolean isSameTarget(PlaceTarget first, PlaceTarget second) {
        return first != null
                && second != null
                && first.support.equals(second.support)
                && first.side == second.side;
    }

    private ClutchMode currentMode() {
        return mode.getValue() == null ? ClutchMode.SMART : mode.getValue();
    }

    private double getFallSpeed() {
        return mc.thePlayer == null ? 0.0D : Math.max(0.0D, -mc.thePlayer.motionY);
    }

    private double getHorizontalSpeed() {
        if (mc.thePlayer == null) {
            return 0.0D;
        }
        return Math.sqrt(mc.thePlayer.motionX * mc.thePlayer.motionX + mc.thePlayer.motionZ * mc.thePlayer.motionZ);
    }

    private double getTriggerFallDistance() {
        double base = Math.max(0.2D, fallDistance.getValue());
        if (currentMode() == ClutchMode.LEGIT) {
            return base * 1.12D;
        }
        if (currentMode() == ClutchMode.PANIC) {
            return Math.max(0.2D, base * 0.58D);
        }
        return Math.max(0.2D, base * 0.82D);
    }

    private double getPredictionFactor() {
        if (isUpwardBridgeAttempt()) {
            return clamp(0.34D + getHorizontalSpeed() * 0.9D, 0.30D, 1.15D);
        }
        double fall = getFallSpeed();
        double horizontal = getHorizontalSpeed();
        double prediction = 1.25D + fall * 2.65D + horizontal * 1.35D;
        if (currentMode() == ClutchMode.LEGIT) {
            prediction *= 0.78D;
        } else if (currentMode() == ClutchMode.PANIC) {
            prediction *= 1.25D;
        }
        return clamp(prediction, 0.75D, 5.75D);
    }

    private double getPlaceRange() {
        double range = 4.15D + Math.min(0.85D, getFallSpeed() * 0.42D);
        if (currentMode() == ClutchMode.LEGIT) {
            range -= 0.15D;
        } else if (currentMode() == ClutchMode.PANIC) {
            range += 0.25D;
        }
        return clamp(range, 3.25D, 5.35D);
    }

    private float getAimSpeed() {
        double fall = getFallSpeed();
        double horizontal = getHorizontalSpeed();
        float speed = (float) (42.0D + fall * 32.0D + horizontal * 24.0D);
        if (currentMode() == ClutchMode.LEGIT) {
            speed *= 0.64F;
        } else if (currentMode() == ClutchMode.PANIC) {
            speed = 120.0F;
        }
        if (isUpwardBridgeAttempt()) {
            speed = Math.max(speed, currentMode() == ClutchMode.LEGIT ? 44.0F : 68.0F);
        }
        return MathHelper.clamp_float(speed, 18.0F, 120.0F);
    }

    private double getAimFov() {
        if (isUpwardBridgeAttempt()) {
            return currentMode() == ClutchMode.LEGIT ? 145.0D : 180.0D;
        }
        double fov = 145.0D + getFallSpeed() * 24.0D;
        if (currentMode() == ClutchMode.LEGIT) {
            fov = 122.0D + getFallSpeed() * 16.0D;
        } else if (currentMode() == ClutchMode.PANIC) {
            fov = 180.0D;
        }
        return clamp(fov, 90.0D, 180.0D);
    }

    private int getAimLockTicks() {
        if (currentMode() == ClutchMode.LEGIT) {
            return 5;
        }
        if (currentMode() == ClutchMode.PANIC) {
            return 2;
        }
        return 4;
    }

    private double getSwitchMargin() {
        if (currentMode() == ClutchMode.LEGIT) {
            return 10.0D;
        }
        if (currentMode() == ClutchMode.PANIC) {
            return 28.0D;
        }
        return 16.0D;
    }

    private int getSearchRadius() {
        if (isUpwardBridgeAttempt()) {
            return getHorizontalSpeed() > 0.10D ? 2 : 1;
        }
        if (currentMode() == ClutchMode.PANIC || getHorizontalSpeed() > 0.12D) {
            return 2;
        }
        return 1;
    }

    private int getVerticalSearch() {
        if (isUpwardBridgeAttempt()) {
            return 2;
        }
        int vertical = 2 + (int) Math.ceil(getFallSpeed() * 2.4D);
        if (currentMode() == ClutchMode.PANIC) {
            vertical++;
        }
        return Math.max(2, Math.min(5, vertical));
    }

    private long getPlaceDelay() {
        if (isUpwardBridgeAttempt()) {
            if (currentMode() == ClutchMode.LEGIT) {
                return 45L;
            }
            return currentMode() == ClutchMode.PANIC ? 0L : 12L;
        }
        long delay;
        if (currentMode() == ClutchMode.LEGIT) {
            delay = 70L;
        } else if (currentMode() == ClutchMode.PANIC) {
            delay = 0L;
        } else {
            delay = 35L;
        }
        return Math.max(0L, delay - Math.round(getFallSpeed() * 35.0D));
    }

    private double getMotionScale() {
        if (isUpwardBridgeAttempt()) {
            if (currentMode() == ClutchMode.LEGIT) {
                return 0.94D;
            }
            return currentMode() == ClutchMode.PANIC ? 0.72D : 0.82D;
        }
        if (currentMode() == ClutchMode.LEGIT) {
            return 1.0D;
        }
        if (currentMode() == ClutchMode.PANIC) {
            return 0.66D;
        }
        return clamp(0.96D - getFallSpeed() * 0.12D, 0.78D, 1.0D);
    }

    private boolean shouldWaitForAim() {
        return currentMode() == ClutchMode.LEGIT && !isEmergencyClutch();
    }

    private boolean isEmergencyClutch() {
        if (isUpwardBridgeAttempt()) {
            return false;
        }
        return getFallSpeed() > 0.55D
                || mc.thePlayer.fallDistance > getTriggerFallDistance() + 0.35D;
    }

    private int getMaxAimWaitTicks() {
        if (currentMode() == ClutchMode.LEGIT) {
            return 2;
        }
        return 0;
    }

    private float getPitchAimSpeed(float yawSpeed) {
        if (isUpwardBridgeAttempt()) {
            return MathHelper.clamp_float(Math.max(18.0F, yawSpeed * 1.18F), 14.0F, 120.0F);
        }
        return Math.max(12.0F, yawSpeed * 0.82F);
    }

    private float getAimInertia() {
        return isUpwardBridgeAttempt() ? 0.58F : 0.48F;
    }

    private float getAimMinStep() {
        return isUpwardBridgeAttempt() ? 0.75F : 0.35F;
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

    private void restoreSlot(int oldSlot) {
        if (oldSlot >= 0 && oldSlot < 9) {
            mc.thePlayer.inventory.currentItem = oldSlot;
            syncHeldItem();
        }
    }

    private void syncHeldItem() {
        if (mc.thePlayer != null && mc.thePlayer.sendQueue != null) {
            mc.thePlayer.sendQueue.addToSendQueue(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
        }
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class PlaceTarget {
        final BlockPos target;
        final BlockPos support;
        final EnumFacing side;
        final Vec3 hitVec;
        final double score;

        PlaceTarget(BlockPos target, BlockPos support, EnumFacing side, Vec3 hitVec, double score) {
            this.target = target;
            this.support = support;
            this.side = side;
            this.hitVec = hitVec;
            this.score = score;
        }
    }
}
