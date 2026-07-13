package gq.yozakura.ui.click.sakura;

import gq.yozakura.util.animation.UiClock;

/**
 * Time-based interpolation tuned to retain Sakura's existing 60 FPS response curve.
 */
final class SakuraUiMotion {
    private static final float REFERENCE_FPS = 60.0F;

    private SakuraUiMotion() {
    }

    static float approach(float current, float target, float perFrameSpeed, float deltaSeconds) {
        float speed = Math.max(0.0F, Math.min(1.0F, perFrameSpeed));
        if (speed <= 0.0F || current == target) {
            return current;
        }
        if (speed >= 1.0F) {
            return target;
        }

        float delta = UiClock.clampDelta(deltaSeconds);
        if (delta <= 0.0F) {
            return current;
        }

        float responsePerSecond = -(float) Math.log(1.0F - speed) * REFERENCE_FPS;
        float factor = 1.0F - (float) Math.exp(-responsePerSecond * delta);
        return current + (target - current) * factor;
    }
}
