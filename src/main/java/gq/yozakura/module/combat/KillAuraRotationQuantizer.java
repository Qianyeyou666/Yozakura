package gq.yozakura.module.combat;

/** Applies Minecraft 1.8 mouse-count quantization to KillAura rotation deltas. */
final class KillAuraRotationQuantizer {
    private KillAuraRotationQuantizer() {
    }

    static float quantum(float sensitivity) {
        float bounded = Math.max(0.0F, Math.min(1.0F, sensitivity));
        float scaled = bounded * 0.6F + 0.2F;
        return scaled * scaled * scaled * 1.2F;
    }

    static float quantizeYaw(float current, float target, float sensitivity) {
        float delta = wrapAngleTo180(target - current);
        return current + quantizeDelta(delta, quantum(sensitivity));
    }

    static float quantizePitch(float current, float target, float sensitivity) {
        float quantized = current + quantizeDelta(target - current, quantum(sensitivity));
        return Math.max(-90.0F, Math.min(90.0F, quantized));
    }

    private static float quantizeDelta(float delta, float quantum) {
        return Math.round(delta / quantum) * quantum;
    }

    private static float wrapAngleTo180(float angle) {
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
