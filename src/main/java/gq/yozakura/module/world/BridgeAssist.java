package gq.yozakura.module.world;

import gq.yozakura.bridge.MinecraftAccessor;
import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.event.bus.types.EventType;
import gq.yozakura.event.bus.types.Priority;
import gq.yozakura.event.bridge.PacketEvent;
import gq.yozakura.event.bridge.RightClickMouseEvent;
import gq.yozakura.event.bridge.UpdateEvent;
import gq.yozakura.manager.VisualRotationState;
import gq.yozakura.module.Module;
import gq.yozakura.module.ModuleType;
import gq.yozakura.util.module.BlockUtil;
import gq.yozakura.util.module.ItemUtil;
import gq.yozakura.util.module.RotationUtil;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.List;

public class BridgeAssist extends Module {
    private static final EnumFacing[] SIDES = new EnumFacing[]{
            EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.WEST
    };
    private static final int ROTATION_PRIORITY = 1;
    private static final float MIN_PRE_PLACE_PITCH = 60.0F;
    private static final float MAX_PRE_PLACE_PITCH = 90.0F;

    private final Option<Boolean> prePlace = new Option<Boolean>("Pre Place", "PrePlace", false);
    private final Numbers<Double> edgeOffset =
            new Numbers<Double>("Edge Offset", "EdgeOffset", 0.0D, 0.0D, 0.3D, 0.01D);
    private final Numbers<Double> unsneakDelay =
            new Numbers<Double>("Unsneak Delay", "UnsneakDelay", 50.0D, 50.0D, 300.0D, 5.0D);
    private final Numbers<Double> sneakOnJump =
            new Numbers<Double>("Sneak On Jump", "SneakOnJump", 0.0D, 0.0D, 500.0D, 5.0D);
    private final Option<Boolean> sneakKeyPressed =
            new Option<Boolean>("Sneak Key Pressed", "SneakKeyPressed", false);
    private final Option<Boolean> holdingBlocks =
            new Option<Boolean>("Holding Blocks", "HoldingBlocks", false);
    private final Option<Boolean> lookingDown =
            new Option<Boolean>("Looking Down", "LookingDown", false);
    private final Option<Boolean> notMovingForward =
            new Option<Boolean>("Not Moving Forward", "NotMovingForward", false);

    private boolean sneakingFromModule;
    private boolean placed;
    private boolean forceRelease;
    private int sneakJumpDelayTicks = -1;
    private int sneakJumpStartTick = -1;
    private int unsneakDelayTicks = -1;
    private int unsneakStartTick = -1;
    private boolean hasPrePlaceRotation;
    private float prePlaceYaw;
    private float prePlacePitch;
    private MovingObjectPosition prePlaceMouseOver;

    public BridgeAssist() {
        super("BridgeAssist", Keyboard.KEY_NONE, ModuleType.World, "Assist edge sneaking while bridging");
        this.addValues(prePlace, edgeOffset, unsneakDelay, sneakOnJump,
                sneakKeyPressed, holdingBlocks, lookingDown, notMovingForward);
        Chinese = "搭桥辅助";
    }

    @Override
    public void disable() {
        releaseModuleSneak();
        resetUnsneak();
        resetPrePlace();
        placed = false;
        forceRelease = false;
    }

    @EventTarget(Priority.LOW)
    public void onUpdate(UpdateEvent event) {
        if (!getState() || event.getType() != EventType.PRE) {
            return;
        }
        if (!canAssist()) {
            releaseModuleSneak();
            resetUnsneak();
            return;
        }

        handleSneakAssist();
        handlePrePlace(event);
    }

    @EventTarget(Priority.LOWEST)
    public void onRightClick(RightClickMouseEvent event) {
        if (canUsePrePlaceMouseOver()) {
            mc.objectMouseOver = prePlaceMouseOver;
            if (placePrePlace(prePlaceMouseOver)) {
                placed = true;
                event.setCancelled(true);
            }
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() != EventType.SEND || !(event.getPacket() instanceof C08PacketPlayerBlockPlacement)) {
            return;
        }
        C08PacketPlayerBlockPlacement packet = (C08PacketPlayerBlockPlacement) event.getPacket();
        if (packet.getPlacedBlockDirection() != 255
                && sneakingFromModule
                && Boolean.TRUE.equals(sneakKeyPressed.getValue())) {
            placed = true;
        }
    }

    private boolean canAssist() {
        return isInGame()
                && mc.currentScreen == null
                && mc.playerController != null
                && !mc.thePlayer.capabilities.isFlying;
    }

    private void handleSneakAssist() {
        float forward = getForwardInput();
        float strafe = getStrafeInput();
        boolean moving = forward != 0.0F || strafe != 0.0F;
        boolean manualSneak = isManualSneak();
        boolean requireSneak = Boolean.TRUE.equals(sneakKeyPressed.getValue());

        if (manualSneak && !requireSneak) {
            resetUnsneak();
            return;
        }

        if (requireSneak && (!manualSneak || !moving)) {
            if (!manualSneak) {
                resetUnsneak();
            }
            repressSneak();
            return;
        }

        if (Boolean.TRUE.equals(notMovingForward.getValue()) && forward > 0.0F) {
            clearSneak();
            return;
        }
        if (Boolean.TRUE.equals(lookingDown.getValue()) && mc.thePlayer.rotationPitch < 70.0F) {
            clearSneak();
            return;
        }
        if (Boolean.TRUE.equals(holdingBlocks.getValue()) && !ItemUtil.isBlock(mc.thePlayer.getHeldItem())) {
            clearSneak();
            return;
        }

        if (isJumpPressed()
                && mc.thePlayer.onGround
                && moving
                && sneakOnJump.getValue() > 0.0D
                && (!requireSneak || forceRelease)) {
            sneakJumpStartTick = mc.thePlayer.ticksExisted;
            sneakJumpDelayTicks = ticksFromMillis(sneakOnJump.getValue());
            pressSneak(true);
            return;
        }

        double offset = computeEdgeOffset(predictInputBox(forward, strafe));
        if (Double.isNaN(offset)) {
            if (isJumpPressed() && (sneakOnJump.getValue() <= 0.0D || !moving)) {
                if (sneakingFromModule) {
                    tryReleaseSneak(true);
                }
            } else if (mc.thePlayer.onGround) {
                pressSneak(true);
            } else if (sneakingFromModule) {
                tryReleaseSneak(true);
            }
            return;
        }

        if (offset > edgeOffset.getValue()) {
            pressSneak(true);
        } else if (sneakingFromModule) {
            tryReleaseSneak(true);
        }
    }

    private void handlePrePlace(UpdateEvent event) {
        if (!Boolean.TRUE.equals(prePlace.getValue())) {
            resetPrePlace();
            return;
        }
        if (!ItemUtil.isBlock(mc.thePlayer.getHeldItem())) {
            resetPrePlace();
            return;
        }
        if (Boolean.TRUE.equals(lookingDown.getValue()) && mc.thePlayer.rotationPitch < 70.0F) {
            resetPrePlace();
            return;
        }
        if (Boolean.TRUE.equals(notMovingForward.getValue()) && getForwardInput() > 0.0F) {
            resetPrePlace();
            return;
        }

        float yaw = event.getNewYaw();
        float basePitch = hasPrePlaceRotation ? prePlacePitch : event.getNewPitch();
        TargetResult target = findTarget(yaw, basePitch, mc.playerController.getBlockReachDistance());
        if (target == null) {
            resetPrePlace();
            return;
        }

        float nextYaw = stepAngle(hasPrePlaceRotation ? prePlaceYaw : yaw, target.yaw, 15.0F);
        float nextPitch = stepLinear(basePitch, target.pitch, 20.0F);
        nextYaw = RotationUtil.quantizeAngle(nextYaw);
        nextPitch = RotationUtil.quantizeAngle(MathHelper.clamp_float(nextPitch, -90.0F, 90.0F));

        prePlaceYaw = nextYaw;
        prePlacePitch = nextPitch;
        hasPrePlaceRotation = true;
        event.setRotation(nextYaw, nextPitch,
                ROTATION_PRIORITY);
        event.setPervRotation(nextYaw, ROTATION_PRIORITY);
        VisualRotationState.publish("BridgeAssist", nextYaw, nextPitch, ROTATION_PRIORITY);
        applyClientPrePlaceRotation(nextYaw, nextPitch);

        MovingObjectPosition hit = RotationUtil.rayTrace(nextYaw, nextPitch,
                mc.playerController.getBlockReachDistance(), 1.0F);
        if (isPrePlaceHit(hit, target)) {
            prePlaceMouseOver = hit;
            mc.objectMouseOver = hit;
        } else {
            prePlaceMouseOver = null;
        }
    }

    private void pressSneak(boolean resetDelay) {
        setSneakState(true);
        sneakingFromModule = true;
        if (resetDelay) {
            unsneakStartTick = -1;
        }
        repressSneak();
    }

    private void tryReleaseSneak(boolean resetDelay) {
        int existed = mc.thePlayer.ticksExisted;
        if (unsneakStartTick == -1 && sneakJumpStartTick == -1) {
            unsneakStartTick = existed;
            unsneakDelayTicks = ticksFromMillis(Math.max(0.0D, unsneakDelay.getValue() - 50.0D));
        }

        if (sneakJumpStartTick != -1 && existed - sneakJumpStartTick < sneakJumpDelayTicks) {
            pressSneak(false);
            return;
        }
        if (unsneakStartTick != -1 && existed - unsneakStartTick < unsneakDelayTicks) {
            pressSneak(false);
            return;
        }

        releaseSneak(resetDelay);
    }

    private void releaseSneak(boolean resetDelay) {
        if (!Boolean.TRUE.equals(sneakKeyPressed.getValue())) {
            setSneakState(isManualSneak());
        } else if (sneakingFromModule && isManualSneak() && (placed || !mc.thePlayer.onGround)) {
            setSneakState(false);
            forceRelease = true;
        } else if (forceRelease) {
            setSneakState(false);
        }

        sneakingFromModule = false;
        placed = false;
        if (resetDelay) {
            resetUnsneak();
        }
    }

    private void repressSneak() {
        if (forceRelease && isManualSneak()) {
            setSneakState(true);
        }
        forceRelease = false;
    }

    private void clearSneak() {
        if (sneakingFromModule) {
            releaseModuleSneak();
        }
        sneakingFromModule = false;
        resetUnsneak();
        if (Boolean.TRUE.equals(sneakKeyPressed.getValue())) {
            repressSneak();
        }
    }

    private void resetUnsneak() {
        unsneakStartTick = -1;
        sneakJumpStartTick = -1;
        sneakJumpDelayTicks = -1;
        unsneakDelayTicks = -1;
    }

    private void resetPrePlace() {
        hasPrePlaceRotation = false;
        prePlaceMouseOver = null;
        VisualRotationState.clearSource("BridgeAssist");
    }

    private AxisAlignedBB predictInputBox(float forward, float strafe) {
        AxisAlignedBB box = mc.thePlayer.getEntityBoundingBox();
        double x = mc.thePlayer.motionX;
        double z = mc.thePlayer.motionZ;
        double[] input = inputMotion(forward, strafe, 0.12D);
        return box.offset(x + input[0], 0.0D, z + input[1]);
    }

    private double[] inputMotion(float forward, float strafe, double speed) {
        double input = forward * forward + strafe * strafe;
        if (input < 1.0E-4D) {
            return new double[]{0.0D, 0.0D};
        }
        input = Math.sqrt(input);
        if (input < 1.0D) {
            input = 1.0D;
        }
        input = speed / input;
        strafe *= input;
        forward *= input;
        float yaw = mc.thePlayer.rotationYaw;
        float sin = MathHelper.sin(yaw * (float) Math.PI / 180.0F);
        float cos = MathHelper.cos(yaw * (float) Math.PI / 180.0F);
        return new double[]{strafe * cos - forward * sin, forward * cos + strafe * sin};
    }

    private double computeEdgeOffset(AxisAlignedBB simBox) {
        AxisAlignedBB groundCheck = new AxisAlignedBB(
                simBox.minX, simBox.minY - 0.01D, simBox.minZ,
                simBox.maxX, simBox.minY, simBox.maxZ
        );
        List<AxisAlignedBB> groundBoxes = mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, groundCheck);
        if (groundBoxes.isEmpty()) {
            return Double.NaN;
        }

        double feetX = (simBox.minX + simBox.maxX) * 0.5D;
        double feetZ = (simBox.minZ + simBox.maxZ) * 0.5D;
        double minDistance = Double.MAX_VALUE;
        for (AxisAlignedBB box : groundBoxes) {
            double closestX = MathHelper.clamp_double(feetX, box.minX, box.maxX);
            double closestZ = MathHelper.clamp_double(feetZ, box.minZ, box.maxZ);
            double dx = Math.abs(feetX - closestX);
            double dz = Math.abs(feetZ - closestZ);
            minDistance = Math.min(minDistance, Math.max(dx, dz));
        }
        return minDistance;
    }

    private TargetResult findTarget(float yaw, float currentPitch, double reach) {
        AxisAlignedBB box = mc.thePlayer.getEntityBoundingBox();
        int standY = MathHelper.floor_double(box.minY) - 1;
        int minX = MathHelper.floor_double(box.minX);
        int maxX = MathHelper.floor_double(box.maxX);
        int minZ = MathHelper.floor_double(box.minZ);
        int maxZ = MathHelper.floor_double(box.maxZ);

        ArrayList<FaceTarget> targets = new ArrayList<FaceTarget>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos standBlock = new BlockPos(x, standY, z);
                if (BlockUtil.isReplaceable(standBlock)) {
                    continue;
                }
                for (EnumFacing side : SIDES) {
                    BlockPos placedBlock = standBlock.offset(side);
                    if (BlockUtil.isReplaceable(placedBlock)) {
                        targets.add(new FaceTarget(standBlock, side));
                    }
                }
            }
        }
        if (targets.isEmpty()) {
            return null;
        }

        float bestDelta = Float.MAX_VALUE;
        float bestPitch = Float.NaN;
        BlockPos bestSupport = null;
        EnumFacing bestFace = null;
        for (float pitch = MIN_PRE_PLACE_PITCH; pitch <= MAX_PRE_PLACE_PITCH; pitch += 1.0F) {
            MovingObjectPosition mop = RotationUtil.rayTrace(yaw, pitch, reach, 1.0F);
            if (mop == null || mop.sideHit == EnumFacing.UP || mop.sideHit == EnumFacing.DOWN) {
                continue;
            }
            BlockPos hitBlock = mop.getBlockPos();
            for (FaceTarget target : targets) {
                if (hitBlock.equals(target.block) && mop.sideHit == target.face) {
                    float delta = Math.abs(pitch - currentPitch);
                    if (delta < bestDelta) {
                        bestDelta = delta;
                        bestPitch = pitch;
                        bestSupport = target.block;
                        bestFace = target.face;
                    }
                    break;
                }
            }
        }

        if (bestSupport == null || bestFace == null || Float.isNaN(bestPitch)) {
            return null;
        }
        return new TargetResult(yaw, bestPitch, bestSupport, bestFace);
    }

    private boolean canUsePrePlaceMouseOver() {
        return Boolean.TRUE.equals(prePlace.getValue())
                && canAssist()
                && ItemUtil.isBlock(mc.thePlayer.getHeldItem())
                && prePlaceMouseOver != null;
    }

    private boolean isPrePlaceHit(MovingObjectPosition hit, TargetResult target) {
        return hit != null
                && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && hit.getBlockPos().equals(target.support)
                && hit.sideHit == target.face;
    }

    private void applyClientPrePlaceRotation(float yaw, float pitch) {
        mc.thePlayer.rotationYaw = yaw;
        mc.thePlayer.rotationPitch = pitch;
        mc.thePlayer.rotationYawHead = yaw;
        mc.thePlayer.renderYawOffset = yaw;
    }

    private boolean placePrePlace(MovingObjectPosition hit) {
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            return false;
        }
        ItemStack stack = mc.thePlayer.getHeldItem();
        if (!ItemUtil.isBlock(stack)) {
            return false;
        }
        int slot = mc.thePlayer.inventory.currentItem;
        int oldSize = stack.stackSize;
        MinecraftAccessor.syncCurrentPlayItem(mc.playerController);
        boolean placed = mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, stack,
                hit.getBlockPos(), hit.sideHit, hit.hitVec);
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

    private float stepAngle(float current, float target, float maxStep) {
        float diff = MathHelper.wrapAngleTo180_float(target - current);
        return current + MathHelper.clamp_float(diff, -maxStep, maxStep);
    }

    private float stepLinear(float current, float target, float maxStep) {
        float diff = target - current;
        return current + MathHelper.clamp_float(diff, -maxStep, maxStep);
    }

    private int ticksFromMillis(double millis) {
        double raw = millis / 50.0D;
        int base = (int) raw;
        return base + (Math.random() < raw - base ? 1 : 0);
    }

    private float getForwardInput() {
        float forward = 0.0F;
        if (isPhysicalKeyDown(mc.gameSettings.keyBindForward.getKeyCode())) {
            forward += 1.0F;
        }
        if (isPhysicalKeyDown(mc.gameSettings.keyBindBack.getKeyCode())) {
            forward -= 1.0F;
        }
        return forward;
    }

    private float getStrafeInput() {
        float strafe = 0.0F;
        if (isPhysicalKeyDown(mc.gameSettings.keyBindLeft.getKeyCode())) {
            strafe += 1.0F;
        }
        if (isPhysicalKeyDown(mc.gameSettings.keyBindRight.getKeyCode())) {
            strafe -= 1.0F;
        }
        return strafe;
    }

    private boolean isJumpPressed() {
        return isPhysicalKeyDown(mc.gameSettings.keyBindJump.getKeyCode());
    }

    private boolean isManualSneak() {
        return isPhysicalKeyDown(mc.gameSettings.keyBindSneak.getKeyCode());
    }

    private void setSneakState(boolean sneak) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), sneak);
        if (mc.thePlayer != null && mc.thePlayer.movementInput != null) {
            mc.thePlayer.movementInput.sneak = sneak;
        }
    }

    private void releaseModuleSneak() {
        if (!sneakingFromModule && !forceRelease) {
            return;
        }
        setSneakState(isManualSneak());
        sneakingFromModule = false;
    }

    private boolean isPhysicalKeyDown(int key) {
        try {
            return key < 0 ? Mouse.isCreated() && Mouse.isButtonDown(key + 100) : Keyboard.isKeyDown(key);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static final class FaceTarget {
        final BlockPos block;
        final EnumFacing face;

        FaceTarget(BlockPos block, EnumFacing face) {
            this.block = block;
            this.face = face;
        }
    }

    private static final class TargetResult {
        final float yaw;
        final float pitch;
        final BlockPos support;
        final EnumFacing face;

        TargetResult(float yaw, float pitch, BlockPos support, EnumFacing face) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.support = support;
            this.face = face;
        }
    }
}
