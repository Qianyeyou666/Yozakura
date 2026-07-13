package gq.yozakura.module.world;

import gq.yozakura.util.module.ItemUtil;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovementInput;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.List;

/** Maintains BridgeAssist's module-owned sneak state and edge prediction. */
final class BridgeAssistSneakController {
    private static final int NO_TICK = -1;
    private static final float MIN_LOOK_DOWN_PITCH = 70.0F;
    private static final double INPUT_PREDICTION_SPEED = 0.12D;
    private static final double GROUND_CHECK_DEPTH = 0.01D;
    private static final double TICK_MILLIS = 50.0D;

    private final Minecraft mc;
    private final Numbers<Double> edgeOffset;
    private final Numbers<Double> unsneakDelay;
    private final Numbers<Double> sneakOnJump;
    private final Option<Boolean> sneakKeyPressed;
    private final Option<Boolean> holdingBlocks;
    private final Option<Boolean> lookingDown;
    private final Option<Boolean> notMovingForward;

    private boolean sneakingFromModule;
    private boolean placed;
    private boolean forceRelease;
    private int sneakJumpDelayTicks = NO_TICK;
    private int sneakJumpStartTick = NO_TICK;
    private int unsneakDelayTicks = NO_TICK;
    private int unsneakStartTick = NO_TICK;

    BridgeAssistSneakController(Minecraft mc, Numbers<Double> edgeOffset, Numbers<Double> unsneakDelay,
                                Numbers<Double> sneakOnJump, Option<Boolean> sneakKeyPressed,
                                Option<Boolean> holdingBlocks, Option<Boolean> lookingDown,
                                Option<Boolean> notMovingForward) {
        this.mc = mc;
        this.edgeOffset = edgeOffset;
        this.unsneakDelay = unsneakDelay;
        this.sneakOnJump = sneakOnJump;
        this.sneakKeyPressed = sneakKeyPressed;
        this.holdingBlocks = holdingBlocks;
        this.lookingDown = lookingDown;
        this.notMovingForward = notMovingForward;
    }

    void onMoveInput() {
        MovementInput input = mc.thePlayer.movementInput;
        if (input == null) {
            return;
        }

        float forward = input.moveForward;
        float strafe = input.moveStrafe;
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

        if (shouldClearSneak(forward)) {
            clearSneak();
            return;
        }

        if (input.jump
                && mc.thePlayer.onGround
                && moving
                && sneakOnJump.getValue() > 0.0D
                && (!requireSneak || forceRelease)) {
            sneakJumpStartTick = mc.thePlayer.ticksExisted;
            sneakJumpDelayTicks = ticksFromMillis(sneakOnJump.getValue());
            pressSneak(true);
            return;
        }

        double offset = computeEdgeOffset(predictInputBox(input));
        if (Double.isNaN(offset)) {
            handleMissingGround(input, moving);
            return;
        }

        if (offset > edgeOffset.getValue()) {
            pressSneak(true);
        } else if (sneakingFromModule) {
            tryReleaseSneak(true);
        }
    }

    void onPlacementPacket() {
        if (sneakingFromModule && Boolean.TRUE.equals(sneakKeyPressed.getValue())) {
            placed = true;
        }
    }

    void clearUnavailableState() {
        releaseModuleSneak();
        resetUnsneak();
    }

    void disable() {
        releaseModuleSneak();
        resetUnsneak();
        placed = false;
        forceRelease = false;
    }

    private boolean shouldClearSneak(float forward) {
        return Boolean.TRUE.equals(notMovingForward.getValue()) && forward > 0.0F
                || Boolean.TRUE.equals(lookingDown.getValue()) && mc.thePlayer.rotationPitch < MIN_LOOK_DOWN_PITCH
                || Boolean.TRUE.equals(holdingBlocks.getValue()) && !ItemUtil.isBlock(mc.thePlayer.getHeldItem());
    }

    private void handleMissingGround(MovementInput input, boolean moving) {
        if (input.jump && (sneakOnJump.getValue() <= 0.0D || !moving)) {
            if (sneakingFromModule) {
                tryReleaseSneak(true);
            }
        } else if (mc.thePlayer.onGround) {
            pressSneak(true);
        } else if (sneakingFromModule) {
            tryReleaseSneak(true);
        }
    }

    private void pressSneak(boolean resetDelay) {
        setSneakState(true);
        sneakingFromModule = true;
        if (resetDelay) {
            unsneakStartTick = NO_TICK;
        }
        repressSneak();
    }

    private void tryReleaseSneak(boolean resetDelay) {
        int existed = mc.thePlayer.ticksExisted;
        if (unsneakStartTick == NO_TICK && sneakJumpStartTick == NO_TICK) {
            unsneakStartTick = existed;
            unsneakDelayTicks = ticksFromMillis(Math.max(0.0D, unsneakDelay.getValue() - TICK_MILLIS));
        }

        if (sneakJumpStartTick != NO_TICK && existed - sneakJumpStartTick < sneakJumpDelayTicks) {
            pressSneak(false);
            return;
        }
        if (unsneakStartTick != NO_TICK && existed - unsneakStartTick < unsneakDelayTicks) {
            pressSneak(false);
            return;
        }

        releaseSneak(resetDelay);
    }

    private void releaseSneak(boolean resetDelay) {
        if (!Boolean.TRUE.equals(sneakKeyPressed.getValue())) {
            setSneakState(false);
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
        unsneakStartTick = NO_TICK;
        sneakJumpStartTick = NO_TICK;
        sneakJumpDelayTicks = NO_TICK;
        unsneakDelayTicks = NO_TICK;
    }

    private AxisAlignedBB predictInputBox(MovementInput input) {
        AxisAlignedBB box = mc.thePlayer.getEntityBoundingBox();
        double[] inputMotion = inputMotion(input.moveForward, input.moveStrafe, INPUT_PREDICTION_SPEED);
        return box.offset(mc.thePlayer.motionX + inputMotion[0], 0.0D, mc.thePlayer.motionZ + inputMotion[1]);
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
                simBox.minX, simBox.minY - GROUND_CHECK_DEPTH, simBox.minZ,
                simBox.maxX, simBox.minY, simBox.maxZ
        );
        List<AxisAlignedBB> groundBoxes = mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, groundCheck);
        if (groundBoxes.isEmpty()) {
            return Double.NaN;
        }

        double feetX = (simBox.minX + simBox.maxX) / 2.0D;
        double feetZ = (simBox.minZ + simBox.maxZ) / 2.0D;
        double minDistance = Double.MAX_VALUE;
        for (AxisAlignedBB box : groundBoxes) {
            double closestX = Math.max(box.minX, Math.min(feetX, box.maxX));
            double closestZ = Math.max(box.minZ, Math.min(feetZ, box.maxZ));
            double dx = Math.abs(feetX - closestX);
            double dz = Math.abs(feetZ - closestZ);
            minDistance = Math.min(minDistance, Math.max(dx, dz));
        }
        return minDistance;
    }

    private int ticksFromMillis(double millis) {
        double raw = millis / TICK_MILLIS;
        int base = (int) raw;
        return base + (Math.random() < raw - base ? 1 : 0);
    }

    private boolean isManualSneak() {
        return isPhysicalKeyDown(mc.gameSettings.keyBindSneak.getKeyCode());
    }

    private void setSneakState(boolean sneak) {
        if (mc.thePlayer != null && mc.thePlayer.movementInput != null) {
            mc.thePlayer.movementInput.sneak = sneak;
        }
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), sneak);
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
}
