package gq.yozakura.bridge.modern;

final class ModernVisibleAimState {
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

    float[] update(float currentYaw, float currentPitch, float targetYaw, float targetPitch,
                   float yawSpeed, float pitchSpeed) {
        if (!initialized) {
            smoothedYaw = currentYaw;
            smoothedPitch = currentPitch;
            lastTargetYaw = targetYaw;
            lastTargetPitch = targetPitch;
            filteredTargetYaw = targetYaw;
            filteredTargetPitch = targetPitch;
            yawCovariance = 1.0F;
            pitchCovariance = 1.0F;
            initialized = true;
        }

        float targetShift = Math.abs(ModernRotationBridge.wrapDegrees(targetYaw - lastTargetYaw))
                + Math.abs(ModernRotationBridge.wrapDegrees(targetPitch - lastTargetPitch)) * 0.65F;
        if (targetShift > 28.0F) {
            curveProgress = 0.0F;
            yawVelocity *= 0.35F;
            pitchVelocity *= 0.35F;
            smoothedYaw = currentYaw;
            smoothedPitch = currentPitch;
            filteredTargetYaw = targetYaw;
            filteredTargetPitch = targetPitch;
            yawCovariance = 1.0F;
            pitchCovariance = 1.0F;
        } else {
            curveProgress = clamp(curveProgress + 0.16F, 0.0F, 1.0F);
            float processNoise = 0.018F + clamp(targetShift / 90.0F, 0.0F, 0.16F);
            filteredTargetYaw = kalmanAngle(filteredTargetYaw, targetYaw,
                    yawCovariance + processNoise, 0.28F);
            filteredTargetPitch = kalmanAngle(filteredTargetPitch, targetPitch,
                    pitchCovariance + processNoise * 0.75F, 0.34F);
            yawCovariance = nextCovariance(yawCovariance + processNoise, 0.28F);
            pitchCovariance = nextCovariance(pitchCovariance + processNoise * 0.75F, 0.34F);
        }
        lastTargetYaw = targetYaw;
        lastTargetPitch = targetPitch;

        float nextYaw = currentYaw;
        float yawDiff = ModernRotationBridge.wrapDegrees(filteredTargetYaw - currentYaw);
        if (Math.abs(yawDiff) > 0.10F) {
            float desired = curvedStep(yawDiff, yawSpeed, 0.18F, curveProgress);
            yawVelocity += (desired - yawVelocity) * 0.34F;
            smoothedYaw = limitAngle(smoothedYaw, filteredTargetYaw,
                    Math.max(0.18F, Math.abs(yawVelocity)));
            nextYaw = limitAngle(currentYaw, smoothedYaw,
                    Math.max(0.18F, Math.abs(yawVelocity)));
        } else {
            yawVelocity *= 0.55F;
        }

        float nextPitch = currentPitch;
        float pitchDiff = ModernRotationBridge.wrapDegrees(filteredTargetPitch - currentPitch);
        if (Math.abs(pitchDiff) > 0.10F) {
            float desired = curvedStep(pitchDiff, pitchSpeed, 0.18F, curveProgress);
            pitchVelocity += (desired - pitchVelocity) * (0.34F * 0.92F);
            smoothedPitch = limitAngle(smoothedPitch, filteredTargetPitch,
                    Math.max(0.18F, Math.abs(pitchVelocity)));
            nextPitch = limitAngle(currentPitch, smoothedPitch,
                    Math.max(0.18F, Math.abs(pitchVelocity)));
        } else {
            pitchVelocity *= 0.55F;
        }
        return new float[]{nextYaw, ModernRotationBridge.clampPitch(nextPitch)};
    }

    void reset() {
        yawVelocity = 0.0F;
        pitchVelocity = 0.0F;
        yawCovariance = 1.0F;
        pitchCovariance = 1.0F;
        curveProgress = 0.0F;
        initialized = false;
    }

    private static float curvedStep(float diff, float maxSpeed, float minStep, float progress) {
        float absolute = Math.abs(diff);
        float speed = Math.max(minStep, maxSpeed);
        float distanceCurve = cubicBezierEase(clamp(absolute / 48.0F, 0.0F, 1.0F), 0.10F, 0.92F);
        float startCurve = 0.38F + cubicBezierEase(clamp(progress, 0.0F, 1.0F), 0.08F, 0.96F) * 0.62F;
        float landingCurve = cubicBezierEase(clamp(absolute / 7.5F, 0.0F, 1.0F), 0.18F, 1.0F);
        landingCurve = clamp(landingCurve, 0.20F, 1.0F);
        float curve = clamp((0.22F + distanceCurve * 0.92F) * startCurve * landingCurve,
                0.18F, 1.12F);
        return clamp(speed * curve, minStep, Math.max(minStep, speed));
    }

    private static float cubicBezierEase(float value, float controlY1, float controlY2) {
        float t = clamp(value, 0.0F, 1.0F);
        float inverse = 1.0F - t;
        return 3.0F * inverse * inverse * t * controlY1
                + 3.0F * inverse * t * t * controlY2
                + t * t * t;
    }

    private static float limitAngle(float current, float target, float maximumTurn) {
        float delta = ModernRotationBridge.wrapDegrees(target - current);
        float limit = Math.max(0.0F, maximumTurn);
        return current + clamp(delta, -limit, limit);
    }

    private static float kalmanAngle(float estimate, float measurement,
                                     float covariance, float measurementNoise) {
        float unwrapped = estimate + ModernRotationBridge.wrapDegrees(measurement - estimate);
        float gain = covariance / (covariance + Math.max(0.001F, measurementNoise));
        return estimate + ModernRotationBridge.wrapDegrees(unwrapped - estimate)
                * clamp(gain, 0.0F, 1.0F);
    }

    private static float nextCovariance(float covariance, float measurementNoise) {
        float gain = covariance / (covariance + Math.max(0.001F, measurementNoise));
        return clamp((1.0F - gain) * covariance, 0.01F, 2.0F);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
