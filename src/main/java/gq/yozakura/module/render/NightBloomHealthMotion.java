package gq.yozakura.module.render;

import gq.yozakura.engine.render.ui.VisualPalette;
import gq.yozakura.util.animation.UiClock;

/**
 * Keeps a responsive health value and a slower damage trail without overshooting either value.
 */
final class NightBloomHealthMotion {
    private static final float HEALTH_RESPONSE_SECONDS = 0.09F;
    private static final float DAMAGE_TRAIL_SECONDS = 0.18F;

    private boolean initialized;
    private float health;
    private float damageTrail;

    Snapshot update(float targetHealth, float deltaSeconds) {
        float target = clamp01(targetHealth);
        float delta = UiClock.clampDelta(deltaSeconds);
        if (!initialized) {
            initialized = true;
            health = target;
            damageTrail = target;
            return new Snapshot(health, damageTrail);
        }

        float previousHealth = health;
        health = approach(health, target, delta, HEALTH_RESPONSE_SECONDS);
        if (target < previousHealth) {
            damageTrail = approach(damageTrail, target, delta, DAMAGE_TRAIL_SECONDS);
        } else {
            damageTrail = health;
        }
        damageTrail = Math.max(health, damageTrail);
        return new Snapshot(health, damageTrail);
    }

    void reset() {
        initialized = false;
        health = 0.0F;
        damageTrail = 0.0F;
    }

    static int colorFor(float healthRatio, VisualPalette palette) {
        if (palette == null) {
            throw new IllegalArgumentException("palette must not be null");
        }
        float ratio = clamp01(healthRatio);
        if (ratio <= 0.30F) {
            return palette.getHealthLow();
        }
        if (ratio <= 0.60F) {
            return palette.getHealthMid();
        }
        return palette.getHealthHigh();
    }

    private static float approach(float current, float target, float deltaSeconds, float responseSeconds) {
        if (deltaSeconds <= 0.0F) {
            return current;
        }
        float factor = 1.0F - (float) Math.exp(-deltaSeconds / responseSeconds);
        return current + (target - current) * factor;
    }

    private static float clamp01(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            throw new IllegalArgumentException("healthRatio must be finite");
        }
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    static final class Snapshot {
        private final float health;
        private final float damageTrail;

        private Snapshot(float health, float damageTrail) {
            this.health = health;
            this.damageTrail = damageTrail;
        }

        float getHealth() {
            return health;
        }

        float getDamageTrail() {
            return damageTrail;
        }
    }
}
