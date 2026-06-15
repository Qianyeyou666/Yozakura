package gq.yozakura.manager;

import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;

public final class VisualRotationState {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int HOLD_TICKS = 2;

    private static boolean active;
    private static boolean publishedThisTick;
    private static int holdTicks;
    private static int tickPriority = Integer.MIN_VALUE;
    private static String source = "";
    private static float prevRenderYawOffset;
    private static float renderYawOffset;
    private static float prevRotationYawHead;
    private static float rotationYawHead;
    private static float prevRotationPitch;
    private static float rotationPitch;

    private VisualRotationState() {
    }

    public static void beginTick() {
        publishedThisTick = false;
        tickPriority = Integer.MIN_VALUE;
    }

    public static void publish(String nextSource, float yaw, float pitch, int priority) {
        if (mc.thePlayer == null) {
            clear();
            return;
        }
        if (publishedThisTick && priority < tickPriority) {
            return;
        }
        tickPriority = priority;
        publishedThisTick = true;
        source = nextSource == null ? "" : nextSource;
        if (!active) {
            prevRenderYawOffset = mc.thePlayer.renderYawOffset;
            renderYawOffset = mc.thePlayer.renderYawOffset;
            prevRotationYawHead = mc.thePlayer.rotationYawHead;
            rotationYawHead = mc.thePlayer.rotationYawHead;
            prevRotationPitch = mc.thePlayer.rotationPitch;
            rotationPitch = mc.thePlayer.rotationPitch;
        } else {
            prevRenderYawOffset = renderYawOffset;
            prevRotationYawHead = rotationYawHead;
            prevRotationPitch = rotationPitch;
        }
        rotationYawHead = yaw;
        rotationPitch = MathHelper.clamp_float(pitch, -90.0F, 90.0F);
        renderYawOffset = calculateRenderYawOffset(yaw, renderYawOffset);
        active = true;
        holdTicks = HOLD_TICKS;
    }

    public static void finishTick() {
        if (publishedThisTick) {
            return;
        }
        if (holdTicks > 0) {
            holdTicks--;
            return;
        }
        clear();
    }

    public static void clearSource(String oldSource) {
        if (oldSource == null || oldSource.equals(source)) {
            clear();
        }
    }

    public static void clear() {
        active = false;
        publishedThisTick = false;
        holdTicks = 0;
        tickPriority = Integer.MIN_VALUE;
        source = "";
    }

    public static boolean isActived() {
        return active;
    }

    public static String getSource() {
        return source;
    }

    public static float getPrevRenderYawOffset() {
        return prevRenderYawOffset;
    }

    public static float getRenderYawOffset() {
        return renderYawOffset;
    }

    public static float getPrevRotationYawHead() {
        return prevRotationYawHead;
    }

    public static float getRotationYawHead() {
        return rotationYawHead;
    }

    public static float getPrevRotationPitch() {
        return prevRotationPitch;
    }

    public static float getRotationPitch() {
        return rotationPitch;
    }

    private static float calculateRenderYawOffset(float targetYaw, float currentYawOffset) {
        if (mc.thePlayer == null) {
            return targetYaw;
        }
        float newYawOffset = currentYawOffset;
        double deltaX = mc.thePlayer.posX - mc.thePlayer.prevPosX;
        double deltaZ = mc.thePlayer.posZ - mc.thePlayer.prevPosZ;
        if ((float) (deltaX * deltaX + deltaZ * deltaZ) > 0.0025000002F) {
            newYawOffset = (float) MathHelper.atan2(deltaZ, deltaX) * 180.0F / (float) Math.PI - 90.0F;
        }
        if (mc.thePlayer.swingProgress > 0.0F) {
            newYawOffset = targetYaw;
        }
        float f4 = MathHelper.wrapAngleTo180_float(newYawOffset - currentYawOffset);
        float adjustedYawOffset = currentYawOffset + f4 * 0.3F;
        float f5 = MathHelper.wrapAngleTo180_float(targetYaw - adjustedYawOffset);
        if (f5 < -75.0F) {
            f5 = -75.0F;
        }
        if (f5 >= 75.0F) {
            f5 = 75.0F;
        }
        newYawOffset = targetYaw - f5;
        if (f5 * f5 > 2500.0F) {
            newYawOffset += f5 * 0.2F;
        }
        return newYawOffset;
    }
}
