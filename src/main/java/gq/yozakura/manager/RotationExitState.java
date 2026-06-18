package gq.yozakura.manager;

import gq.yozakura.event.bus.types.EventType;
import gq.yozakura.event.bridge.UpdateEvent;
import gq.yozakura.runtime.YozakuraRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;

public final class RotationExitState {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final float COMPLETE_EPSILON = 0.75F;
    private static final float GCD_STEP = 0.0096F;

    private static boolean active;
    private static String source = "";
    private static float yaw;
    private static float pitch;
    private static float maxStep;
    private static float smoothFactor;
    private static int priority;
    private static int remainingTicks;
    private static boolean moveFix;
    private static boolean lockView;

    private RotationExitState() {
    }

    public static void request(String nextSource, float startYaw, float startPitch, int nextPriority,
                               float nextMaxStep, float nextSmoothFactor, int ticks,
                               boolean nextMoveFix, boolean nextLockView) {
        if (mc.thePlayer == null || !isFinite(startYaw) || !isFinite(startPitch)) {
            clear();
            return;
        }
        active = true;
        source = nextSource == null ? "" : nextSource;
        yaw = quantizeAngle(startYaw);
        pitch = quantizeAngle(MathHelper.clamp_float(startPitch, -90.0F, 90.0F));
        maxStep = MathHelper.clamp_float(nextMaxStep, 5.0F, 180.0F);
        smoothFactor = MathHelper.clamp_float(nextSmoothFactor, 0.0F, 1.0F);
        priority = nextPriority;
        remainingTicks = Math.max(1, ticks);
        moveFix = nextMoveFix;
        lockView = nextLockView;
    }

    public static void apply(UpdateEvent event) {
        if (!active || event == null || event.getType() != EventType.PRE) {
            return;
        }
        if (mc.thePlayer == null) {
            clear();
            return;
        }
        if (event.isRotated()) {
            clear();
            return;
        }

        float targetYaw = event.getYaw();
        float targetPitch = MathHelper.clamp_float(event.getPitch(), -90.0F, 90.0F);
        if (isComplete(targetYaw, targetPitch)) {
            clear();
            return;
        }

        yaw = quantizeAngle(stepAngle(yaw, targetYaw));
        pitch = quantizeAngle(MathHelper.clamp_float(stepLinear(pitch, targetPitch), -90.0F, 90.0F));

        event.setRotation(yaw, pitch, priority);
        VisualRotationState.publish(source, yaw, pitch, priority);
        if (moveFix || lockView) {
            event.setPervRotation(yaw, priority);
        }
        if (lockView) {
            YozakuraRuntime.rotationManager.setRotation(yaw, pitch, priority, true);
        }

        remainingTicks--;
        if (remainingTicks <= 0 || isComplete(targetYaw, targetPitch)) {
            clear();
        }
    }

    public static void clearSource(String oldSource) {
        if (oldSource == null || oldSource.equals(source)) {
            clear();
        }
    }

    public static void clear() {
        active = false;
        source = "";
        yaw = 0.0F;
        pitch = 0.0F;
        maxStep = 0.0F;
        smoothFactor = 0.0F;
        priority = -1;
        remainingTicks = 0;
        moveFix = false;
        lockView = false;
    }

    private static float stepAngle(float current, float target) {
        float delta = MathHelper.wrapAngleTo180_float(target - current);
        return current + smoothStep(MathHelper.clamp_float(delta, -maxStep, maxStep));
    }

    private static float stepLinear(float current, float target) {
        float delta = target - current;
        return current + smoothStep(MathHelper.clamp_float(delta, -maxStep, maxStep));
    }

    private static float smoothStep(float delta) {
        float multiplier = 0.5F + 0.5F * (1.0F - smoothFactor);
        return delta * multiplier;
    }

    private static boolean isComplete(float targetYaw, float targetPitch) {
        return Math.abs(MathHelper.wrapAngleTo180_float(targetYaw - yaw)) <= COMPLETE_EPSILON
                && Math.abs(targetPitch - pitch) <= COMPLETE_EPSILON;
    }

    private static float quantizeAngle(float angle) {
        return angle - angle % GCD_STEP;
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
}
