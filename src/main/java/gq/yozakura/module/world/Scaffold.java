package gq.yozakura.module.world;

import gq.yozakura.module.Module;
import gq.yozakura.module.ModuleType;
import gq.yozakura.value.Mode;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.potion.Potion;
import net.minecraft.util.*;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.world.WorldSettings;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;

public class Scaffold extends Module {
    public enum RotationMode {
        NONE,
        VANILLA,
        BACKWARDS,
        STRAFE,
        TEST,
        PREDICTION
    }

    public enum KeepYMode {
        NONE,
        VANILLA,
        EXTRA,
        TELLY
    }

    public enum TowerMode {
        NONE,
        VANILLA,
        EXTRA,
        TELLY
    }

    private static final double[] PLACE_OFFSETS = new double[]{
            0.03125D, 0.09375D, 0.15625D, 0.21875D,
            0.28125D, 0.34375D, 0.40625D, 0.46875D,
            0.53125D, 0.59375D, 0.65625D, 0.71875D,
            0.78125D, 0.84375D, 0.90625D, 0.96875D
    };
    private static final String ROTATION_HANDLER_NAME = "vapulite_scaffold_rotation";

    private final Mode<RotationMode> rotationMode =
            new Mode<RotationMode>("Rotations", "Rotations", RotationMode.values(), RotationMode.PREDICTION);
    private final Mode<KeepYMode> keepY =
            new Mode<KeepYMode>("Keep Y", "KeepY", KeepYMode.values(), KeepYMode.NONE);
    private final Mode<TowerMode> tower =
            new Mode<TowerMode>("Tower", "Tower", TowerMode.values(), TowerMode.NONE);
    private final Option<Boolean> swing = new Option<Boolean>("Swing", "Swing", true);
    private final Option<Boolean> itemSpoof = new Option<Boolean>("Item Spoof", "ItemSpoof", false);
    private final Option<Boolean> safeWalk = new Option<Boolean>("Safe Walk", "SafeWalk", true);
    private final Option<Boolean> multiPlace = new Option<Boolean>("Multi Place", "MultiPlace", false);
    private final Option<Boolean> stopSprint = new Option<Boolean>("Stop Sprint", "StopSprint", true);
    private final Option<Boolean> keepYOnPress = new Option<Boolean>("Keep Y On Press", "KeepYOnPress", false);
    private final Option<Boolean> blockCounter = new Option<Boolean>("Block Counter", "BlockCounter", true);
    private final Numbers<Double> groundMotion = new Numbers<Double>("Ground Motion", "GroundMotion", 1.0D, 0.2D, 1.2D, 0.05D);
    private final Numbers<Double> airMotion = new Numbers<Double>("Air Motion", "AirMotion", 1.0D, 0.2D, 1.2D, 0.05D);
    private final Numbers<Double> speedMotion = new Numbers<Double>("Speed Motion", "SpeedMotion", 1.0D, 0.2D, 1.2D, 0.05D);
    private final Numbers<Float> tellyStartRotationMinSpeed =
            new Numbers<Float>("Telly Start Min", "TellyStartMin", 90.0F, 1.0F, 180.0F, 1.0F);
    private final Numbers<Float> tellyStartRotationMaxSpeed =
            new Numbers<Float>("Telly Start Max", "TellyStartMax", 95.0F, 1.0F, 180.0F, 1.0F);
    private final Numbers<Float> tellyNormalRotationMinSpeed =
            new Numbers<Float>("Telly Normal Min", "TellyNormalMin", 30.0F, 1.0F, 180.0F, 1.0F);
    private final Numbers<Float> tellyNormalRotationMaxSpeed =
            new Numbers<Float>("Telly Normal Max", "TellyNormalMax", 35.0F, 1.0F, 180.0F, 1.0F);
    public static int count;

    private final float[] lastErrors = new float[20];
    private int errorIndex;
    private int rotationTick;
    private int lastSlot = -1;
    private int blockCount = -1;
    private float yaw = -180.0F;
    private float pitch = 0.0F;
    private boolean canRotate;
    private int towerTick;
    private int towerDelay;
    private int stage;
    private int startY = 256;
    private boolean shouldKeepY;
    private boolean towering;
    private EnumFacing targetFacing;
    private float lastYaw;
    private float lastYawChange;
    private float lastPitchChange;
    private boolean holdingSneak;
    private Field rightClickDelayField;
    private Channel rotationChannel;

    public Scaffold() {
        super("Scaffold", Keyboard.KEY_NONE, ModuleType.World, "Place blocks under you");
        addValues(rotationMode, keepY, tower, keepYOnPress, multiPlace, safeWalk, swing, itemSpoof,
                blockCounter, stopSprint, groundMotion, airMotion, speedMotion,
                tellyStartRotationMinSpeed, tellyStartRotationMaxSpeed,
                tellyNormalRotationMinSpeed, tellyNormalRotationMaxSpeed);
        Chinese = "自动搭路";
    }

    @Override
    public void enable() {
        lastSlot = isInGame() ? mc.thePlayer.inventory.currentItem : -1;
        blockCount = -1;
        rotationTick = 3;
        yaw = -180.0F;
        pitch = 0.0F;
        canRotate = false;
        towerTick = 0;
        towerDelay = 0;
        stage = 0;
        startY = isInGame() ? MathHelper.floor_double(mc.thePlayer.posY) : 256;
        shouldKeepY = false;
        towering = false;
        targetFacing = null;
        lastYaw = 0.0F;
        lastYawChange = 0.0F;
        lastPitchChange = 0.0F;
        errorIndex = 0;
        injectRotationHandler();
    }

    @Override
    public void disable() {
        if (mc.thePlayer != null && lastSlot != -1 && !Boolean.TRUE.equals(itemSpoof.getValue())) {
            mc.thePlayer.inventory.currentItem = lastSlot;
        }
        removeRotationHandler();
        releaseSneak();
        resetVisibleRotation();
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        if (!isInGame() || mc.currentScreen != null) {
            releaseSneak();
            canRotate = false;
            return;
        }

        injectRotationHandler();
        resetRightClickDelay();
        onPreUpdate();
        updateMovement();
        updateSafeWalk();
        updateTower();
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (isInGame() && getState() && rotationMode.getValue() != RotationMode.NONE && canRotate) {
            syncVisibleRotation(yaw, pitch);
        }
    }

    @SubscribeEvent
    public void onRender(RenderGameOverlayEvent.Text event) {
        if (!isInGame() || !Boolean.TRUE.equals(blockCounter.getValue())) {
            return;
        }
        int total = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.stackSize > 0) {
                Item item = stack.getItem();
                if (item instanceof ItemBlock && isBlock(stack)) {
                    total += stack.stackSize;
                }
            }
        }
        count = total;
        ScaledResolution sr = new ScaledResolution(mc);
        String text = String.format("%d block%s left", total, total != 1 ? "s" : "");
        int color = total > 0 ? Color.WHITE.getRGB() : new Color(255, 85, 85).getRGB();
        GlStateManager.pushMatrix();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        mc.fontRendererObj.drawStringWithShadow(text,
                sr.getScaledWidth() / 2.0F + mc.fontRendererObj.FONT_HEIGHT * 1.5F,
                sr.getScaledHeight() / 2.0F - mc.fontRendererObj.FONT_HEIGHT / 2.0F + 1.0F,
                color);
        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
    }

    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        if (!isInGame()) {
            return;
        }
        if (event.button == 0 || event.button == 1) {
            event.setCanceled(true);
        }
    }

    private void onPreUpdate() {
        if (rotationTick > 0) {
            rotationTick--;
        }
        if (mc.thePlayer.onGround) {
            if (stage > 0) {
                stage--;
            }
            if (stage < 0) {
                stage++;
            }
            if (stage == 0
                    && keepY.getValue() != KeepYMode.NONE
                    && (!Boolean.TRUE.equals(keepYOnPress.getValue()) || mc.thePlayer.isUsingItem())
                    && !mc.gameSettings.keyBindJump.isKeyDown()) {
                stage = 1;
            }
            startY = shouldKeepY ? startY : MathHelper.floor_double(mc.thePlayer.posY);
            shouldKeepY = false;
            towering = false;
        }

        updateHeldBlock();
        if (blockCount <= 0) {
            canRotate = false;
            return;
        }

        float eventYaw = mc.thePlayer.rotationYaw;
        float eventPitch = mc.thePlayer.rotationPitch;
        float currentYaw = getCurrentYaw();
        float yawDiffTo180 = wrapAngleDiff(currentYaw - 180.0F, eventYaw);
        float diagonalYaw = isDiagonal(currentYaw)
                ? yawDiffTo180
                : wrapAngleDiff(currentYaw - 135.0F * ((currentYaw + 180.0F) % 90.0F < 45.0F ? 1.0F : -1.0F), eventYaw);

        if (!canRotate) {
            updateBaseRotation(yawDiffTo180, diagonalYaw, eventYaw);
        }

        BlockData blockData = getBlockData();
        Vec3 hitVec = null;
        if (blockData != null) {
            hitVec = updatePreciseRotation(blockData, eventYaw);
        }

        if (canRotate && isMoving() && Math.abs(MathHelper.wrapAngleTo180_float(yawDiffTo180 - yaw)) < 90.0F) {
            if (rotationMode.getValue() == RotationMode.BACKWARDS) {
                yaw = quantizeAngle(yawDiffTo180);
            } else if (rotationMode.getValue() == RotationMode.STRAFE) {
                yaw = quantizeAngle(diagonalYaw);
            }
        }

        if (rotationMode.getValue() != RotationMode.NONE && canRotate) {
            float targetYaw = yaw;
            float targetPitch = pitch;
            if (towering && (mc.thePlayer.motionY > 0.0D || mc.thePlayer.posY > startY + 1.0D)) {
                float yawDiff = MathHelper.wrapAngleTo180_float(yaw - eventYaw);
                float tolerance = rotationTick >= 2
                        ? randomFloat(tellyStartRotationMinSpeed.getValue(), tellyStartRotationMaxSpeed.getValue())
                        : randomFloat(tellyNormalRotationMinSpeed.getValue(), tellyNormalRotationMaxSpeed.getValue());
                if (Math.abs(yawDiff) > tolerance) {
                    targetYaw = quantizeAngle(eventYaw + clampAngle(yawDiff, tolerance));
                    rotationTick = Math.max(rotationTick, 1);
                }
            }
            if (isTowering()) {
                float yawDelta = MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw - eventYaw);
                targetYaw = quantizeAngle(eventYaw + yawDelta * randomFloat(0.98F, 0.99F));
                targetPitch = quantizeAngle(randomFloat(30.0F, 80.0F));
                rotationTick = 3;
                towering = true;
            }
            yaw = targetYaw;
            pitch = MathHelper.clamp_float(targetPitch, -90.0F, 90.0F);
            syncVisibleRotation(yaw, pitch);
        }

        if (blockData != null && hitVec != null && rotationTick <= 0) {
            place(blockData.blockPos(), blockData.facing(), hitVec);
            if (Boolean.TRUE.equals(multiPlace.getValue())) {
                for (int i = 0; i < 3; i++) {
                    blockData = getBlockData();
                    if (blockData == null) {
                        break;
                    }
                    MovingObjectPosition mop = rayTrace(yaw, pitch);
                    if (mop != null && mop.typeOfHit == MovingObjectType.BLOCK
                            && mop.getBlockPos().equals(blockData.blockPos()) && mop.sideHit == blockData.facing()) {
                        place(blockData.blockPos(), blockData.facing(), mop.hitVec);
                    } else {
                        hitVec = getClickVec(blockData.blockPos(), blockData.facing());
                        double dx = hitVec.xCoord - mc.thePlayer.posX;
                        double dy = hitVec.yCoord - mc.thePlayer.posY - mc.thePlayer.getEyeHeight();
                        double dz = hitVec.zCoord - mc.thePlayer.posZ;
                        float[] rotations = getRotationsTo(dx, dy, dz, eventYaw, eventPitch);
                        if (Math.abs(rotations[0] - yaw) >= 120.0F || Math.abs(rotations[1] - pitch) >= 60.0F) {
                            break;
                        }
                        mop = rayTrace(rotations[0], rotations[1]);
                        if (mop == null || mop.typeOfHit != MovingObjectType.BLOCK
                                || !mop.getBlockPos().equals(blockData.blockPos()) || mop.sideHit != blockData.facing()) {
                            break;
                        }
                        place(blockData.blockPos(), blockData.facing(), mop.hitVec);
                    }
                }
            }
        }

        if (targetFacing != null) {
            if (rotationTick <= 0) {
                BlockPos belowPlayer = new BlockPos(MathHelper.floor_double(mc.thePlayer.posX),
                        MathHelper.floor_double(mc.thePlayer.posY) - 1,
                        MathHelper.floor_double(mc.thePlayer.posZ));
                place(belowPlayer, targetFacing, getHitVec(belowPlayer, targetFacing, yaw, pitch));
            }
            targetFacing = null;
        } else if ((keepY.getValue() == KeepYMode.EXTRA || keepY.getValue() == KeepYMode.TELLY)
                && stage > 0 && !mc.thePlayer.onGround) {
            int nextBlockY = MathHelper.floor_double(mc.thePlayer.posY + mc.thePlayer.motionY);
            if (nextBlockY <= startY && mc.thePlayer.posY > startY + 1.0D) {
                shouldKeepY = true;
                blockData = getBlockData();
                if (blockData != null && rotationTick <= 0) {
                    place(blockData.blockPos(), blockData.facing(), getHitVec(blockData.blockPos(), blockData.facing(), yaw, pitch));
                }
            }
        }
    }

    private void updateBaseRotation(float yawDiffTo180, float diagonalYaw, float eventYaw) {
        switch (rotationMode.getValue()) {
            case VANILLA:
            case STRAFE:
                if (yaw == -180.0F && pitch == 0.0F) {
                    yaw = quantizeAngle(diagonalYaw);
                    pitch = quantizeAngle(85.0F);
                } else {
                    yaw = quantizeAngle(diagonalYaw);
                }
                break;
            case BACKWARDS:
                if (yaw == -180.0F && pitch == 0.0F) {
                    yaw = quantizeAngle(yawDiffTo180);
                    pitch = quantizeAngle(85.0F);
                } else {
                    yaw = quantizeAngle(yawDiffTo180);
                }
                break;
            case TEST:
                if (yaw == -180.0F && pitch == 0.0F) {
                    yaw = quantizeAngle((float) (diagonalYaw + randomDouble(0.7D, 1.5D)));
                    pitch = quantizeAngle(85.0F);
                }
                break;
            case PREDICTION:
                BlockData currentBlockData = getBlockData();
                if (currentBlockData != null) {
                    float[] targetRots = getRotations(getVec3(currentBlockData));
                    float targetYaw = targetRots[0];
                    float targetPitch = targetRots[1];
                    float predictedYaw = getPredictedYaw();
                    float currentYaw = yaw;
                    float currentPitch = pitch;
                    float yawToTarget = MathHelper.wrapAngleTo180_float(targetYaw - currentYaw);
                    float pitchToTarget = targetPitch - currentPitch;
                    float absYawDiff = Math.abs(yawToTarget);
                    float distance = (float) mc.thePlayer.getDistance(
                            currentBlockData.blockPos().getX() + 0.5D,
                            currentBlockData.blockPos().getY() + 0.5D,
                            currentBlockData.blockPos().getZ() + 0.5D);
                    float currentSpeed = getCurrentSpeed(distance);
                    float actualYawDiff = MathHelper.wrapAngleTo180_float(getCurrentYaw() - lastYaw);
                    float error = Math.abs(actualYawDiff - lastYawChange);
                    lastErrors[errorIndex] = error;
                    errorIndex = (errorIndex + 1) % lastErrors.length;
                    float avgError = 0.0F;
                    for (float value : lastErrors) {
                        avgError += value;
                    }
                    avgError /= lastErrors.length;
                    if (avgError > 5.0F) {
                        currentSpeed *= 0.8F;
                    } else if (avgError < 1.0F) {
                        currentSpeed *= 1.1F;
                    }
                    float yawChange;
                    if (absYawDiff > 90.0F) {
                        yawChange = Math.signum(yawToTarget) * currentSpeed * 1.2F;
                    } else if (absYawDiff > 30.0F) {
                        yawChange = Math.signum(yawToTarget) * currentSpeed * 0.8F;
                    } else {
                        float fineSpeed = currentSpeed * 0.3F;
                        yawChange = MathHelper.clamp_float(yawToTarget * 0.2F, -fineSpeed, fineSpeed);
                    }
                    float inertia = 0.3F;
                    yawChange = lastYawChange * inertia + yawChange * (1.0F - inertia);
                    lastYawChange = yawChange;
                    float pitchChange = Math.signum(pitchToTarget) * currentSpeed * 0.3F;
                    pitchChange = lastPitchChange * inertia + pitchChange * (1.0F - inertia);
                    lastPitchChange = pitchChange;
                    BlockPos futureBlockPos = new BlockPos(
                            MathHelper.floor_double(mc.thePlayer.posX + mc.thePlayer.motionX),
                            MathHelper.floor_double(mc.thePlayer.posY + mc.thePlayer.motionY) - 1,
                            MathHelper.floor_double(mc.thePlayer.posZ + mc.thePlayer.motionZ));
                    if (isReplaceable(futureBlockPos)) {
                        float[] futureRots = getRotations(
                                currentBlockData.blockPos().getX() + 0.5D,
                                currentBlockData.blockPos().getY() + 0.5D,
                                currentBlockData.blockPos().getZ() + 0.5D,
                                mc.thePlayer.posX + mc.thePlayer.motionX,
                                mc.thePlayer.posY + mc.thePlayer.motionY + mc.thePlayer.getEyeHeight(),
                                mc.thePlayer.posZ + mc.thePlayer.motionZ);
                        yawChange = yawChange * 0.7F + MathHelper.wrapAngleTo180_float(futureRots[0] - getCurrentYaw()) * 0.3F;
                    }
                    float jitterAmount = absYawDiff < 5.0F
                            ? randomFloat(-0.35F, 0.35F)
                            : towering ? randomFloat(-0.2F, 0.2F) : randomFloat(-0.3F, 0.3F);
                    yawChange = MathHelper.clamp_float(yawChange + jitterAmount, -currentSpeed, currentSpeed);
                    pitchChange = MathHelper.clamp_float(pitchChange, -currentSpeed * 0.4F, currentSpeed * 0.4F);
                    float newYaw = currentYaw + yawChange;
                    float newPitch = MathHelper.clamp_float(currentPitch + pitchChange, -90.0F, 90.0F);
                    if (yaw == -180.0F && pitch == 0.0F) {
                        newYaw = quantizeAngle(predictedYaw);
                        newPitch = 85.0F;
                    }
                    yaw = quantizeAngle(newYaw);
                    pitch = quantizeAngle(newPitch);
                    lastYaw = yaw;
                } else if (yaw != -180.0F) {
                    float yawDiff = MathHelper.clamp_float(MathHelper.wrapAngleTo180_float(eventYaw - yaw), -3.0F, 3.0F);
                    yaw = quantizeAngle(yaw + yawDiff);
                    if (pitch > 10.0F) {
                        pitch -= 1.0F;
                    } else if (pitch < -10.0F) {
                        pitch += 1.0F;
                    }
                }
                break;
            case NONE:
            default:
                break;
        }
    }

    private Vec3 updatePreciseRotation(BlockData blockData, float eventYaw) {
        double[] x = PLACE_OFFSETS;
        double[] y = PLACE_OFFSETS;
        double[] z = PLACE_OFFSETS;
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
        Vec3 hitVec = null;
        for (double dx : x) {
            for (double dy : y) {
                for (double dz : z) {
                    double relX = blockData.blockPos().getX() + dx - mc.thePlayer.posX;
                    double relY = blockData.blockPos().getY() + dy - mc.thePlayer.posY - mc.thePlayer.getEyeHeight();
                    double relZ = blockData.blockPos().getZ() + dz - mc.thePlayer.posZ;
                    float baseYaw = wrapAngleDiff(yaw, eventYaw);
                    float[] rotations = getRotationsTo(relX, relY, relZ, baseYaw, pitch);
                    MovingObjectPosition mop = rayTrace(rotations[0], rotations[1]);
                    if (mop != null && mop.typeOfHit == MovingObjectType.BLOCK
                            && mop.getBlockPos().equals(blockData.blockPos()) && mop.sideHit == blockData.facing()) {
                        float totalDiff = Math.abs(rotations[0] - baseYaw) + Math.abs(rotations[1] - pitch);
                        if ((bestYaw == -180.0F && bestPitch == 0.0F) || totalDiff < bestDiff) {
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
            yaw = bestYaw;
            pitch = bestPitch;
            canRotate = true;
        }
        return hitVec;
    }

    private BlockData getBlockData() {
        int playerY = MathHelper.floor_double(mc.thePlayer.posY);
        int targetY = (stage != 0 && !shouldKeepY ? Math.min(playerY, startY) : playerY) - 1;
        BlockPos targetPos = new BlockPos(MathHelper.floor_double(mc.thePlayer.posX), targetY,
                MathHelper.floor_double(mc.thePlayer.posZ));
        if (!isReplaceable(targetPos)) {
            return null;
        }

        ArrayList<BlockPos> positions = new ArrayList<BlockPos>();
        for (int x = -4; x <= 4; x++) {
            for (int y = -4; y <= 0; y++) {
                for (int z = -4; z <= 4; z++) {
                    BlockPos pos = targetPos.add(x, y, z);
                    if (!isReplaceable(pos)
                            && !isInteractable(pos)
                            && mc.thePlayer.getDistance(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D)
                            <= mc.playerController.getBlockReachDistance()
                            && (stage == 0 || shouldKeepY || pos.getY() < startY)) {
                        for (EnumFacing facing : EnumFacing.VALUES) {
                            if (facing != EnumFacing.DOWN && isReplaceable(pos.offset(facing))) {
                                positions.add(pos);
                            }
                        }
                    }
                }
            }
        }
        if (positions.isEmpty()) {
            return null;
        }
        positions.sort(Comparator.comparingDouble(o -> o.distanceSqToCenter(
                targetPos.getX() + 0.5D, targetPos.getY() + 0.5D, targetPos.getZ() + 0.5D)));
        BlockPos blockPos = positions.get(0);
        EnumFacing facing = getBestFacing(blockPos, targetPos);
        return facing == null ? null : new BlockData(blockPos, facing);
    }

    private EnumFacing getBestFacing(BlockPos support, BlockPos target) {
        double offset = 0.0D;
        EnumFacing best = null;
        for (EnumFacing facing : EnumFacing.VALUES) {
            if (facing == EnumFacing.DOWN) {
                continue;
            }
            BlockPos pos = support.offset(facing);
            if (pos.getY() <= target.getY()) {
                double distance = pos.distanceSqToCenter(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D);
                if (best == null || distance < offset || distance == offset && facing == EnumFacing.UP) {
                    offset = distance;
                    best = facing;
                }
            }
        }
        return best;
    }

    private void place(BlockPos blockPos, EnumFacing facing, Vec3 hitVec) {
        ItemStack stack = mc.thePlayer.getHeldItem();
        if (!isBlock(stack) || blockCount <= 0) {
            return;
        }
        if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, stack, blockPos, facing, hitVec)) {
            if (mc.playerController.getCurrentGameType() != WorldSettings.GameType.CREATIVE) {
                blockCount--;
            }
            if (Boolean.TRUE.equals(swing.getValue())) {
                mc.thePlayer.swingItem();
            } else {
                mc.thePlayer.sendQueue.addToSendQueue(new C0APacketAnimation());
            }
        }
    }

    private void updateHeldBlock() {
        ItemStack stack = mc.thePlayer.getHeldItem();
        int count = isBlock(stack) ? stack.stackSize : 0;
        blockCount = blockCount < 0 ? count : Math.min(blockCount, count);
        if (blockCount > 0) {
            return;
        }
        int slot = mc.thePlayer.inventory.currentItem;
        if (blockCount == 0) {
            slot--;
        }
        for (int i = slot; i > slot - 9; i--) {
            int hotbarSlot = (i % 9 + 9) % 9;
            ItemStack candidate = mc.thePlayer.inventory.getStackInSlot(hotbarSlot);
            if (isBlock(candidate)) {
                if (Boolean.TRUE.equals(itemSpoof.getValue())) {
                    mc.thePlayer.sendQueue.addToSendQueue(new C09PacketHeldItemChange(hotbarSlot));
                } else {
                    mc.thePlayer.inventory.currentItem = hotbarSlot;
                }
                blockCount = candidate.stackSize;
                break;
            }
        }
    }

    private void updateMovement() {
        double speed = getSpeedMultiplier();
        if (speed != 1.0D) {
            if (mc.thePlayer.movementInput.moveForward != 0.0F && mc.thePlayer.movementInput.moveStrafe != 0.0F) {
                mc.thePlayer.movementInput.moveForward *= 1.0F / MathHelper.sqrt_float(2.0F);
                mc.thePlayer.movementInput.moveStrafe *= 1.0F / MathHelper.sqrt_float(2.0F);
            }
            mc.thePlayer.movementInput.moveForward *= speed;
            mc.thePlayer.movementInput.moveStrafe *= speed;
        }
        if (Boolean.TRUE.equals(stopSprint.getValue()) && !isTowering()) {
            mc.thePlayer.setSprinting(false);
        }
        if (mc.thePlayer.onGround && stage > 0 && isMoving()) {
            mc.thePlayer.movementInput.jump = true;
        }
    }

    private void updateTower() {
        if (!mc.gameSettings.keyBindJump.isKeyDown() || !isHoldingBlock()) {
            towerTick = 0;
            towerDelay = 0;
            return;
        }
        int yState = (int) (mc.thePlayer.posY % 1.0D * 100.0D);
        switch (tower.getValue()) {
            case VANILLA:
                if (mc.thePlayer.onGround) {
                    mc.thePlayer.motionY = 0.42D;
                } else if (mc.thePlayer.motionY < 0.1D) {
                    mc.thePlayer.motionY = -0.08D;
                }
                break;
            case EXTRA:
                switch (towerTick) {
                    case 0:
                        if (mc.thePlayer.onGround) {
                            towerTick = 1;
                            mc.thePlayer.motionY = -0.0784000015258789D;
                        }
                        break;
                    case 1:
                        if (yState == 0 && isAirBelow()) {
                            startY = MathHelper.floor_double(mc.thePlayer.posY);
                            towerTick = 2;
                            towerDelay++;
                            mc.thePlayer.motionY = 0.42F;
                        } else {
                            towerTick = 0;
                            towerDelay = 0;
                        }
                        break;
                    case 2:
                        towerTick = 3;
                        mc.thePlayer.motionY = 0.75D - mc.thePlayer.posY % 1.0D;
                        break;
                    case 3:
                        if (towerDelay >= 4) {
                            towerTick = 4;
                            towerDelay = 0;
                        } else {
                            towerTick = 1;
                            mc.thePlayer.motionY = 1.0D - mc.thePlayer.posY % 1.0D;
                        }
                        break;
                    case 4:
                        towerTick = 5;
                        break;
                    case 5:
                        if (!isAirBelow()) {
                            towerTick = 0;
                        } else {
                            towerTick = 1;
                            mc.thePlayer.motionY -= 0.08D;
                            mc.thePlayer.motionY *= 0.98F;
                            mc.thePlayer.motionY -= 0.08D;
                            mc.thePlayer.motionY *= 0.98F;
                        }
                        break;
                    default:
                        towerTick = 0;
                        towerDelay = 0;
                        break;
                }
                break;
            case TELLY:
                switch (towerTick) {
                    case 0:
                        if (mc.thePlayer.onGround) {
                            towerTick = 1;
                            mc.thePlayer.motionY = -0.0784000015258789D;
                        }
                        break;
                    case 1:
                        if (yState == 0 && isAirBelow()) {
                            startY = MathHelper.floor_double(mc.thePlayer.posY);
                            if (!isMoving()) {
                                towerDelay = 2;
                                setSpeed(0.0D, getMoveYaw());
                                EnumFacing facing = yawToFacing(MathHelper.wrapAngleTo180_float(yaw - 180.0F));
                                double distance = distanceToEdge(facing);
                                if (distance > 0.1D && mc.thePlayer.onGround) {
                                    Vec3i directionVec = facing.getDirectionVec();
                                    double offset = Math.min(getRandomOffset(), distance - 0.05D);
                                    double jitter = randomDouble(0.02D, 0.03D);
                                    AxisAlignedBB nextBox = mc.thePlayer.getEntityBoundingBox().offset(
                                            directionVec.getX() * (offset - jitter), 0.0D,
                                            directionVec.getZ() * (offset - jitter));
                                    if (mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, nextBox).isEmpty()) {
                                        mc.thePlayer.motionY = -0.0784000015258789D;
                                        mc.thePlayer.setPosition(nextBox.minX + (nextBox.maxX - nextBox.minX) / 2.0D,
                                                nextBox.minY,
                                                nextBox.minZ + (nextBox.maxZ - nextBox.minZ) / 2.0D);
                                    }
                                } else {
                                    towerTick = 2;
                                    targetFacing = facing;
                                    mc.thePlayer.motionY = 0.42F;
                                }
                            } else {
                                towerTick = 2;
                                towerDelay++;
                                mc.thePlayer.motionY = 0.42F;
                                setSpeed(getHorizontalSpeed(), getMoveYaw());
                            }
                        } else {
                            towerTick = 0;
                            towerDelay = 0;
                        }
                        break;
                    case 2:
                        towerTick = 3;
                        mc.thePlayer.motionY -= randomDouble(0.00101D, 0.00109D);
                        break;
                    case 3:
                        if (towerDelay >= 4) {
                            towerTick = 4;
                            towerDelay = 0;
                        } else {
                            towerTick = 1;
                            mc.thePlayer.motionY = 1.0D - mc.thePlayer.posY % 1.0D;
                        }
                        break;
                    case 4:
                        towerTick = 5;
                        break;
                    case 5:
                        if (!isAirBelow()) {
                            towerTick = 0;
                        } else {
                            towerTick = 1;
                            mc.thePlayer.motionY -= 0.08D;
                            mc.thePlayer.motionY *= 0.98F;
                            mc.thePlayer.motionY -= 0.08D;
                            mc.thePlayer.motionY *= 0.98F;
                        }
                        break;
                    default:
                        towerTick = 0;
                        towerDelay = 0;
                        break;
                }
                break;
            case NONE:
            default:
                towerTick = 0;
                towerDelay = 0;
                break;
        }
    }

    private void updateSafeWalk() {
        if (!Boolean.TRUE.equals(safeWalk.getValue())) {
            releaseSneak();
            return;
        }
        boolean edge = mc.thePlayer.onGround && mc.thePlayer.motionY <= 0.0D
                && canMove(mc.thePlayer.motionX, mc.thePlayer.motionZ, -1.0D);
        setSneak(edge);
    }

    private boolean canMove(double x, double z, double y) {
        AxisAlignedBB box = mc.thePlayer.getEntityBoundingBox().offset(x, y, z);
        return mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, box).isEmpty();
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

    private boolean isTowering() {
        if (mc.thePlayer.onGround && isMoving() && !isAirAbove()) {
            boolean keepYTelly = keepY.getValue() == KeepYMode.TELLY;
            boolean towerTelly = tower.getValue() == TowerMode.TELLY;
            return keepYTelly && stage > 0 || towerTelly && mc.gameSettings.keyBindJump.isKeyDown();
        }
        return false;
    }

    private boolean isMoving() {
        return mc.gameSettings.keyBindForward.isKeyDown() != mc.gameSettings.keyBindBack.isKeyDown()
                || mc.gameSettings.keyBindLeft.isKeyDown() != mc.gameSettings.keyBindRight.isKeyDown();
    }

    private int getForwardValue() {
        int forward = 0;
        if (mc.gameSettings.keyBindForward.isKeyDown()) {
            forward++;
        }
        if (mc.gameSettings.keyBindBack.isKeyDown()) {
            forward--;
        }
        return forward;
    }

    private int getLeftValue() {
        int left = 0;
        if (mc.gameSettings.keyBindLeft.isKeyDown()) {
            left++;
        }
        if (mc.gameSettings.keyBindRight.isKeyDown()) {
            left--;
        }
        return left;
    }

    private float getCurrentYaw() {
        return adjustYaw(mc.thePlayer.rotationYaw, getForwardValue(), getLeftValue());
    }

    private float getMoveYaw() {
        return adjustYaw(mc.thePlayer.rotationYaw, mc.thePlayer.movementInput.moveForward,
                mc.thePlayer.movementInput.moveStrafe);
    }

    private double getHorizontalSpeed() {
        return Math.hypot(mc.thePlayer.motionX, mc.thePlayer.motionZ);
    }

    private void setSpeed(double speed, float yaw) {
        mc.thePlayer.motionX = -Math.sin(Math.toRadians(yaw)) * speed;
        mc.thePlayer.motionZ = Math.cos(Math.toRadians(yaw)) * speed;
    }

    private EnumFacing yawToFacing(float yaw) {
        if (yaw < -135.0F || yaw > 135.0F) {
            return EnumFacing.NORTH;
        }
        if (yaw < -45.0F) {
            return EnumFacing.EAST;
        }
        return yaw < 45.0F ? EnumFacing.SOUTH : EnumFacing.WEST;
    }

    private double distanceToEdge(EnumFacing facing) {
        switch (facing) {
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

    private double getRandomOffset() {
        return 0.2155D - randomDouble(1.0E-4D, 9.0E-4D);
    }

    private float adjustYaw(float yaw, float forward, float strafe) {
        if (forward < 0.0F) {
            yaw += 180.0F;
        }
        if (strafe != 0.0F) {
            float multiplier = forward == 0.0F ? 1.0F : 0.5F * Math.signum(forward);
            yaw += -90.0F * multiplier * Math.signum(strafe);
        }
        return MathHelper.wrapAngleTo180_float(yaw);
    }

    private boolean isDiagonal(float yaw) {
        float absYaw = Math.abs(yaw % 90.0F);
        return absYaw > 20.0F && absYaw < 70.0F;
    }

    private float getPredictedYaw() {
        float currentMoveYaw = getCurrentYaw();
        if (isDiagonal(currentMoveYaw)) {
            return currentMoveYaw - 180.0F;
        }
        float sideMultiplier = (currentMoveYaw + 180.0F) % 90.0F < 45.0F ? 1.0F : -1.0F;
        return currentMoveYaw - 135.0F * sideMultiplier;
    }

    private float getCurrentSpeed(float distance) {
        float baseSpeed = towering ? 40.0F : getSpeedLevel() > 0 ? 35.0F : 25.0F;
        return Math.min(45.0F, Math.max(10.0F, baseSpeed * Math.min(1.2F, distance)));
    }

    private double getSpeedMultiplier() {
        if (!mc.thePlayer.onGround) {
            return airMotion.getValue();
        }
        return getSpeedLevel() > 0 ? speedMotion.getValue() : groundMotion.getValue();
    }

    private int getSpeedLevel() {
        if (mc.thePlayer.isPotionActive(Potion.moveSpeed)) {
            return mc.thePlayer.getActivePotionEffect(Potion.moveSpeed).getAmplifier() + 1;
        }
        return 0;
    }

    private boolean isAirBelow() {
        AxisAlignedBB box = mc.thePlayer.getEntityBoundingBox().offset(0.0D, -1.0D, 0.0D);
        return !mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, box).isEmpty();
    }

    private boolean isAirAbove() {
        return !mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer,
                mc.thePlayer.getEntityBoundingBox().offset(0.0D, 1.0D, 0.0D)).isEmpty();
    }

    private boolean isHoldingBlock() {
        return isBlock(mc.thePlayer.getHeldItem());
    }

    private boolean isBlock(ItemStack stack) {
        if (stack == null || stack.stackSize < 1 || !(stack.getItem() instanceof ItemBlock)) {
            return false;
        }
        Block block = ((ItemBlock) stack.getItem()).getBlock();
        return block != null && block != Blocks.air && !isInteractable(block) && isSolid(block);
    }

    private boolean isReplaceable(BlockPos pos) {
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        if (block == null) {
            return true;
        }
        if (!block.getMaterial().isReplaceable()) {
            return false;
        }
        return !(block instanceof BlockSnow) || !(block.getBlockBoundsMaxY() > 0.125D);
    }

    private boolean isInteractable(BlockPos pos) {
        return isInteractable(mc.theWorld.getBlockState(pos).getBlock());
    }

    private boolean isInteractable(Block block) {
        if (block instanceof BlockContainer || block instanceof BlockWorkbench || block instanceof BlockAnvil
                || block instanceof BlockBed || block instanceof BlockTrapDoor || block instanceof BlockFenceGate
                || block instanceof BlockFence || block instanceof BlockButton || block instanceof BlockLever
                || block instanceof BlockJukebox) {
            return true;
        }
        return block instanceof BlockDoor && block.getMaterial() != Material.iron;
    }

    private boolean isSolid(Block block) {
        return !(block instanceof BlockStairs)
                && !(block instanceof BlockSlab)
                && !(block instanceof BlockEndPortalFrame)
                && !(block instanceof BlockEndPortal)
                && !(block instanceof BlockVine)
                && !(block instanceof BlockPumpkin)
                && !(block instanceof BlockCactus)
                && !(block instanceof BlockBush)
                && !(block instanceof BlockFalling)
                && !(block instanceof BlockWeb)
                && !(block instanceof BlockPane)
                && !(block instanceof BlockCarpet)
                && !(block instanceof BlockSnow)
                && !(block instanceof BlockFence)
                && !(block instanceof BlockFenceGate)
                && !(block instanceof BlockWall)
                && !(block instanceof BlockLadder)
                && !(block instanceof BlockTorch)
                && !(block instanceof BlockRedstoneWire)
                && !(block instanceof BlockRedstoneDiode)
                && !(block instanceof BlockBasePressurePlate)
                && !(block instanceof BlockTripWire)
                && !(block instanceof BlockTripWireHook)
                && !(block instanceof BlockRailBase)
                && !(block instanceof BlockSlime)
                && !(block instanceof BlockTNT);
    }

    private Vec3 getVec3(BlockData data) {
        BlockPos pos = data.blockPos();
        EnumFacing face = data.facing();
        return new Vec3(pos.getX() + 0.5D + face.getFrontOffsetX() * 0.5D,
                pos.getY() + 0.5D + face.getFrontOffsetY() * 0.5D,
                pos.getZ() + 0.5D + face.getFrontOffsetZ() * 0.5D);
    }

    private Vec3 getHitVec(BlockPos blockPos, EnumFacing facing, float yaw, float pitch) {
        MovingObjectPosition mop = rayTrace(yaw, pitch);
        if (mop != null && mop.typeOfHit == MovingObjectType.BLOCK
                && mop.getBlockPos().equals(blockPos) && mop.sideHit == facing) {
            return mop.hitVec;
        }
        return getClickVec(blockPos, facing);
    }

    private Vec3 getClickVec(BlockPos blockPos, EnumFacing facing) {
        Block block = mc.theWorld.getBlockState(blockPos).getBlock();
        Vec3 vec = new Vec3(blockPos.getX() + clamp(randomDouble(0.0D, 1.0D), block.getBlockBoundsMinX(), block.getBlockBoundsMaxX()),
                blockPos.getY() + clamp(randomDouble(0.0D, 1.0D), block.getBlockBoundsMinY(), block.getBlockBoundsMaxY()),
                blockPos.getZ() + clamp(randomDouble(0.0D, 1.0D), block.getBlockBoundsMinZ(), block.getBlockBoundsMaxZ()));
        switch (facing) {
            case UP:
                return new Vec3(vec.xCoord, blockPos.getY() + block.getBlockBoundsMaxY(), vec.zCoord);
            case NORTH:
                return new Vec3(vec.xCoord, vec.yCoord, blockPos.getZ() + block.getBlockBoundsMinZ());
            case EAST:
                return new Vec3(blockPos.getX() + block.getBlockBoundsMaxX(), vec.yCoord, vec.zCoord);
            case SOUTH:
                return new Vec3(vec.xCoord, vec.yCoord, blockPos.getZ() + block.getBlockBoundsMaxZ());
            case WEST:
                return new Vec3(blockPos.getX() + block.getBlockBoundsMinX(), vec.yCoord, vec.zCoord);
            case DOWN:
            default:
                return new Vec3(vec.xCoord, blockPos.getY() + block.getBlockBoundsMinY(), vec.zCoord);
        }
    }

    private MovingObjectPosition rayTrace(float yaw, float pitch) {
        Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 lookVec = getLook(yaw, pitch);
        Vec3 targetPos = eyePos.addVector(lookVec.xCoord * mc.playerController.getBlockReachDistance(),
                lookVec.yCoord * mc.playerController.getBlockReachDistance(),
                lookVec.zCoord * mc.playerController.getBlockReachDistance());
        return mc.theWorld.rayTraceBlocks(eyePos, targetPos);
    }

    private Vec3 getLook(float yaw, float pitch) {
        float pitchRad = pitch * ((float) Math.PI / 180.0F);
        float yawRad = -yaw * ((float) Math.PI / 180.0F);
        float cosYaw = MathHelper.cos(yawRad);
        float sinYaw = MathHelper.sin(yawRad);
        float cosPitch = MathHelper.cos(pitchRad);
        float sinPitch = MathHelper.sin(pitchRad);
        return new Vec3(sinYaw * cosPitch, -sinPitch, cosYaw * cosPitch);
    }

    private float[] getRotations(Vec3 vec) {
        return getRotations(vec.xCoord, vec.yCoord, vec.zCoord);
    }

    private float[] getRotations(double posX, double posY, double posZ) {
        return getRotations(posX, posY, posZ, mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
    }

    private float[] getRotations(double rotX, double rotY, double rotZ, double startX, double startY, double startZ) {
        double x = rotX - startX;
        double y = rotY - startY;
        double z = rotZ - startZ;
        double dist = MathHelper.sqrt_double(x * x + z * z);
        float yaw = (float) (Math.atan2(z, x) * 180.0D / Math.PI) - 90.0F;
        float pitch = (float) (-(Math.atan2(y, dist) * 180.0D / Math.PI));
        return new float[]{yaw, pitch};
    }

    private float[] getRotationsTo(double targetX, double targetY, double targetZ, float currentYaw, float currentPitch) {
        double horizontalDistance = Math.sqrt(targetX * targetX + targetZ * targetZ);
        float yawDelta = MathHelper.wrapAngleTo180_float((float) (Math.atan2(targetZ, targetX) * 180.0D / Math.PI) - 90.0F - currentYaw);
        float pitchDelta = MathHelper.wrapAngleTo180_float((float) (-Math.atan2(targetY, horizontalDistance) * 180.0D / Math.PI) - currentPitch);
        yawDelta = Math.abs(yawDelta) <= 1.0F ? 0.0F : clampAngle(yawDelta, 180.0F);
        pitchDelta = Math.abs(pitchDelta) <= 1.0F ? 0.0F : clampAngle(pitchDelta, 180.0F);
        return new float[]{quantizeAngle(currentYaw + yawDelta), quantizeAngle(currentPitch + pitchDelta)};
    }

    private float wrapAngleDiff(float angle, float target) {
        return target + MathHelper.wrapAngleTo180_float(angle - target);
    }

    private float clampAngle(float angle, float maxAngle) {
        maxAngle = Math.max(0.0F, Math.min(180.0F, maxAngle));
        return MathHelper.clamp_float(angle, -maxAngle, maxAngle);
    }

    private float quantizeAngle(float angle) {
        return (float) (angle - angle % 0.0096F);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double randomDouble(double min, double max) {
        return min + Math.random() * (max - min);
    }

    private float randomFloat(float min, float max) {
        return (float) (min + Math.random() * (max - min));
    }

    private void syncVisibleRotation(float yaw, float pitch) {
        if (mc.thePlayer == null) {
            return;
        }
        mc.thePlayer.rotationYawHead = yaw;
        mc.thePlayer.prevRotationYawHead = yaw;
        mc.thePlayer.renderYawOffset = yaw;
        mc.thePlayer.prevRenderYawOffset = yaw;
    }

    private void resetVisibleRotation() {
        if (mc.thePlayer != null) {
            syncVisibleRotation(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
        }
    }

    private boolean shouldRewriteRotationPacket() {
        return getState() && isInGame() && rotationMode.getValue() != RotationMode.NONE && canRotate;
    }

    private C03PacketPlayer rewriteRotationPacket(C03PacketPlayer packet) {
        if (!shouldRewriteRotationPacket() || packet == null) {
            return null;
        }
        boolean onGround = packet.isOnGround();
        if (packet instanceof C03PacketPlayer.C06PacketPlayerPosLook
                || packet instanceof C03PacketPlayer.C04PacketPlayerPosition) {
            return new C03PacketPlayer.C06PacketPlayerPosLook(packet.getPositionX(), packet.getPositionY(),
                    packet.getPositionZ(), yaw, pitch, onGround);
        }
        return new C03PacketPlayer.C05PacketPlayerLook(yaw, pitch, onGround);
    }

    private void injectRotationHandler() {
        if (!isInGame() || mc.getNetHandler() == null) {
            return;
        }
        try {
            NetworkManager manager = mc.getNetHandler().getNetworkManager();
            Channel channel = getChannel(manager);
            if (channel == null || !channel.isOpen()) {
                return;
            }
            if (rotationChannel != null && rotationChannel != channel) {
                removeRotationHandler();
            }
            if (channel.pipeline().get(ROTATION_HANDLER_NAME) == null) {
                channel.pipeline().addBefore("packet_handler", ROTATION_HANDLER_NAME, new SilentRotationHandler(this));
            }
            rotationChannel = channel;
        } catch (Throwable ignored) {
            rotationChannel = null;
        }
    }

    private void removeRotationHandler() {
        Channel channel = rotationChannel;
        rotationChannel = null;
        if (channel == null) {
            return;
        }
        try {
            if (channel.isOpen() && channel.pipeline().get(ROTATION_HANDLER_NAME) != null) {
                channel.pipeline().remove(ROTATION_HANDLER_NAME);
            }
        } catch (Throwable ignored) {
        }
    }

    private Channel getChannel(NetworkManager manager) {
        for (String name : new String[]{"channel", "field_150746_k"}) {
            try {
                Field field = NetworkManager.class.getDeclaredField(name);
                field.setAccessible(true);
                Object value = field.get(manager);
                if (value instanceof Channel) {
                    return (Channel) value;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private void resetRightClickDelay() {
        try {
            if (rightClickDelayField == null) {
                for (String name : new String[]{"rightClickDelayTimer", "field_71467_ac"}) {
                    try {
                        rightClickDelayField = mc.getClass().getDeclaredField(name);
                        rightClickDelayField.setAccessible(true);
                        break;
                    } catch (Throwable ignored) {
                    }
                }
            }
            if (rightClickDelayField != null) {
                rightClickDelayField.setInt(mc, 0);
            }
        } catch (Throwable ignored) {
            rightClickDelayField = null;
        }
    }

    public boolean doSafeWalk() {
        return getState() && Boolean.TRUE.equals(safeWalk.getValue());
    }

    public int getSlot() {
        return lastSlot;
    }

    private static final class BlockData {
        private final BlockPos blockPos;
        private final EnumFacing facing;

        private BlockData(BlockPos blockPos, EnumFacing facing) {
            this.blockPos = blockPos;
            this.facing = facing;
        }

        private BlockPos blockPos() {
            return blockPos;
        }

        private EnumFacing facing() {
            return facing;
        }
    }

    private static final class SilentRotationHandler extends ChannelDuplexHandler {
        private final Scaffold scaffold;

        private SilentRotationHandler(Scaffold scaffold) {
            this.scaffold = scaffold;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (msg instanceof C03PacketPlayer) {
                C03PacketPlayer replacement = scaffold.rewriteRotationPacket((C03PacketPlayer) msg);
                if (replacement != null) {
                    super.write(ctx, replacement, promise);
                    return;
                }
            }
            super.write(ctx, msg, promise);
        }
    }
}
