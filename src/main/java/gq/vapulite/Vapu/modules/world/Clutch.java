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

    private final Mode<ClutchMode> mode =
            new Mode<ClutchMode>("Mode", "Mode", ClutchMode.values(), ClutchMode.SMART);
    private final Numbers<Double> fallDistance =
            new Numbers<Double>("Fall Distance", "FallDistance", 0.8D, 0.0D, 8.0D, 0.1D);
    private final Option<Boolean> autoPlace = new Option<Boolean>("Auto Place", "AutoPlace", true);
    private final Option<Boolean> autoSwap = new Option<Boolean>("Auto Swap", "AutoSwap", true);
    private final Option<Boolean> restoreSlot = new Option<Boolean>("Restore Slot", "RestoreSlot", false);

    private final RotationUtil.State rotationState = new RotationUtil.State();

    private PlaceTarget lockedTarget;
    private int lockedTicks;
    private long lastPlaceMillis;

    public Clutch() {
        super("Clutch", Keyboard.KEY_NONE, ModuleType.World, "Place a block under you while falling");
        this.addValues(mode, fallDistance, autoPlace, autoSwap, restoreSlot);
        Chinese = "落地救方块";
    }

    @Override
    public void enable() {
        reset();
    }

    @Override
    public void disable() {
        reset();
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!canRun()) {
            reset();
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

        rememberTarget(target);
        aimAt(target, upward);
        slowHorizontalMotion(upward);

        if (!Boolean.TRUE.equals(autoPlace.getValue())) {
            return;
        }
        if (!canPlaceNow(target, upward)) {
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

    private void aimAt(PlaceTarget target, boolean upward) {
        float[] rotations = rotationsTo(target);
        if (currentMode() == ClutchMode.PANIC || lockedTicks > 2 && !isAimClose(rotations, upward)) {
            mc.thePlayer.rotationYaw = rotations[0];
            mc.thePlayer.rotationPitch = rotations[1];
            RotationUtil.syncHead(mc, rotations[0]);
            rotationState.reset();
            return;
        }

        float yawSpeed = getAimSpeed(upward);
        float pitchSpeed = upward ? Math.max(34.0F, yawSpeed * 1.12F) : Math.max(28.0F, yawSpeed);
        RotationUtil.applyToPlayer(mc, rotations[0], rotations[1], yawSpeed, pitchSpeed,
                false, 0.0F, rotationState, upward ? 0.62F : 0.55F,
                upward ? 0.82F : 0.55F, true);
    }

    private float[] rotationsTo(PlaceTarget target) {
        Vec3 hitVec = target.hitVec;
        return RotationUtil.getRotationsTo(mc, hitVec.xCoord, hitVec.yCoord, hitVec.zCoord);
    }

    private boolean canPlaceNow(PlaceTarget target, boolean upward) {
        if (System.currentTimeMillis() - lastPlaceMillis < getPlaceDelay(upward)) {
            return false;
        }
        if (currentMode() == ClutchMode.PANIC || isEmergency()) {
            return true;
        }
        return currentMode() == ClutchMode.SMART
                || isAimClose(rotationsTo(target), upward)
                || lockedTicks >= getAimWaitTicks();
    }

    private boolean isAimClose(float[] rotations, boolean upward) {
        float yawDiff = Math.abs(MathHelper.wrapAngleTo180_float(rotations[0] - mc.thePlayer.rotationYaw));
        float pitchDiff = Math.abs(MathHelper.wrapAngleTo180_float(rotations[1] - mc.thePlayer.rotationPitch));
        float limit = upward ? 18.0F : 15.0F;
        if (currentMode() == ClutchMode.LEGIT) {
            limit *= 0.72F;
        }
        return yawDiff + pitchDiff * 0.75F <= limit;
    }

    private boolean place(PlaceTarget target) {
        ItemStack stack = mc.thePlayer.getHeldItem();
        if (!isPlaceableStack(stack) || !canPlaceOnSide(target.support, target.side)) {
            return false;
        }

        int slot = mc.thePlayer.inventory.currentItem;
        int oldSize = stack.stackSize;
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
            rotationState.reset();
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
        mc.thePlayer.sendQueue.addToSendQueue(new C09PacketHeldItemChange(slot));
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
        rotationState.reset();
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
        if (currentMode() == ClutchMode.PANIC || isEmergency()) {
            return 0L;
        }
        if (currentMode() == ClutchMode.LEGIT) {
            return upward ? 55L : 70L;
        }
        return upward ? 18L : 25L;
    }

    private float getAimSpeed(boolean upward) {
        if (currentMode() == ClutchMode.PANIC) {
            return 150.0F;
        }
        double fall = Math.max(0.0D, -mc.thePlayer.motionY);
        double horizontal = Math.sqrt(mc.thePlayer.motionX * mc.thePlayer.motionX
                + mc.thePlayer.motionZ * mc.thePlayer.motionZ);
        float speed = (float) (52.0D + fall * 40.0D + horizontal * 36.0D);
        if (upward) {
            speed = Math.max(speed, 82.0F);
        }
        if (currentMode() == ClutchMode.LEGIT) {
            speed *= 0.68F;
        }
        return MathHelper.clamp_float(speed, 24.0F, 150.0F);
    }

    private int getLockTicks() {
        if (currentMode() == ClutchMode.PANIC) {
            return 1;
        }
        if (currentMode() == ClutchMode.LEGIT) {
            return 4;
        }
        return 3;
    }

    private int getAimWaitTicks() {
        return currentMode() == ClutchMode.LEGIT ? 2 : 0;
    }

    private double getLockBonus() {
        if (currentMode() == ClutchMode.PANIC) {
            return 6.0D;
        }
        return currentMode() == ClutchMode.LEGIT ? 10.0D : 8.0D;
    }

    private double getSwitchMargin() {
        if (currentMode() == ClutchMode.PANIC) {
            return 14.0D;
        }
        return currentMode() == ClutchMode.LEGIT ? 8.0D : 11.0D;
    }

    private double getFallMotionScale() {
        if (currentMode() == ClutchMode.PANIC) {
            return 0.62D;
        }
        if (currentMode() == ClutchMode.LEGIT) {
            return 0.92D;
        }
        return 0.76D;
    }

    private double getUpwardMotionScale() {
        if (currentMode() == ClutchMode.PANIC) {
            return 0.72D;
        }
        if (currentMode() == ClutchMode.LEGIT) {
            return 0.94D;
        }
        return 0.84D;
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
}
