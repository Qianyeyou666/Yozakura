package gq.yozakura.module.render;

final class TargetHudMotion {
    static final int NO_TARGET = -1;

    private static final float ENTER_SECONDS = 0.18F;
    private static final float EXIT_SECONDS = 0.14F;
    private static final float SWITCH_SECONDS = 0.14F;
    private static final float MAX_DELTA_SECONDS = 0.05F;
    private static final float HEALTH_RESPONSE_SECONDS = 0.11F;
    private static final float DAMAGE_FALL_RESPONSE_SECONDS = 0.42F;
    private static final float DAMAGE_RISE_RESPONSE_SECONDS = 0.09F;

    private int currentTargetId = NO_TARGET;
    private int previousTargetId = NO_TARGET;
    private boolean targetPresent;
    private float visibility;
    private float switchProgress = 1.0F;
    private float health;
    private float damageTrail;

    void reset() {
        currentTargetId = NO_TARGET;
        previousTargetId = NO_TARGET;
        targetPresent = false;
        visibility = 0.0F;
        switchProgress = 1.0F;
        health = 0.0F;
        damageTrail = 0.0F;
    }

    void acquire(int targetId, float targetHealth) {
        if (targetId == NO_TARGET) {
            throw new IllegalArgumentException("targetId must identify a target");
        }
        float resolvedHealth = clamp01(targetHealth);
        targetPresent = true;
        if (currentTargetId == targetId) {
            return;
        }

        boolean hadTarget = currentTargetId != NO_TARGET && visibility > 0.0001F;
        previousTargetId = hadTarget ? currentTargetId : NO_TARGET;
        currentTargetId = targetId;
        switchProgress = hadTarget ? 0.0F : 1.0F;
        if (!hadTarget) {
            health = resolvedHealth;
            damageTrail = resolvedHealth;
        }
    }

    void release() {
        targetPresent = false;
    }

    void update(float deltaSeconds, float targetHealth) {
        float delta = sanitizeDelta(deltaSeconds);
        float visibilityStep = delta / (targetPresent ? ENTER_SECONDS : EXIT_SECONDS);
        visibility = moveTowards(visibility, targetPresent ? 1.0F : 0.0F, visibilityStep);

        if (previousTargetId != NO_TARGET) {
            switchProgress = moveTowards(switchProgress, 1.0F, delta / SWITCH_SECONDS);
            if (switchProgress >= 1.0F) {
                previousTargetId = NO_TARGET;
            }
        }

        if (currentTargetId != NO_TARGET) {
            float resolvedHealth = clamp01(targetHealth);
            health = expApproach(health, resolvedHealth, delta, HEALTH_RESPONSE_SECONDS);
            float damageResponse = resolvedHealth < damageTrail
                    ? DAMAGE_FALL_RESPONSE_SECONDS : DAMAGE_RISE_RESPONSE_SECONDS;
            damageTrail = expApproach(damageTrail, resolvedHealth, delta, damageResponse);
            if (damageTrail < health && resolvedHealth <= health) {
                damageTrail = health;
            }
        }

        if (!targetPresent && visibility <= 0.0001F) {
            visibility = 0.0F;
            currentTargetId = NO_TARGET;
            previousTargetId = NO_TARGET;
            switchProgress = 1.0F;
            health = 0.0F;
            damageTrail = 0.0F;
        }
    }

    int getCurrentTargetId() {
        return currentTargetId;
    }

    int getPreviousTargetId() {
        return previousTargetId;
    }

    boolean hasRetainedTarget() {
        return currentTargetId != NO_TARGET;
    }

    float getVisibility() {
        return visibility;
    }

    float getCurrentContentAlpha() {
        if (!hasRetainedTarget()) {
            return 0.0F;
        }
        float content = previousTargetId == NO_TARGET ? 1.0F : smoothStep(switchProgress);
        return smoothStep(visibility) * content;
    }

    float getPreviousContentAlpha() {
        if (previousTargetId == NO_TARGET) {
            return 0.0F;
        }
        return smoothStep(visibility) * (1.0F - smoothStep(switchProgress));
    }

    float getPanelScale() {
        return 0.96F + 0.04F * smoothStep(visibility);
    }

    float getPanelYOffset() {
        return 6.0F * (1.0F - smoothStep(visibility));
    }

    float getHealth() {
        return health;
    }

    float getDamageTrail() {
        return damageTrail;
    }

    private static float sanitizeDelta(float deltaSeconds) {
        if (Float.isNaN(deltaSeconds) || Float.isInfinite(deltaSeconds)) {
            throw new IllegalArgumentException("deltaSeconds must be finite");
        }
        return Math.max(0.0F, Math.min(MAX_DELTA_SECONDS, deltaSeconds));
    }

    private static float expApproach(float current, float target, float deltaSeconds, float responseSeconds) {
        if (deltaSeconds <= 0.0F) {
            return current;
        }
        float factor = 1.0F - (float) Math.exp(-deltaSeconds / responseSeconds);
        return current + (target - current) * factor;
    }

    private static float moveTowards(float current, float target, float maximumDelta) {
        if (current < target) {
            return Math.min(target, current + maximumDelta);
        }
        return Math.max(target, current - maximumDelta);
    }

    private static float smoothStep(float value) {
        float t = clamp01(value);
        return t * t * (3.0F - 2.0F * t);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
