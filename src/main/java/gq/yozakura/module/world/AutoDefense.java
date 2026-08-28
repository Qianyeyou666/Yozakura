package gq.yozakura.module.world;

import gq.yozakura.bridge.MinecraftAccessor;
import gq.yozakura.event.bridge.PacketAcceptedEvent;
import gq.yozakura.event.bridge.UpdateEvent;
import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.event.bus.types.EventType;
import gq.yozakura.event.bus.types.Priority;
import gq.yozakura.manager.VisualRotationState;
import gq.yozakura.module.Module;
import gq.yozakura.module.ModuleType;
import gq.yozakura.util.module.BlockUtil;
import gq.yozakura.util.module.ItemUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.block.BlockLiquid;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Keyboard;

/** Builds a compact floor and two-block-high wall shell around the local player. */
public final class AutoDefense extends Module {
    private static final int ROTATION_PRIORITY = 4;
    private static final int PLACE_DELAY_TICKS = 1;

    private BlockPos anchor;
    private int originalSlot = -1;
    private int placeDelay;
    private boolean submittingPlacement;
    private boolean switchingSlot;
    private boolean optionalRoofAttempted;

    public AutoDefense() {
        super("AutoDefense", Keyboard.KEY_NONE, ModuleType.World,
                "Place a compact defensive shell around the player");
        Chinese = "自动防御";
    }

    @Override
    public void enable() {
        if (!isInGame() || mc.playerController == null) {
            setState(false, false);
            return;
        }
        anchor = new BlockPos(
                MathHelper.floor_double(mc.thePlayer.posX),
                MathHelper.floor_double(mc.thePlayer.getEntityBoundingBox().minY),
                MathHelper.floor_double(mc.thePlayer.posZ));
        originalSlot = mc.thePlayer.inventory.currentItem;
        placeDelay = 0;
        optionalRoofAttempted = false;
    }

    @Override
    public void disable() {
        restoreSlot();
        anchor = null;
        submittingPlacement = false;
        switchingSlot = false;
        optionalRoofAttempted = false;
    }

    @EventTarget(Priority.HIGH)
    public void onUpdate(UpdateEvent event) {
        if (!getState() || event == null || event.getType() != EventType.PRE) {
            return;
        }
        if (!isInGame() || mc.playerController == null || anchor == null) {
            setState(false, false);
            return;
        }
        if (placeDelay > 0) {
            placeDelay--;
            return;
        }

        int slot = findBlockSlot();
        if (slot < 0) {
            setState(false, false);
            return;
        }

        switchSlot(slot);
        ItemStack selectedStack = mc.thePlayer.inventory.getStackInSlot(slot);
        if (!isPlaceableStack(selectedStack)) {
            return;
        }

        BlockPos target = findNextPlaceableTarget(selectedStack);
        Placement placement = null;
        boolean optionalRoofTarget = false;
        if (target == null) {
            if (!isShellComplete()) {
                return;
            }
            if (!optionalRoofAttempted) {
                placement = findOptionalRoofPlacement(selectedStack);
            }
            if (placement == null) {
                setState(false, false);
                return;
            }
            target = placement.target;
            optionalRoofTarget = true;
        }
        if (!BlockUtil.isReplaceable(target) || intersectsBlockingEntity(target)) {
            return;
        }

        if (placement == null) {
            placement = findPlacement(target, selectedStack);
        }
        if (placement == null) {
            return;
        }

        float[] rotations = rotationsTo(placement.hitVec);
        if (!event.trySetRotation(rotations[0], rotations[1], ROTATION_PRIORITY)) {
            return;
        }
        VisualRotationState.publish("AutoDefense", rotations[0], rotations[1], ROTATION_PRIORITY);
        if (optionalRoofTarget) {
            optionalRoofAttempted = true;
        }
        if (place(placement)) {
            placeDelay = PLACE_DELAY_TICKS;
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onPacketAccepted(PacketAcceptedEvent event) {
        if (event == null) {
            return;
        }
        if ((switchingSlot && event.getPacket() instanceof C09PacketHeldItemChange)
                || (submittingPlacement && event.getPacket() instanceof C08PacketPlayerBlockPlacement)) {
            event.requestOriginalPacketOrder();
        }
    }

    private BlockPos findNextPlaceableTarget(ItemStack stack) {
        for (AutoDefensePlan.Offset offset : AutoDefensePlan.shellOffsets()) {
            BlockPos target = toWorld(offset);
            if (!BlockUtil.isReplaceable(target) || intersectsBlockingEntity(target)) {
                continue;
            }
            if (findPlacement(target, stack) != null) {
                return target;
            }
        }
        return null;
    }

    private boolean isShellComplete() {
        for (AutoDefensePlan.Offset offset : AutoDefensePlan.shellOffsets()) {
            BlockPos target = toWorld(offset);
            if (BlockUtil.isReplaceable(target) && !intersectsBlockingEntity(target)) {
                return false;
            }
        }
        return true;
    }

    private Placement findOptionalRoofPlacement(ItemStack stack) {
        BlockPos roof = toWorld(AutoDefensePlan.optionalRoofOffset());
        if (!BlockUtil.isReplaceable(roof) || intersectsBlockingEntity(roof)) {
            return null;
        }
        return findPlacement(roof, stack);
    }

    private BlockPos toWorld(AutoDefensePlan.Offset offset) {
        return anchor.add(offset.x, offset.y, offset.z);
    }

    private Placement findPlacement(BlockPos target, ItemStack stack) {
        if (!isPlaceableStack(stack)) {
            return null;
        }
        ItemBlock itemBlock = (ItemBlock) stack.getItem();
        Placement best = null;
        double bestDistance = Double.MAX_VALUE;
        for (EnumFacing side : EnumFacing.VALUES) {
            BlockPos support = target.offset(side.getOpposite());
            if (!isSolidSupport(support)
                    || BlockUtil.isInteractable(support)
                    || !itemBlock.canPlaceBlockOnSide(mc.theWorld, support, side, mc.thePlayer, stack)) {
                continue;
            }
            Vec3 hitVec = faceCenter(support, side);
            double distance = mc.thePlayer.getPositionEyes(1.0F).distanceTo(hitVec);
            if (distance <= mc.playerController.getBlockReachDistance() && distance < bestDistance) {
                bestDistance = distance;
                best = new Placement(target, support, side, hitVec);
            }
        }
        return best;
    }

    private boolean place(Placement placement) {
        if (!BlockUtil.isReplaceable(placement.target) || intersectsBlockingEntity(placement.target)) {
            return false;
        }
        ItemStack stack = mc.thePlayer.getHeldItem();
        if (!ItemUtil.isBlock(stack)) {
            return false;
        }
        MinecraftAccessor.syncCurrentPlayItem(mc.playerController);
        submittingPlacement = true;
        try {
            boolean placed = mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, stack,
                    placement.support, placement.side, placement.hitVec);
            if (placed) {
                mc.thePlayer.swingItem();
            }
            return placed;
        } finally {
            submittingPlacement = false;
        }
    }

    private int findBlockSlot() {
        int bestSlot = -1;
        int bestCount = 0;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (isPlaceableStack(stack) && stack.stackSize > bestCount) {
                bestSlot = slot;
                bestCount = stack.stackSize;
            }
        }
        return bestSlot;
    }

    private boolean isPlaceableStack(ItemStack stack) {
        return ItemUtil.isBlock(stack)
                && stack.getItem() instanceof ItemBlock
                && stack.stackSize > 0;
    }

    private boolean intersectsBlockingEntity(BlockPos target) {
        AxisAlignedBB targetBox = new AxisAlignedBB(target, target.add(1, 1, 1));
        return !mc.theWorld.getEntitiesWithinAABBExcludingEntity(mc.thePlayer, targetBox).isEmpty()
                || mc.thePlayer.getEntityBoundingBox().intersectsWith(targetBox);
    }

    private boolean isSolidSupport(BlockPos position) {
        Block block = mc.theWorld.getBlockState(position).getBlock();
        return block != null
                && !(block instanceof BlockAir)
                && !(block instanceof BlockLiquid)
                && !block.isReplaceable(mc.theWorld, position)
                && block.getCollisionBoundingBox(mc.theWorld, position,
                mc.theWorld.getBlockState(position)) != null;
    }

    private static Vec3 faceCenter(BlockPos support, EnumFacing side) {
        return new Vec3(
                support.getX() + 0.5D + side.getFrontOffsetX() * 0.5D,
                support.getY() + 0.5D + side.getFrontOffsetY() * 0.5D,
                support.getZ() + 0.5D + side.getFrontOffsetZ() * 0.5D);
    }

    private float[] rotationsTo(Vec3 hitVec) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        double x = hitVec.xCoord - eyes.xCoord;
        double y = hitVec.yCoord - eyes.yCoord;
        double z = hitVec.zCoord - eyes.zCoord;
        double horizontal = Math.sqrt(x * x + z * z);
        return new float[]{
                (float) (Math.toDegrees(Math.atan2(z, x)) - 90.0D),
                MathHelper.clamp_float((float) -Math.toDegrees(Math.atan2(y, horizontal)), -90.0F, 90.0F)
        };
    }

    private void switchSlot(int slot) {
        if (slot < 0 || slot >= 9 || mc.thePlayer.inventory.currentItem == slot) {
            return;
        }
        mc.thePlayer.inventory.currentItem = slot;
        switchingSlot = true;
        try {
            MinecraftAccessor.syncCurrentPlayItem(mc.playerController);
        } finally {
            switchingSlot = false;
        }
    }

    private void restoreSlot() {
        if (mc.thePlayer != null && mc.playerController != null && originalSlot >= 0 && originalSlot < 9) {
            switchSlot(originalSlot);
        }
        originalSlot = -1;
    }

    private static final class Placement {
        private final BlockPos target;
        private final BlockPos support;
        private final EnumFacing side;
        private final Vec3 hitVec;

        private Placement(BlockPos target, BlockPos support, EnumFacing side, Vec3 hitVec) {
            this.target = target;
            this.support = support;
            this.side = side;
            this.hitVec = hitVec;
        }
    }
}
