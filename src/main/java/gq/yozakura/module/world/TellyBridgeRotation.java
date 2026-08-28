package gq.yozakura.module.world;

/** 时间驱动且按鼠标灵敏度步长量化的静默旋转段。 */
final class TellyBridgeRotation {
    static final class Sample {
        final float yaw;
        final float pitch;

        Sample(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    private final float gcd;
    private float startYaw;
    private float startPitch;
    private float targetYaw;
    private float targetPitch;
    private long startedAt;
    private long duration;
    private boolean active;

    TellyBridgeRotation(float gcd) {
        this.gcd = Math.max(0.0001F, Math.abs(gcd));
    }

    boolean setTarget(float currentYaw, float currentPitch, float yaw, float pitch,
                      long now, long durationMs) {
        Sample current = active ? sample(now) : new Sample(currentYaw, currentPitch);
        float shortestTargetYaw = current.yaw + wrapDegrees(yaw - current.yaw);
        float clampedTargetPitch = clampPitch(pitch);
        float quantizedYaw = quantizeFrom(current.yaw, shortestTargetYaw);
        float quantizedPitch = clampPitch(quantizeFrom(current.pitch, clampedTargetPitch));
        if (Math.abs(quantizedYaw - current.yaw) < 0.0001F
                && Math.abs(quantizedPitch - current.pitch) < 0.0001F) {
            active = false;
            return false;
        }
        startYaw = current.yaw;
        startPitch = current.pitch;
        targetYaw = quantizedYaw;
        targetPitch = quantizedPitch;
        startedAt = now;
        duration = Math.max(1L, durationMs);
        active = true;
        return true;
    }

    Sample sample(long now) {
        if (!active) {
            return new Sample(targetYaw, targetPitch);
        }
        float progress = (float) (now - startedAt) / (float) duration;
        if (progress <= 0.0F) {
            return new Sample(startYaw, startPitch);
        }
        if (progress >= 1.0F) {
            active = false;
            return new Sample(targetYaw, targetPitch);
        }
        float eased = progress * progress * (3.0F - 2.0F * progress);
        float yaw = quantizeFrom(startYaw, startYaw + (targetYaw - startYaw) * eased);
        float pitch = clampPitch(quantizeFrom(startPitch,
                startPitch + (targetPitch - startPitch) * eased));
        return new Sample(yaw, pitch);
    }

    boolean isActive() {
        return active;
    }

    void reset(float yaw, float pitch) {
        startYaw = yaw;
        targetYaw = yaw;
        startPitch = clampPitch(pitch);
        targetPitch = startPitch;
        active = false;
    }

    private float quantizeFrom(float origin, float value) {
        float delta = value - origin;
        delta -= delta % gcd;
        return origin + delta;
    }

    private static float clampPitch(float pitch) {
        return Math.max(-90.0F, Math.min(90.0F, pitch));
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
