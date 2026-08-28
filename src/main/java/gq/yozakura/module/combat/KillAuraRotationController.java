package gq.yozakura.module.combat;

/**
 * Bounded rotation controller for silent combat rotations. It follows the
 * shortest yaw path and removes stale turn momentum when the target changes.
 */
final class KillAuraRotationController {
    private static final float SETTLE_EPSILON = 0.15F;

    private float previousYawDirection;
    private float previousPitchDirection;

    Rotation step(float currentYaw, float currentPitch, float targetYaw, float targetPitch,
                  float angleStep, float smoothingPercent) {
        float smoothing = clamp(smoothingPercent, 0.0F, 100.0F);
        float maximumStep = Math.max(1.0F, angleStep) * (0.75F - smoothing * 0.005F);
        float yaw = currentYaw + stepAxis(wrapTo180(targetYaw - currentYaw), maximumStep, true);
        float pitch = clamp(currentPitch + stepAxis(targetPitch - currentPitch, maximumStep, false),
                -90.0F, 90.0F);
        return new Rotation(yaw, pitch);
    }

    void reset() {
        previousYawDirection = 0.0F;
        previousPitchDirection = 0.0F;
    }

    private float stepAxis(float error, float maximumStep, boolean yaw) {
        if (Math.abs(error) <= SETTLE_EPSILON) {
            setPreviousDirection(yaw, 0.0F);
            return error;
        }
        float direction = Math.signum(error);
        float previousDirection = yaw ? previousYawDirection : previousPitchDirection;
        if (previousDirection != 0.0F && previousDirection != direction) {
            previousDirection = 0.0F;
        }
        float proportional = Math.abs(error) * 0.72F;
        float step = Math.min(Math.abs(error), Math.min(maximumStep, proportional));
        if (previousDirection != 0.0F) {
            step = Math.min(Math.abs(error), Math.min(maximumStep, step + maximumStep * 0.05F));
        }
        setPreviousDirection(yaw, direction);
        return direction * step;
    }

    private void setPreviousDirection(boolean yaw, float direction) {
        if (yaw) {
            previousYawDirection = direction;
        } else {
            previousPitchDirection = direction;
        }
    }

    private static float wrapTo180(float angle) {
        angle %= 360.0F;
        if (angle >= 180.0F) {
            angle -= 360.0F;
        }
        if (angle < -180.0F) {
            angle += 360.0F;
        }
        return angle;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(value, max));
    }

    static final class Rotation {
        private final float yaw;
        private final float pitch;

        private Rotation(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
        }

        float getYaw() {
            return yaw;
        }

        float getPitch() {
            return pitch;
        }
    }
}
