package gq.yozakura.module.world;

import gq.yozakura.bridge.MinecraftAccessor;
import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.event.bus.types.EventType;
import gq.yozakura.event.bus.types.Priority;
import gq.yozakura.event.bridge.MoveInputEvent;
import gq.yozakura.event.bridge.UpdateEvent;
import gq.yozakura.manager.RotationState;
import gq.yozakura.manager.VisualRotationState;
import gq.yozakura.module.ModuleType;
import gq.yozakura.module.Module;
import gq.yozakura.util.module.BlockUtil;
import gq.yozakura.util.module.MoveUtil;
import gq.yozakura.util.module.RotationUtil;
import gq.yozakura.value.Mode;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.block.BlockFalling;
import net.minecraft.block.BlockLiquid;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

public class Clutch extends Module {
    public enum ClutchMode {
        LEGIT,
        SMART,
        PANIC
    }

    private final Mode<ClutchMode> mode =
            new Mode<ClutchMode>("Mode", "Mode", ClutchMode.values(), ClutchMode.SMART);
    private final Numbers<Double> fallDistance =
            new Numbers<Double>("Fall Distance", "FallDistance", 0.8D, 0.0D, 8.0D, 0.1D);
    private final Option<Boolean> autoPlace = new Option<Boolean>("Auto Place", "AutoPlace", true);
    private final Option<Boolean> autoSwap = new Option<Boolean>("Auto Swap", "AutoSwap", true);
    private final Option<Boolean> restoreSlot = new Option<Boolean>("Restore Slot", "RestoreSlot", false);
    private final Option<Boolean> moveFix = new Option<Boolean>("Move Fix", "MoveFix", true);
    private final Option<Boolean> voidOnly = new Option<Boolean>("Void Only", "VoidOnly", false);

    private static final int ROTATION_PRIORITY = 4;
    private static final double[] PLACE_OFFSETS = new double[]{
            0.03125D,
            0.09375D,
            0.15625D,
            0.21875D,
            0.28125D,
            0.34375D,
            0.40625D,
            0.46875D,
            0.53125D,
            0.59375D,
            0.65625D,
            0.71875D,
            0.78125D,
            0.84375D,
            0.90625D,
            0.96875D
    };

    private PlaceTarget lockedTarget;
    private int lockedTicks;
    private long lastPlaceMillis;
    private float yaw = -180.0F;
    private float pitch = 85.0F;
    private boolean hasRotation;

    public Clutch() {
        super("Clutch", Keyboard.KEY_NONE, ModuleType.World, "Place a block under you while falling");
        this.addValues(mode, fallDistance, autoPlace, autoSwap, restoreSlot, moveFix, voidOnly);
        Chinese = "落地救方块";
    }

    @Override
    public void enable() {
        reset();
    }

    @Override
    public void disable() {
        reset();
        VisualRotationState.clearSource("Clutch");
    }

    @EventTarget(Priority.HIGH)
    public void onUpdate(UpdateEvent event) {
        if (event.getType() != EventType.PRE) {
            return;
        }
        if (!canRun()) {
            reset();
            return;
        }
        if (Boolean.TRUE.equals(voidOnly.getValue()) && !isFallingIntoVoid()) {
            resetTarget();
            return;
        }

        boolean upward = isUpwardBridgeAttempt();
        boolean falling = isFallingDanger();
        if (!upward && !falling) {
            resetTarget();
            return;
        }

        int originalSlot = mc.thePlayer.inventory.currentItem;
        if (!ensureBlockSelected()) {
            resetTarget();
            return;
        }

        PlaceTarget target = findPlaceTarget(upward);
        if (target == null) {
            restoreSlot(originalSlot);
            resetTarget();
            return;
        }

        AimData aim = resolveAim(target, event, upward);
        if (aim == null) {
            restoreSlot(originalSlot);
            resetTarget();
            return;
        }

        target = new PlaceTarget(target.target, target.support, target.side, aim.hitVec, target.score);
        rememberTarget(target);
        applySilentRotation(event, aim);
        slowHorizontalMotion(upward);

        if (!Boolean.TRUE.equals(autoPlace.getValue())) {
            return;
        }
        if (!canPlaceNow(target, upward, aim.exactHit)) {
            return;
        }
        if (place(target)) {
            lastPlaceMillis = System.currentTimeMillis();
            resetTarget();
            restoreSlot(originalSlot);
        }
    }

    private boolean canRun() {
        return isInGame()
                && mc.currentScreen == null
                && mc.playerController != null
                && !mc.thePlayer.capabilities.isFlying
                && !mc.thePlayer.capabilities.isCreativeMode
                && !mc.thePlayer.isInWater()
                && !mc.thePlayer.isInLava()
                && !mc.thePlayer.isOnLadder();
    }

    private boolean isFallingDanger() {
        if (mc.thePlayer.onGround || mc.thePlayer.motionY > -0.015D) {
            return false;
        }
        return mc.thePlayer.fallDistance >= getTriggerFallDistance() || !hasPredictedFeetSupport();
    }

    private boolean isUpwardBridgeAttempt() {
        if (mc.gameSettings == null || mc.thePlayer.onGround || mc.thePlayer.motionY <= 0.0D) {
            return false;
        }
        if (!mc.gameSettings.keyBindJump.isKeyDown() || mc.gameSettings.keyBindSneak.isKeyDown()) {
            return false;
        }
        if (!mc.gameSettings.keyBindUseItem.isKeyDown() && !Boolean.TRUE.equals(autoPlace.getValue())) {
            return false;
        }
        return !hasPredictedFeetSupport();
    }

    private boolean hasPredictedFeetSupport() {
        AxisAlignedBB box = mc.thePlayer.getEntityBoundingBox();
        double prediction = getPrediction(false);
        double x = mc.thePlayer.posX + mc.thePlayer.motionX * prediction;
        double z = mc.thePlayer.posZ + mc.thePlayer.motionZ * prediction;
        double y = box.minY - 0.08D + Math.min(0.0D, mc.thePlayer.motionY) * Math.min(1.0D, prediction);
        double halfX = Math.max(0.18D, (box.maxX - box.minX) * 0.38D);
        double halfZ = Math.max(0.18D, (box.maxZ - box.minZ) * 0.38D);

        return hasSupportAt(x - halfX, y, z - halfZ)
                && hasSupportAt(x - halfX, y, z + halfZ)
                && hasSupportAt(x + halfX, y, z - halfZ)
                && hasSupportAt(x + halfX, y, z + halfZ);
    }

    private boolean isFallingIntoVoid() {
        AxisAlignedBB box = mc.thePlayer.getEntityBoundingBox();
        Vec3 predictedFeet = getPredictedFeet(false);
        return isFootprintOverVoid(mc.thePlayer.posX, box.minY - 0.08D, mc.thePlayer.posZ)
                || isFootprintOverVoid(predictedFeet.xCoord, predictedFeet.yCoord - 0.08D, predictedFeet.zCoord);
    }

    private boolean isFootprintOverVoid(double x, double y, double z) {
        AxisAlignedBB box = mc.thePlayer.getEntityBoundingBox();
        double halfX = Math.max(0.18D, (box.maxX - box.minX) * 0.38D);
        double halfZ = Math.max(0.18D, (box.maxZ - box.minZ) * 0.38D);
        return !hasSolidBelow(x, y, z)
                && !hasSolidBelow(x - halfX, y, z - halfZ)
                && !hasSolidBelow(x - halfX, y, z + halfZ)
                && !hasSolidBelow(x + halfX, y, z - halfZ)
                && !hasSolidBelow(x + halfX, y, z + halfZ);
    }

    private boolean hasSolidBelow(double x, double y, double z) {
        int startY = Math.min(255, MathHelper.floor_double(y));
        for (int blockY = startY; blockY >= 0; blockY--) {
            if (isSolidSupport(new BlockPos(x, blockY, z))) {
                return true;
            }
        }
        return false;
    }

    private boolean ensureBlockSelected() {
        if (isHoldingPlaceableBlock()) {
            return true;
        }
        if (!Boolean.TRUE.equals(autoSwap.getValue())) {
            return false;
        }
        int slot = findBestBlockSlot();
        if (slot == -1) {
            return false;
        }
        switchSlot(slot);
        return true;
    }

    private boolean isHoldingPlaceableBlock() {
        return isPlaceableStack(mc.thePlayer.getHeldItem());
    }

    private int findBestBlockSlot() {
        int bestSlot = -1;
        int bestCount = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.mainInventory[i];
            if (isPlaceableStack(stack) && stack.stackSize > bestCount) {
                bestSlot = i;
                bestCount = stack.stackSize;
            }
        }
        return bestSlot;
    }

    private boolean isPlaceableStack(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0 || !(stack.getItem() instanceof ItemBlock)) {
            return false;
        }
        Block block = ((ItemBlock) stack.getItem()).getBlock();
        return block != null
                && block != Blocks.air
                && !(block instanceof BlockAir)
                && !(block instanceof BlockLiquid)
                && !(block instanceof BlockFalling)
                && block.isFullBlock();
    }

    private PlaceTarget findPlaceTarget(boolean upward) {
        Vec3 predictedFeet = getPredictedFeet(upward);
        PlaceTarget locked = refreshLockedTarget(predictedFeet);
        if (locked != null && lockedTicks < getLockTicks()) {
            return locked;
        }

        List<BlockPos> candidates = collectCandidates(predictedFeet, upward);
        PlaceTarget best = null;
        double bestScore = Double.MAX_VALUE;

        for (int i = 0; i < candidates.size(); i++) {
            BlockPos targetPos = candidates.get(i);
            if (!canReplace(targetPos) || intersectsPlayer(targetPos)) {
                continue;
            }

            PlaceTarget target = findSupport(targetPos, predictedFeet, upward);
            if (target == null) {
                continue;
            }

            double score = score(target, predictedFeet, upward, i);
            if (locked != null && samePlacement(locked, target)) {
                score -= getLockBonus();
            }
            if (score < bestScore) {
                bestScore = score;
                best = new PlaceTarget(target.target, target.support, target.side, target.hitVec, score);
            }
        }

        if (locked != null && best != null && locked.score <= best.score + getSwitchMargin()) {
            return locked;
        }
        return best == null ? locked : best;
    }

    private Vec3 getPredictedFeet(boolean upward) {
        double prediction = getPrediction(upward);
        double yMotion = upward
                ? Math.max(0.0D, mc.thePlayer.motionY) * 0.35D
                : Math.min(0.0D, mc.thePlayer.motionY) * Math.min(1.0D, prediction);
        return new Vec3(
                mc.thePlayer.posX + mc.thePlayer.motionX * prediction,
                mc.thePlayer.getEntityBoundingBox().minY + yMotion,
                mc.thePlayer.posZ + mc.thePlayer.motionZ * prediction
        );
    }

    private List<BlockPos> collectCandidates(Vec3 predictedFeet, boolean upward) {
        ArrayList<BlockPos> candidates = new ArrayList<BlockPos>();
        AxisAlignedBB box = mc.thePlayer.getEntityBoundingBox();
        int radius = getSearchRadius(upward);
        int baseY = upward
                ? MathHelper.floor_double(box.minY - 1.0D)
                : MathHelper.floor_double(predictedFeet.yCoord - 1.0D);

        addFootprint(candidates, predictedFeet.xCoord, predictedFeet.zCoord, baseY, radius);
        addFootprint(candidates, mc.thePlayer.posX, mc.thePlayer.posZ,
                MathHelper.floor_double(box.minY - 1.0D), radius);

        Vec3 movement = getMovementDirection();
        if (movement != null) {
            addFootprint(candidates, predictedFeet.xCoord + movement.xCoord * 0.62D,
                    predictedFeet.zCoord + movement.zCoord * 0.62D, baseY, radius);
            addFootprint(candidates, predictedFeet.xCoord + movement.xCoord * 1.12D,
                    predictedFeet.zCoord + movement.zCoord * 1.12D, baseY, Math.max(1, radius - 1));
        }

        int verticalSearch = upward ? 1 : getVerticalSearch();
        for (int dy = 1; dy <= verticalSearch; dy++) {
            addFootprint(candidates, predictedFeet.xCoord, predictedFeet.zCoord, baseY - dy, radius);
        }
        return candidates;
    }

    private void addFootprint(List<BlockPos> candidates, double x, double z, int y, int radius) {
        addUnique(candidates, new BlockPos(x, y, z));
        addUnique(candidates, new BlockPos(x - 0.31D, y, z - 0.31D));
        addUnique(candidates, new BlockPos(x - 0.31D, y, z + 0.31D));
        addUnique(candidates, new BlockPos(x + 0.31D, y, z - 0.31D));
        addUnique(candidates, new BlockPos(x + 0.31D, y, z + 0.31D));

        BlockPos center = new BlockPos(x, y, z);
        for (int ring = 1; ring <= radius; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == ring) {
                        addUnique(candidates, center.add(dx, 0, dz));
                    }
                }
            }
        }
    }

    private void addUnique(List<BlockPos> positions, BlockPos pos) {
        if (!positions.contains(pos)) {
            positions.add(pos);
        }
    }

    private PlaceTarget findSupport(BlockPos targetPos, Vec3 predictedFeet, boolean upward) {
        PlaceTarget best = null;
        double bestScore = Double.MAX_VALUE;
        EnumFacing[] sides = getPlaceSides(upward);

        for (int i = 0; i < sides.length; i++) {
            EnumFacing side = sides[i];
            BlockPos support = targetPos.offset(side.getOpposite());
            if (!isSolidSupport(support) || !canPlaceOnSide(support, side)) {
                continue;
            }

            Vec3 hitVec = getHitVec(support, side, predictedFeet);
            double range = mc.thePlayer.getPositionEyes(1.0F).distanceTo(hitVec);
            if (range > getPlaceRange()) {
                continue;
            }

            double score = range * 1.15D + i * 0.55D;
            if (side != EnumFacing.UP) {
                score += upward ? 0.85D : 0.25D;
            }
            if (score < bestScore) {
                bestScore = score;
                best = new PlaceTarget(targetPos, support, side, hitVec, score);
            }
        }
        return best;
    }

    private boolean canPlaceOnSide(BlockPos support, EnumFacing side) {
        ItemStack stack = mc.thePlayer.getHeldItem();
        if (!isPlaceableStack(stack)) {
            return false;
        }
        ItemBlock itemBlock = (ItemBlock) stack.getItem();
        return itemBlock.canPlaceBlockOnSide(mc.theWorld, support, side, mc.thePlayer, stack);
    }

    private EnumFacing[] getPlaceSides(boolean upward) {
        if (upward) {
            return new EnumFacing[]{
                    EnumFacing.NORTH,
                    EnumFacing.SOUTH,
                    EnumFacing.WEST,
                    EnumFacing.EAST,
                    EnumFacing.UP,
                    EnumFacing.DOWN
            };
        }
        return new EnumFacing[]{
                EnumFacing.UP,
                EnumFacing.NORTH,
                EnumFacing.SOUTH,
                EnumFacing.WEST,
                EnumFacing.EAST,
                EnumFacing.DOWN
        };
    }

    private Vec3 getHitVec(BlockPos support, EnumFacing side, Vec3 preferred) {
        double minX = support.getX() + 0.20D;
        double maxX = support.getX() + 0.80D;
        double minY = support.getY() + 0.18D;
        double maxY = support.getY() + 0.82D;
        double minZ = support.getZ() + 0.20D;
        double maxZ = support.getZ() + 0.80D;

        double x = support.getX() + 0.5D;
        double y = support.getY() + 0.5D;
        double z = support.getZ() + 0.5D;

        if (side == EnumFacing.UP) {
            x = clamp(preferred.xCoord, minX, maxX);
            y = support.getY() + 1.0D;
            z = clamp(preferred.zCoord, minZ, maxZ);
        } else if (side == EnumFacing.DOWN) {
            x = clamp(preferred.xCoord, minX, maxX);
            y = support.getY();
            z = clamp(preferred.zCoord, minZ, maxZ);
        } else if (side == EnumFacing.NORTH) {
            x = clamp(preferred.xCoord, minX, maxX);
            y = clamp(preferred.yCoord - 0.35D, minY, maxY);
            z = support.getZ();
        } else if (side == EnumFacing.SOUTH) {
            x = clamp(preferred.xCoord, minX, maxX);
            y = clamp(preferred.yCoord - 0.35D, minY, maxY);
            z = support.getZ() + 1.0D;
        } else if (side == EnumFacing.WEST) {
            x = support.getX();
            y = clamp(preferred.yCoord - 0.35D, minY, maxY);
            z = clamp(preferred.zCoord, minZ, maxZ);
        } else if (side == EnumFacing.EAST) {
            x = support.getX() + 1.0D;
            y = clamp(preferred.yCoord - 0.35D, minY, maxY);
            z = clamp(preferred.zCoord, minZ, maxZ);
        }
        return new Vec3(x, y, z);
    }

    private double score(PlaceTarget target, Vec3 predictedFeet, boolean upward, int priority) {
        float[] rotations = rotationsTo(target);
        float yawDiff = Math.abs(MathHelper.wrapAngleTo180_float(rotations[0] - mc.thePlayer.rotationYaw));
        float pitchDiff = Math.abs(MathHelper.wrapAngleTo180_float(rotations[1] - mc.thePlayer.rotationPitch));
        double dx = predictedFeet.xCoord - (target.target.getX() + 0.5D);
        double dz = predictedFeet.zCoord - (target.target.getZ() + 0.5D);
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double sidePenalty = target.side == EnumFacing.UP ? 0.0D : (upward ? 1.6D : 0.65D);
        double stability = samePlacement(lockedTarget, target) ? -getLockBonus() : 0.0D;

        return priority * 0.48D
                + horizontal * 8.0D
                + yawDiff * 0.16D
                + pitchDiff * 0.12D
                + target.score
                + sidePenalty
                + stability;
    }

    private float[] rotationsTo(PlaceTarget target) {
        Vec3 hitVec = target.hitVec;
        return RotationUtil.getRotations(hitVec);
    }

    private AimData resolveAim(PlaceTarget target, UpdateEvent event, boolean upward) {
        AimData exact = findExactAim(target, event, upward);
        if (exact != null) {
            return exact;
        }

        Vec3 hitVec = BlockUtil.getClickVec(target.support, target.side);
        double dx = hitVec.xCoord - mc.thePlayer.posX;
        double dy = hitVec.yCoord - mc.thePlayer.posY - (double) mc.thePlayer.getEyeHeight();
        double dz = hitVec.zCoord - mc.thePlayer.posZ;
        float baseYaw = hasRotation ? yaw : event.getYaw();
        float basePitch = hasRotation ? pitch : event.getPitch();
        float[] rotations = RotationUtil.getRotationsTo(dx, dy, dz, baseYaw, basePitch);
        float yawSpeed = getAimSpeed(upward);
        float pitchSpeed = getPitchAimSpeed(yawSpeed, upward);
        float nextYaw = stepRotation(baseYaw, rotations[0], yawSpeed);
        float nextPitch = stepRotation(basePitch, rotations[1], pitchSpeed);
        return new AimData(RotationUtil.quantizeAngle(nextYaw), RotationUtil.quantizeAngle(nextPitch), hitVec, false);
    }

    private AimData findExactAim(PlaceTarget target, UpdateEvent event, boolean upward) {
        double[] xOffsets = PLACE_OFFSETS;
        double[] yOffsets = PLACE_OFFSETS;
        double[] zOffsets = PLACE_OFFSETS;
        switch (target.side) {
            case NORTH:
                zOffsets = new double[]{0.0D};
                break;
            case EAST:
                xOffsets = new double[]{1.0D};
                break;
            case SOUTH:
                zOffsets = new double[]{1.0D};
                break;
            case WEST:
                xOffsets = new double[]{0.0D};
                break;
            case DOWN:
                yOffsets = new double[]{0.0D};
                break;
            case UP:
                yOffsets = new double[]{1.0D};
                break;
        }

        float baseYaw = hasRotation ? yaw : event.getYaw();
        float basePitch = hasRotation ? pitch : event.getPitch();
        AimData best = null;
        float bestDiff = 0.0F;
        for (double dx : xOffsets) {
            for (double dy : yOffsets) {
                for (double dz : zOffsets) {
                    double relX = (double) target.support.getX() + dx - mc.thePlayer.posX;
                    double relY = (double) target.support.getY() + dy - mc.thePlayer.posY - (double) mc.thePlayer.getEyeHeight();
                    double relZ = (double) target.support.getZ() + dz - mc.thePlayer.posZ;
                    float[] rotations = RotationUtil.getRotationsTo(relX, relY, relZ, baseYaw, basePitch);
                    float yawSpeed = getAimSpeed(upward);
                    float pitchSpeed = getPitchAimSpeed(yawSpeed, upward);
                    float nextYaw = RotationUtil.quantizeAngle(stepRotation(baseYaw, rotations[0], yawSpeed));
                    float nextPitch = RotationUtil.quantizeAngle(stepRotation(basePitch, rotations[1], pitchSpeed));
                    MovingObjectPosition mop = RotationUtil.rayTrace(nextYaw, nextPitch,
                            mc.playerController.getBlockReachDistance(), 1.0F);
                    if (mop == null
                            || mop.typeOfHit != MovingObjectType.BLOCK
                            || !mop.getBlockPos().equals(target.support)
                            || mop.sideHit != target.side) {
                        continue;
                    }
                    float diff = Math.abs(MathHelper.wrapAngleTo180_float(nextYaw - baseYaw))
                            + Math.abs(nextPitch - basePitch) * 0.75F;
                    if (best == null || diff < bestDiff) {
                        best = new AimData(nextYaw, nextPitch, mop.hitVec, true);
                        bestDiff = diff;
                    }
                }
            }
        }
        return best;
    }

    private float stepRotation(float current, float target, float maxSpeed) {
        float diff = MathHelper.wrapAngleTo180_float(target - current);
        float speed = Math.max(1.0F, maxSpeed);
        if (currentMode() == ClutchMode.PANIC) {
            speed = Math.max(speed, 115.0F);
        } else if (isEmergency()) {
            speed = Math.max(speed, 86.0F);
        }
        speed = Math.min(speed, Math.max(getMinAimStep(), Math.abs(diff) * getAimEase()));
        return current + MathHelper.clamp_float(diff, -speed, speed);
    }

    private void applySilentRotation(UpdateEvent event, AimData aim) {
        yaw = aim.yaw;
        pitch = MathHelper.clamp_float(aim.pitch, -90.0F, 90.0F);
        hasRotation = true;
        event.setRotation(yaw, pitch, ROTATION_PRIORITY);
        if (Boolean.TRUE.equals(moveFix.getValue())) {
            event.setPervRotation(yaw, ROTATION_PRIORITY);
        }
        VisualRotationState.publish("Clutch", yaw, pitch, ROTATION_PRIORITY);
    }

    private boolean canPlaceNow(PlaceTarget target, boolean upward, boolean exactHit) {
        if (System.currentTimeMillis() - lastPlaceMillis < getPlaceDelay(upward)) {
            return false;
        }
        if (lockedTicks < getAimWaitTicks()) {
            return false;
        }
        if (exactHit) {
            return true;
        }
        return currentMode() == ClutchMode.PANIC || isEmergency();
    }

    private boolean place(PlaceTarget target) {
        ItemStack stack = mc.thePlayer.getHeldItem();
        if (!isPlaceableStack(stack) || !canPlaceOnSide(target.support, target.side)) {
            return false;
        }

        int slot = mc.thePlayer.inventory.currentItem;
        int oldSize = stack.stackSize;
        MinecraftAccessor.syncCurrentPlayItem(mc.playerController);
        boolean placed = mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, stack,
                target.support, target.side, target.hitVec);
        if (!placed) {
            return false;
        }

        mc.thePlayer.swingItem();
        if (stack.stackSize <= 0) {
            mc.thePlayer.inventory.mainInventory[slot] = null;
        } else if (stack.stackSize != oldSize || mc.playerController.isInCreativeMode()) {
            mc.entityRenderer.itemRenderer.resetEquippedProgress();
        }
        return true;
    }

    private PlaceTarget refreshLockedTarget(Vec3 predictedFeet) {
        if (lockedTarget == null || !canReplace(lockedTarget.target)
                || !isSolidSupport(lockedTarget.support)
                || !canPlaceOnSide(lockedTarget.support, lockedTarget.side)
                || intersectsPlayer(lockedTarget.target)) {
            return null;
        }
        Vec3 hitVec = getHitVec(lockedTarget.support, lockedTarget.side, predictedFeet);
        if (mc.thePlayer.getPositionEyes(1.0F).distanceTo(hitVec) > getPlaceRange()) {
            return null;
        }
        PlaceTarget refreshed = new PlaceTarget(lockedTarget.target, lockedTarget.support,
                lockedTarget.side, hitVec, lockedTarget.score);
        return new PlaceTarget(refreshed.target, refreshed.support, refreshed.side, hitVec,
                score(refreshed, predictedFeet, isUpwardBridgeAttempt(), 0));
    }

    private void rememberTarget(PlaceTarget target) {
        if (samePlacement(lockedTarget, target)) {
            lockedTicks++;
        } else {
            lockedTarget = target;
            lockedTicks = 0;
        }
    }

    private boolean samePlacement(PlaceTarget first, PlaceTarget second) {
        return first != null
                && second != null
                && first.target.equals(second.target)
                && first.support.equals(second.support)
                && first.side == second.side;
    }

    private void slowHorizontalMotion(boolean upward) {
        double scale = upward ? getUpwardMotionScale() : getFallMotionScale();
        if (scale >= 1.0D) {
            return;
        }
        mc.thePlayer.motionX *= scale;
        mc.thePlayer.motionZ *= scale;
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

    private boolean hasSupportAt(double x, double y, double z) {
        return isSolidSupport(new BlockPos(x, y, z));
    }

    private boolean canReplace(BlockPos pos) {
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

    private boolean intersectsPlayer(BlockPos pos) {
        AxisAlignedBB box = mc.theWorld.getBlockState(pos).getBlock()
                .getCollisionBoundingBox(mc.theWorld, pos, mc.theWorld.getBlockState(pos));
        return box != null && mc.thePlayer.getEntityBoundingBox().intersectsWith(box);
    }

    private void switchSlot(int slot) {
        if (slot < 0 || slot > 8 || mc.thePlayer.inventory.currentItem == slot) {
            return;
        }
        mc.thePlayer.inventory.currentItem = slot;
        MinecraftAccessor.syncCurrentPlayItem(mc.playerController);
    }

    private void restoreSlot(int originalSlot) {
        if (Boolean.TRUE.equals(restoreSlot.getValue())) {
            switchSlot(originalSlot);
        }
    }

    private void reset() {
        lastPlaceMillis = 0L;
        resetTarget();
    }

    private void resetTarget() {
        lockedTarget = null;
        lockedTicks = 0;
        hasRotation = false;
        yaw = -180.0F;
        pitch = 85.0F;
        VisualRotationState.clearSource("Clutch");
    }

    private ClutchMode currentMode() {
        return mode.getValue() == null ? ClutchMode.SMART : mode.getValue();
    }

    private double getTriggerFallDistance() {
        double base = Math.max(0.0D, fallDistance.getValue());
        if (currentMode() == ClutchMode.PANIC) {
            return base * 0.35D;
        }
        if (currentMode() == ClutchMode.SMART) {
            return base * 0.65D;
        }
        return base;
    }

    private double getPrediction(boolean upward) {
        double horizontal = Math.sqrt(mc.thePlayer.motionX * mc.thePlayer.motionX
                + mc.thePlayer.motionZ * mc.thePlayer.motionZ);
        if (upward) {
            return clamp(0.35D + horizontal * 0.9D, 0.25D, 1.2D);
        }

        double fall = Math.max(0.0D, -mc.thePlayer.motionY);
        double prediction = 0.8D + fall * 2.8D + horizontal * 1.55D;
        if (currentMode() == ClutchMode.PANIC) {
            prediction *= 1.25D;
        } else if (currentMode() == ClutchMode.LEGIT) {
            prediction *= 0.82D;
        }
        return clamp(prediction, 0.55D, currentMode() == ClutchMode.PANIC ? 5.6D : 4.6D);
    }

    private int getSearchRadius(boolean upward) {
        if (currentMode() == ClutchMode.PANIC) {
            return 2;
        }
        if (upward) {
            return 1;
        }
        double horizontal = mc.thePlayer.motionX * mc.thePlayer.motionX + mc.thePlayer.motionZ * mc.thePlayer.motionZ;
        return horizontal > 0.018D ? 2 : 1;
    }

    private int getVerticalSearch() {
        if (currentMode() == ClutchMode.PANIC) {
            return 5;
        }
        if (currentMode() == ClutchMode.SMART) {
            return 4;
        }
        return 3;
    }

    private double getPlaceRange() {
        if (currentMode() == ClutchMode.PANIC) {
            return 5.25D;
        }
        if (currentMode() == ClutchMode.LEGIT) {
            return 4.35D;
        }
        return 4.85D;
    }

    private long getPlaceDelay(boolean upward) {
        if (currentMode() == ClutchMode.PANIC) {
            return upward ? 30L : 40L;
        }
        if (isEmergency()) {
            return upward ? 45L : 55L;
        }
        if (currentMode() == ClutchMode.LEGIT) {
            return upward ? 95L : 120L;
        }
        return upward ? 55L : 75L;
    }

    private float getAimSpeed(boolean upward) {
        if (currentMode() == ClutchMode.PANIC) {
            return 110.0F;
        }
        double fall = Math.max(0.0D, -mc.thePlayer.motionY);
        double horizontal = Math.sqrt(mc.thePlayer.motionX * mc.thePlayer.motionX
                + mc.thePlayer.motionZ * mc.thePlayer.motionZ);
        float speed = (float) (34.0D + fall * 28.0D + horizontal * 24.0D);
        if (upward) {
            speed = Math.max(speed, 58.0F);
        }
        if (currentMode() == ClutchMode.LEGIT) {
            speed *= 0.55F;
        }
        return MathHelper.clamp_float(speed, 14.0F, 96.0F);
    }

    private float getPitchAimSpeed(float yawSpeed, boolean upward) {
        if (upward) {
            return Math.max(26.0F, yawSpeed * 1.05F);
        }
        return Math.max(20.0F, yawSpeed * 0.92F);
    }

    private float getAimEase() {
        if (currentMode() == ClutchMode.PANIC) {
            return 0.72F;
        }
        if (currentMode() == ClutchMode.LEGIT) {
            return 0.42F;
        }
        return 0.52F;
    }

    private float getMinAimStep() {
        if (currentMode() == ClutchMode.PANIC) {
            return 2.2F;
        }
        if (currentMode() == ClutchMode.LEGIT) {
            return 0.75F;
        }
        return 1.1F;
    }

    private int getLockTicks() {
        if (currentMode() == ClutchMode.PANIC) {
            return 2;
        }
        if (currentMode() == ClutchMode.LEGIT) {
            return 6;
        }
        return 5;
    }

    private int getAimWaitTicks() {
        if (currentMode() == ClutchMode.PANIC) {
            return 0;
        }
        return currentMode() == ClutchMode.LEGIT ? 3 : 1;
    }

    private double getLockBonus() {
        if (currentMode() == ClutchMode.PANIC) {
            return 6.0D;
        }
        return currentMode() == ClutchMode.LEGIT ? 10.0D : 8.0D;
    }

    private double getSwitchMargin() {
        if (currentMode() == ClutchMode.PANIC) {
            return 16.0D;
        }
        return currentMode() == ClutchMode.LEGIT ? 12.0D : 14.0D;
    }

    private double getFallMotionScale() {
        if (currentMode() == ClutchMode.PANIC) {
            return 0.72D;
        }
        if (currentMode() == ClutchMode.LEGIT) {
            return 0.95D;
        }
        return 0.86D;
    }

    private double getUpwardMotionScale() {
        if (currentMode() == ClutchMode.PANIC) {
            return 0.82D;
        }
        if (currentMode() == ClutchMode.LEGIT) {
            return 0.96D;
        }
        return 0.91D;
    }

    private boolean isEmergency() {
        return !mc.thePlayer.onGround
                && (mc.thePlayer.motionY < -0.55D
                || mc.thePlayer.fallDistance > getTriggerFallDistance() + 0.55D);
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

    private static final class AimData {
        final float yaw;
        final float pitch;
        final Vec3 hitVec;
        final boolean exactHit;

        AimData(float yaw, float pitch, Vec3 hitVec, boolean exactHit) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.hitVec = hitVec;
            this.exactHit = exactHit;
        }
    }
}
