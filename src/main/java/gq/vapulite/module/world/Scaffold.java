package gq.vapulite.module.world;

import gq.vapulite.module.Module;
import gq.vapulite.module.ModuleType;
import gq.vapulite.util.minecraft.RotationUtil;
import gq.vapulite.value.Mode;
import gq.vapulite.value.Numbers;
import gq.vapulite.value.Option;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.block.BlockFalling;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.BlockSnow;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class Scaffold extends Module {
    public enum SwingMode {
        CLIENT,
        SILENT,
        NONE
    }

    private enum ActionType {
        ROTATION,
        SWING,
        PLACE
    }

    private static final EnumFacing[] HORIZONTAL_FACES = new EnumFacing[]{
            EnumFacing.EAST,
            EnumFacing.WEST,
            EnumFacing.SOUTH,
            EnumFacing.NORTH
    };
    private static final String ROTATION_HANDLER_NAME = "vapulite_scaffold_silent_rotations";

    private final Option<Boolean> tower = new Option<Boolean>("Tower", "Tower", true);
    private final Option<Boolean> spoofHeldItem = new Option<Boolean>("Spoof Held Item", "SpoofHeldItem", false);
    private final Option<Boolean> autoJump = new Option<Boolean>("Auto Jump", "AutoJump", false);
    private final Option<Boolean> safeWalk = new Option<Boolean>("Safe Walk", "SafeWalk", true);
    private final Option<Boolean> keepY = new Option<Boolean>("No Y Gain", "NoYGain", true);
    private final Option<Boolean> noSprint = new Option<Boolean>("No Sprint", "NoSprint", false);
    private final Option<Boolean> rayTraceCheck = new Option<Boolean>("Ray Trace Check", "RayTraceCheck", false);
    private final Option<Boolean> expoPacing = new Option<Boolean>("Expo Pacing", "ExpoPacing", true);
    private final Mode<SwingMode> swing = new Mode<SwingMode>("Swing", "Swing", SwingMode.values(), SwingMode.SILENT);
    private final Numbers<Integer> placeDelay = new Numbers<Integer>("Place Delay", "PlaceDelay", 0, 0, 8, 1);
    private final Numbers<Double> expand = new Numbers<Double>("Expand", "Expand", 0.85D, 0.0D, 3.0D, 0.05D);
    private final Numbers<Double> rotationSpeed = new Numbers<Double>("Rotation Speed", "RotationSpeed", 28.0D, 4.0D, 90.0D, 1.0D);

    private final RotationUtil.State rotationState = new RotationUtil.State();
    private final List<ActionRecord> actionHistory = new ArrayList<ActionRecord>();

    private BlockData data;
    private BlockData lastPlacement;
    private int bestHotbarSlot = -1;
    private int ticksSincePlace;
    private int ticksSinceTarget;
    private int placedBlocks;
    private double startY;
    private boolean holdingSneak;
    private boolean wasOnGround;
    private float lastYaw;
    private float lastPitch;
    private int repeatedAngles;
    private int scaffoldTicks;
    private boolean hasRotationSample;
    private float sampledYaw;
    private float sampledPitch;
    private Channel rotationChannel;
    private Field rightClickDelayField;
    private boolean silentRotationReady;
    private float silentYaw;
    private float silentPitch;
    private float[] scaffoldAngles;
    private int expoHoldTicks;
    private int missingTargetTicks;

    public Scaffold() {
        super("Scaffold", Keyboard.KEY_NONE, ModuleType.World, "Place blocks under you");
        addValues(tower, spoofHeldItem, autoJump, safeWalk, keepY, noSprint, rayTraceCheck, expoPacing,
                swing, placeDelay, expand, rotationSpeed);
        Chinese = "自动搭路";
    }

    @Override
    public void enable() {
        startY = isInGame() ? mc.thePlayer.posY : 0.0D;
        data = null;
        lastPlacement = null;
        bestHotbarSlot = -1;
        ticksSincePlace = 20;
        ticksSinceTarget = 0;
        placedBlocks = 0;
        repeatedAngles = 0;
        scaffoldTicks = 0;
        hasRotationSample = false;
        silentRotationReady = false;
        scaffoldAngles = null;
        expoHoldTicks = 0;
        missingTargetTicks = 0;
        actionHistory.clear();
        wasOnGround = false;
        rotationState.reset();
        injectRotationHandler();
        releaseSneak();
    }

    @Override
    public void disable() {
        data = null;
        lastPlacement = null;
        actionHistory.clear();
        hasRotationSample = false;
        silentRotationReady = false;
        scaffoldAngles = null;
        expoHoldTicks = 0;
        missingTargetTicks = 0;
        resetVisibleRotation();
        removeRotationHandler();
        rotationState.reset();
        releaseSneak();
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!isInGame() || mc.currentScreen != null) {
            clearRuntime();
            return;
        }
        injectRotationHandler();

        ticksSincePlace++;
        scaffoldTicks++;
        if (expoHoldTicks > 0) {
            expoHoldTicks--;
        }
        pruneActions();
        bestHotbarSlot = findBestHotbarBlock();
        if (bestHotbarSlot == -1) {
            clearRuntime();
            return;
        }

        if (Boolean.TRUE.equals(noSprint.getValue())) {
            mc.thePlayer.setSprinting(false);
        }
        updateAutoJump();
        updateTower();
        updateSafeWalk();
        resetRightClickDelay();

        data = findBlockData();
        if (data == null || !validateReplaceable(data) || !validateRange(data)) {
            ticksSinceTarget = 0;
            if (++missingTargetTicks > 4) {
                silentRotationReady = false;
                resetVisibleRotation();
                rotationState.reset();
            } else if (silentRotationReady) {
                syncVisibleRotation(silentYaw);
            }
            return;
        }

        missingTargetTicks = 0;
        ticksSinceTarget++;
        rotate(data);
        recordRotationSample();
        if (canPlace(data)) {
            place(data);
        }
    }

    private void clearRuntime() {
        data = null;
        ticksSinceTarget = 0;
        hasRotationSample = false;
        silentRotationReady = false;
        scaffoldAngles = null;
        expoHoldTicks = 0;
        resetVisibleRotation();
        rotationState.reset();
        releaseSneak();
    }

    private int findBestHotbarBlock() {
        int bestSlot = -1;
        int bestSize = 0;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.thePlayer.inventory.mainInventory[slot];
            if (isValidBlockStack(stack) && stack.stackSize > bestSize) {
                bestSlot = slot;
                bestSize = stack.stackSize;
            }
        }
        return bestSlot;
    }

    private boolean isValidBlockStack(ItemStack stack) {
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

    private BlockData findBlockData() {
        BlockPos[] positions = candidatePositions();
        for (BlockPos pos : positions) {
            BlockData data = blockData(pos);
            if (data != null) {
                return data;
            }
            data = blockData(pos.down());
            if (data != null) {
                return data;
            }
        }
        return null;
    }

    private BlockPos blockUnder() {
        double y = mc.thePlayer.posY - 1.0D;
        if (Boolean.TRUE.equals(keepY.getValue()) && !mc.gameSettings.keyBindJump.isKeyDown()) {
            y = Math.min(startY, mc.thePlayer.posY) - 1.0D;
        } else {
            startY = mc.thePlayer.posY;
        }
        return new BlockPos(mc.thePlayer.posX, y, mc.thePlayer.posZ);
    }

    private BlockPos[] candidatePositions() {
        BlockPos base = blockUnder();
        ArrayList<BlockPos> positions = new ArrayList<BlockPos>();
        addCandidate(positions, base);
        double[] direction = moveDirection();
        if (direction == null) {
            addCandidate(positions, new BlockPos(mc.thePlayer.posX + mc.thePlayer.motionX * 0.65D, base.getY(),
                    mc.thePlayer.posZ + mc.thePlayer.motionZ * 0.65D));
            addCandidate(positions, new BlockPos(mc.thePlayer.posX + mc.thePlayer.motionX * 1.35D, base.getY(),
                    mc.thePlayer.posZ + mc.thePlayer.motionZ * 1.35D));
            return positions.toArray(new BlockPos[positions.size()]);
        }
        double speed = horizontalSpeed();
        double baseExpand = Math.max(0.0D, expand.getValue());
        double sprintBoost = mc.thePlayer.isSprinting() ? 0.45D : 0.0D;
        double motionLead = MathHelper.clamp_double(speed * 4.4D, 0.35D, 2.05D);
        double[] steps = new double[]{
                Math.max(0.30D, baseExpand * 0.50D),
                Math.max(0.55D, baseExpand),
                baseExpand + 0.35D,
                baseExpand + 0.70D + sprintBoost,
                baseExpand + motionLead + sprintBoost
        };
        for (double step : steps) {
            addCandidate(positions, new BlockPos(mc.thePlayer.posX + direction[0] * step, base.getY(),
                    mc.thePlayer.posZ + direction[1] * step));
        }
        addCandidate(positions, base.offset(EnumFacing.getHorizontal(MathHelper.floor_double(
                (MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw) + 180.0F) * 4.0F / 360.0F + 0.5D) & 3)));
        addCandidate(positions, new BlockPos(mc.thePlayer.posX + mc.thePlayer.motionX * 2.7D, base.getY(),
                mc.thePlayer.posZ + mc.thePlayer.motionZ * 2.7D));
        return positions.toArray(new BlockPos[positions.size()]);
    }

    private void addCandidate(List<BlockPos> positions, BlockPos pos) {
        if (!positions.contains(pos)) {
            positions.add(pos);
        }
    }

    private double[] moveDirection() {
        float forward = mc.thePlayer.movementInput.moveForward;
        float strafe = mc.thePlayer.movementInput.moveStrafe;
        if (forward == 0.0F && strafe == 0.0F) {
            double dx = mc.thePlayer.posX - mc.thePlayer.prevPosX;
            double dz = mc.thePlayer.posZ - mc.thePlayer.prevPosZ;
            double len = Math.sqrt(dx * dx + dz * dz);
            return len < 0.03D ? null : new double[]{dx / len, dz / len};
        }
        float yaw = mc.thePlayer.rotationYaw;
        if (forward < 0.0F) {
            yaw += 180.0F;
        }
        float factor = forward < 0.0F ? -0.5F : forward > 0.0F ? 0.5F : 1.0F;
        if (strafe > 0.0F) {
            yaw -= 90.0F * factor;
        }
        if (strafe < 0.0F) {
            yaw += 90.0F * factor;
        }
        double rad = Math.toRadians(yaw);
        return new double[]{-Math.sin(rad), Math.cos(rad)};
    }

    private BlockData blockData(BlockPos placePos) {
        if (!isReplaceable(placePos)) {
            return null;
        }
        for (EnumFacing face : HORIZONTAL_FACES) {
            BlockPos support = placePos.offset(face.getOpposite());
            if (canPlaceOn(support) && canPlaceOnSide(support, face)) {
                BlockData data = new BlockData(placePos, support, face);
                return data.hitVec == null ? null : data;
            }
        }
        return null;
    }

    private boolean canPlaceOn(BlockPos pos) {
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        return block != null
                && !(block instanceof BlockAir)
                && !(block instanceof BlockLiquid)
                && !block.isReplaceable(mc.theWorld, pos)
                && block.getCollisionBoundingBox(mc.theWorld, pos, mc.theWorld.getBlockState(pos)) != null;
    }

    private boolean validateReplaceable(BlockData data) {
        return isReplaceable(data.target);
    }

    private boolean validateRange(BlockData data) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        return eyes.distanceTo(data.hitVec) <= mc.playerController.getBlockReachDistance();
    }

    private void rotate(BlockData data) {
        float[] target = shouldUseFallbackAngles()
                ? fallbackAngles()
                : RotationUtil.getRotationsTo(mc, data.hitVec.xCoord, data.hitVec.yCoord, data.hitVec.zCoord);
        target = shapedAngles(target);
        float[] previous = scaffoldAngles != null ? scaffoldAngles
                : new float[]{silentRotationReady ? silentYaw : mc.thePlayer.rotationYaw,
                silentRotationReady ? silentPitch : mc.thePlayer.rotationPitch};
        float speed = rotationSpeed.getValue().floatValue();
        float yawSpeed = MathHelper.clamp_float(speed + Math.abs(MathHelper.wrapAngleTo180_float(target[0] - previous[0])) * 0.22F,
                6.0F, 120.0F);
        float pitchSpeed = MathHelper.clamp_float(speed * 0.82F + Math.abs(target[1] - previous[1]) * 0.16F,
                5.0F, 95.0F);
        scaffoldAngles = new float[]{
                RotationUtil.limitAngleChange(previous[0], target[0], yawSpeed),
                MathHelper.clamp_float(RotationUtil.limitAngleChange(previous[1], target[1], pitchSpeed), -90.0F, 90.0F)
        };
        silentYaw = scaffoldAngles[0];
        silentPitch = scaffoldAngles[1];
        silentRotationReady = true;
        syncVisibleRotation(silentYaw);
        updateAngleMemory(scaffoldAngles);
    }

    private void syncVisibleRotation(float yaw) {
        if (mc.thePlayer == null) {
            return;
        }
        mc.thePlayer.rotationYawHead = yaw;
        mc.thePlayer.prevRotationYawHead = yaw;
        mc.thePlayer.renderYawOffset = yaw;
        mc.thePlayer.prevRenderYawOffset = yaw;
    }

    private void resetVisibleRotation() {
        if (mc.thePlayer == null) {
            return;
        }
        syncVisibleRotation(mc.thePlayer.rotationYaw);
    }

    private boolean shouldUseFallbackAngles() {
        return lastPlacement == null || ticksSinceTarget <= 1;
    }

    private float[] fallbackAngles() {
        double[] direction = moveDirection();
        float yaw;
        if (direction != null) {
            yaw = (float) Math.toDegrees(Math.atan2(direction[1], direction[0])) - 90.0F;
        } else {
            yaw = mc.thePlayer.rotationYaw;
        }
        yaw += 180.0F + (float) ThreadLocalRandom.current().nextDouble(-1.6D, 1.6D);
        float pitch = 86.8F + (float) ThreadLocalRandom.current().nextDouble(-0.9D, 0.6D);
        return new float[]{yaw, pitch};
    }

    private float[] shapedAngles(float[] target) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        float yaw = target[0] + (float) random.nextDouble(-0.65D, 0.65D);
        float pitch = target[1] + (float) random.nextDouble(-0.42D, 0.52D);
        if (repeatedAngles > 2) {
            yaw += (float) random.nextDouble(-1.15D, 1.15D);
            pitch += (float) random.nextDouble(-0.7D, 0.7D);
        }
        boolean fallback = shouldUseFallbackAngles();
        boolean highPitchNeeded = fallback || Boolean.TRUE.equals(rayTraceCheck.getValue()) || mc.gameSettings.keyBindJump.isKeyDown();
        pitch = highPitchNeeded
                ? MathHelper.clamp_float(pitch, 72.0F, 88.7F)
                : MathHelper.clamp_float(pitch, 58.0F, 84.0F);
        float[] clusters = new float[]{30.0F, 35.0F, 45.0F, 90.0F, 135.0F, 180.0F};
        float baseYaw = silentRotationReady ? silentYaw : mc.thePlayer.rotationYaw;
        float yawDelta = Math.abs(MathHelper.wrapAngleTo180_float(yaw - baseYaw));
        for (float cluster : clusters) {
            float band = cluster >= 90.0F ? 8.5F : 6.5F;
            if (Math.abs(yawDelta - cluster) < band) {
                yaw += yawDelta >= cluster ? band + 1.35F : -band - 1.35F;
                break;
            }
        }
        if (!highPitchNeeded && Math.abs(pitch - 55.0F) < 0.4F) {
            pitch += pitch > 55.0F ? 0.65F : -0.65F;
        }
        return new float[]{yaw, pitch};
    }

    private void updateAngleMemory(float[] angles) {
        if (Math.abs(MathHelper.wrapAngleTo180_float(lastYaw - angles[0])) < 0.35F
                && Math.abs(lastPitch - angles[1]) < 0.35F) {
            repeatedAngles++;
        } else {
            repeatedAngles = 0;
        }
        lastYaw = angles[0];
        lastPitch = angles[1];
    }

    private void recordRotationSample() {
        if (!hasRotationSample) {
            sampledYaw = silentRotationReady ? silentYaw : mc.thePlayer.rotationYaw;
            sampledPitch = silentRotationReady ? silentPitch : mc.thePlayer.rotationPitch;
            hasRotationSample = true;
            return;
        }
        float currentYaw = silentRotationReady ? silentYaw : mc.thePlayer.rotationYaw;
        float currentPitch = silentRotationReady ? silentPitch : mc.thePlayer.rotationPitch;
        float yawDelta = Math.abs(MathHelper.wrapAngleTo180_float(currentYaw - sampledYaw));
        float pitchDelta = Math.abs(currentPitch - sampledPitch);
        double motion = Math.sqrt(mc.thePlayer.motionX * mc.thePlayer.motionX + mc.thePlayer.motionZ * mc.thePlayer.motionZ);
        sampledYaw = currentYaw;
        sampledPitch = currentPitch;
        if (yawDelta < 0.01F && pitchDelta < 0.01F) {
            return;
        }
        actionHistory.add(new ActionRecord(ActionType.ROTATION, scaffoldTicks, yawDelta, pitchDelta,
                Math.abs(currentPitch), motion));
    }

    private void recordAction(ActionType type) {
        actionHistory.add(new ActionRecord(type, scaffoldTicks, 0.0F, 0.0F,
                Math.abs(silentRotationReady ? silentPitch : mc.thePlayer.rotationPitch),
                Math.sqrt(mc.thePlayer.motionX * mc.thePlayer.motionX + mc.thePlayer.motionZ * mc.thePlayer.motionZ)));
    }

    private void pruneActions() {
        int cutoff = scaffoldTicks - 120;
        while (!actionHistory.isEmpty() && actionHistory.get(0).tick < cutoff) {
            actionHistory.remove(0);
        }
        if (actionHistory.size() > 48) {
            actionHistory.subList(0, actionHistory.size() - 48).clear();
        }
    }

    private boolean wouldMatchExpoWindow() {
        int start = scaffoldTicks - 30;
        int end = scaffoldTicks + 12;
        int places = 0;
        int swings = 0;
        int movingRotations = 0;
        int pitchEvidence = 0;
        int yawEvidence = 0;
        int clustered = 0;
        boolean nearPlaceEvidence = false;

        for (ActionRecord record : actionHistory) {
            if (record.tick < start || record.tick > end) {
                continue;
            }
            if (record.type == ActionType.PLACE) {
                places++;
                if (hasNearbySwingAndRotation(record.tick)) {
                    nearPlaceEvidence = true;
                }
            } else if (record.type == ActionType.SWING) {
                swings++;
            } else if (record.type == ActionType.ROTATION) {
                if (record.motion > 0.015D) {
                    movingRotations++;
                }
                if (record.pitchAbs >= 60.0F || record.pitchDelta >= 18.0F) {
                    pitchEvidence++;
                }
                if (record.yawDelta >= 24.0F) {
                    yawEvidence++;
                }
                if (isExpoCluster(record.yawDelta)) {
                    clustered++;
                }
            }
        }

        boolean nextSwing = swing.getValue() == SwingMode.NONE ? false : true;
        boolean primaryWindow = places >= 2 && (swings + (nextSwing ? 1 : 0)) >= 2 && movingRotations >= 2
                && pitchEvidence >= 2 && yawEvidence >= 2;
        boolean clusterWindow = clustered >= 2 && yawEvidence >= 2;
        return primaryWindow || nearPlaceEvidence && clusterWindow;
    }

    private boolean hasNearbySwingAndRotation(int placeTick) {
        boolean swingNear = false;
        boolean rotationNear = false;
        for (ActionRecord record : actionHistory) {
            if (Math.abs(record.tick - placeTick) > 8) {
                continue;
            }
            if (record.type == ActionType.SWING) {
                swingNear = true;
            } else if (record.type == ActionType.ROTATION
                    && record.motion > 0.015D
                    && (record.yawDelta >= 24.0F || record.pitchAbs >= 70.0F || record.pitchDelta >= 18.0F)) {
                rotationNear = true;
            }
        }
        return swingNear && rotationNear;
    }

    private boolean isExpoCluster(float yawDelta) {
        float[] clusters = new float[]{30.0F, 35.0F, 45.0F, 90.0F, 135.0F, 180.0F};
        for (float cluster : clusters) {
            float band = cluster >= 90.0F ? 8.0F : 6.0F;
            if (Math.abs(yawDelta - cluster) <= band) {
                return true;
            }
        }
        return false;
    }

    private boolean canPlace(BlockData data) {
        if (ticksSincePlace <= placeDelay.getValue()) {
            return false;
        }
        if (expoHoldTicks > 0) {
            return false;
        }
        if (Boolean.TRUE.equals(expoPacing.getValue()) && horizontalSpeed() < 0.23D && wouldMatchExpoWindow()) {
            expoHoldTicks = ThreadLocalRandom.current().nextInt(1, 4);
            trimActionWindowAfterPacing();
            return false;
        }
        if (Boolean.TRUE.equals(rayTraceCheck.getValue())) {
            MovingObjectPosition ray = rayTrace(silentRotationReady ? silentYaw : mc.thePlayer.rotationYaw,
                    silentRotationReady ? silentPitch : mc.thePlayer.rotationPitch);
            if (ray == null || ray.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
                return false;
            }
            if (ray.sideHit != data.face || !ray.getBlockPos().equals(data.support)) {
                return false;
            }
        }
        return ticksSinceTarget > 0;
    }

    private void trimActionWindowAfterPacing() {
        int cutoff = scaffoldTicks - 10;
        while (!actionHistory.isEmpty() && actionHistory.get(0).tick < cutoff) {
            actionHistory.remove(0);
        }
    }

    private MovingObjectPosition rayTrace(float yaw, float pitch) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 look = lookVector(yaw, pitch);
        Vec3 end = eyes.addVector(look.xCoord * mc.playerController.getBlockReachDistance(),
                look.yCoord * mc.playerController.getBlockReachDistance(),
                look.zCoord * mc.playerController.getBlockReachDistance());
        return mc.theWorld.rayTraceBlocks(eyes, end, false, false, true);
    }

    private Vec3 lookVector(float yaw, float pitch) {
        float yawRad = -yaw * 0.017453292F - (float) Math.PI;
        float pitchRad = -pitch * 0.017453292F;
        float cosYaw = MathHelper.cos(yawRad);
        float sinYaw = MathHelper.sin(yawRad);
        float cosPitch = -MathHelper.cos(pitchRad);
        float sinPitch = MathHelper.sin(pitchRad);
        return new Vec3(sinYaw * cosPitch, sinPitch, cosYaw * cosPitch);
    }

    private void place(BlockData data) {
        ItemStack stack = mc.thePlayer.inventory.mainInventory[bestHotbarSlot];
        if (!isValidBlockStack(stack) || !canItemPlace(stack, data)) {
            return;
        }

        int oldSlot = mc.thePlayer.inventory.currentItem;
        int oldSize = stack.stackSize;
        if (Boolean.TRUE.equals(spoofHeldItem.getValue()) && bestHotbarSlot != oldSlot) {
            mc.thePlayer.sendQueue.addToSendQueue(new C09PacketHeldItemChange(bestHotbarSlot));
        } else {
            mc.thePlayer.inventory.currentItem = bestHotbarSlot;
        }

        boolean placed = mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, stack,
                data.support, data.face, data.hitVec);

        if (Boolean.TRUE.equals(spoofHeldItem.getValue()) && bestHotbarSlot != oldSlot) {
            mc.thePlayer.sendQueue.addToSendQueue(new C09PacketHeldItemChange(oldSlot));
        }

        if (!placed) {
            return;
        }
        ticksSincePlace = 0;
        placedBlocks++;
        lastPlacement = data;
        recordAction(ActionType.PLACE);
        swing();
        if (stack.stackSize <= 0) {
            mc.thePlayer.inventory.mainInventory[bestHotbarSlot] = null;
        } else if (stack.stackSize != oldSize || mc.playerController.isInCreativeMode()) {
            mc.entityRenderer.itemRenderer.resetEquippedProgress();
        }
    }

    private boolean canItemPlace(ItemStack stack, BlockData data) {
        return ((ItemBlock) stack.getItem()).canPlaceBlockOnSide(mc.theWorld, data.support, data.face, mc.thePlayer, stack);
    }

    private void swing() {
        SwingMode current = swing.getValue() == null ? SwingMode.CLIENT : swing.getValue();
        if (current == SwingMode.CLIENT) {
            mc.thePlayer.swingItem();
            recordAction(ActionType.SWING);
        } else if (current == SwingMode.SILENT) {
            mc.thePlayer.sendQueue.addToSendQueue(new C0APacketAnimation());
            recordAction(ActionType.SWING);
        }
    }

    private void updateAutoJump() {
        if (!Boolean.TRUE.equals(autoJump.getValue())) {
            wasOnGround = mc.thePlayer.onGround;
            return;
        }
        if (isMoving() && mc.thePlayer.onGround && !wasOnGround) {
            mc.thePlayer.motionY = 0.41999998688698D;
            wasOnGround = true;
        } else if (!mc.thePlayer.onGround) {
            wasOnGround = false;
        }
    }

    private void updateTower() {
        if (!Boolean.TRUE.equals(tower.getValue()) || !mc.gameSettings.keyBindJump.isKeyDown()) {
            return;
        }
        BlockPos above = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY + 2.0D, mc.thePlayer.posZ);
        if (!(mc.theWorld.getBlockState(above).getBlock() instanceof BlockAir)) {
            return;
        }
        if (mc.thePlayer.onGround) {
            mc.thePlayer.motionY = 0.41999998688698D;
        } else if (mc.thePlayer.motionY < 0.1D) {
            mc.thePlayer.motionY = -0.08D;
        }
        mc.thePlayer.motionX *= 0.82D;
        mc.thePlayer.motionZ *= 0.82D;
    }

    private void updateSafeWalk() {
        if (!Boolean.TRUE.equals(safeWalk.getValue())) {
            releaseSneak();
            return;
        }
        boolean edge = isOnEdge(0.26D);
        setSneak(edge && mc.thePlayer.onGround && !Boolean.TRUE.equals(autoJump.getValue()));
    }

    private boolean isOnEdge(double offset) {
        AxisAlignedBB box = mc.thePlayer.getEntityBoundingBox();
        double y = box.minY - 0.06D;
        return !hasSupport(box.minX - offset, y, box.minZ - offset)
                || !hasSupport(box.minX - offset, y, box.maxZ + offset)
                || !hasSupport(box.maxX + offset, y, box.minZ - offset)
                || !hasSupport(box.maxX + offset, y, box.maxZ + offset);
    }

    private boolean hasSupport(double x, double y, double z) {
        return canPlaceOn(new BlockPos(x, y, z));
    }

    private boolean isReplaceable(BlockPos pos) {
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        return block == null || block.isReplaceable(mc.theWorld, pos);
    }

    private boolean canPlaceOnSide(BlockPos support, EnumFacing face) {
        ItemStack stack = mc.thePlayer.inventory.mainInventory[bestHotbarSlot];
        return isValidBlockStack(stack)
                && ((ItemBlock) stack.getItem()).canPlaceBlockOnSide(mc.theWorld, support, face, mc.thePlayer, stack);
    }

    private boolean isMoving() {
        return mc.thePlayer.movementInput.moveForward != 0.0F || mc.thePlayer.movementInput.moveStrafe != 0.0F;
    }

    private double horizontalSpeed() {
        return Math.sqrt(mc.thePlayer.motionX * mc.thePlayer.motionX + mc.thePlayer.motionZ * mc.thePlayer.motionZ);
    }

    private void resetRightClickDelay() {
        try {
            Field field = rightClickDelayField;
            if (field == null) {
                field = findMinecraftField("rightClickDelayTimer", "field_71467_ac");
                rightClickDelayField = field;
            }
            if (field != null) {
                field.setInt(mc, 0);
            }
        } catch (Throwable ignored) {
            rightClickDelayField = null;
        }
    }

    private Field findMinecraftField(String... names) {
        for (String name : names) {
            try {
                Field field = mc.getClass().getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (Throwable ignored) {
            }
        }
        return null;
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
        String[] names = new String[]{"channel", "field_150746_k"};
        for (String name : names) {
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

    private boolean shouldRewriteRotationPacket() {
        return getState() && isInGame() && data != null && silentRotationReady;
    }

    private C03PacketPlayer rewriteRotationPacket(C03PacketPlayer packet) {
        if (!shouldRewriteRotationPacket() || packet == null) {
            return null;
        }
        boolean onGround = packet.isOnGround();
        if (packet instanceof C03PacketPlayer.C06PacketPlayerPosLook
                || packet instanceof C03PacketPlayer.C04PacketPlayerPosition) {
            return new C03PacketPlayer.C06PacketPlayerPosLook(packet.getPositionX(), packet.getPositionY(),
                    packet.getPositionZ(), silentYaw, silentPitch, onGround);
        }
        return new C03PacketPlayer.C05PacketPlayerLook(silentYaw, silentPitch, onGround);
    }

    public boolean doSafeWalk() {
        return getState() && Boolean.TRUE.equals(safeWalk.getValue()) && !Boolean.TRUE.equals(autoJump.getValue());
    }

    private boolean isAirBlock(Block block) {
        if (block == null) {
            return true;
        }
        if (block.getMaterial().isReplaceable()) {
            return !(block instanceof BlockSnow) || block.getBlockBoundsMaxY() <= 0.125D;
        }
        return false;
    }

    private final class BlockData {
        final BlockPos target;
        final BlockPos support;
        final EnumFacing face;
        final Vec3 hitVec;

        BlockData(BlockPos target, BlockPos support, EnumFacing face) {
            this.target = target;
            this.support = support;
            this.face = face;
            this.hitVec = calculateHitVec();
        }

        private Vec3 calculateHitVec() {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            double x = support.getX() + 0.5D;
            double y = support.getY() + 0.5D;
            double z = support.getZ() + 0.5D;

            if (face.getAxis() == EnumFacing.Axis.X) {
                x = face == EnumFacing.EAST ? support.getX() + 1.0D : support.getX();
                z = clampToBlock(mc.thePlayer.posZ, support.getZ()) + random.nextDouble(-0.035D, 0.035D);
                y = clampToBlock(mc.thePlayer.posY + mc.thePlayer.getEyeHeight() - 0.35D,
                        support.getY()) + random.nextDouble(-0.025D, 0.025D);
            } else if (face.getAxis() == EnumFacing.Axis.Z) {
                x = clampToBlock(mc.thePlayer.posX, support.getX()) + random.nextDouble(-0.035D, 0.035D);
                y = clampToBlock(mc.thePlayer.posY + mc.thePlayer.getEyeHeight() - 0.35D,
                        support.getY()) + random.nextDouble(-0.025D, 0.025D);
                z = face == EnumFacing.SOUTH ? support.getZ() + 1.0D : support.getZ();
            }

            Vec3 hit = new Vec3(x, y, z);
            if (!Boolean.TRUE.equals(rayTraceCheck.getValue())) {
                return hit;
            }
            MovingObjectPosition ray = mc.theWorld.rayTraceBlocks(mc.thePlayer.getPositionEyes(1.0F),
                    hit, false, false, true);
            if (ray == null || ray.hitVec == null || ray.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
                return null;
            }
            return ray.hitVec;
        }

        private double clampToBlock(double value, int block) {
            return MathHelper.clamp_double(value, block + 0.22D, block + 0.78D);
        }
    }

    private static final class ActionRecord {
        final ActionType type;
        final int tick;
        final float yawDelta;
        final float pitchDelta;
        final float pitchAbs;
        final double motion;

        ActionRecord(ActionType type, int tick, float yawDelta, float pitchDelta, float pitchAbs, double motion) {
            this.type = type;
            this.tick = tick;
            this.yawDelta = yawDelta;
            this.pitchDelta = pitchDelta;
            this.pitchAbs = pitchAbs;
            this.motion = motion;
        }
    }

    private static final class SilentRotationHandler extends ChannelDuplexHandler {
        private final Scaffold scaffold;

        SilentRotationHandler(Scaffold scaffold) {
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
