package gq.yozakura.module.world;

/**
 * 参考 Telly 的固定二十一阶段动作轨迹。
 *
 * <p>该类只保存确定性程序数据，不持有玩家或世界状态。</p>
 */
final class TellyBridgeProgram {
    private static final float[] YAW = {
            91.68F, 98.88F, 78.94F, 37.45F, 1.61F, -21.69F, -33.98F,
            -35.8F, -34.64F, -33.85F, -33.06F, -31.55F, -29.26F,
            -26.65F, -24.19F, -21.07F, -18.84F, -17.06F, -8.87F,
            2.61F, 41.94F
    };
    private static final float[] PITCH = {
            64.31F, 59.95F, 60.57F, 61.46F, 60.64F, 58.89F, 56.91F,
            56.63F, 58.65F, 61.63F, 64.2F, 66.74F, 68.69F, 70.64F,
            73.01F, 75.37F, 77.46F, 78.56F, 78.9F, 77.22F, 72.25F
    };
    private static final float[] FORWARD = {
            1.0F, 1.0F, 0.0F, 0.0F, -1.0F, -1.0F, -1.0F, -1.0F,
            -1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F,
            -1.0F, -1.0F, -1.0F, -1.0F, -1.0F, 1.0F
    };
    private static final float[] STRAFE = {
            -1.0F, -1.0F, -1.0F, -1.0F, 0.0F, 0.0F, 0.0F, 0.0F,
            0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F,
            0.0F, -1.0F, -1.0F, -1.0F, -1.0F
    };

    private TellyBridgeProgram() {
    }

    static int length() {
        return YAW.length;
    }

    static float yaw(int phase) {
        return YAW[wrap(phase)];
    }

    static float pitch(int phase) {
        return PITCH[wrap(phase)];
    }

    static float forward(int phase) {
        return FORWARD[wrap(phase)];
    }

    static float strafe(int phase) {
        return STRAFE[wrap(phase)];
    }

    static boolean sprinting(int phase) {
        int wrapped = wrap(phase);
        return wrapped == 0 || wrapped == 1;
    }

    static boolean jumping(int phase) {
        int wrapped = wrap(phase);
        return wrapped >= 1 && wrapped <= 19;
    }

    static boolean using(int phase) {
        return wrap(phase) >= 7;
    }

    static boolean isActivationYawAligned(float yaw) {
        float diagonal = Math.abs(wrapDegrees(yaw - 45.0F)) % 90.0F;
        diagonal = Math.min(diagonal, 90.0F - diagonal);
        return diagonal <= 1.9F;
    }

    static int travelX(float activationYaw) {
        double radians = Math.toRadians(activationYaw);
        double rawX = Math.sin(radians) - Math.cos(radians);
        double rawZ = -Math.cos(radians) - Math.sin(radians);
        if (Math.abs(rawX) < Math.abs(rawZ)) {
            return 0;
        }
        return rawX >= 0.0D ? 1 : -1;
    }

    static int travelZ(float activationYaw) {
        double radians = Math.toRadians(activationYaw);
        double rawX = Math.sin(radians) - Math.cos(radians);
        double rawZ = -Math.cos(radians) - Math.sin(radians);
        if (Math.abs(rawX) >= Math.abs(rawZ)) {
            return 0;
        }
        return rawZ >= 0.0D ? 1 : -1;
    }

    private static int wrap(int phase) {
        int wrapped = phase % length();
        return wrapped < 0 ? wrapped + length() : wrapped;
    }

    private static float wrapDegrees(float angle) {
        angle %= 360.0F;
        if (angle >= 180.0F) {
            angle -= 360.0F;
        }
        if (angle < -180.0F) {
            angle += 360.0F;
        }
        return angle;
    }
}
