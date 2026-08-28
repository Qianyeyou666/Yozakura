package gq.yozakura.module.render;

import gq.yozakura.util.animation.MotionValue;
import gq.yozakura.util.animation.UiClock;

/**
 * Time-based, interruptible motion for the Apple-style TargetHUD.
 * Every value is a critically damped spring so a retarget starts from the
 * currently rendered value and can be reversed without a visual jump.
 */
final class AppleTargetHudMotion {
    static final int NO_TARGET = -1;

    private static final float ENTER_SETTLE_SECONDS = 0.18F;
    private static final float EXIT_SETTLE_SECONDS = 0.14F;
    private static final float SWITCH_SETTLE_SECONDS = 0.14F;
    private static final float HEALTH_SETTLE_SECONDS = 0.16F;
    private static final float DAMAGE_FALL_SETTLE_SECONDS = 0.50F;
    private static final float DAMAGE_RISE_SETTLE_SECONDS = 0.12F;
    private static final float HURT_SETTLE_SECONDS = 0.14F;
    private static final float PANEL_Y_OFFSET = 8.0F;

    private int currentTargetId = NO_TARGET;
    private int previousTargetId = NO_TARGET;
    private boolean targetPresent;
    private final MotionValue visibility = new MotionValue(0.0F);
    private final MotionValue switchProgress = new MotionValue(1.0F);
    private final MotionValue health = new MotionValue(0.0F);
    private final MotionValue damageTrail = new MotionValue(0.0F);
    private final MotionValue hurt = new MotionValue(0.0F);

    void acquire(int targetId, float targetHealth) {
        if (targetId == NO_TARGET) {
            throw new IllegalArgumentException("targetId must identify a target");
        }
        float resolvedHealth = clamp01(targetHealth);
        targetPresent = true;
        if (currentTargetId == targetId) {
            return;
        }

        boolean hadTarget = currentTargetId != NO_TARGET && visibility.get() > 0.0001F;
        boolean hadCrossfade = previousTargetId != NO_TARGET;
        previousTargetId = hadTarget ? currentTargetId : NO_TARGET;
        currentTargetId = targetId;
        if (hadTarget) {
            if (!hadCrossfade || switchProgress.get() >= 1.0F) {
                switchProgress.snapTo(0.0F);
            }
        } else {
            switchProgress.snapTo(1.0F);
            health.snapTo(resolvedHealth);
            damageTrail.snapTo(resolvedHealth);
        }
    }

    void release() {
        targetPresent = false;
    }

    void update(float deltaSeconds, float targetHealth, boolean hurtNow) {
        float delta = UiClock.clampDelta(deltaSeconds);
        visibility.setTarget(targetPresent ? 1.0F : 0.0F);
        visibility.updateSpring(delta, targetPresent ? ENTER_SETTLE_SECONDS : EXIT_SETTLE_SECONDS);

        if (previousTargetId != NO_TARGET) {
            switchProgress.setTarget(1.0F);
            switchProgress.updateSpring(delta, SWITCH_SETTLE_SECONDS);
            if (switchProgress.get() >= 0.98F) {
                switchProgress.snapTo(1.0F);
                previousTargetId = NO_TARGET;
            }
        }

        if (currentTargetId != NO_TARGET) {
            float resolvedHealth = clamp01(targetHealth);
            health.setTarget(resolvedHealth);
            health.updateSpring(delta, HEALTH_SETTLE_SECONDS);

            damageTrail.setTarget(resolvedHealth);
            float damageSettle = resolvedHealth < damageTrail.get()
                    ? DAMAGE_FALL_SETTLE_SECONDS : DAMAGE_RISE_SETTLE_SECONDS;
            damageTrail.updateSpring(delta, damageSettle);
            if (damageTrail.get() < health.get()) {
                damageTrail.snapTo(health.get());
            }
        }

        hurt.setTarget(hurtNow ? 1.0F : 0.0F);
        hurt.updateSpring(delta, HURT_SETTLE_SECONDS);

        if (!targetPresent && visibility.get() <= 0.02F) {
            visibility.snapTo(0.0F);
            currentTargetId = NO_TARGET;
            previousTargetId = NO_TARGET;
            switchProgress.snapTo(1.0F);
            health.snapTo(0.0F);
            damageTrail.snapTo(0.0F);
            hurt.snapTo(0.0F);
        }
    }

    void reset() {
        currentTargetId = NO_TARGET;
        previousTargetId = NO_TARGET;
        targetPresent = false;
        visibility.snapTo(0.0F);
        switchProgress.snapTo(1.0F);
        health.snapTo(0.0F);
        damageTrail.snapTo(0.0F);
        hurt.snapTo(0.0F);
    }

    boolean isPresent() {
        return targetPresent;
    }

    boolean hasRetainedTarget() {
        return currentTargetId != NO_TARGET;
    }

    int getCurrentTargetId() {
        return currentTargetId;
    }

    int getPreviousTargetId() {
        return previousTargetId;
    }

    float getVisibility() {
        return visibility.get();
    }

    float getCurrentContentAlpha() {
        if (!hasRetainedTarget()) {
            return 0.0F;
        }
        float content = previousTargetId == NO_TARGET ? 1.0F : smoothStep(switchProgress.get());
        return smoothStep(visibility.get()) * content;
    }

    float getPreviousContentAlpha() {
        if (previousTargetId == NO_TARGET) {
            return 0.0F;
        }
        return smoothStep(visibility.get()) * (1.0F - smoothStep(switchProgress.get()));
    }

    float getPanelScale() {
        return 0.96F + 0.04F * smoothStep(visibility.get());
    }

    float getPanelYOffset() {
        return PANEL_Y_OFFSET * (1.0F - smoothStep(visibility.get()));
    }

    float getHealth() {
        return health.get();
    }

    float getDamageTrail() {
        return damageTrail.get();
    }

    float getHurt() {
        return hurt.get();
    }

    private static float smoothStep(float value) {
        float t = clamp01(value);
        return t * t * (3.0F - 2.0F * t);
    }

    private static float clamp01(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            throw new IllegalArgumentException("value must be finite");
        }
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
