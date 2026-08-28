package gq.yozakura.module.render;

import net.minecraft.entity.EntityLivingBase;

/** Short impact response triggered when the displayed Cool target receives damage. */
final class CoolTargetHudHurtMotion {
    private static final long DURATION_MILLIS = 180L;
    private static final int NO_TARGET = Integer.MIN_VALUE;

    private int targetId = NO_TARGET;
    private int previousHurtTime;
    private long startedAt;

    Snapshot update(EntityLivingBase target, long nowMillis) {
        if (target == null) {
            reset();
            return Snapshot.NONE;
        }
        int nextTargetId = target.getEntityId();
        int hurtTime = Math.max(0, target.hurtTime);
        if (targetId != nextTargetId) {
            targetId = nextTargetId;
            previousHurtTime = hurtTime;
            startedAt = 0L;
            return Snapshot.NONE;
        }
        if (hurtTime > previousHurtTime) {
            trigger(nowMillis);
        }
        previousHurtTime = hurtTime;
        return snapshot(nowMillis);
    }

    void reset() {
        targetId = NO_TARGET;
        previousHurtTime = 0;
        startedAt = 0L;
    }

    void trigger(long nowMillis) {
        startedAt = nowMillis;
    }

    Snapshot snapshot(long nowMillis) {
        if (startedAt <= 0L) {
            return Snapshot.NONE;
        }
        float progress = clamp01((nowMillis - startedAt) / (float) DURATION_MILLIS);
        if (progress >= 1.0F) {
            startedAt = 0L;
            return Snapshot.NONE;
        }
        // Smooth envelope and a low-frequency damped wave keep the hit response springy without snapping.
        float easedProgress = progress * progress * (3.0F - 2.0F * progress);
        float intensity = 1.0F - easedProgress;
        float shakeX = (float) Math.sin(progress * Math.PI * 3.0D) * intensity * 1.35F;
        return new Snapshot(intensity, shakeX);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    static final class Snapshot {
        static final Snapshot NONE = new Snapshot(0.0F, 0.0F);

        private final float intensity;
        private final float shakeX;

        Snapshot(float intensity, float shakeX) {
            this.intensity = intensity;
            this.shakeX = shakeX;
        }

        float getIntensity() {
            return intensity;
        }

        float getShakeX() {
            return shakeX;
        }
    }
}
