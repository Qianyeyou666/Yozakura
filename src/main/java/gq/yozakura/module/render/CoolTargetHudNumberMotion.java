package gq.yozakura.module.render;

/** Smooths Cool HUD health digits separately from the health-bar animation. */
final class CoolTargetHudNumberMotion {
    private static final float HEALTH_EASING = 12.0F;
    private static final float PULSE_DECAY = 6.0F;
    private static final float PULSE_SCALE = 0.08F;

    private int targetId = Integer.MIN_VALUE;
    private float health;
    private int displayedDigit;
    private float pulse;

    void snap(int nextTargetId, float nextHealth) {
        targetId = nextTargetId;
        health = Math.max(0.0F, nextHealth);
        displayedDigit = Math.round(health);
        pulse = 0.0F;
    }

    Snapshot update(int nextTargetId, float targetHealth, float deltaSeconds) {
        float clampedTarget = Math.max(0.0F, targetHealth);
        if (targetId != nextTargetId) {
            snap(nextTargetId, clampedTarget);
            return snapshot();
        }

        float seconds = Math.max(0.0F, Math.min(0.1F, deltaSeconds));
        float blend = 1.0F - (float) Math.exp(-HEALTH_EASING * seconds);
        health += (clampedTarget - health) * blend;
        if (Math.abs(clampedTarget - health) < 0.005F) {
            health = clampedTarget;
        }
        int nextDigit = Math.round(health);
        if (nextDigit != displayedDigit) {
            displayedDigit = nextDigit;
            pulse = 1.0F;
        }
        pulse = Math.max(0.0F, pulse - PULSE_DECAY * seconds);
        return snapshot();
    }

    void reset() {
        targetId = Integer.MIN_VALUE;
        health = 0.0F;
        displayedDigit = 0;
        pulse = 0.0F;
    }

    private Snapshot snapshot() {
        return new Snapshot(health, 1.0F + pulse * PULSE_SCALE);
    }

    static final class Snapshot {
        private final float health;
        private final float scale;

        Snapshot(float health, float scale) {
            this.health = health;
            this.scale = scale;
        }

        float getHealth() {
            return health;
        }

        float getScale() {
            return scale;
        }
    }
}
