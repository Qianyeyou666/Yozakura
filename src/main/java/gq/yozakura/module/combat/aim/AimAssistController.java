package gq.yozakura.module.combat.aim;

public final class AimAssistController {
    public enum Profile {
        REGULAR,
        BLATANT
    }

    private static final float MIN_FRAME_SECONDS = 0.0001F;
    private static final float MAX_FRAME_SECONDS = 0.05F;

    private int targetId = -1;
    private long engageAtMillis;
    private boolean targetRotationInitialized;
    private float targetYaw;
    private float targetPitch;
    private float yawVelocity;
    private float pitchVelocity;
    private float yawResidual;
    private float pitchResidual;
    private boolean outputInitialized;
    private float lastOutputYaw;
    private float lastOutputPitch;

    public boolean acquireTarget(int nextTargetId, long nowMillis, long reactionDelayMillis,
                                 float viewYaw, float viewPitch) {
        if (nextTargetId < 0) {
            throw new IllegalArgumentException("Target id must be non-negative");
        }
        requireFinite(viewYaw, "viewYaw");
        requireFinite(viewPitch, "viewPitch");
        if (targetId == nextTargetId) {
            return false;
        }

        targetId = nextTargetId;
        engageAtMillis = safeAdd(nowMillis, Math.max(0L, reactionDelayMillis));
        targetRotationInitialized = false;
        yawVelocity = 0.0F;
        pitchVelocity = 0.0F;
        yawResidual = 0.0F;
        pitchResidual = 0.0F;
        outputInitialized = true;
        lastOutputYaw = viewYaw;
        lastOutputPitch = clampPitch(viewPitch);
        return true;
    }

    public void setTargetRotation(float yaw, float pitch, float blend) {
        requireFinite(yaw, "yaw");
        requireFinite(pitch, "pitch");
        if (targetId < 0) {
            throw new IllegalStateException("Cannot set an aim rotation without an acquired target");
        }

        float safeBlend = clamp(blend, 0.0F, 1.0F);
        float safePitch = clampPitch(pitch);
        if (!targetRotationInitialized) {
            targetYaw = yaw;
            targetPitch = safePitch;
            targetRotationInitialized = true;
            return;
        }

        targetYaw += wrapDegrees(yaw - targetYaw) * safeBlend;
        targetPitch = clampPitch(targetPitch + (safePitch - targetPitch) * safeBlend);
    }

    public Rotation step(float currentYaw, float currentPitch, float deltaSeconds, long nowMillis,
                         Settings settings) {
        requireFinite(currentYaw, "currentYaw");
        requireFinite(currentPitch, "currentPitch");
        requireFinite(deltaSeconds, "deltaSeconds");
        if (settings == null) {
            throw new IllegalArgumentException("settings cannot be null");
        }

        float safePitch = clampPitch(currentPitch);
        if (targetId < 0 || !targetRotationInitialized || nowMillis < engageAtMillis || deltaSeconds <= 0.0F) {
            return idleRotation(currentYaw, safePitch);
        }

        float quantum = mouseQuantum(settings.mouseSensitivity);
        dampForExternalViewChange(currentYaw, safePitch, quantum, settings.profile);
        float frameSeconds = clamp(deltaSeconds, MIN_FRAME_SECONDS, MAX_FRAME_SECONDS);

        AxisStep yawStep = stepAxis(wrapDegrees(targetYaw - currentYaw), yawVelocity, yawResidual,
                settings.maxYawSpeed, frameSeconds, quantum, settings.profile);
        yawVelocity = yawStep.velocity;
        yawResidual = yawStep.residual;

        AxisStep pitchStep;
        if (settings.verticalAim) {
            pitchStep = stepAxis(targetPitch - safePitch, pitchVelocity, pitchResidual,
                    settings.maxPitchSpeed, frameSeconds, quantum, settings.profile);
        } else {
            pitchStep = new AxisStep(0.0F, pitchVelocity * 0.5F, 0.0F);
        }
        pitchVelocity = pitchStep.velocity;
        pitchResidual = pitchStep.residual;

        float nextYaw = currentYaw + yawStep.delta;
        float nextPitch = clampPitch(safePitch + pitchStep.delta);
        lastOutputYaw = nextYaw;
        lastOutputPitch = nextPitch;
        outputInitialized = true;
        return new Rotation(currentYaw, safePitch, nextYaw, nextPitch);
    }

    public boolean isTrackingTarget(int entityId) {
        return targetId >= 0 && targetId == entityId;
    }

    public int getTargetId() {
        return targetId;
    }

    public boolean isReady(long nowMillis) {
        return targetId >= 0 && targetRotationInitialized && nowMillis >= engageAtMillis;
    }

    public void releaseTarget() {
        targetId = -1;
        engageAtMillis = 0L;
        targetRotationInitialized = false;
        targetYaw = 0.0F;
        targetPitch = 0.0F;
        yawVelocity = 0.0F;
        pitchVelocity = 0.0F;
        yawResidual = 0.0F;
        pitchResidual = 0.0F;
        outputInitialized = false;
        lastOutputYaw = 0.0F;
        lastOutputPitch = 0.0F;
    }

    public static float mouseQuantum(float sensitivity) {
        float safeSensitivity = clamp(sensitivity, 0.0F, 1.0F);
        float scaled = safeSensitivity * 0.6F + 0.2F;
        return scaled * scaled * scaled * 8.0F * 0.15F;
    }

    private Rotation idleRotation(float currentYaw, float currentPitch) {
        yawVelocity = 0.0F;
        pitchVelocity = 0.0F;
        yawResidual = 0.0F;
        pitchResidual = 0.0F;
        lastOutputYaw = currentYaw;
        lastOutputPitch = currentPitch;
        outputInitialized = true;
        return new Rotation(currentYaw, currentPitch, currentYaw, currentPitch);
    }

    private void dampForExternalViewChange(float currentYaw, float currentPitch, float quantum, Profile profile) {
        if (!outputInitialized) {
            return;
        }
        float yawChange = Math.abs(wrapDegrees(currentYaw - lastOutputYaw));
        float pitchChange = Math.abs(currentPitch - lastOutputPitch);
        float threshold = Math.max(0.08F, quantum * 1.5F);
        if (yawChange <= threshold && pitchChange <= threshold) {
            return;
        }

        float damping = profile == Profile.BLATANT ? 0.45F : 0.22F;
        yawVelocity *= damping;
        pitchVelocity *= damping;
        yawResidual *= damping;
        pitchResidual *= damping;
    }

    private static AxisStep stepAxis(float error, float velocity, float residual, float maxSpeed,
                                     float deltaSeconds, float quantum, Profile profile) {
        float deadZone = Math.max(0.01F, quantum * (profile == Profile.BLATANT ? 0.20F : 0.35F));
        float response = profile == Profile.BLATANT ? 14.0F : 8.5F;
        float acceleration = profile == Profile.BLATANT ? 18.0F : 10.0F;
        float desiredVelocity = Math.abs(error) <= deadZone
                ? 0.0F
                : clamp(error * response, -maxSpeed, maxSpeed);
        float velocityBlend = 1.0F - (float) Math.exp(-acceleration * deltaSeconds);
        float nextVelocity = velocity + (desiredVelocity - velocity) * velocityBlend;
        float rawDelta = nextVelocity * deltaSeconds;
        if (Math.abs(rawDelta) > Math.abs(error)) {
            rawDelta = error;
        }

        QuantizedStep quantized = quantize(rawDelta, residual, error, quantum);
        if (quantized.blockedByTarget) {
            nextVelocity = 0.0F;
        }
        return new AxisStep(quantized.delta, nextVelocity, quantized.residual);
    }

    private static QuantizedStep quantize(float delta, float residual, float error, float quantum) {
        if (Math.abs(error) < 0.000001F) {
            return new QuantizedStep(0.0F, 0.0F, true);
        }
        float total = delta + residual;
        if (Math.signum(total) != 0.0F && Math.signum(total) != Math.signum(error)) {
            total = 0.0F;
        }
        int counts = Math.round(total / quantum);
        float quantized = counts * quantum;
        if (Math.signum(quantized) != Math.signum(error)) {
            quantized = 0.0F;
        }
        if (Math.abs(quantized) > Math.abs(error)) {
            int availableCounts = (int) Math.floor(Math.abs(error) / quantum + 0.000001F);
            if (availableCounts <= 0) {
                float retainedResidual = Math.copySign(Math.min(Math.abs(total), Math.abs(error)), error);
                return new QuantizedStep(0.0F, retainedResidual, false);
            }
            quantized = Math.copySign(availableCounts * quantum, error);
        }
        return new QuantizedStep(quantized, total - quantized, false);
    }

    private static long safeAdd(long value, long addition) {
        if (addition > 0L && value > Long.MAX_VALUE - addition) {
            return Long.MAX_VALUE;
        }
        return value + addition;
    }

    private static float clampPitch(float pitch) {
        return clamp(pitch, -90.0F, 90.0F);
    }

    private static float wrapDegrees(float angle) {
        float wrapped = angle % 360.0F;
        if (wrapped >= 180.0F) {
            wrapped -= 360.0F;
        }
        if (wrapped < -180.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void requireFinite(float value, String name) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    public static final class Settings {
        private final float maxYawSpeed;
        private final float maxPitchSpeed;
        private final float mouseSensitivity;
        private final boolean verticalAim;
        private final Profile profile;

        public Settings(float maxYawSpeed, float maxPitchSpeed, float mouseSensitivity,
                        boolean verticalAim, Profile profile) {
            requireFinite(maxYawSpeed, "maxYawSpeed");
            requireFinite(maxPitchSpeed, "maxPitchSpeed");
            requireFinite(mouseSensitivity, "mouseSensitivity");
            if (maxYawSpeed <= 0.0F || maxPitchSpeed <= 0.0F) {
                throw new IllegalArgumentException("Aim speeds must be positive");
            }
            if (profile == null) {
                throw new IllegalArgumentException("profile cannot be null");
            }
            this.maxYawSpeed = maxYawSpeed;
            this.maxPitchSpeed = maxPitchSpeed;
            this.mouseSensitivity = mouseSensitivity;
            this.verticalAim = verticalAim;
            this.profile = profile;
        }
    }

    public static final class Rotation {
        private final float sourceYaw;
        private final float sourcePitch;
        private final float yaw;
        private final float pitch;

        private Rotation(float sourceYaw, float sourcePitch, float yaw, float pitch) {
            this.sourceYaw = sourceYaw;
            this.sourcePitch = sourcePitch;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        public float getYaw() {
            return yaw;
        }

        public float getPitch() {
            return pitch;
        }

        public float getYawDelta() {
            return wrapDegrees(yaw - sourceYaw);
        }

        public float getPitchDelta() {
            return pitch - sourcePitch;
        }
    }

    private static final class AxisStep {
        private final float delta;
        private final float velocity;
        private final float residual;

        private AxisStep(float delta, float velocity, float residual) {
            this.delta = delta;
            this.velocity = velocity;
            this.residual = residual;
        }
    }

    private static final class QuantizedStep {
        private final float delta;
        private final float residual;
        private final boolean blockedByTarget;

        private QuantizedStep(float delta, float residual, boolean blockedByTarget) {
            this.delta = delta;
            this.residual = residual;
            this.blockedByTarget = blockedByTarget;
        }
    }
}
