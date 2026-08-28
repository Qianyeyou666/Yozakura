package gq.yozakura.module.world;

import gq.yozakura.bridge.MinecraftAccessor;
import gq.yozakura.event.bus.EventManager;
import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.event.bus.types.EventType;
import gq.yozakura.event.bus.types.Priority;
import gq.yozakura.event.bridge.HitBlockEvent;
import gq.yozakura.event.bridge.LeftClickMouseEvent;
import gq.yozakura.event.bridge.LivingUpdateEvent;
import gq.yozakura.event.bridge.MoveInputEvent;
import gq.yozakura.event.bridge.Render2DEvent;
import gq.yozakura.event.bridge.RightClickMouseEvent;
import gq.yozakura.event.bridge.SwapItemEvent;
import gq.yozakura.event.bridge.UpdateEvent;
import gq.yozakura.manager.ModuleManager;
import gq.yozakura.manager.RotationState;
import gq.yozakura.engine.font.FontLoaders;
import gq.yozakura.engine.render.glow.GlowProfile;
import gq.yozakura.engine.render.glow.GlowRenderer;
import gq.yozakura.engine.render.ui.RenderServices;
import gq.yozakura.module.ModuleType;
import gq.yozakura.module.movement.LongJump;
import gq.yozakura.module.runtime.Module;
import gq.yozakura.util.module.BlockUtil;
import gq.yozakura.util.module.ItemUtil;
import gq.yozakura.util.module.MoveUtil;
import gq.yozakura.util.module.PacketUtil;
import gq.yozakura.util.module.PlayerUtil;
import gq.yozakura.util.module.RandomUtil;
import gq.yozakura.util.module.RotationUtil;
import gq.yozakura.util.animation.MotionValue;
import gq.yozakura.util.animation.UiClock;
import gq.yozakura.util.render.RenderUtil;
import gq.yozakura.value.properties.BooleanProperty;
import gq.yozakura.value.properties.FloatProperty;
import gq.yozakura.value.properties.IntProperty;
import gq.yozakura.value.properties.ModeProperty;
import net.minecraft.block.Block;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldSettings.GameType;

import java.util.ArrayList;
import java.util.Comparator;

public class Scaffold extends Module {
    private static final double[] placeOffsets = new double[]{
            0.03125D, 0.09375D, 0.15625D, 0.21875D,
            0.28125D, 0.34375D, 0.40625D, 0.46875D,
            0.53125D, 0.59375D, 0.65625D, 0.71875D,
            0.78125D, 0.84375D, 0.90625D, 0.96875D
    };
    private static final float BLOCK_COUNTER_HEIGHT = 19.0F;
    private static final float BLOCK_COUNTER_ICON_SIZE = 14.0F;
    private static final float BLOCK_COUNTER_EPSILON = 0.001F;

    public final ModeProperty mode = new ModeProperty(
            "mode", 1, new String[]{"Normal", "Telly", "Snap"});
    public final ModeProperty rotationMode = new ModeProperty(
            "rotate-mode", 3, new String[]{"None", "Vanilla", "Backwards", "Prediction"});
    public final ModeProperty moveFix = new ModeProperty(
            "move-fix", 1, new String[]{"None", "Silent"});
    public final IntProperty jumpDelay = new IntProperty(
            "jump-delay", 2, 0, 5, () -> this.mode.getValue() == 1);
    public final IntProperty placeDelay = new IntProperty("place-delay", 1, 0, 5);
    public final FloatProperty startRotSpeed = new FloatProperty(
            "start-rotate-speed", 180.0F, 1.0F, 180.0F, () -> this.mode.getValue() == 1);
    public final FloatProperty normalRotSpeed = new FloatProperty(
            "normal-rotate-speed", 180.0F, 1.0F, 180.0F, () -> this.mode.getValue() == 1);
    public final BooleanProperty swing = new BooleanProperty("swing", true);
    public final BooleanProperty itemSpoof = new BooleanProperty("item-spoof", false);
    public final IntProperty blockCounterBackgroundAlpha = new IntProperty(
            "block-counter-background-alpha", 180, 0, 255);
    public final BooleanProperty blockCounterBlur = new BooleanProperty("block-counter-blur", true);
    public final FloatProperty edgeThreshold = new FloatProperty(
            "edge-threshold", 0.15F, 0.01F, 0.5F, () -> this.mode.getValue() == 2);
    public final BooleanProperty ticksLimit = new BooleanProperty(
            "ticks-limit", false, () -> this.mode.getValue() == 2);
    public final IntProperty limitTicks = new IntProperty(
            "limit-ticks", 10, 1, 40,
            () -> this.mode.getValue() == 2 && this.ticksLimit.getValue());
    public final FloatProperty snapForwardSpeed = new FloatProperty(
            "forward-speed", 180.0F, 1.0F, 180.0F, () -> this.mode.getValue() == 2);
    public final FloatProperty snapBackSpeed = new FloatProperty(
            "back-speed", 180.0F, 1.0F, 180.0F, () -> this.mode.getValue() == 2);
    public final BooleanProperty snapRotation = new BooleanProperty(
            "snap-rotation", false, () -> this.mode.getValue() == 2);
    public final BooleanProperty speedLimit = new BooleanProperty(
            "speed-limit", false, () -> this.mode.getValue() == 1);
    public final IntProperty speedLimitTicks = new IntProperty(
            "speed-limit-ticks", 3, 0, 5,
            () -> this.mode.getValue() == 1 && this.speedLimit.getValue());
    public final IntProperty forwardRotationTicks = new IntProperty(
            "forward-rotation-ticks", 1, 1, 5,
            () -> this.mode.getValue() == 1 && this.speedLimit.getValue());

    private int rotationTick;
    private int lastSlot = -1;
    private int blockCount = -1;
    private float yaw = -180.0F;
    private float pitch;
    private boolean canRotate;
    private int tellyJumpDelayTimer;
    private int jumpDelayOverride = -1;
    private boolean wasInAir;
    private int stage;
    private int startY = 256;
    private boolean shouldKeepY;
    private boolean towering;
    private int placeDelayCounter;
    private boolean snapForward = true;
    private int snapForwardTimer;
    private boolean snapLocked;
    private int airTicks;
    private boolean pendingSpeedLimitRot;
    private int forwardRotateTicksLeft;
    private final UiClock blockCounterClock = new UiClock();
    private final MotionValue blockCounterPulse = new MotionValue(0.0F);
    private final ScaffoldBlockCounterMotion blockCounterMotion = new ScaffoldBlockCounterMotion();
    private int displayedBlockCount;
    private int lastObservedBlockCount = -1;
    private ItemStack displayedBlockStack;
    private final BlockCounterExitRenderer blockCounterExitRenderer = new BlockCounterExitRenderer();
    private boolean blockCounterExitRendererRegistered;

    public Scaffold() {
        super("Scaffold", false);
        this.category = ModuleType.World;
        this.Chinese = "自动搭路";
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.mode.getModeString()};
    }

    private boolean shouldStopSprint() {
        return !this.isTowering() && this.stage <= 0 && this.mode.getValue() != 2;
    }

    private boolean canPlace() {
        gq.yozakura.module.Module bedNukerModule = ModuleManager.getModule("BedNuker");
        if (bedNukerModule instanceof BedNuker) {
            BedNuker bedNuker = (BedNuker) bedNukerModule;
            if (bedNuker.isEnabled() && bedNuker.isReady()) {
                return false;
            }
        }
        gq.yozakura.module.Module longJumpModule = ModuleManager.getModule("LongJump");
        if (longJumpModule instanceof LongJump) {
            LongJump longJump = (LongJump) longJumpModule;
            return !longJump.isEnabled() || !longJump.isAutoMode() || longJump.isJumping();
        }
        return true;
    }

    private EnumFacing getBestFacing(BlockPos blockPos, BlockPos targetPos) {
        double bestDistance = 0.0D;
        EnumFacing bestFacing = null;
        for (EnumFacing facing : EnumFacing.VALUES) {
            if (facing == EnumFacing.DOWN) {
                continue;
            }
            BlockPos placedPos = blockPos.offset(facing);
            if (placedPos.getY() > targetPos.getY()) {
                continue;
            }
            double distance = placedPos.distanceSqToCenter(
                    targetPos.getX() + 0.5D,
                    targetPos.getY() + 0.5D,
                    targetPos.getZ() + 0.5D);
            if (bestFacing == null || distance < bestDistance
                    || distance == bestDistance && facing == EnumFacing.UP) {
                bestDistance = distance;
                bestFacing = facing;
            }
        }
        return bestFacing;
    }

    private BlockData getBlockData() {
        int playerY = MathHelper.floor_double(mc.thePlayer.posY);
        BlockPos targetPos = new BlockPos(
                MathHelper.floor_double(mc.thePlayer.posX),
                (this.stage != 0 && !this.shouldKeepY
                        ? Math.min(playerY, this.startY) : playerY) - 1,
                MathHelper.floor_double(mc.thePlayer.posZ));
        return this.getBlockData(targetPos);
    }

    private BlockData getBlockData(BlockPos targetPos) {
        if (!BlockUtil.isReplaceable(targetPos)) {
            return null;
        }
        ArrayList<BlockPos> positions = new ArrayList<BlockPos>();
        for (int x = -4; x <= 4; x++) {
            for (int y = -4; y <= 0; y++) {
                for (int z = -4; z <= 4; z++) {
                    BlockPos pos = targetPos.add(x, y, z);
                    if (BlockUtil.isReplaceable(pos)
                            || BlockUtil.isInteractable(pos)
                            || mc.thePlayer.getDistance(
                            pos.getX() + 0.5D,
                            pos.getY() + 0.5D,
                            pos.getZ() + 0.5D)
                            > mc.playerController.getBlockReachDistance()) {
                        continue;
                    }
                    for (EnumFacing facing : EnumFacing.VALUES) {
                        if (facing != EnumFacing.DOWN
                                && BlockUtil.isReplaceable(pos.offset(facing))) {
                            positions.add(pos);
                        }
                    }
                }
            }
        }
        if (positions.isEmpty()) {
            return null;
        }
        positions.sort(Comparator.comparingDouble(pos -> pos.distanceSqToCenter(
                targetPos.getX() + 0.5D,
                targetPos.getY() + 0.5D,
                targetPos.getZ() + 0.5D)));
        BlockPos blockPos = positions.get(0);
        EnumFacing facing = this.getBestFacing(blockPos, targetPos);
        return facing == null ? null : new BlockData(blockPos, facing);
    }

    private boolean place(BlockPos blockPos, EnumFacing facing, Vec3 hitVec) {
        if (!ItemUtil.isHoldingBlock() || this.blockCount <= 0) {
            return false;
        }
        if (!mc.playerController.onPlayerRightClick(
                mc.thePlayer,
                mc.theWorld,
                mc.thePlayer.inventory.getCurrentItem(),
                blockPos,
                facing,
                hitVec)) {
            return false;
        }
        if (mc.playerController.getCurrentGameType() != GameType.CREATIVE) {
            this.blockCount--;
        }
        if (this.swing.getValue()) {
            mc.thePlayer.swingItem();
        } else {
            PacketUtil.sendPacket(new C0APacketAnimation());
        }
        return true;
    }

    private float getCurrentYaw() {
        return MoveUtil.adjustYaw(
                mc.thePlayer.rotationYaw,
                MoveUtil.getForwardValue(),
                MoveUtil.getLeftValue());
    }

    private boolean isDiagonal(float currentYaw) {
        float absoluteYaw = Math.abs(currentYaw % 90.0F);
        return absoluteYaw > 20.0F && absoluteYaw < 70.0F;
    }

    private boolean isTowering() {
        if (!MoveUtil.isForwardPressed() || PlayerUtil.isAirAbove()) {
            return false;
        }
        if (mc.thePlayer.onGround
                && (this.stage > 0 || mc.gameSettings.keyBindJump.isKeyDown())) {
            return true;
        }
        return this.tellyJumpDelayTimer > 0;
    }

    private boolean isOnEdge() {
        if (!mc.thePlayer.onGround) {
            return true;
        }
        int baseX = MathHelper.floor_double(mc.thePlayer.posX);
        int baseY = MathHelper.floor_double(mc.thePlayer.posY) - 1;
        int baseZ = MathHelper.floor_double(mc.thePlayer.posZ);
        if (BlockUtil.isReplaceable(new BlockPos(baseX, baseY, baseZ))) {
            return true;
        }
        double threshold = this.edgeThreshold.getValue();
        double xOffset = mc.thePlayer.posX - baseX;
        double zOffset = mc.thePlayer.posZ - baseZ;
        if (xOffset < threshold || xOffset > 1.0D - threshold
                || zOffset < threshold || zOffset > 1.0D - threshold) {
            int checkX = baseX + (xOffset < threshold ? -1
                    : xOffset > 1.0D - threshold ? 1 : 0);
            int checkZ = baseZ + (zOffset < threshold ? -1
                    : zOffset > 1.0D - threshold ? 1 : 0);
            if ((checkX != baseX || checkZ != baseZ)
                    && BlockUtil.isReplaceable(new BlockPos(checkX, baseY, checkZ))) {
                return true;
            }
        }
        return false;
    }

    private boolean isPlacementRayTrace(MovingObjectPosition trace, BlockData data) {
        return trace != null
                && trace.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && trace.getBlockPos().equals(data.blockPos())
                && trace.sideHit == data.facing();
    }

    private void selectScaffoldBlock() {
        ItemStack held = mc.thePlayer.getHeldItem();
        int heldCount = ItemUtil.isBlock(held) ? held.stackSize : 0;
        this.blockCount = Math.min(this.blockCount, heldCount);
        if (this.blockCount > 0) {
            return;
        }
        int slot = mc.thePlayer.inventory.currentItem;
        if (this.blockCount == 0) {
            slot--;
        }
        for (int i = slot; i > slot - 9; i--) {
            int hotbarSlot = (i % 9 + 9) % 9;
            ItemStack candidate = mc.thePlayer.inventory.getStackInSlot(hotbarSlot);
            if (ItemUtil.isBlock(candidate)) {
                mc.thePlayer.inventory.currentItem = hotbarSlot;
                this.blockCount = candidate.stackSize;
                return;
            }
        }
    }

    @EventTarget(Priority.HIGH)
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) {
            return;
        }
        boolean tellyMode = this.mode.getValue() == 1;
        if (this.rotationTick > 0) {
            this.rotationTick--;
        }
        if (this.forwardRotateTicksLeft > 0) {
            this.forwardRotateTicksLeft--;
        }
        if (mc.thePlayer.onGround) {
            if (this.stage > 0) {
                this.stage--;
            }
            if (this.stage < 0) {
                this.stage++;
            }
            this.startY = this.shouldKeepY
                    ? this.startY : MathHelper.floor_double(mc.thePlayer.posY);
            this.shouldKeepY = false;
            this.towering = false;
            if (this.wasInAir) {
                this.tellyJumpDelayTimer = tellyMode
                        ? (this.jumpDelayOverride >= 0
                        ? this.jumpDelayOverride : this.jumpDelay.getValue())
                        : 0;
                this.wasInAir = false;
            }
            if (this.tellyJumpDelayTimer > 0) {
                this.tellyJumpDelayTimer--;
            }
            if (this.speedLimit.getValue()) {
                this.pendingSpeedLimitRot = false;
                this.airTicks = 0;
            }
        } else {
            if (this.speedLimit.getValue()) {
                this.airTicks++;
            }
            this.wasInAir = true;
        }
        if (tellyMode && mc.thePlayer.onGround && MoveUtil.isForwardPressed()
                && !mc.gameSettings.keyBindJump.isKeyDown() && this.stage == 0) {
            this.stage = 1;
        }
        if (tellyMode) {
            this.jumpDelayOverride = mc.gameSettings.keyBindJump.isKeyDown() ? 2 : -1;
        } else {
            this.jumpDelayOverride = -1;
            this.tellyJumpDelayTimer = 0;
        }
        if (!this.canPlace()) {
            return;
        }
        this.selectScaffoldBlock();
        if (this.mode.getValue() == 2) {
            if (this.ticksLimit.getValue()) {
                boolean canForward = mc.thePlayer.onGround && !this.isOnEdge();
                if (!canForward) {
                    this.snapForward = false;
                    this.snapForwardTimer = 0;
                    this.snapLocked = false;
                } else if (this.snapLocked) {
                    this.snapForward = false;
                } else if (!this.snapForward) {
                    this.snapForward = true;
                    this.snapForwardTimer = 1;
                } else if (++this.snapForwardTimer >= this.limitTicks.getValue()) {
                    this.snapForward = false;
                    this.snapLocked = true;
                    this.snapForwardTimer = 0;
                }
            } else {
                this.snapForward = mc.thePlayer.onGround && !this.isOnEdge();
            }
            if (this.snapForward) {
                this.yaw = RotationUtil.quantizeAngle(this.getCurrentYaw());
                this.pitch = 80.0F;
                this.canRotate = true;
            } else if (!this.snapRotation.getValue()) {
                this.yaw = RotationUtil.quantizeAngle(this.getCurrentYaw() + 180.0F);
                this.pitch = 85.0F;
                this.canRotate = true;
            }
        }

        float currentYaw = this.getCurrentYaw();
        float backwardsYaw = RotationUtil.wrapAngleDiff(currentYaw - 180.0F, event.getYaw());
        float diagonalYaw = this.isDiagonal(currentYaw)
                ? backwardsYaw
                : RotationUtil.wrapAngleDiff(currentYaw - 135.0F
                * ((currentYaw + 180.0F) % 90.0F < 45.0F ? 1.0F : -1.0F),
                event.getYaw());
        if (!this.canRotate) {
            switch (this.rotationMode.getValue()) {
                case 1:
                    this.yaw = RotationUtil.quantizeAngle(diagonalYaw);
                    if (this.pitch == 0.0F) {
                        this.pitch = RotationUtil.quantizeAngle(85.0F);
                    }
                    break;
                case 2:
                    this.yaw = RotationUtil.quantizeAngle(backwardsYaw);
                    if (this.pitch == 0.0F) {
                        this.pitch = RotationUtil.quantizeAngle(85.0F);
                    }
                    break;
                case 3:
                    this.yaw = RotationUtil.quantizeAngle(diagonalYaw);
                    if (this.pitch == 0.0F) {
                        this.pitch = RotationUtil.quantizeAngle(85.0F);
                    }
                    break;
                default:
                    break;
            }
        }

        BlockData blockData = this.getBlockData();
        if (this.mode.getValue() == 2 && this.snapForward) {
            blockData = null;
        }
        Vec3 hitVec = null;
        if (blockData != null) {
            if (this.rotationMode.getValue() == 3) {
                double[] offsets = {0.1D, 0.3D, 0.5D, 0.7D, 0.9D};
                double[] x = offsets;
                double[] y = offsets;
                double[] z = offsets;
                switch (blockData.facing()) {
                    case NORTH:
                        z = new double[]{0.02D};
                        break;
                    case EAST:
                        x = new double[]{0.98D};
                        break;
                    case SOUTH:
                        z = new double[]{0.98D};
                        break;
                    case WEST:
                        x = new double[]{0.02D};
                        break;
                    case DOWN:
                        y = new double[]{0.02D};
                        break;
                    case UP:
                        y = new double[]{0.98D};
                        break;
                    default:
                        break;
                }
                float bestYaw = -180.0F;
                float bestPitch = 0.0F;
                double bestDist = Double.MAX_VALUE;
                Vec3 bestHitVec = null;
                for (double dx : x) {
                    for (double dy : y) {
                        for (double dz : z) {
                            double targetX = blockData.blockPos().getX() + dx;
                            double targetY = blockData.blockPos().getY() + dy;
                            double targetZ = blockData.blockPos().getZ() + dz;
                            float[] rot = RotationUtil.getRotations(targetX, targetY, targetZ);
                            MovingObjectPosition mop = RotationUtil.rayTrace(rot[0], rot[1],
                                    mc.playerController.getBlockReachDistance(), 1.0F);
                            if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                                    && mop.getBlockPos().equals(blockData.blockPos())
                                    && mop.sideHit == blockData.facing()) {
                                float yawDiff = Math.abs(MathHelper.wrapAngleTo180_float(rot[0] - this.yaw));
                                float pitchDiff = Math.abs(rot[1] - this.pitch);
                                double dist = Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
                                if (dist < bestDist) {
                                    bestDist = dist;
                                    bestYaw = rot[0];
                                    bestPitch = rot[1];
                                    bestHitVec = mop.hitVec;
                                }
                            }
                        }
                    }
                }
                if (bestYaw != -180.0F || bestPitch != 0.0F) {
                    bestYaw += RandomUtil.nextFloat(-0.5F, 0.5F);
                    bestPitch += RandomUtil.nextFloat(-0.3F, 0.3F);
                    this.yaw = bestYaw;
                    this.pitch = bestPitch;
                    this.canRotate = true;
                    hitVec = bestHitVec;
                }
            } else {
                double[] x = placeOffsets;
                double[] y = placeOffsets;
                double[] z = placeOffsets;
                switch (blockData.facing()) {
                    case NORTH:
                        z = new double[]{0.0D};
                        break;
                    case EAST:
                        x = new double[]{1.0D};
                        break;
                    case SOUTH:
                        z = new double[]{1.0D};
                        break;
                    case WEST:
                        x = new double[]{0.0D};
                        break;
                    case DOWN:
                        y = new double[]{0.0D};
                        break;
                    case UP:
                        y = new double[]{1.0D};
                        break;
                    default:
                        break;
                }
                float bestYaw = -180.0F;
                float bestPitch = 0.0F;
                float bestDiff = 0.0F;
                for (double dx : x) {
                    for (double dy : y) {
                        for (double dz : z) {
                            double relX = blockData.blockPos().getX() + dx - mc.thePlayer.posX;
                            double relY = blockData.blockPos().getY() + dy - mc.thePlayer.posY
                                    - mc.thePlayer.getEyeHeight();
                            double relZ = blockData.blockPos().getZ() + dz - mc.thePlayer.posZ;
                            float baseYaw = RotationUtil.wrapAngleDiff(this.yaw, event.getYaw());
                            float[] rotations = RotationUtil.getRotationsTo(
                                    relX, relY, relZ, baseYaw, this.pitch);
                            MovingObjectPosition mop = RotationUtil.rayTrace(rotations[0], rotations[1],
                                    mc.playerController.getBlockReachDistance(), 1.0F);
                            if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                                    && mop.getBlockPos().equals(blockData.blockPos())
                                    && mop.sideHit == blockData.facing()) {
                                float totalDiff = Math.abs(rotations[0] - baseYaw)
                                        + Math.abs(rotations[1] - this.pitch);
                                if (bestYaw == -180.0F || totalDiff < bestDiff) {
                                    bestYaw = rotations[0];
                                    bestPitch = rotations[1];
                                    bestDiff = totalDiff;
                                    hitVec = mop.hitVec;
                                }
                            }
                        }
                    }
                }
                if (bestYaw != -180.0F || bestPitch != 0.0F) {
                    this.yaw = bestYaw;
                    this.pitch = bestPitch;
                    this.canRotate = true;
                }
            }
        }

        if (this.canRotate && MoveUtil.isForwardPressed()
                && Math.abs(MathHelper.wrapAngleTo180_float(backwardsYaw - this.yaw)) < 90.0F
                && this.rotationMode.getValue() == 2) {
            this.yaw = RotationUtil.quantizeAngle(backwardsYaw);
        }

        float targetYaw = this.yaw;
        float targetPitch = this.pitch;
        if (tellyMode && this.speedLimit.getValue() && this.forwardRotateTicksLeft > 0) {
            float yawDelta = MathHelper.wrapAngleTo180_float(
                    mc.thePlayer.rotationYaw - event.getYaw());
            targetYaw = RotationUtil.quantizeAngle(event.getYaw()
                    + yawDelta * RandomUtil.nextFloat(0.98F, 0.99F));
            targetPitch = RotationUtil.quantizeAngle(RandomUtil.nextFloat(30.0F, 80.0F));
            this.rotationTick = 0;
        } else if (this.towering
                && (mc.thePlayer.motionY > 0.0D
                || mc.thePlayer.posY > this.startY + 1.0D)) {
            float yawDifference = MathHelper.wrapAngleTo180_float(this.yaw - event.getYaw());
            float tolerance = this.rotationTick >= 2
                    ? this.startRotSpeed.getValue() : this.normalRotSpeed.getValue();
            if (Math.abs(yawDifference) > tolerance) {
                targetYaw = RotationUtil.quantizeAngle(event.getYaw()
                        + RotationUtil.clampAngle(yawDifference, tolerance));
                this.rotationTick = Math.max(this.rotationTick, 1);
            }
        }
        if (tellyMode && this.isTowering() && this.tellyJumpDelayTimer <= 0
                && this.forwardRotateTicksLeft <= 0) {
            if (!this.speedLimit.getValue()) {
                float yawDelta = MathHelper.wrapAngleTo180_float(
                        mc.thePlayer.rotationYaw - event.getYaw());
                targetYaw = RotationUtil.quantizeAngle(event.getYaw()
                        + yawDelta * RandomUtil.nextFloat(0.98F, 0.99F));
                targetPitch = RotationUtil.quantizeAngle(RandomUtil.nextFloat(30.0F, 80.0F));
                this.rotationTick = 3;
                this.towering = true;
            } else {
                this.pendingSpeedLimitRot = true;
                this.airTicks = 0;
            }
        } else if (tellyMode && this.tellyJumpDelayTimer > 0) {
            targetYaw = this.yaw != -180.0F ? this.yaw
                    : RotationUtil.quantizeAngle(MathHelper.wrapAngleTo180_float(
                    mc.thePlayer.rotationYaw - event.getYaw()) + event.getYaw());
            targetPitch = Math.abs(this.pitch) > 10.0F ? this.pitch : 60.0F;
        }
        if (this.speedLimit.getValue() && this.pendingSpeedLimitRot
                && !mc.thePlayer.onGround
                && this.airTicks >= this.speedLimitTicks.getValue()) {
            float yawDelta = MathHelper.wrapAngleTo180_float(
                    mc.thePlayer.rotationYaw - event.getYaw());
            targetYaw = RotationUtil.quantizeAngle(event.getYaw()
                    + yawDelta * RandomUtil.nextFloat(0.98F, 0.99F));
            targetPitch = RotationUtil.quantizeAngle(RandomUtil.nextFloat(30.0F, 80.0F));
            this.forwardRotateTicksLeft = this.forwardRotationTicks.getValue();
            this.rotationTick = 0;
            this.towering = true;
            this.pendingSpeedLimitRot = false;
            this.airTicks = 0;
        }
        if (this.rotationMode.getValue() != 0 && this.mode.getValue() != 2) {
            event.setRotation(targetYaw, targetPitch, 3);
            if (this.moveFix.getValue() == 1) {
                event.setPervRotation(targetYaw, 3, false);
            }
        } else if (this.mode.getValue() == 2 && this.rotationMode.getValue() != 0
                && this.canRotate) {
            float yawDifference = MathHelper.wrapAngleTo180_float(targetYaw - event.getYaw());
            float tolerance = this.snapForward
                    ? this.snapForwardSpeed.getValue() : this.snapBackSpeed.getValue();
            if (Math.abs(yawDifference) > tolerance) {
                targetYaw = RotationUtil.quantizeAngle(event.getYaw()
                        + RotationUtil.clampAngle(yawDifference, tolerance));
                this.rotationTick = Math.max(this.rotationTick, 1);
            }
            event.setRotation(targetYaw, targetPitch, 3);
            if (this.moveFix.getValue() == 1) {
                event.setPervRotation(targetYaw, 3, false);
            }
        }

        // Place with the same rotation this PRE publishes; the C03 that follows
        // must agree with the C08 target so post-flying rotation checks match.
        this.yaw = targetYaw;
        this.pitch = targetPitch;
        if (blockData != null && hitVec != null && this.rotationTick <= 0) {
            if (this.placeDelayCounter > 0) {
                this.placeDelayCounter--;
            } else {
                MovingObjectPosition finalCheck = RotationUtil.rayTrace(
                        targetYaw, targetPitch,
                        mc.playerController.getBlockReachDistance(), 1.0F);
                if (this.isPlacementRayTrace(finalCheck, blockData)) {
                    this.place(blockData.blockPos(), blockData.facing(), finalCheck.hitVec);
                    this.placeDelayCounter = this.placeDelay.getValue();
                } else if (this.canRotate) {
                    this.place(blockData.blockPos(), blockData.facing(), hitVec);
                    this.placeDelayCounter = this.placeDelay.getValue();
                }
            }
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (!this.isEnabled()) {
            return;
        }
        boolean tellyMode = this.mode.getValue() == 1;
        if (this.moveFix.getValue() == 1 && RotationState.isActived()
                && RotationState.getPriority() == 3.0F && MoveUtil.isForwardPressed()) {
            MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
        }
        if (tellyMode && mc.thePlayer.onGround
                && this.stage > 0 && MoveUtil.isForwardPressed()
                && this.tellyJumpDelayTimer <= 0) {
            mc.thePlayer.movementInput.jump = true;
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (this.isEnabled() && this.shouldStopSprint()) {
            mc.thePlayer.setSprinting(false);
        }
    }

    private int countHotbarBlocks() {
        int total = 0;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (this.isUsableScaffoldStack(stack)) {
                total += stack.stackSize;
            }
        }
        return total;
    }

    private ItemStack findDisplayedBlockStack() {
        ItemStack held = mc.thePlayer.getHeldItem();
        if (this.isUsableScaffoldStack(held)) {
            return held;
        }
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (this.isUsableScaffoldStack(stack)) {
                return stack;
            }
        }
        return null;
    }

    private boolean isUsableScaffoldStack(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0 || !(stack.getItem() instanceof ItemBlock)) {
            return false;
        }
        Block block = ((ItemBlock) stack.getItem()).getBlock();
        return !BlockUtil.isInteractable(block) && BlockUtil.isSolid(block);
    }

    private void updateBlockCounter(int count, ItemStack stack, float deltaSeconds) {
        boolean visible = this.isEnabled() && count > 0 && stack != null;
        if (visible) {
            this.displayedBlockCount = count;
            this.displayedBlockStack = stack.copy();
            this.displayedBlockStack.stackSize = 1;
        }
        this.blockCounterMotion.setVisible(visible, System.currentTimeMillis());
        if (visible && this.lastObservedBlockCount >= 0 && count != this.lastObservedBlockCount) {
            this.blockCounterPulse.snapTo(1.0F);
            this.blockCounterPulse.setTarget(0.0F);
        }
        this.lastObservedBlockCount = visible ? count : -1;
        this.blockCounterPulse.updateSpring(deltaSeconds, 0.28F);
        if (!visible && !this.blockCounterMotion.snapshot(System.currentTimeMillis()).isRetained()) {
            this.clearBlockCounterRetainedState();
        }
    }

    private boolean isBlockCounterExiting() {
        return this.blockCounterMotion.snapshot(System.currentTimeMillis()).isRetained();
    }

    private void clearBlockCounterRetainedState() {
        this.displayedBlockCount = 0;
        this.displayedBlockStack = null;
        this.lastObservedBlockCount = -1;
        this.blockCounterPulse.snapTo(0.0F);
    }

    private void renderDisplayedBlockStack(float x, float y, float opacity) {
        if (this.displayedBlockStack == null) {
            return;
        }
        GlStateManager.pushMatrix();
        try {
            float scale = BLOCK_COUNTER_ICON_SIZE / 16.0F;
            GlStateManager.translate(x, y, 0.0F);
            GlStateManager.scale(scale, scale, 1.0F);
            GlStateManager.color(1.0F, 1.0F, 1.0F, opacity);
            GlStateManager.depthMask(true);
            GlStateManager.enableAlpha();
            GlStateManager.enableDepth();
            GlStateManager.enableRescaleNormal();
            RenderHelper.enableGUIStandardItemLighting();
            mc.getRenderItem().renderItemAndEffectIntoGUI(this.displayedBlockStack, 0, 0);
        } finally {
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableRescaleNormal();
            GlStateManager.disableDepth();
            GlStateManager.depthMask(true);
            GlStateManager.enableTexture2D();
            GlStateManager.enableAlpha();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }
    }

    private void renderBlockCounterFrame() {
        if (mc.thePlayer == null) {
            return;
        }
        float deltaSeconds = this.blockCounterClock.tick(System.nanoTime());
        int count = this.isEnabled() ? this.countHotbarBlocks() : 0;
        ItemStack stack = this.isEnabled() ? this.findDisplayedBlockStack() : null;
        this.updateBlockCounter(count, stack, deltaSeconds);
        ScaffoldBlockCounterMotion.Snapshot motion =
                this.blockCounterMotion.snapshot(System.currentTimeMillis());
        if (!motion.isRetained() || motion.getOpacity() <= BLOCK_COUNTER_EPSILON
                || this.displayedBlockStack == null) {
            if (!this.isEnabled()) {
                this.unregisterBlockCounterExitRenderer();
            }
            return;
        }

        gq.yozakura.engine.font.CFontRenderer font = FontLoaders.circular(14);
        String text = this.displayedBlockCount + (this.displayedBlockCount == 1 ? " block" : " blocks");
        float textWidth = font.getStringWidth(text);
        float width = 6.0F + BLOCK_COUNTER_ICON_SIZE + 4.0F + textWidth + 7.0F;
        ScaledResolution resolution = new ScaledResolution(mc);
        float centerX = resolution.getScaledWidth() * 0.5F;
        float baseY = resolution.getScaledHeight() * 0.5F + 20.0F;
        float y = baseY;
        float x = centerX - width * 0.5F;
        float panelScale = motion.getScale();
        int opacity = Math.max(0, Math.min(255, Math.round(255.0F * motion.getOpacity())));
        int fill = withAlpha(0xFF1A1A20,
                Math.round(this.blockCounterBackgroundAlpha.getValue() * motion.getOpacity()));
        int border = withAlpha(0xFFFFFFFF, Math.round(48.0F * motion.getOpacity()));
        int primary = withAlpha(0xFFF4F4F7, opacity);
        float textY = y + (BLOCK_COUNTER_HEIGHT - font.getHeight()) * 0.5F + 0.5F;
        float textX = x + 6.0F + BLOCK_COUNTER_ICON_SIZE + 4.0F;

        if (this.blockCounterBlur.getValue()) {
            this.queueBlockCounterBloomMask(x, y, width, BLOCK_COUNTER_HEIGHT,
                    panelScale, motion.getOpacity());
        }

        GlStateManager.pushMatrix();
        try {
            GlStateManager.enableBlend();
            GlStateManager.enableAlpha();
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            RenderUtil.scaleStart(centerX, y + BLOCK_COUNTER_HEIGHT * 0.5F, panelScale);
            if (this.blockCounterBlur.getValue()) {
                RenderServices.blur().glass(x, y, x + width, y + BLOCK_COUNTER_HEIGHT,
                        5.0F, 0.6F, fill, border);
            } else {
                RenderServices.shapes().roundedBorder(x, y, x + width, y + BLOCK_COUNTER_HEIGHT,
                        5.0F, 0.6F, fill, border);
            }
            this.renderDisplayedBlockStack(x + 6.0F, y + 2.5F, motion.getOpacity());
            font.drawString(text, textX, textY, primary);
            RenderUtil.scaleEnd();
        } finally {
            GlStateManager.depthMask(true);
            GlStateManager.enableDepth();
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.enableAlpha();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }
    }

    /** Queues the Nymph TargetHUD-style animated mask for the HUD bloom pass. */
    private void queueBlockCounterBloomMask(float x, float y, float width, float height,
                                            float scale, float opacity) {
        float left = x + width * (1.0F - scale) * 0.5F;
        float top = y + height * (1.0F - scale) * 0.5F;
        float right = left + width * scale;
        float bottom = top + height * scale;
        int maskAlpha = Math.max(0, Math.min(255, Math.round(255.0F * opacity)));
        if (right <= left || bottom <= top || maskAlpha <= 0) {
            return;
        }
        GlowRenderer shadows = RenderServices.shadows();
        boolean isolatedFrame = !shadows.isFrameOpen();
        if (isolatedFrame) {
            shadows.beginFrame();
            shadows.beginCommandSnapshotCache();
        }
        try {
            shadows.queueRoundedRect(left, top, right, bottom, 5.0F * scale,
                    withAlpha(0xFF000000, maskAlpha), 1.0F, GlowProfile.SHADOW);
        } finally {
            if (isolatedFrame) {
                shadows.flush();
            }
        }
    }

    private static int withAlpha(int color, int alpha) {
        return color & 0x00FFFFFF | Math.max(0, Math.min(255, alpha)) << 24;
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (this.isEnabled()) {
            this.renderBlockCounterFrame();
        }
    }

    @EventTarget
    public void onLeftClick(LeftClickMouseEvent event) {
        if (this.isEnabled()) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onRightClick(RightClickMouseEvent event) {
        if (this.isEnabled()) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onHitBlock(HitBlockEvent event) {
        if (this.isEnabled()) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onSwap(SwapItemEvent event) {
        if (this.isEnabled()) {
            this.lastSlot = event.setSlot(this.lastSlot);
            event.setCancelled(true);
        }
    }

    private void registerBlockCounterExitRenderer() {
        if (!this.blockCounterExitRendererRegistered) {
            EventManager.register(this.blockCounterExitRenderer);
            this.blockCounterExitRendererRegistered = true;
        }
    }

    private void unregisterBlockCounterExitRenderer() {
        if (this.blockCounterExitRendererRegistered) {
            EventManager.unregister(this.blockCounterExitRenderer);
            this.blockCounterExitRendererRegistered = false;
            this.clearBlockCounterRetainedState();
        }
    }

    private final class BlockCounterExitRenderer {
        @EventTarget
        public void onRender(Render2DEvent event) {
            if (!Scaffold.this.isEnabled()) {
                Scaffold.this.renderBlockCounterFrame();
            }
        }
    }

    @Override
    public void onEnabled() {
        this.unregisterBlockCounterExitRenderer();
        this.lastSlot = mc.thePlayer != null ? mc.thePlayer.inventory.currentItem : -1;
        this.blockCount = -1;
        this.rotationTick = 3;
        this.yaw = -180.0F;
        this.pitch = 0.0F;
        this.canRotate = false;
        this.towering = false;
        this.tellyJumpDelayTimer = 0;
        this.jumpDelayOverride = -1;
        this.wasInAir = false;
        this.stage = 0;
        this.startY = mc.thePlayer != null
                ? MathHelper.floor_double(mc.thePlayer.posY) : 256;
        this.shouldKeepY = false;
        this.placeDelayCounter = 0;
        this.snapForward = true;
        this.snapForwardTimer = 0;
        this.snapLocked = false;
        this.airTicks = 0;
        this.pendingSpeedLimitRot = false;
        this.forwardRotateTicksLeft = 0;
        this.blockCounterClock.reset();
        this.blockCounterMotion.reset();
        this.blockCounterPulse.snapTo(0.0F);
        this.displayedBlockCount = 0;
        this.displayedBlockStack = null;
        this.lastObservedBlockCount = -1;
    }

    @Override
    public void onDisabled() {
        this.blockCounterMotion.setVisible(false, System.currentTimeMillis());
        if (this.isBlockCounterExiting()) {
            this.registerBlockCounterExitRenderer();
        }
        if (mc.thePlayer != null && this.lastSlot != -1) {
            mc.thePlayer.inventory.currentItem = this.lastSlot;
            MinecraftAccessor.syncCurrentPlayItem(mc.playerController);
        }
    }

    public int getSlot() {
        return this.lastSlot;
    }

    public static final class BlockData {
        private final BlockPos blockPos;
        private final EnumFacing facing;

        public BlockData(BlockPos blockPos, EnumFacing facing) {
            this.blockPos = blockPos;
            this.facing = facing;
        }

        public BlockPos blockPos() {
            return this.blockPos;
        }

        public EnumFacing facing() {
            return this.facing;
        }
    }
}
