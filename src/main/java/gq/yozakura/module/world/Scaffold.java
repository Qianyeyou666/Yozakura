package gq.yozakura.module.world;

import gq.yozakura.engine.font.CFontRenderer;
import gq.yozakura.engine.font.FontLoaders;
import gq.yozakura.engine.render.ui.LiquidGlassSettings;
import gq.yozakura.engine.render.ui.RenderServices;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.potion.Potion;
import net.minecraft.util.*;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.world.WorldSettings.GameType;
import gq.yozakura.module.ModuleType;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import gq.yozakura.bridge.MinecraftAccessor;
import gq.yozakura.runtime.YozakuraRuntime;
import gq.yozakura.event.bus.EventManager;
import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.event.bus.types.EventType;
import gq.yozakura.event.bus.types.Priority;
import gq.yozakura.event.bridge.*;
import gq.yozakura.manager.RotationDebug;
import gq.yozakura.manager.RotationState;
import gq.yozakura.manager.VisualRotationState;
import gq.yozakura.manager.RotationCleanup;
import gq.yozakura.module.runtime.Module;
import gq.yozakura.module.world.BedNuker;
import gq.yozakura.module.movement.LongJump;
import gq.yozakura.module.render.runtime.HUD;
import gq.yozakura.value.properties.BooleanProperty;
import gq.yozakura.value.properties.FloatProperty;
import gq.yozakura.value.properties.ModeProperty;
import gq.yozakura.value.properties.PercentProperty;
import gq.yozakura.util.module.*;

import java.util.ArrayList;
import java.util.Comparator;

public class Scaffold extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int SAKURA = 0xFFFFA8C8;
    private static final int SAKURA_STRONG = 0xFFFF76AA;
    private static final int SAKURA_TEXT = 0xFFFDF3F8;
    private static final int SAKURA_MUTED = 0xFFD9B7C8;
    private static final int SAKURA_GLASS = 0xFF09070C;
    private static final int SAKURA_BORDER = 0xFFFFC4DB;
    private static final float[][] SAKURA_PETAL_POINTS = new float[][]{
            {0.00F, -0.18F}, {-0.30F, -0.07F}, {-0.64F, 0.25F}, {-0.66F, 0.62F},
            {-0.36F, 0.94F}, {-0.10F, 0.82F}, {0.00F, 0.74F}, {0.10F, 0.82F},
            {0.36F, 0.94F}, {0.66F, 0.62F}, {0.64F, 0.25F}, {0.30F, -0.07F},
            {0.00F, -0.18F}
    };
    private static final LiquidGlassSettings BLOCK_COUNTER_GLASS = LiquidGlassSettings.defaults()
            .withBlurRadius(16.0f)
            .withBlurDownscale(0.90f)
            .withNoise(0.015f)
            .withRefractionScale(1.10f)
            .withGlow(0.58f, 0.06f, 0.08f, 0.0f)
            .withHighlight(1.04f);
    private static final double[] placeOffsets = new double[]{
            0.03125,
            0.09375,
            0.15625,
            0.21875,
            0.28125,
            0.34375,
            0.40625,
            0.46875,
            0.53125,
            0.59375,
            0.65625,
            0.71875,
            0.78125,
            0.84375,
            0.90625,
            0.96875
    };
    private final float[] lastErrors = new float[20];
    private int errorIndex = 0;
    public final ModeProperty rotationMode = new ModeProperty("rotations", 5, new String[]{"None", "Vanilla", "BackWards", "Strafe", "Test", "Prediction"});
    public final PercentProperty rotationSmoothing = new PercentProperty("rotation-smoothing", 35, () -> this.rotationMode.getValue() != 0);
    public final ModeProperty moveFix = new ModeProperty("move-fix", 1, new String[]{"NONE", "SILENT"});
    public final ModeProperty sprintMode = new ModeProperty("sprint", 0, new String[]{"NONE", "VANILLA"});
    public final PercentProperty groundMotion = new PercentProperty("ground-motion", 100);
    public final PercentProperty airMotion = new PercentProperty("air-motion", 100);
    public final PercentProperty speedMotion = new PercentProperty("speed-motion", 100);
    public final ModeProperty tower = new ModeProperty("tower", 0, new String[]{"NONE", "VANILLA", "EXTRA", "TELLY"});
    public final ModeProperty keepY = new ModeProperty("keep-y", 0, new String[]{"NONE", "VANILLA", "EXTRA", "TELLY"});
    public final FloatProperty tellystartrotationminspeed = new FloatProperty("telly-start-rotation-min-speed", 90.0F, 1.0F, 180.0F, () -> this.keepY.getValue() == 3);
    public final FloatProperty tellystartrotationmaxspeed = new FloatProperty("telly-start-rotation-max-speed", 95.0F, 1.0F, 180.0F, () -> this.keepY.getValue() == 3);
    public final FloatProperty tellynormalrotationminspeed = new FloatProperty("telly-normal-rotation-min-speed", 30.0F, 1.0F, 180.0F, () -> this.keepY.getValue() == 3);
    public final FloatProperty tellynormalrotationmaxspeed = new FloatProperty("telly-normal-rotation-max-speed", 35.0F, 1.0F, 180.0F, () -> this.keepY.getValue() == 3);
    public final BooleanProperty keepYonPress = new BooleanProperty("keep-y-on-press", false, () -> this.keepY.getValue() != 0);
    public final BooleanProperty multiplace = new BooleanProperty("multi-place", false);
    public final BooleanProperty safeWalk = new BooleanProperty("safe-walk", true);
    public final BooleanProperty swing = new BooleanProperty("swing", true);
    public final BooleanProperty itemSpoof = new BooleanProperty("item-spoof", false);
    public final BooleanProperty blockCounter = new BooleanProperty("block-counter", true);
    public final BooleanProperty rotationDebug = new BooleanProperty("rotation-debug", false);
    private int rotationTick = 0;
    private int lastSlot = -1;
    private int blockCount = -1;
    private float yaw = -180.0F;
    private float pitch = 0.0F;
    private boolean canRotate = false;
    private int towerTick = 0;
    private int towerDelay = 0;
    private int stage = 0;
    private int startY = 256;
    private boolean shouldKeepY = false;
    private boolean towering = false;
    private EnumFacing targetFacing = null;
    private float lastYaw = 0;
    private float lastYawChange = 0;
    private float lastPitchChange = 0;
    private boolean publishRotationSmoothActive = false;
    private float publishYaw = 0.0F;
    private float publishPitch = 0.0F;
    private float blockCounterAlpha = 0.0F;
    private float displayedBlockCount = -1.0F;
    private float blockCounterPulse = 0.0F;
    private int blockCounterMaxCount = 0;
    private float blockCounterReserve = 1.0F;
    private float blockCounterDelayedReserve = 1.0F;
    private float blockCounterFlowerReserve = 1.0F;
    private int lastRenderedBlockCount = -1;
    private long lastBlockCounterFrame = System.nanoTime();
    private ItemStack lastCounterStack;
    private BlockCounterExitRenderer blockCounterExitRenderer;
    public static int count = 0;

    public Scaffold() {
        super("Scaffold", false);
        this.key = Keyboard.KEY_NONE;
        this.category = ModuleType.World;
        this.Chinese = "自动搭路";
        this.Descript = "Places blocks under the player";
        this.About = this.Descript;
    }

    private boolean shouldStopSprint() {
        if (this.isTowering()) {
            return false;
        } else {
            boolean stage = this.keepY.getValue() == 1 || this.keepY.getValue() == 2;
            return (!stage || this.stage <= 0) && this.sprintMode.getValue() == 0;
        }
    }

    private boolean canPlace() {
        BedNuker bedNuker = (BedNuker) YozakuraRuntime.moduleManager.modules.get(BedNuker.class);
        if (bedNuker.isEnabled() && bedNuker.isReady()) {
            return false;
        } else {
            LongJump longJump = (LongJump) YozakuraRuntime.moduleManager.modules.get(LongJump.class);
            return !longJump.isEnabled() || !longJump.isAutoMode() || longJump.isJumping();
        }
    }

    private EnumFacing getBestFacing(BlockPos blockPos1, BlockPos blockPos3) {
        double offset = 0.0;
        EnumFacing enumFacing = null;
        for (EnumFacing facing : EnumFacing.values()) {
            if (facing != EnumFacing.DOWN) {
                BlockPos pos = blockPos1.offset(facing);
                if (pos.getY() <= blockPos3.getY()) {
                    double distance = pos.distanceSqToCenter((double) blockPos3.getX() + 0.5, (double) blockPos3.getY() + 0.5, (double) blockPos3.getZ() + 0.5);
                    if (enumFacing == null || distance < offset || distance == offset && facing == EnumFacing.UP) {
                        offset = distance;
                        enumFacing = facing;
                    }
                }
            }
        }
        return enumFacing;
    }

    private BlockData getBlockData() {
        int startY = MathHelper.floor_double(mc.thePlayer.posY);
        BlockPos targetPos = new BlockPos(
                MathHelper.floor_double(mc.thePlayer.posX),
                (this.stage != 0 && !this.shouldKeepY ? Math.min(startY, this.startY) : startY) - 1,
                MathHelper.floor_double(mc.thePlayer.posZ)
        );
        if (!BlockUtil.isReplaceable(targetPos)) {
            return null;
        } else {
            ArrayList<BlockPos> positions = new ArrayList<>();
            for (int x = -4; x <= 4; x++) {
                for (int y = -4; y <= 0; y++) {
                    for (int z = -4; z <= 4; z++) {
                        BlockPos pos = targetPos.add(x, y, z);
                        if (!BlockUtil.isReplaceable(pos)
                                && !BlockUtil.isInteractable(pos)
                                && !(
                                mc.thePlayer.getDistance((double) pos.getX() + 0.5, (double) pos.getY() + 0.5, (double) pos.getZ() + 0.5)
                                        > (double) mc.playerController.getBlockReachDistance()
                        )
                                && (this.stage == 0 || this.shouldKeepY || pos.getY() < this.startY)) {
                            for (EnumFacing facing : EnumFacing.values()) {
                                if (facing != EnumFacing.DOWN) {
                                    BlockPos blockPos = pos.offset(facing);
                                    if (BlockUtil.isReplaceable(blockPos)) {
                                        positions.add(pos);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (positions.isEmpty()) {
                return null;
            } else {
                positions.sort(
                        Comparator.comparingDouble(
                                o -> o.distanceSqToCenter((double) targetPos.getX() + 0.5, (double) targetPos.getY() + 0.5, (double) targetPos.getZ() + 0.5)
                        )
                );
                BlockPos blockPos = positions.get(0);
                EnumFacing facing = this.getBestFacing(blockPos, targetPos);
                return facing == null ? null : new BlockData(blockPos, facing);
            }
        }
    }

    private void place(BlockPos blockPos, EnumFacing enumFacing, Vec3 vec3) {
        if (ItemUtil.isHoldingBlock() && this.blockCount > 0) {
            MinecraftAccessor.syncCurrentPlayItem(mc.playerController);
            if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.inventory.getCurrentItem(), blockPos, enumFacing, vec3)) {
                if (mc.playerController.getCurrentGameType() != GameType.CREATIVE) {
                    this.blockCount--;
                }
                if (this.swing.getValue()) {
                    mc.thePlayer.swingItem();
                } else {
                    PacketUtil.sendPacket(new C0APacketAnimation());
                }
            }
        }
    }

    private EnumFacing yawToFacing(float yaw) {
        if (yaw < -135.0F || yaw > 135.0F) {
            return EnumFacing.NORTH;
        } else if (yaw < -45.0F) {
            return EnumFacing.EAST;
        } else {
            return yaw < 45.0F ? EnumFacing.SOUTH : EnumFacing.WEST;
        }
    }

    private double distanceToEdge(EnumFacing enumFacing) {
        switch (enumFacing) {
            case NORTH:
                return mc.thePlayer.posZ - Math.floor(mc.thePlayer.posZ);
            case EAST:
                return Math.ceil(mc.thePlayer.posX) - mc.thePlayer.posX;
            case SOUTH:
                return Math.ceil(mc.thePlayer.posZ) - mc.thePlayer.posZ;
            case WEST:
            default:
                return mc.thePlayer.posX - Math.floor(mc.thePlayer.posX);
        }
    }

    private float getSpeed() {
        if (!mc.thePlayer.onGround) {
            return (float) this.airMotion.getValue() / 100.0F;
        } else {
            return MoveUtil.getSpeedLevel() > 0
                    ? (float) this.speedMotion.getValue() / 100.0F
                    : (float) this.groundMotion.getValue() / 100.0F;
        }
    }

    private double getRandomOffset() {
        return 0.2155 - RandomUtil.nextDouble(1.0E-4, 9.0E-4);
    }

    private float getCurrentYaw() {
        return MoveUtil.adjustYaw(
                mc.thePlayer.rotationYaw, (float) MoveUtil.getForwardValue(), (float) MoveUtil.getLeftValue()
        );
    }

    private boolean isDiagonal(float yaw) {
        float absYaw = Math.abs(yaw % 90.0F);
        return absYaw > 20.0F && absYaw < 70.0F;
    }

    private boolean isTowering() {
        if (mc.thePlayer.onGround && MoveUtil.isForwardPressed() && !PlayerUtil.isAirAbove()) {
            boolean keepY = this.keepY.getValue() == 3;
            boolean tower = this.tower.getValue() == 3;
            return keepY && this.stage > 0 || tower && mc.gameSettings.keyBindJump.isKeyDown();
        } else {
            return false;
        }
    }

    public int getSlot() {
        return this.lastSlot;
    }

    @EventTarget(Priority.HIGH)
    public void onUpdate(UpdateEvent event) {
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            RotationDebug.setSourceEnabled("Scaffold", this.rotationDebug.getValue());
            if (this.rotationTick > 0) {
                this.rotationTick--;
            }
            if (mc.thePlayer.onGround) {
                if (this.stage > 0) {
                    this.stage--;
                }
                if (this.stage < 0) {
                    this.stage++;
                }
                if (this.stage == 0
                        && this.keepY.getValue() != 0
                        && (!(Boolean) this.keepYonPress.getValue() || PlayerUtil.isUsingItem())
                        && !mc.gameSettings.keyBindJump.isKeyDown()) {
                    this.stage = 1;
                }
                this.startY = this.shouldKeepY ? this.startY : MathHelper.floor_double(mc.thePlayer.posY);
                this.shouldKeepY = false;
                this.towering = false;
            }
            if (this.canPlace()) {
                ItemStack stack = mc.thePlayer.getHeldItem();
                int count = ItemUtil.isBlock(stack) ? stack.stackSize : 0;
                this.blockCount = Math.min(this.blockCount, count);
                if (this.blockCount <= 0) {
                    int slot = mc.thePlayer.inventory.currentItem;
                    if (this.blockCount == 0) {
                        slot--;
                    }
                    for (int i = slot; i > slot - 9; i--) {
                        int hotbarSlot = (i % 9 + 9) % 9;
                        ItemStack candidate = mc.thePlayer.inventory.getStackInSlot(hotbarSlot);
                        if (ItemUtil.isBlock(candidate)) {
                            this.selectHotbarSlot(hotbarSlot);
                            this.blockCount = candidate.stackSize;
                            break;
                        }
                    }
                }
                float currentYaw = this.getCurrentYaw();
                float yawDiffTo180 = RotationUtil.wrapAngleDiff(currentYaw - 180.0F, event.getYaw());
                float diagonalYaw = this.isDiagonal(currentYaw)
                        ? yawDiffTo180
                        : RotationUtil.wrapAngleDiff(currentYaw - 135.0F * ((currentYaw + 180.0F) % 90.0F < 45.0F ? 1.0F : -1.0F), event.getYaw());
                if (!this.canRotate) {
                    switch (this.rotationMode.getValue()) {
                        case 1:
                            if (this.yaw == -180.0F && this.pitch == 0.0F) {
                                this.yaw = RotationUtil.quantizeAngle(diagonalYaw);
                                this.pitch = RotationUtil.quantizeAngle(85.0F);
                            } else {
                                this.yaw = RotationUtil.quantizeAngle(diagonalYaw);
                            }
                            break;
                        case 2:
                            if (this.yaw == -180.0F && this.pitch == 0.0F) {
                                this.yaw = RotationUtil.quantizeAngle(yawDiffTo180);
                                this.pitch = RotationUtil.quantizeAngle(85.0F);
                            } else {
                                this.yaw = RotationUtil.quantizeAngle(yawDiffTo180);
                            }
                            break;
                        case 3:
                            if (this.yaw == -180.0F && this.pitch == 0.0F) {
                                this.yaw = RotationUtil.quantizeAngle(diagonalYaw);
                                this.pitch = RotationUtil.quantizeAngle(85.0F);
                            } else {
                                this.yaw = RotationUtil.quantizeAngle(diagonalYaw);
                            }
                            break;
                        case 4:
                            if (this.yaw == -180.0F && this.pitch == 0.0F) {
                                this.yaw = (float) (RotationUtil.quantizeAngle(diagonalYaw) + RandomUtil.nextDouble(0.7d, 1.5d));
                                this.pitch = RotationUtil.quantizeAngle(85.0F);
                            }
                            break;
                        case 5:
                            BlockData currentBlockData = this.getBlockData();

                            if (currentBlockData != null) {
                                float[] targetRots = RotationUtil.getRotations(getVec3(currentBlockData));
                                float targetYaw = targetRots[0];
                                float targetPitch = targetRots[1];
                                float predictedYaw = getPredictedYaw();
                                float currentYaw2 = this.yaw;
                                float currentPitch = this.pitch;

                                float yawToTarget = MathHelper.wrapAngleTo180_float(targetYaw - currentYaw2);
                                float pitchToTarget = targetPitch - currentPitch;
                                float absYawDiff = Math.abs(yawToTarget);
                                float distance = (float) mc.thePlayer.getDistance(
                                        currentBlockData.blockPos().getX() + 0.5,
                                        currentBlockData.blockPos().getY() + 0.5,
                                        currentBlockData.blockPos().getZ() + 0.5
                                );
                                float currentSpeed = getCurrentSpeed(distance);
                                float actualYawDiff = MathHelper.wrapAngleTo180_float(currentYaw - lastYaw);
                                float error = Math.abs(actualYawDiff - lastYawChange);
                                lastErrors[errorIndex] = error;
                                errorIndex = (errorIndex + 1) % 20;

                                float avgError = 0;
                                for (float e : lastErrors) avgError += e;
                                avgError /= 20;

                                if (avgError > 5) currentSpeed *= 0.8F;
                                else if (avgError < 1) currentSpeed *= 1.1F;
                                float yawChange;
                                if (absYawDiff > 90) {
                                    yawChange = Math.signum(yawToTarget) * currentSpeed * 1.2F;
                                } else if (absYawDiff > 30) {
                                    yawChange = Math.signum(yawToTarget) * currentSpeed * 0.8F;
                                } else {
                                    float fineSpeed = currentSpeed * 0.3F;
                                    yawChange = yawToTarget * 0.2F;
                                    yawChange = MathHelper.clamp_float(yawChange, -fineSpeed, fineSpeed);
                                }
                                float inertia = 0.3F;
                                yawChange = lastYawChange * inertia + yawChange * (1 - inertia);
                                lastYawChange = yawChange;
                                float pitchChange = Math.signum(pitchToTarget) * currentSpeed * 0.3F;
                                pitchChange = lastPitchChange * inertia + pitchChange * (1 - inertia);
                                lastPitchChange = pitchChange;
                                double ticks = 1.0;
                                double futureX = mc.thePlayer.posX + mc.thePlayer.motionX * ticks;
                                double futureY = mc.thePlayer.posY + mc.thePlayer.motionY * ticks;
                                double futureZ = mc.thePlayer.posZ + mc.thePlayer.motionZ * ticks;
                                BlockPos futureBlockPos = new BlockPos(
                                        MathHelper.floor_double(futureX),
                                        MathHelper.floor_double(futureY) - 1,
                                        MathHelper.floor_double(futureZ)
                                );

                                if (BlockUtil.isReplaceable(futureBlockPos)) {
                                    float[] futureRots = RotationUtil.getRotations(
                                            futureX, futureY + mc.thePlayer.getEyeHeight(), futureZ,
                                            currentBlockData.blockPos().getX() + 0.5,
                                            currentBlockData.blockPos().getY() + 0.5,
                                            currentBlockData.blockPos().getZ() + 0.5
                                    );
                                    yawChange = yawChange * 0.7F + (futureRots[0] - currentYaw) * 0.3F;
                                }

                                float jitterAmount;
                                if (absYawDiff < 5) {
                                    jitterAmount = RandomUtil.nextFloat(-2.0F, 2.0F);
                                    if (RandomUtil.nextDouble(0, 1) < 0.05) jitterAmount *= 3;
                                } else if (this.towering) {
                                    jitterAmount = RandomUtil.nextFloat(-0.5F, 0.5F);
                                } else {
                                    jitterAmount = RandomUtil.nextFloat(-1.0F, 1.0F);
                                }
                                yawChange += jitterAmount;
                                yawChange = MathHelper.clamp_float(yawChange, -currentSpeed, currentSpeed);
                                pitchChange = MathHelper.clamp_float(pitchChange, -currentSpeed * 0.4F, currentSpeed * 0.4F);
                                yawChange += RandomUtil.nextFloat(-0.5F, 0.5F);
                                pitchChange += RandomUtil.nextFloat(-0.3F, 0.3F);
                                float newYaw = currentYaw + yawChange;
                                float newPitch = currentPitch + pitchChange;
                                newPitch = MathHelper.clamp_float(newPitch, -90F, 90F);
                                if (this.yaw == -180.0F && this.pitch == 0.0F) {
                                    newYaw = RotationUtil.quantizeAngle(predictedYaw);
                                    newPitch = 85.0F;
                                }
                                this.yaw = RotationUtil.quantizeAngle(newYaw);
                                this.pitch = RotationUtil.quantizeAngle(newPitch);
                                lastYaw = this.yaw;

                            } else {
                                if (this.yaw != -180.0F) {
                                    float targetYaw = event.getYaw();
                                    float yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - this.yaw);
                                    float returnSpeed = 3.0F;
                                    yawDiff = MathHelper.clamp_float(yawDiff, -returnSpeed, returnSpeed);

                                    this.yaw = RotationUtil.quantizeAngle(this.yaw + yawDiff);

                                    if (this.pitch > 10) {
                                        this.pitch -= 1.0F;
                                    } else if (this.pitch < -10) {
                                        this.pitch += 1.0F;
                                    }
                                }
                            }
                            break;
                    }
                }
                BlockData blockData = this.getBlockData();
                Vec3 hitVec = null;
                if (blockData != null) {
                    double[] x = placeOffsets;
                    double[] y = placeOffsets;
                    double[] z = placeOffsets;
                    switch (blockData.facing()) {
                        case NORTH:
                            z = new double[]{0.0};
                            break;
                        case EAST:
                            x = new double[]{1.0};
                            break;
                        case SOUTH:
                            z = new double[]{1.0};
                            break;
                        case WEST:
                            x = new double[]{0.0};
                            break;
                        case DOWN:
                            y = new double[]{0.0};
                            break;
                        case UP:
                            y = new double[]{1.0};
                    }
                    float bestYaw = -180.0F;
                    float bestPitch = 0.0F;
                    float bestDiff = 0.0F;
                    for (double dx : x) {
                        for (double dy : y) {
                            for (double dz : z) {
                                double relX = (double) blockData.blockPos().getX() + dx - mc.thePlayer.posX;
                                double relY = (double) blockData.blockPos().getY() + dy - mc.thePlayer.posY - (double) mc.thePlayer.getEyeHeight();
                                double relZ = (double) blockData.blockPos().getZ() + dz - mc.thePlayer.posZ;
                                float baseYaw = RotationUtil.wrapAngleDiff(this.yaw, event.getYaw());
                                float[] rotations = RotationUtil.getRotationsTo(relX, relY, relZ, baseYaw, this.pitch);
                                MovingObjectPosition mop = RotationUtil.rayTrace(rotations[0], rotations[1], mc.playerController.getBlockReachDistance(), 1.0F);
                                if (mop != null
                                        && mop.typeOfHit == MovingObjectType.BLOCK
                                        && mop.getBlockPos().equals(blockData.blockPos())
                                        && mop.sideHit == blockData.facing()) {
                                    float totalDiff = Math.abs(rotations[0] - baseYaw) + Math.abs(rotations[1] - this.pitch);
                                    if (bestYaw == -180.0F && bestPitch == 0.0F || totalDiff < bestDiff) {
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
                if (this.canRotate && MoveUtil.isForwardPressed() && Math.abs(MathHelper.wrapAngleTo180_float(yawDiffTo180 - this.yaw)) < 90.0F) {
                    switch (this.rotationMode.getValue()) {
                        case 2:
                            this.yaw = RotationUtil.quantizeAngle(yawDiffTo180);
                            break;
                        case 3:
                            this.yaw = RotationUtil.quantizeAngle(diagonalYaw);
                    }
                }
                if (this.rotationMode.getValue() == 0) {
                    this.resetRotationSmoothing();
                }
                if (this.rotationMode.getValue() != 0) {
                    float targetYaw = this.yaw;
                    float targetPitch = this.pitch;
                    if (this.towering && (mc.thePlayer.motionY > 0.0 || mc.thePlayer.posY > (double) (this.startY + 1))) {
                        float yawDiff = MathHelper.wrapAngleTo180_float(this.yaw - event.getYaw());
                        float tolerance = this.rotationTick >= 2 ? RandomUtil.nextFloat(tellystartrotationminspeed.getValue(), tellystartrotationmaxspeed.getValue()) : RandomUtil.nextFloat(tellynormalrotationminspeed.getValue(), tellynormalrotationmaxspeed.getValue());
                        if (Math.abs(yawDiff) > tolerance) {
                            float clampedYaw = RotationUtil.clampAngle(yawDiff, tolerance);
                            targetYaw = RotationUtil.quantizeAngle(event.getYaw() + clampedYaw);
                            this.rotationTick = Math.max(this.rotationTick, 1);
                        }
                    }
                    if (this.isTowering()) {
                        float yawDelta = MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw - event.getYaw());
                        targetYaw = RotationUtil.quantizeAngle(event.getYaw() + yawDelta * RandomUtil.nextFloat(0.98F, 0.99F));
                        targetPitch = RotationUtil.quantizeAngle(RandomUtil.nextFloat(30.0F, 80.0F));
                        this.rotationTick = 3;
                        this.towering = true;
                    }
                    float[] smoothedRotation = this.smoothPublishedRotation(targetYaw, targetPitch, event);
                    targetYaw = smoothedRotation[0];
                    targetPitch = smoothedRotation[1];
                    this.yaw = targetYaw;
                    this.pitch = targetPitch;
                    if (blockData != null && hitVec != null) {
                        MovingObjectPosition smoothedMop = RotationUtil.rayTrace(this.yaw, this.pitch, mc.playerController.getBlockReachDistance(), 1.0F);
                        if (this.isPlacementRayTrace(smoothedMop, blockData)) {
                            hitVec = smoothedMop.hitVec;
                        } else {
                            hitVec = null;
                        }
                    }
                    event.setRotation(targetYaw, targetPitch, 3);
                    VisualRotationState.publish("Scaffold", targetYaw, targetPitch, 3);
                    if (this.moveFix.getValue() == 1) {
                        event.setPervRotation(targetYaw, 3);
                    }
                }
                if (blockData != null && hitVec != null && this.rotationTick <= 0) {
                    this.place(blockData.blockPos(), blockData.facing(), hitVec);
                    if (this.multiplace.getValue()) {
                        for (int i = 0; i < 3; i++) {
                            blockData = this.getBlockData();
                            if (blockData == null) {
                                break;
                            }
                            MovingObjectPosition mop = RotationUtil.rayTrace(this.yaw, this.pitch, mc.playerController.getBlockReachDistance(), 1.0F);
                            if (mop != null
                                    && mop.typeOfHit == MovingObjectType.BLOCK
                                    && mop.getBlockPos().equals(blockData.blockPos())
                                    && mop.sideHit == blockData.facing()) {
                                this.place(blockData.blockPos(), blockData.facing(), mop.hitVec);
                            } else {
                                hitVec = BlockUtil.getClickVec(blockData.blockPos(), blockData.facing());
                                double dx = hitVec.xCoord - mc.thePlayer.posX;
                                double dy = hitVec.yCoord - mc.thePlayer.posY - (double) mc.thePlayer.getEyeHeight();
                                double dz = hitVec.zCoord - mc.thePlayer.posZ;
                                float[] rotations = RotationUtil.getRotationsTo(dx, dy, dz, event.getYaw(), event.getPitch());
                                if (!(Math.abs(rotations[0] - this.yaw) < 120.0F) || !(Math.abs(rotations[1] - this.pitch) < 60.0F)) {
                                    break;
                                }
                                mop = RotationUtil.rayTrace(rotations[0], rotations[1], mc.playerController.getBlockReachDistance(), 1.0F);
                                if (mop == null
                                        || mop.typeOfHit != MovingObjectType.BLOCK
                                        || !mop.getBlockPos().equals(blockData.blockPos())
                                        || mop.sideHit != blockData.facing()) {
                                    break;
                                }
                                this.place(blockData.blockPos(), blockData.facing(), mop.hitVec);
                            }
                        }
                    }
                }
                if (this.targetFacing != null) {
                    if (this.rotationTick <= 0) {
                        int playerBlockX = MathHelper.floor_double(mc.thePlayer.posX);
                        int playerBlockY = MathHelper.floor_double(mc.thePlayer.posY);
                        int playerBlockZ = MathHelper.floor_double(mc.thePlayer.posZ);
                        BlockPos belowPlayer = new BlockPos(playerBlockX, playerBlockY - 1, playerBlockZ);
                        hitVec = BlockUtil.getHitVec(belowPlayer, this.targetFacing, this.yaw, this.pitch);
                        this.place(belowPlayer, this.targetFacing, hitVec);
                    }
                    this.targetFacing = null;
                } else if (this.keepY.getValue() == 2 && this.stage > 0 && !mc.thePlayer.onGround) {
                    int nextBlockY = MathHelper.floor_double(mc.thePlayer.posY + mc.thePlayer.motionY);
                    if (nextBlockY <= this.startY && mc.thePlayer.posY > (double) (this.startY + 1)) {
                        this.shouldKeepY = true;
                        blockData = this.getBlockData();
                        if (blockData != null && this.rotationTick <= 0) {
                            hitVec = BlockUtil.getHitVec(blockData.blockPos(), blockData.facing(), this.yaw, this.pitch);
                            this.place(blockData.blockPos(), blockData.facing(), hitVec);
                        }
                    }
                }
            }
        }
    }

    private float[] smoothPublishedRotation(float targetYaw, float targetPitch, UpdateEvent event) {
        if (!this.publishRotationSmoothActive) {
            this.publishYaw = event.getNewYaw();
            this.publishPitch = event.getNewPitch();
            this.publishRotationSmoothActive = true;
        }
        float smoothing = MathHelper.clamp_float((float) this.rotationSmoothing.getValue() / 100.0F, 0.0F, 1.0F);
        float yawSpeed = 90.0F - smoothing * 48.0F;
        float pitchSpeed = 65.0F - smoothing * 36.0F;
        if (this.towering || this.rotationTick > 0) {
            yawSpeed += 18.0F;
            pitchSpeed += 12.0F;
        }
        this.publishYaw = RotationUtil.quantizeAngle(this.publishYaw + MathHelper.clamp_float(
                MathHelper.wrapAngleTo180_float(targetYaw - this.publishYaw), -yawSpeed, yawSpeed));
        this.publishPitch = RotationUtil.quantizeAngle(this.publishPitch + MathHelper.clamp_float(
                targetPitch - this.publishPitch, -pitchSpeed, pitchSpeed));
        this.publishPitch = MathHelper.clamp_float(this.publishPitch, -90.0F, 90.0F);
        return new float[]{this.publishYaw, this.publishPitch};
    }

    private boolean isPlacementRayTrace(MovingObjectPosition mop, BlockData blockData) {
        return mop != null
                && mop.typeOfHit == MovingObjectType.BLOCK
                && mop.getBlockPos().equals(blockData.blockPos())
                && mop.sideHit == blockData.facing();
    }

    private void resetRotationSmoothing() {
        this.publishRotationSmoothActive = false;
        this.publishYaw = 0.0F;
        this.publishPitch = 0.0F;
    }

    private float getCurrentSpeed(float distance) {
        float baseSpeed;
        if (this.towering) {
            baseSpeed = 40.0F;
        } else if (MoveUtil.getSpeedLevel() > 0) {
            baseSpeed = 35.0F;
        } else {
            baseSpeed = 25.0F;
        }
        float speedMultiplier = Math.min(1.2F, distance);
        float currentSpeed = baseSpeed * speedMultiplier;
        currentSpeed = Math.min(45.0F, Math.max(10.0F, currentSpeed));
        return currentSpeed;
    }

    private float getPredictedYaw() {
        float currentMoveYaw = this.getCurrentYaw();
        float predictedYaw;
        if (this.isDiagonal(currentMoveYaw)) {
            predictedYaw = currentMoveYaw - 180.0F;
        } else {
            float sideMultiplier = (currentMoveYaw + 180.0F) % 90.0F < 45.0F ? 1.0F : -1.0F;
            predictedYaw = currentMoveYaw - 135.0F * sideMultiplier;
        }
        return predictedYaw;
    }

    private Vec3 getVec3(BlockData data) {
        if (data == null) return null;

        BlockPos pos = data.blockPos();
        EnumFacing face = data.facing();
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.5D;
        double z = pos.getZ() + 0.5D;
        x += (double) face.getFrontOffsetX() * 0.5D;
        y += (double) face.getFrontOffsetY() * 0.5D;
        z += (double) face.getFrontOffsetZ() * 0.5D;

        return new Vec3(x, y, z);
    }
    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (this.isEnabled()) {
            if (!mc.thePlayer.isCollidedHorizontally
                    && mc.thePlayer.hurtTime <= 5
                    && !mc.thePlayer.isPotionActive(Potion.jump)
                    && mc.gameSettings.keyBindJump.isKeyDown()
                    && ItemUtil.isHoldingBlock()) {
                int yState = (int) (mc.thePlayer.posY % 1.0 * 100.0);
                switch (this.tower.getValue()) {
                    case 1:
                        switch (this.towerTick) {
                            case 0:
                                if (mc.thePlayer.onGround) {
                                    this.towerTick = 1;
                                    mc.thePlayer.motionY = -0.0784000015258789;
                                }
                                return;
                            case 1:
                                if (yState == 0 && PlayerUtil.isAirBelow()) {
                                    this.startY = MathHelper.floor_double(mc.thePlayer.posY);
                                    this.towerTick = 2;
                                    mc.thePlayer.motionY = 0.42F;
                                    if (MoveUtil.isForwardPressed()) {
                                        MoveUtil.setSpeed(MoveUtil.getSpeed(), MoveUtil.getMoveYaw());
                                    } else {
                                        MoveUtil.setSpeed(0.0);
                                        event.setForward(0.0F);
                                        event.setStrafe(0.0F);
                                    }
                                    return;
                                } else {
                                    this.towerTick = 0;
                                    return;
                                }
                            case 2:
                                this.towerTick = 3;
                                mc.thePlayer.motionY = 0.75 - mc.thePlayer.posY % 1.0;
                                return;
                            case 3:
                                this.towerTick = 1;
                                mc.thePlayer.motionY = 1.0 - mc.thePlayer.posY % 1.0;
                                return;
                            default:
                                this.towerTick = 0;
                                return;
                        }
                    case 2:
                        switch (this.towerTick) {
                            case 0:
                                if (mc.thePlayer.onGround) {
                                    this.towerTick = 1;
                                    mc.thePlayer.motionY = -0.0784000015258789;
                                }
                                return;
                            case 1:
                                if (yState == 0 && PlayerUtil.isAirBelow()) {
                                    this.startY = MathHelper.floor_double(mc.thePlayer.posY);
                                    if (!MoveUtil.isForwardPressed()) {
                                        this.towerDelay = 2;
                                        MoveUtil.setSpeed(0.0);
                                        event.setForward(0.0F);
                                        event.setStrafe(0.0F);
                                        EnumFacing facing = this.yawToFacing(MathHelper.wrapAngleTo180_float(this.yaw - 180.0F));
                                        double distance = this.distanceToEdge(facing);
                                        if (distance > 0.1) {
                                            if (mc.thePlayer.onGround) {
                                                Vec3i directionVec = facing.getDirectionVec();
                                                double offset = Math.min(this.getRandomOffset(), distance - 0.05);
                                                double jitter = RandomUtil.nextDouble(0.02, 0.03);
                                                AxisAlignedBB nextBox = mc.thePlayer
                                                        .getEntityBoundingBox()
                                                        .offset((double) directionVec.getX() * (offset - jitter), 0.0, (double) directionVec.getZ() * (offset - jitter));
                                                if (mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, nextBox).isEmpty()) {
                                                    mc.thePlayer.motionY = -0.0784000015258789;
                                                    mc.thePlayer
                                                            .setPosition(nextBox.minX + (nextBox.maxX - nextBox.minX) / 2.0, nextBox.minY, nextBox.minZ + (nextBox.maxZ - nextBox.minZ) / 2.0);
                                                }
                                                return;
                                            }
                                        } else {
                                            this.towerTick = 2;
                                            this.targetFacing = facing;
                                            mc.thePlayer.motionY = 0.42F;
                                        }
                                        return;
                                    } else {
                                        this.towerTick = 2;
                                        this.towerDelay++;
                                        mc.thePlayer.motionY = 0.42F;
                                        MoveUtil.setSpeed(MoveUtil.getSpeed(), MoveUtil.getMoveYaw());
                                        return;
                                    }
                                } else {
                                    this.towerTick = 0;
                                    this.towerDelay = 0;
                                    return;
                                }
                            case 2:
                                this.towerTick = 3;
                                mc.thePlayer.motionY = mc.thePlayer.motionY - RandomUtil.nextDouble(0.00101, 0.00109);
                                return;
                            case 3:
                                if (this.towerDelay >= 4) {
                                    this.towerTick = 4;
                                    this.towerDelay = 0;
                                } else {
                                    this.towerTick = 1;
                                    mc.thePlayer.motionY = 1.0 - mc.thePlayer.posY % 1.0;
                                }
                                return;
                            case 4:
                                this.towerTick = 5;
                                return;
                            case 5:
                                if (!PlayerUtil.isAirBelow()) {
                                    this.towerTick = 0;
                                } else {
                                    this.towerTick = 1;
                                    mc.thePlayer.motionY -= 0.08;
                                    mc.thePlayer.motionY *= 0.98F;
                                    mc.thePlayer.motionY -= 0.08;
                                    mc.thePlayer.motionY *= 0.98F;
                                }
                                return;
                            default:
                                this.towerTick = 0;
                                this.towerDelay = 0;
                                return;
                        }
                    default:
                        this.towerTick = 0;
                        this.towerDelay = 0;
                }
            } else {
                this.towerTick = 0;
                this.towerDelay = 0;
            }
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (this.isEnabled()) {
            if (this.moveFix.getValue() == 1
                    && RotationState.isActived()
                    && RotationState.getPriority() == 3.0F
                    && MoveUtil.isForwardPressed()) {
                MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
            }
            if (mc.thePlayer.onGround && this.stage > 0 && MoveUtil.isForwardPressed()) {
                mc.thePlayer.movementInput.jump = true;
            }
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (this.isEnabled()) {
            float speed = this.getSpeed();
            if (speed != 1.0F) {
                if (mc.thePlayer.movementInput.moveForward != 0.0F && mc.thePlayer.movementInput.moveStrafe != 0.0F) {
                    mc.thePlayer.movementInput.moveForward = mc.thePlayer.movementInput.moveForward * (1.0F / (float) Math.sqrt(2.0));
                    mc.thePlayer.movementInput.moveStrafe = mc.thePlayer.movementInput.moveStrafe * (1.0F / (float) Math.sqrt(2.0));
                }
                mc.thePlayer.movementInput.moveForward *= speed;
                mc.thePlayer.movementInput.moveStrafe *= speed;
            }
            if (this.shouldStopSprint()) {
                mc.thePlayer.setSprinting(false);
            }
        }
    }

    @EventTarget
    public void onSafeWalk(SafeWalkEvent event) {
        if (this.isEnabled() && this.safeWalk.getValue()) {
            if (mc.thePlayer.onGround && mc.thePlayer.motionY <= 0.0 && PlayerUtil.canMove(mc.thePlayer.motionX, mc.thePlayer.motionZ, -1.0)) {
                event.setSafeWalk(true);
            }
        }
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (this.isEnabled()) {
            int count = 0;
            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
                if (stack != null && stack.stackSize > 0) {
                    Item item = stack.getItem();
                    if (item instanceof ItemBlock) {
                        Block block = ((ItemBlock) item).getBlock();
                        if (!BlockUtil.isInteractable(block) && BlockUtil.isSolid(block)) {
                            count += stack.stackSize;
                        }
                    }
                }
            }
            Scaffold.count = count;
            if (this.blockCounter.getValue()) {
                this.drawSakuraBlockCounter(count, this.findCounterBlockStack(), true);
            }
        }
    }

    private void selectHotbarSlot(int hotbarSlot) {
        if (hotbarSlot < 0 || hotbarSlot > 8 || mc.thePlayer == null) {
            return;
        }
        if (mc.thePlayer.inventory.currentItem != hotbarSlot) {
            mc.thePlayer.inventory.currentItem = hotbarSlot;
            MinecraftAccessor.syncCurrentPlayItem(mc.playerController);
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

    @Override
    public void onEnabled() {
        if (mc.thePlayer != null) {
            this.lastSlot = mc.thePlayer.inventory.currentItem;
        } else {
            this.lastSlot = -1;
        }
        this.blockCount = -1;
        this.rotationTick = 3;
        this.yaw = -180.0F;
        this.pitch = 0.0F;
        this.canRotate = false;
        this.resetRotationSmoothing();
        this.towerTick = 0;
        this.towerDelay = 0;
        this.towering = false;
        this.blockCounterAlpha = 0.0F;
        this.displayedBlockCount = -1.0F;
        this.blockCounterPulse = 0.0F;
        this.blockCounterMaxCount = 0;
        this.blockCounterReserve = 1.0F;
        this.blockCounterDelayedReserve = 1.0F;
        this.blockCounterFlowerReserve = 1.0F;
        this.lastRenderedBlockCount = -1;
        this.lastBlockCounterFrame = System.nanoTime();
        this.lastCounterStack = null;
        if (this.blockCounterExitRenderer != null) {
            EventManager.unregister(this.blockCounterExitRenderer);
            this.blockCounterExitRenderer = null;
        }
    }

    @Override
    public void onDisabled() {
        this.resetRotationSmoothing();
        RotationCleanup.clearModuleRotations("Scaffold", 3);
        RotationDebug.setSourceEnabled("Scaffold", false);
        if (mc.thePlayer != null && this.lastSlot != -1) {
            this.selectHotbarSlot(this.lastSlot);
        }
        if (this.blockCounter.getValue() && this.blockCounterAlpha > 0.025F) {
            if (this.blockCounterExitRenderer != null) {
                EventManager.unregister(this.blockCounterExitRenderer);
            }
            this.blockCounterExitRenderer = new BlockCounterExitRenderer(this.lastRenderedBlockCount,
                    this.lastCounterStack == null ? null : this.lastCounterStack.copy());
            EventManager.register(this.blockCounterExitRenderer);
        }
    }

    private ItemStack findCounterBlockStack() {
        ItemStack held = mc.thePlayer.getHeldItem();
        if (ItemUtil.isBlock(held)) {
            return held;
        }
        ItemStack best = null;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (!ItemUtil.isBlock(stack)) {
                continue;
            }
            if (best == null || stack.stackSize > best.stackSize) {
                best = stack;
            }
        }
        return best;
    }

    private void drawSakuraBlockCounter(int blockCount, ItemStack stack, boolean active) {
        long now = System.nanoTime();
        float delta = Math.min(0.06F, (now - this.lastBlockCounterFrame) / 1.0E9F);
        this.lastBlockCounterFrame = now;
        int safeBlockCount = Math.max(0, blockCount);
        if (active && stack != null) {
            this.lastCounterStack = stack.copy();
        }

        if (this.displayedBlockCount < 0.0F) {
            this.displayedBlockCount = safeBlockCount;
            this.lastRenderedBlockCount = safeBlockCount;
        }
        if (safeBlockCount != this.lastRenderedBlockCount) {
            this.blockCounterPulse = 1.0F;
            this.lastRenderedBlockCount = safeBlockCount;
        }
        if (this.blockCounterMaxCount <= 0 || (active && safeBlockCount > this.blockCounterMaxCount)) {
            this.blockCounterMaxCount = Math.max(1, safeBlockCount);
        }

        this.blockCounterAlpha = animate(this.blockCounterAlpha, active ? 1.0F : 0.0F, delta, active ? 12.0F : 8.0F);
        this.displayedBlockCount = animate(this.displayedBlockCount, safeBlockCount, delta, 8.0F);
        this.blockCounterPulse = animate(this.blockCounterPulse, 0.0F, delta, 5.2F);
        float targetReserve = this.blockCounterMaxCount <= 0 ? 0.0F
                : clamp01(safeBlockCount / (float) this.blockCounterMaxCount);
        this.blockCounterReserve = animate(this.blockCounterReserve, targetReserve, delta,
                targetReserve < this.blockCounterReserve ? 4.0F : 12.0F);
        this.blockCounterDelayedReserve = animate(this.blockCounterDelayedReserve,
                Math.max(targetReserve, this.blockCounterReserve), delta,
                targetReserve < this.blockCounterDelayedReserve ? 1.8F : 10.0F);
        this.blockCounterFlowerReserve = animate(this.blockCounterFlowerReserve, this.blockCounterReserve, delta, 5.0F);
        if (this.blockCounterAlpha <= 0.01F) {
            return;
        }

        HUD hud = (HUD) YozakuraRuntime.moduleManager.modules.get(HUD.class);
        float uiScale = MathHelper.clamp_float(hud.scale.getValue() * 0.80F, 0.58F, 1.10F);
        ScaledResolution sr = new ScaledResolution(mc);
        float width = 108.0F * uiScale;
        float height = 30.0F * uiScale;
        float x = sr.getScaledWidth() / 2.0F - width / 2.0F;
        float y = sr.getScaledHeight() / 2.0F + 24.0F * uiScale;
        float alpha = smoothStep(this.blockCounterAlpha);
        float pulse = smoothStep(this.blockCounterPulse);
        float panelScale = (active ? 0.94F + 0.06F * alpha : 0.98F + 0.02F * alpha) + 0.014F * pulse;
        float reserve = clamp01(this.blockCounterReserve);
        float delayedReserve = clamp01(this.blockCounterDelayedReserve);
        float flowerReserve = clamp01(this.blockCounterFlowerReserve);

        GlStateManager.pushMatrix();
        try {
            float centerX = x + width / 2.0F;
            float centerY = y + height / 2.0F;
            GlStateManager.translate(centerX, centerY, 0.0F);
            GlStateManager.scale(panelScale, panelScale, 1.0F);
            GlStateManager.translate(-centerX, -centerY, 0.0F);
            if (!active) {
                GlStateManager.translate(0.0F, (1.0F - alpha) * 8.0F * uiScale, 0.0F);
            }
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.disableDepth();

            float radius = 8.0F * uiScale;
            RenderServices.shapes().shadow(x, y, x + width, y + height, radius,
                    withAlpha(0xFF000000, 96.0F * alpha), 8, 3.2F * uiScale);
            RenderServices.shapes().shadow(x, y, x + width, y + height, radius,
                    withAlpha(SAKURA, (28.0F + 26.0F * pulse) * alpha), 5, 2.2F * uiScale);
            RenderServices.liquidGlass().roundedBorder(x, y, x + width, y + height, radius, 0.55F * uiScale,
                    withAlpha(SAKURA_GLASS, (158.0F + 18.0F * pulse) * alpha),
                    withAlpha(SAKURA_BORDER, (24.0F + 18.0F * pulse) * alpha),
                    BLOCK_COUNTER_GLASS);
            this.drawCounterAccents(x, y, width, height, uiScale, alpha, reserve, delayedReserve, flowerReserve, pulse);
            this.drawCounterItem(stack, x + 8.0F * uiScale, y + 4.0F * uiScale,
                    22.0F * uiScale, uiScale, alpha);
            this.drawCounterText(x, y, width, uiScale, alpha, pulse, Math.round(this.displayedBlockCount));
            this.drawFloatingPetals(x, y, width, height, uiScale, alpha);
        } finally {
            GlStateManager.enableDepth();
            GlStateManager.enableBlend();
            GlStateManager.enableAlpha();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }
    }

    private void drawCounterAccents(float x, float y, float width, float height,
                                    float uiScale, float alpha, float reserve,
                                    float delayedReserve, float flowerReserve, float pulse) {
        float barX = x + 36.0F * uiScale;
        float barY = y + height - 6.4F * uiScale;
        float barW = width - 47.0F * uiScale;
        float lineH = Math.max(0.72F, 0.85F * uiScale);
        float shadowY = barY + Math.max(0.9F, 1.0F * uiScale);
        float fillW = Math.max(0.0F, barW * clamp01(reserve));
        float delayedW = Math.max(fillW, barW * clamp01(delayedReserve));
        float markerSize = (2.55F + 0.38F * pulse) * uiScale;
        float markerOffset = Math.max(markerSize, Math.min(barW - markerSize, barW * clamp01(flowerReserve)));

        RenderServices.shapes().rect(barX, shadowY, barX + barW, shadowY + Math.max(0.45F, 0.48F * uiScale),
                withAlpha(0xFF09090D, 72.0F * alpha));
        RenderServices.shapes().rect(barX, barY, barX + barW, barY + lineH,
                withAlpha(0xFFFFD3E3, (34.0F + 10.0F * pulse) * alpha));
        if (delayedW > fillW + 0.5F * uiScale) {
            RenderServices.shapes().rect(barX, barY, barX + delayedW, barY + lineH,
                    withAlpha(0xFFFF6F9A, 66.0F * alpha));
        }
        if (fillW > 0.8F) {
            RenderServices.shapes().rect(barX, barY, barX + fillW, barY + Math.max(lineH, 1.05F * uiScale),
                    withAlpha(SAKURA_STRONG, (218.0F + 12.0F * pulse) * alpha));
            RenderServices.shapes().rect(barX, barY - Math.max(0.45F, 0.32F * uiScale),
                    barX + fillW, barY,
                    withAlpha(0xFFFFF3F8, 56.0F * alpha));
        }
        drawSakuraFlower(barX + markerOffset, barY + lineH * 0.5F, markerSize, alpha);
    }

    private void drawCounterText(float x, float y, float width, float uiScale,
                                 float alpha, float pulse, int blockCount) {
        CFontRenderer titleFont = FontLoaders.regular(Math.max(11, Math.round(12.5F * uiScale)));
        CFontRenderer smallFont = FontLoaders.regular(Math.max(8, Math.round(10.0F * uiScale)));
        float textX = x + 36.0F * uiScale;
        String amount = formatBlockCount(blockCount) + " blocks";

        this.drawTextGlow(titleFont, "Scaffold", textX, y + 5.0F * uiScale, uiScale, alpha,
                withAlpha(SAKURA_STRONG, (34.0F + 42.0F * pulse) * alpha));
        titleFont.drawString("Scaffold", textX, y + 5.0F * uiScale,
                withAlpha(SAKURA_MUTED, 214.0F * alpha));
        smallFont.drawString(amount, textX, y + 15.0F * uiScale,
                withAlpha(SAKURA_TEXT, 186.0F * alpha));
    }

    private void drawCounterItem(ItemStack stack, float x, float y, float size, float uiScale, float alpha) {
        float centerX = x + size * 0.5F;
        float centerY = y + size * 0.5F;
        RenderServices.shapes().shadow(centerX - 7.2F * uiScale, centerY - 7.2F * uiScale,
                centerX + 7.2F * uiScale, centerY + 7.2F * uiScale,
                7.2F * uiScale, withAlpha(SAKURA, 48.0F * alpha), 4, 1.8F * uiScale);
        if (stack == null || alpha <= 0.02F) {
            CFontRenderer iconFont = FontLoaders.icon(Math.max(13, Math.round(15.0F * uiScale)));
            String icon = FontLoaders.ICON_CUBE;
            iconFont.drawString(icon, centerX - iconFont.getStringWidth(icon) / 2.0F,
                    centerY - iconFont.getHeight() / 2.0F + 1.0F * uiScale,
                    withAlpha(SAKURA, 218.0F * alpha));
            return;
        }
        GlStateManager.pushMatrix();
        try {
            float itemScale = 1.02F * uiScale;
            GlStateManager.translate(centerX - 8.0F * itemScale, centerY - 8.0F * itemScale, 0.0F);
            GlStateManager.scale(itemScale, itemScale, 1.0F);
            RenderHelper.enableGUIStandardItemLighting();
            GlStateManager.enableRescaleNormal();
            GlStateManager.enableDepth();
            mc.getRenderItem().renderItemAndEffectIntoGUI(stack, 0, 0);
            GlStateManager.disableDepth();
            GlStateManager.disableRescaleNormal();
            RenderHelper.disableStandardItemLighting();
        } finally {
            GlStateManager.popMatrix();
            GlStateManager.enableBlend();
            GlStateManager.enableAlpha();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private void drawCounterCapsule(float x, float y, float width, float height, int color) {
        if (width <= 0.0F || height <= 0.0F || ((color >>> 24) & 255) <= 0) {
            return;
        }
        float radius = height * 0.5F;
        if (width <= height) {
            RenderServices.shapes().circle(x + width * 0.5F, y + radius, 0, 360, width * 0.5F, color);
            return;
        }
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        glColor(color, 1.0F);
        float centerY = y + radius;
        int segments = 12;
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f(x + width * 0.5F, centerY);
        for (int i = 0; i <= segments; i++) {
            float angle = (float) Math.toRadians(90.0F + 180.0F * i / segments);
            GL11.glVertex2f(x + radius + (float) Math.cos(angle) * radius,
                    centerY + (float) Math.sin(angle) * radius);
        }
        for (int i = 0; i <= segments; i++) {
            float angle = (float) Math.toRadians(270.0F + 180.0F * i / segments);
            GL11.glVertex2f(x + width - radius + (float) Math.cos(angle) * radius,
                    centerY + (float) Math.sin(angle) * radius);
        }
        GL11.glEnd();
        GlStateManager.enableTexture2D();
    }

    private void drawCounterBarSheen(float x, float y, float width, float height, float alpha) {
        if (width <= height || alpha <= 0.002F) {
            return;
        }
        float t = (System.currentTimeMillis() % 2300L) / 2300.0F;
        float bandW = Math.max(height * 3.2F, width * 0.22F);
        float start = x - bandW + (width + bandW * 2.0F) * t;
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GL11.glBegin(GL11.GL_QUADS);
        glColor(0xFFFFD8E8, alpha * 0.00F);
        GL11.glVertex2f(start - bandW * 0.48F, y + height);
        GL11.glVertex2f(start - bandW * 0.24F, y);
        glColor(0xFFFFD8E8, alpha * 0.30F);
        GL11.glVertex2f(start, y);
        GL11.glVertex2f(start - bandW * 0.24F, y + height);

        glColor(0xFFFFD8E8, alpha * 0.30F);
        GL11.glVertex2f(start, y);
        GL11.glVertex2f(start - bandW * 0.24F, y + height);
        glColor(0xFFFFD8E8, alpha * 0.00F);
        GL11.glVertex2f(start + bandW * 0.42F, y + height);
        GL11.glVertex2f(start + bandW * 0.66F, y);
        GL11.glEnd();
        GlStateManager.enableTexture2D();
    }

    private String formatBlockCount(int blockCount) {
        int amount = Math.max(0, blockCount);
        if (amount >= 10000) {
            return Math.round(amount / 1000.0F) + "k";
        }
        if (amount >= 1000) {
            return String.format("%.1fk", amount / 1000.0F);
        }
        return String.valueOf(amount);
    }

    private void drawFloatingPetals(float x, float y, float width, float height, float uiScale, float alpha) {
        if (alpha <= 0.02F) {
            return;
        }
        float time = (System.currentTimeMillis() % 4200L) / 4200.0F;
        for (int i = 0; i < 5; i++) {
            float phase = (time * (0.74F + i * 0.07F) + i * 0.19F) % 1.0F;
            float px = x + width * (0.08F + 0.92F * phase);
            float py = y + height * (0.10F + 0.34F * (float) Math.sin((time * 6.2831855F) + i * 1.46F));
            float petalAlpha = alpha * (0.30F + 0.30F * (float) Math.sin((phase + i * 0.33F) * 6.2831855F));
            this.drawSinglePetal(px, py, (2.2F + i * 0.35F) * uiScale, (phase * 120.0F + i * 58.0F), petalAlpha);
        }
    }

    private void drawSakuraFlower(float centerX, float centerY, float size, float alpha) {
        if (alpha <= 0.002F || size <= 0.002F) {
            return;
        }
        RenderServices.shapes().shadow(centerX - size, centerY - size, centerX + size, centerY + size,
                size, withAlpha(SAKURA, 68.0F * alpha), 4, size * 0.62F);
        GlStateManager.pushMatrix();
        GlStateManager.translate(centerX, centerY, 0.0F);
        GlStateManager.rotate((System.currentTimeMillis() % 2600L) / 2600.0F * 28.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        for (int i = 0; i < 5; i++) {
            GL11.glPushMatrix();
            GL11.glRotatef(i * 72.0F, 0.0F, 0.0F, 1.0F);
            GL11.glTranslatef(0.0F, size * 0.20F, 0.0F);
            this.drawSakuraPetal2D(size, alpha);
            GL11.glPopMatrix();
        }
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
        RenderServices.shapes().circle(centerX, centerY, 0, 360, size * 0.30F,
                withAlpha(0xFFFFF4FA, 230.0F * alpha));
    }

    private void drawSinglePetal(float centerX, float centerY, float size, float rotation, float alpha) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(centerX, centerY, 0.0F);
        GlStateManager.rotate(rotation, 0.0F, 0.0F, 1.0F);
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        this.drawSakuraPetal2D(size, alpha);
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    private void drawSakuraPetal2D(float size, float alpha) {
        float width = size * 0.58F;
        float length = size * 1.12F;

        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        glColor(0xFFFFEDF5, alpha * 0.94F);
        GL11.glVertex2f(0.0F, length * 0.36F);
        for (float[] point : SAKURA_PETAL_POINTS) {
            glColor(SAKURA, alpha * 0.72F);
            GL11.glVertex2f(point[0] * width, point[1] * length);
        }
        GL11.glEnd();

        GL11.glLineWidth(0.7F);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        glColor(0xFFFFF8FB, alpha * 0.44F);
        for (float[] point : SAKURA_PETAL_POINTS) {
            GL11.glVertex2f(point[0] * width, point[1] * length);
        }
        GL11.glEnd();
    }

    private void drawTextGlow(CFontRenderer font, String text, float x, float y,
                              float uiScale, float alpha, int color) {
        if (alpha <= 0.018F) {
            return;
        }
        float spread = Math.max(0.42F, 0.60F * uiScale);
        font.drawString(text, x - spread, y, color);
        font.drawString(text, x + spread, y, color);
        font.drawString(text, x, y - spread, color);
        font.drawString(text, x, y + spread, color);
    }

    private final class BlockCounterExitRenderer {
        private final int count;
        private final ItemStack stack;

        private BlockCounterExitRenderer(int count, ItemStack stack) {
            this.count = Math.max(0, count);
            this.stack = stack;
        }

        @EventTarget
        public void onRender(Render2DEvent event) {
            drawSakuraBlockCounter(this.count, this.stack, false);
            if (blockCounterAlpha <= 0.012F) {
                EventManager.unregister(this);
                if (blockCounterExitRenderer == this) {
                    blockCounterExitRenderer = null;
                }
            }
        }
    }

    private static float animate(float current, float target, float delta, float speed) {
        float factor = 1.0F - (float) Math.pow(0.001D, Math.max(0.0F, delta) * speed);
        return current + (target - current) * clamp01(factor);
    }

    private static float smoothStep(float value) {
        float t = clamp01(value);
        return t * t * (3.0F - 2.0F * t);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static int withAlpha(int color, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha)));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    private static void glColor(int color, float alpha) {
        float a = clamp01(alpha) * ((color >>> 24) & 255) / 255.0F;
        GL11.glColor4f(((color >> 16) & 255) / 255.0F,
                ((color >> 8) & 255) / 255.0F,
                (color & 255) / 255.0F, a);
    }

    public static class BlockData {
        private final BlockPos blockPos;
        private final EnumFacing facing;

        public BlockData(BlockPos blockPos, EnumFacing enumFacing) {
            this.blockPos = blockPos;
            this.facing = enumFacing;
        }

        public BlockPos blockPos() {
            return this.blockPos;
        }

        public EnumFacing facing() {
            return this.facing;
        }
    }
}
