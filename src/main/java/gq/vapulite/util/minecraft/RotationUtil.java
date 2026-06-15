package gq.vapulite.util.minecraft;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;

public final class RotationUtil {
    private RotationUtil() {
    }

    public static final class State {
        private float yawVelocity;
        private float pitchVelocity;
        private float smoothedYaw;
        private float smoothedPitch;
        private float lastTargetYaw;
        private float lastTargetPitch;
        private float filteredTargetYaw;
        private float filteredTargetPitch;
        private float yawCovariance;
        private float pitchCovariance;
        private float curveProgress;
        private boolean initialized;

        public void reset() {
            yawVelocity = 0.0f;
            pitchVelocity = 0.0f;
            yawCovariance = 1.0f;
            pitchCovariance = 1.0f;
            curveProgress = 0.0f;
            initialized = false;
        }
    }

    public static float[] getRotations(Minecraft mc, Entity entity, double prediction, double heightRatio) {
        if (mc == null || mc.thePlayer == null || entity == null) {
            return new float[]{0.0f, 0.0f};
        }

        double targetX = entity.posX + (entity.posX - entity.lastTickPosX) * prediction;
        double targetZ = entity.posZ + (entity.posZ - entity.lastTickPosZ) * prediction;
        double targetY;
        if (entity instanceof EntityLivingBase) {
            EntityLivingBase living = (EntityLivingBase) entity;
            double ratio = MathHelper.clamp_double(heightRatio, 0.18D, 1.0D);
            targetY = living.posY + living.getEyeHeight() * ratio
                    + (living.posY - living.lastTickPosY) * Math.min(0.85D, prediction);
        } else {
            targetY = (entity.getEntityBoundingBox().minY + entity.getEntityBoundingBox().maxY) * 0.5D;
        }
        return getRotationsTo(mc, targetX, targetY, targetZ);
    }

    public static float[] getRotationsTo(Minecraft mc, double targetX, double targetY, double targetZ) {
        double diffX = targetX - mc.thePlayer.posX;
        double diffY = targetY - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        double diffZ = targetZ - mc.thePlayer.posZ;
        double dist = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float yaw = (float) (Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0D);
        float pitch = (float) (-Math.toDegrees(Math.atan2(diffY, dist)));
        return new float[]{yaw, MathHelper.clamp_float(pitch, -90.0f, 90.0f)};
    }

    public static void applyToPlayer(Minecraft mc, float targetYaw, float targetPitch,
                                     float yawSpeed, float pitchSpeed, boolean onlyYaw,
                                     float freeZone, State state) {
        applyToPlayer(mc, targetYaw, targetPitch, yawSpeed, pitchSpeed, onlyYaw, freeZone,
                state, 0.42f, 0.22f, true);
    }

    public static void applyToPlayer(Minecraft mc, float targetYaw, float targetPitch,
                                     float yawSpeed, float pitchSpeed, boolean onlyYaw,
                                     float freeZone, State state, float inertia,
                                     float minStep, boolean syncHead) {
        if (mc == null || mc.thePlayer == null) {
            return;
        }
        State current = state == null ? new State() : state;
        if (!current.initialized) {
            current.smoothedYaw = mc.thePlayer.rotationYaw;
            current.smoothedPitch = mc.thePlayer.rotationPitch;
            current.lastTargetYaw = targetYaw;
            current.lastTargetPitch = targetPitch;
            current.filteredTargetYaw = targetYaw;
            current.filteredTargetPitch = targetPitch;
            current.yawCovariance = 1.0f;
            current.pitchCovariance = 1.0f;
            current.initialized = true;
        }

        float targetShift = Math.abs(MathHelper.wrapAngleTo180_float(targetYaw - current.lastTargetYaw))
                + Math.abs(MathHelper.wrapAngleTo180_float(targetPitch - current.lastTargetPitch)) * 0.65f;
        if (targetShift > 28.0f) {
            current.curveProgress = 0.0f;
            current.yawVelocity *= 0.35f;
            current.pitchVelocity *= 0.35f;
            current.smoothedYaw = mc.thePlayer.rotationYaw;
            current.smoothedPitch = mc.thePlayer.rotationPitch;
            current.filteredTargetYaw = targetYaw;
            current.filteredTargetPitch = targetPitch;
            current.yawCovariance = 1.0f;
            current.pitchCovariance = 1.0f;
        } else {
            current.curveProgress = MathHelper.clamp_float(current.curveProgress + 0.16f, 0.0f, 1.0f);
            float processNoise = 0.018f + MathHelper.clamp_float(targetShift / 90.0f, 0.0f, 0.16f);
            current.filteredTargetYaw = kalmanAngle(current.filteredTargetYaw, targetYaw,
                    current.yawCovariance + processNoise, 0.28f);
            current.filteredTargetPitch = kalmanAngle(current.filteredTargetPitch, targetPitch,
                    current.pitchCovariance + processNoise * 0.75f, 0.34f);
            current.yawCovariance = nextCovariance(current.yawCovariance + processNoise, 0.28f);
            current.pitchCovariance = nextCovariance(current.pitchCovariance + processNoise * 0.75f, 0.34f);
        }
        current.lastTargetYaw = targetYaw;
        current.lastTargetPitch = targetPitch;
        targetYaw = current.filteredTargetYaw;
        targetPitch = current.filteredTargetPitch;

        float yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - mc.thePlayer.rotationYaw);
        float pitchDiff = MathHelper.wrapAngleTo180_float(targetPitch - mc.thePlayer.rotationPitch);

        float safeInertia = MathHelper.clamp_float(inertia, 0.05f, 0.85f);
        float safeMinStep = Math.max(0.0f, minStep);

        if (Math.abs(yawDiff) > freeZone) {
            float desiredYawStep = curvedStep(yawDiff, yawSpeed, safeMinStep, current.curveProgress);
            current.yawVelocity += (desiredYawStep - current.yawVelocity) * safeInertia;
            current.smoothedYaw = limitAngleChange(current.smoothedYaw, targetYaw, Math.max(safeMinStep, Math.abs(current.yawVelocity)));
            mc.thePlayer.rotationYaw = limitAngleChange(mc.thePlayer.rotationYaw, current.smoothedYaw,
                    Math.max(safeMinStep, Math.abs(current.yawVelocity)));
        } else {
            current.yawVelocity *= 0.55f;
        }

        if (!onlyYaw && Math.abs(pitchDiff) > freeZone) {
            float desiredPitchStep = curvedStep(pitchDiff, pitchSpeed, safeMinStep, current.curveProgress);
            current.pitchVelocity += (desiredPitchStep - current.pitchVelocity) * (safeInertia * 0.92f);
            current.smoothedPitch = limitAngleChange(current.smoothedPitch, targetPitch,
                    Math.max(safeMinStep, Math.abs(current.pitchVelocity)));
            mc.thePlayer.rotationPitch = limitAngleChange(mc.thePlayer.rotationPitch, current.smoothedPitch,
                    Math.max(safeMinStep, Math.abs(current.pitchVelocity)));
            mc.thePlayer.rotationPitch = MathHelper.clamp_float(mc.thePlayer.rotationPitch, -90.0f, 90.0f);
        } else {
            current.pitchVelocity *= 0.55f;
        }

        if (syncHead) {
            syncHead(mc, mc.thePlayer.rotationYaw);
        }
    }

    public static float curvedStep(float diff, float maxSpeed, float minStep, float progress) {
        float abs = Math.abs(diff);
        float speed = Math.max(minStep, maxSpeed);
        float distanceCurve = cubicBezierEase(MathHelper.clamp_float(abs / 48.0f, 0.0f, 1.0f), 0.10f, 0.92f);
        float startCurve = 0.38f + cubicBezierEase(MathHelper.clamp_float(progress, 0.0f, 1.0f), 0.08f, 0.96f) * 0.62f;
        float landingCurve = cubicBezierEase(MathHelper.clamp_float(abs / 7.5f, 0.0f, 1.0f), 0.18f, 1.0f);
        landingCurve = MathHelper.clamp_float(landingCurve, 0.20f, 1.0f);
        float curve = MathHelper.clamp_float((0.22f + distanceCurve * 0.92f) * startCurve * landingCurve,
                0.18f, 1.12f);
        return MathHelper.clamp_float(speed * curve, minStep, Math.max(minStep, speed));
    }

    public static float cubicBezierEase(float value, float controlY1, float controlY2) {
        float t = MathHelper.clamp_float(value, 0.0f, 1.0f);
        float inv = 1.0f - t;
        return 3.0f * inv * inv * t * controlY1
                + 3.0f * inv * t * t * controlY2
                + t * t * t;
    }

    public static float smoothStep(float value) {
        float t = MathHelper.clamp_float(value, 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    public static float adaptiveStep(float diff, float maxSpeed, float minStep) {
        float abs = Math.abs(diff);
        float speed = Math.max(minStep, maxSpeed);
        float scale = MathHelper.clamp_float(abs / 65.0f, 0.20f, 1.12f);
        return MathHelper.clamp_float(speed * scale, minStep, Math.max(minStep, speed));
    }

    public static float limitAngleChange(float current, float target, float maxTurn) {
        float delta = MathHelper.wrapAngleTo180_float(target - current);
        float limit = Math.max(0.0f, maxTurn);
        return current + MathHelper.clamp_float(delta, -limit, limit);
    }

    private static float kalmanAngle(float estimate, float measurement, float covariance, float measurementNoise) {
        float unwrappedMeasurement = estimate + MathHelper.wrapAngleTo180_float(measurement - estimate);
        float gain = covariance / (covariance + Math.max(0.001f, measurementNoise));
        return estimate + MathHelper.wrapAngleTo180_float(unwrappedMeasurement - estimate)
                * MathHelper.clamp_float(gain, 0.0f, 1.0f);
    }

    private static float nextCovariance(float covariance, float measurementNoise) {
        float gain = covariance / (covariance + Math.max(0.001f, measurementNoise));
        return MathHelper.clamp_float((1.0f - gain) * covariance, 0.01f, 2.0f);
    }

    public static void syncHead(Minecraft mc, float yaw) {
        if (mc == null || mc.thePlayer == null) {
            return;
        }
        mc.thePlayer.rotationYawHead = yaw;
        mc.thePlayer.prevRotationYawHead = yaw;
        mc.thePlayer.renderYawOffset = yaw;
    }
}
