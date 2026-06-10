package gq.vapulite.Vapu.modules.world;

import gq.vapulite.Vapu.ModuleType;
import gq.vapulite.Vapu.modules.Module;
import gq.vapulite.Vapu.utils.RotationUtil;
import gq.vapulite.Vapu.value.Mode;
import gq.vapulite.Vapu.value.Numbers;
import gq.vapulite.Vapu.value.Option;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.block.BlockLiquid;
import net.minecraft.client.settings.KeyBinding;
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

public class BridgeAssist extends Module {
    public enum BridgeMode {
        SAFE,
        GOD_BRIDGE
    }

    public enum GodAimMode {
        OFF,
        ASSIST,
        LEGIT
    }

    private static final EnumFacing[] HORIZONTAL_FACES =
            new EnumFacing[]{EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST};

    private final Mode<BridgeMode> mode =
            new Mode<BridgeMode>("Mode", "Mode", BridgeMode.values(), BridgeMode.SAFE);
    private final Numbers<Double> edgeDistance = new Numbers<Double>("Edge Distance", "EdgeDistance", 0.28, 0.05, 0.75, 0.01);
    private final Numbers<Double> slowPercent = new Numbers<Double>("Slow Percent", "SlowPercent", 55.0, 10.0, 100.0, 1.0);
    private final Numbers<Double> predictTicks = new Numbers<Double>("Prediction", "Prediction", 2.0, 0.0, 5.0, 1.0);
    private final Numbers<Double> godAimSpeed = new Numbers<Double>("God Aim Speed", "GodAimSpeed", 14.0, 2.0, 45.0, 1.0);
    private final Mode<GodAimMode> godAim =
            new Mode<GodAimMode>("God Aim", "GodAim", GodAimMode.values(), GodAimMode.LEGIT);
    private final Option<Boolean> onlyBlocks = new Option<Boolean>("Only Blocks", "OnlyBlocks", true);
    private final Option<Boolean> holdSneak = new Option<Boolean>("Hold Sneak", "HoldSneak", true);
    private final Option<Boolean> motionGuard = new Option<Boolean>("Motion Guard", "MotionGuard", true);
    private final Option<Boolean> groundOnly = new Option<Boolean>("Ground Only", "GroundOnly", true);
    private final Option<Boolean> godStrafe = new Option<Boolean>("God Strafe", "GodStrafe", true);

    private boolean holdingSneak;
    private boolean holdingGodLeft;
    private boolean holdingGodRight;
    private int godSide;
    private int eagleTicks;
    private int godInactiveTicks;
    private int lockedAimTicks;
    private boolean bridgeLineLocked;
    private double bridgeLineX;
    private double bridgeLineZ;
    private double bridgeLineRightX;
    private double bridgeLineRightZ;
    private float bridgeLineYaw;
    private float stableGodYaw;
    private float stableGodPitch;
    private boolean stableGodLook;
    private BridgeAim lockedAim;
    private final RotationUtil.State godAimState = new RotationUtil.State();

    public BridgeAssist() {
        super("BridgeAssist", Keyboard.KEY_NONE, ModuleType.World, "Assist safe edge movement while bridging");
        holdSneak.visibleWhen(() -> mode.getValue() == BridgeMode.SAFE);
        motionGuard.visibleWhen(() -> mode.getValue() == BridgeMode.SAFE);
        slowPercent.visibleWhen(() -> mode.getValue() == BridgeMode.SAFE && Boolean.TRUE.equals(motionGuard.getValue()));
        godAim.visibleWhen(() -> mode.getValue() == BridgeMode.GOD_BRIDGE);
        godStrafe.visibleWhen(() -> mode.getValue() == BridgeMode.GOD_BRIDGE);
        godAimSpeed.visibleWhen(() -> mode.getValue() == BridgeMode.GOD_BRIDGE && currentGodAimMode() != GodAimMode.OFF);
        this.addValues(mode, edgeDistance, slowPercent, predictTicks, godAim, godAimSpeed, onlyBlocks,
                holdSneak, motionGuard, groundOnly, godStrafe);
        Chinese = "搭桥辅助";
    }

    @Override
    public void disable() {
        releaseSneak();
        releaseGodStrafe();
        resetGodBridgeState();
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            if (mode.getValue() == BridgeMode.GOD_BRIDGE) {
                updateGodBridge();
            } else {
                releaseGodStrafe();
                resetGodBridgeState();
            }
            return;
        }
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!canAssist()) {
            releaseSneak();
            releaseGodStrafe();
            resetGodBridgeState();
            return;
        }
        if (mode.getValue() == BridgeMode.GOD_BRIDGE) {
            releaseSneak();
            return;
        }

        boolean unsafe = isNearUnsafeEdge();
        setSneak(Boolean.TRUE.equals(holdSneak.getValue()) && unsafe);
        if (unsafe && Boolean.TRUE.equals(motionGuard.getValue())) {
            double scale = Math.max(0.1D, Math.min(1.0D, slowPercent.getValue() / 100.0D));
            mc.thePlayer.motionX *= scale;
            mc.thePlayer.motionZ *= scale;
        }
    }

    private void updateGodBridge() {
        if (!canAssist() || !isGodBridgeActive()) {
            releaseGodStrafe();
            releaseSneak();
            if (++godInactiveTicks > 3) {
                resetGodBridgeState();
            }
            return;
        }
        godInactiveTicks = 0;
        updateBridgeLineLock();
        GodAimMode aimMode = currentGodAimMode();
        if (aimMode == GodAimMode.LEGIT) {
            updateLegitEagle();
        } else {
            releaseSneak();
            eagleTicks = 0;
        }
        if (Boolean.TRUE.equals(godStrafe.getValue()) || aimMode == GodAimMode.LEGIT) {
            maintainGodStrafe();
            applyBridgeLineCorrection(aimMode);
        } else {
            releaseGodStrafe();
        }
        if (aimMode == GodAimMode.OFF) {
            godAimState.reset();
            return;
        }
        BridgeAim aim = findGodBridgeAim(aimMode);
        if (aim != null) {
            aimAt(aim, aimMode);
        }
    }

    private boolean canAssist() {
        if (!isInGame() || mc.currentScreen != null || mc.thePlayer.capabilities.isFlying) {
            return false;
        }
        if (Boolean.TRUE.equals(groundOnly.getValue()) && !mc.thePlayer.onGround) {
            return false;
        }
        return !Boolean.TRUE.equals(onlyBlocks.getValue()) || isHoldingBlock();
    }

    private boolean isHoldingBlock() {
        ItemStack stack = mc.thePlayer.getHeldItem();
        return stack != null && stack.getItem() instanceof ItemBlock;
    }

    private boolean isGodBridgeActive() {
        boolean edge = isNearUnsafeEdge();
        boolean sideInput = isPhysicalKeyDown(mc.gameSettings.keyBindLeft)
                || isPhysicalKeyDown(mc.gameSettings.keyBindRight);
        boolean movementInput = sideInput
                || isPhysicalKeyDown(mc.gameSettings.keyBindForward)
                || isPhysicalKeyDown(mc.gameSettings.keyBindBack)
                || Math.abs(mc.thePlayer.moveForward) > 0.01f
                || Math.abs(mc.thePlayer.moveStrafing) > 0.01f;
        return sideInput
                || edge && movementInput
                || mc.gameSettings.keyBindUseItem.isKeyDown() && (edge || movementInput);
    }

    private void maintainGodStrafe() {
        int side = getGodSide();
        if (side < 0) {
            setMovementKey(mc.gameSettings.keyBindLeft, true);
            setMovementKey(mc.gameSettings.keyBindRight, false);
            holdingGodLeft = true;
            holdingGodRight = false;
        } else {
            setMovementKey(mc.gameSettings.keyBindRight, true);
            setMovementKey(mc.gameSettings.keyBindLeft, false);
            holdingGodRight = true;
            holdingGodLeft = false;
        }
    }

    private int getGodSide() {
        if (isPhysicalKeyDown(mc.gameSettings.keyBindLeft)) {
            godSide = -1;
        } else if (isPhysicalKeyDown(mc.gameSettings.keyBindRight)) {
            godSide = 1;
        } else if (godSide == 0) {
            godSide = 1;
        }
        return godSide;
    }

    private void updateBridgeLineLock() {
        if (bridgeLineLocked) {
            return;
        }
        double forwardX = mc.thePlayer.motionX;
        double forwardZ = mc.thePlayer.motionZ;
        double speed = Math.sqrt(forwardX * forwardX + forwardZ * forwardZ);
        if (speed < 0.035D) {
            double yaw = Math.toRadians(mc.thePlayer.rotationYaw);
            forwardX = -Math.sin(yaw);
            forwardZ = Math.cos(yaw);
            if (isPhysicalKeyDown(mc.gameSettings.keyBindBack) || mc.thePlayer.moveForward < -0.01f) {
                forwardX = -forwardX;
                forwardZ = -forwardZ;
            }
            speed = Math.sqrt(forwardX * forwardX + forwardZ * forwardZ);
        }
        if (speed < 0.001D) {
            return;
        }
        double lineForwardX = forwardX / speed;
        double lineForwardZ = forwardZ / speed;
        bridgeLineRightX = lineForwardZ;
        bridgeLineRightZ = -lineForwardX;
        bridgeLineYaw = yawFromForward(lineForwardX, lineForwardZ);
        bridgeLineX = mc.thePlayer.posX;
        bridgeLineZ = mc.thePlayer.posZ;
        bridgeLineLocked = true;
    }

    private void applyBridgeLineCorrection(GodAimMode aimMode) {
        if (!bridgeLineLocked || !mc.thePlayer.onGround) {
            return;
        }
        double deltaX = mc.thePlayer.posX - bridgeLineX;
        double deltaZ = mc.thePlayer.posZ - bridgeLineZ;
        double lateralError = deltaX * bridgeLineRightX + deltaZ * bridgeLineRightZ;
        double lateralMotion = mc.thePlayer.motionX * bridgeLineRightX + mc.thePlayer.motionZ * bridgeLineRightZ;
        if (Math.abs(lateralError) < 0.012D && Math.abs(lateralMotion) < 0.006D) {
            return;
        }
        double stiffness = aimMode == GodAimMode.LEGIT ? 0.16D : 0.11D;
        double damping = aimMode == GodAimMode.LEGIT ? 0.48D : 0.36D;
        double limit = aimMode == GodAimMode.LEGIT ? 0.020D : 0.015D;
        double correction = clamp(-lateralError * stiffness - lateralMotion * damping, -limit, limit);
        mc.thePlayer.motionX += bridgeLineRightX * correction;
        mc.thePlayer.motionZ += bridgeLineRightZ * correction;
    }

    private BridgeAim findGodBridgeAim(GodAimMode aimMode) {
        Vec3 predicted = new Vec3(mc.thePlayer.posX + mc.thePlayer.motionX * predictTicks.getValue(),
                mc.thePlayer.posY, mc.thePlayer.posZ + mc.thePlayer.motionZ * predictTicks.getValue());
        if (lockedAim != null && lockedAimTicks > 0 && isLockedAimValid(lockedAim, predicted)) {
            lockedAimTicks--;
            return lockedAim;
        }
        BlockPos base = new BlockPos(predicted.xCoord, mc.thePlayer.posY - 1.0D, predicted.zCoord);
        BridgeAim best = null;
        double bestScore = Double.MAX_VALUE;

        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                BlockPos support = base.add(x, 0, z);
                if (!isSolidSupport(support)) {
                    continue;
                }
                for (EnumFacing side : HORIZONTAL_FACES) {
                    BlockPos place = support.offset(side);
                    if (!isReplaceable(place)) {
                        continue;
                    }
                    Vec3 hitVec = hitVecForSide(support, side);
                    float[] rotations = RotationUtil.getRotationsTo(mc, hitVec.xCoord, hitVec.yCoord, hitVec.zCoord);
                    float yawDiff = Math.abs(MathHelper.wrapAngleTo180_float(rotations[0] - mc.thePlayer.rotationYaw));
                    double placeDx = place.getX() + 0.5D - predicted.xCoord;
                    double placeDz = place.getZ() + 0.5D - predicted.zCoord;
                    double placeDistance = Math.sqrt(placeDx * placeDx + placeDz * placeDz);
                    double targetPitch = aimMode == GodAimMode.LEGIT ? 73.0D : 76.0D;
                    double pitchPenalty = Math.abs(rotations[1] - targetPitch) * 0.045D;
                    double sidePenalty = aimMode == GodAimMode.LEGIT
                            ? legitSidePenalty(place, predicted, rotations[0]) : 0.0D;
                    double godYawPenalty = aimMode == GodAimMode.LEGIT
                            ? Math.abs(MathHelper.wrapAngleTo180_float(rotations[0] - getGodSideYaw())) * 0.08D : 0.0D;
                    double score = placeDistance * 5.0D + yawDiff * 0.035D
                            + pitchPenalty + sidePenalty + godYawPenalty;
                    if (score < bestScore) {
                        bestScore = score;
                        best = new BridgeAim(support, side, place, hitVec);
                    }
                }
            }
        }
        lockedAim = best;
        lockedAimTicks = best == null ? 0 : aimMode == GodAimMode.LEGIT ? 5 : 3;
        return best;
    }

    private boolean isLockedAimValid(BridgeAim aim, Vec3 predicted) {
        if (aim == null || !isSolidSupport(aim.support) || !isReplaceable(aim.place)) {
            return false;
        }
        double dx = aim.place.getX() + 0.5D - predicted.xCoord;
        double dz = aim.place.getZ() + 0.5D - predicted.zCoord;
        if (dx * dx + dz * dz > 4.2D) {
            return false;
        }
        float[] rotations = RotationUtil.getRotationsTo(mc, aim.hitVec.xCoord, aim.hitVec.yCoord, aim.hitVec.zCoord);
        return Math.abs(MathHelper.wrapAngleTo180_float(rotations[0] - mc.thePlayer.rotationYaw)) < 78.0f;
    }

    private Vec3 hitVecForSide(BlockPos support, EnumFacing side) {
        double x = support.getX() + 0.5D + side.getFrontOffsetX() * 0.485D;
        double z = support.getZ() + 0.5D + side.getFrontOffsetZ() * 0.485D;
        double y = support.getY() + 0.78D;
        return new Vec3(x, y, z);
    }

    private double legitSidePenalty(BlockPos place, Vec3 predicted, float targetYaw) {
        int side = getGodSide();
        float yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - mc.thePlayer.rotationYaw);
        double rightX = bridgeLineLocked ? bridgeLineRightX : Math.cos(Math.toRadians(mc.thePlayer.rotationYaw));
        double rightZ = bridgeLineLocked ? bridgeLineRightZ : Math.sin(Math.toRadians(mc.thePlayer.rotationYaw));
        double lateral = (place.getX() + 0.5D - predicted.xCoord) * rightX
                + (place.getZ() + 0.5D - predicted.zCoord) * rightZ;
        double sideMismatch = side < 0 ? Math.max(0.0D, lateral) : Math.max(0.0D, -lateral);
        return sideMismatch * 2.1D + Math.max(0.0D, Math.abs(yawDiff) - 55.0f) * 0.025D;
    }

    private void aimAt(BridgeAim aim, GodAimMode aimMode) {
        float[] rotations = aimMode == GodAimMode.LEGIT
                ? getLegitGodRotations(aim)
                : RotationUtil.getRotationsTo(mc, aim.hitVec.xCoord, aim.hitVec.yCoord, aim.hitVec.zCoord);
        float speed = godAimSpeed.getValue().floatValue();
        if (aimMode == GodAimMode.LEGIT) {
            float yawSpeed = Math.min(Math.max(2.4f, speed * 0.62f), 8.0f);
            float pitchSpeed = Math.min(Math.max(1.9f, speed * 0.34f), 4.8f);
            RotationUtil.applyToPlayer(mc, rotations[0], rotations[1], yawSpeed, pitchSpeed,
                    false, 0.20f, godAimState, 0.15f, 0.018f, true);
            return;
        }
        RotationUtil.applyToPlayer(mc, rotations[0], rotations[1], speed, Math.max(3.0f, speed * 0.72f),
                false, 0.18f, godAimState, 0.30f, 0.05f, true);
    }

    private float[] getLegitGodRotations(BridgeAim aim) {
        float[] blockRotations = RotationUtil.getRotationsTo(mc,
                aim.hitVec.xCoord, aim.hitVec.yCoord, aim.hitVec.zCoord);
        float baseYaw = getGodSideYaw();
        float yawAssist = MathHelper.clamp_float(
                MathHelper.wrapAngleTo180_float(blockRotations[0] - baseYaw), -14.0f, 14.0f) * 0.34f;
        float targetYaw = baseYaw + yawAssist;
        float targetPitch = 75.2f
                + MathHelper.clamp_float(blockRotations[1] - 75.2f, -5.5f, 5.5f) * 0.32f;
        if (!stableGodLook) {
            stableGodYaw = targetYaw;
            stableGodPitch = targetPitch;
            stableGodLook = true;
        } else {
            stableGodYaw = RotationUtil.limitAngleChange(stableGodYaw, targetYaw, 3.4f);
            stableGodPitch = RotationUtil.limitAngleChange(stableGodPitch, targetPitch, 1.8f);
        }
        return new float[]{stableGodYaw, MathHelper.clamp_float(stableGodPitch, 69.0f, 82.0f)};
    }

    private float getGodSideYaw() {
        float lineYaw = bridgeLineLocked ? bridgeLineYaw : mc.thePlayer.rotationYaw;
        return lineYaw - getGodSide() * 90.0f;
    }

    private void updateLegitEagle() {
        double edgeGuard = Math.min(0.24D, Math.max(0.08D, edgeDistance.getValue() * 0.72D));
        boolean shouldSneak = isNearUnsafeEdge(edgeGuard, 0.45D);
        if (shouldSneak) {
            eagleTicks = 3;
        } else if (eagleTicks > 0) {
            eagleTicks--;
        }
        setSneak(eagleTicks > 0);
    }

    private boolean isNearUnsafeEdge() {
        return isNearUnsafeEdge(edgeDistance.getValue(), predictTicks.getValue());
    }

    private boolean isNearUnsafeEdge(double guard, double prediction) {
        AxisAlignedBB box = mc.thePlayer.getEntityBoundingBox();
        double xOffset = clamp(mc.thePlayer.motionX * prediction, -guard, guard);
        double zOffset = clamp(mc.thePlayer.motionZ * prediction, -guard, guard);
        double y = box.minY - 0.05D;

        return !hasSupportAt(box.minX + xOffset, y, box.minZ + zOffset)
                || !hasSupportAt(box.minX + xOffset, y, box.maxZ + zOffset)
                || !hasSupportAt(box.maxX + xOffset, y, box.minZ + zOffset)
                || !hasSupportAt(box.maxX + xOffset, y, box.maxZ + zOffset);
    }

    private boolean hasSupportAt(double x, double y, double z) {
        return isSolidSupport(new BlockPos(x, y, z));
    }

    private boolean isSolidSupport(BlockPos pos) {
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        return block != null
                && !(block instanceof BlockAir)
                && !(block instanceof BlockLiquid)
                && !block.isReplaceable(mc.theWorld, pos)
                && block.getCollisionBoundingBox(mc.theWorld, pos, mc.theWorld.getBlockState(pos)) != null;
    }

    private boolean isReplaceable(BlockPos pos) {
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        return block == null || block instanceof BlockAir || block instanceof BlockLiquid
                || block.isReplaceable(mc.theWorld, pos);
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

    private void releaseGodStrafe() {
        if (holdingGodLeft) {
            restoreMovementKey(mc.gameSettings.keyBindLeft);
            holdingGodLeft = false;
        }
        if (holdingGodRight) {
            restoreMovementKey(mc.gameSettings.keyBindRight);
            holdingGodRight = false;
        }
    }

    private void setMovementKey(KeyBinding keyBinding, boolean down) {
        KeyBinding.setKeyBindState(keyBinding.getKeyCode(), down);
    }

    private void restoreMovementKey(KeyBinding keyBinding) {
        int key = keyBinding.getKeyCode();
        KeyBinding.setKeyBindState(key, key > 0 && Keyboard.isKeyDown(key));
    }

    private boolean isPhysicalKeyDown(KeyBinding keyBinding) {
        int key = keyBinding.getKeyCode();
        return key > 0 && Keyboard.isKeyDown(key);
    }

    private GodAimMode currentGodAimMode() {
        return godAim.getValue() == null ? GodAimMode.ASSIST : godAim.getValue();
    }

    private void resetGodBridgeState() {
        godAimState.reset();
        godSide = 0;
        eagleTicks = 0;
        godInactiveTicks = 0;
        lockedAimTicks = 0;
        bridgeLineLocked = false;
        stableGodLook = false;
        lockedAim = null;
    }

    private float yawFromForward(double forwardX, double forwardZ) {
        return (float) Math.toDegrees(Math.atan2(-forwardX, forwardZ));
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class BridgeAim {
        final BlockPos support;
        final EnumFacing side;
        final BlockPos place;
        final Vec3 hitVec;

        BridgeAim(BlockPos support, EnumFacing side, BlockPos place, Vec3 hitVec) {
            this.support = support;
            this.side = side;
            this.place = place;
            this.hitVec = hitVec;
        }
    }
}
