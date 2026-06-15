package gq.yozakura.manager;

import net.minecraft.util.MathHelper;

public final class PacketRotationState {
    private static boolean active;
    private static int holdTicks;
    private static float yaw;
    private static float pitch;
    private static int priority = Integer.MIN_VALUE;
    private static String source = "";

    private PacketRotationState() {
    }

    public static void request(String nextSource, float nextYaw, float nextPitch, int nextPriority, int ticks) {
        if (active && nextPriority < priority) {
            return;
        }
        active = true;
        source = nextSource == null ? "" : nextSource;
        yaw = nextYaw;
        pitch = MathHelper.clamp_float(nextPitch, -90.0F, 90.0F);
        priority = nextPriority;
        holdTicks = Math.max(1, ticks);
    }

    public static void tick() {
        priority = Integer.MIN_VALUE;
        if (!active) {
            return;
        }
        if (holdTicks > 0) {
            holdTicks--;
            return;
        }
        clear();
    }

    public static void clear() {
        active = false;
        holdTicks = 0;
        yaw = 0.0F;
        pitch = 0.0F;
        priority = Integer.MIN_VALUE;
        source = "";
    }

    public static void clearSource(String oldSource) {
        if (oldSource == null || oldSource.equals(source)) {
            clear();
        }
    }

    public static boolean isActived() {
        return active;
    }

    public static float getYaw() {
        return yaw;
    }

    public static float getPitch() {
        return pitch;
    }

    public static String getSource() {
        return source;
    }
}
