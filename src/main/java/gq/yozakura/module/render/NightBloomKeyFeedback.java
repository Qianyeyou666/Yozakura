package gq.yozakura.module.render;

import gq.yozakura.util.animation.MotionValue;

/**
 * A short, interruptible key press response for Night Bloom widgets.
 */
final class NightBloomKeyFeedback {
    static final float DURATION_SECONDS = 0.105F;

    private final MotionValue motion = new MotionValue(0.0F);

    void setPressed(boolean pressed) {
        motion.setTarget(pressed ? 1.0F : 0.0F);
    }

    float update(float deltaSeconds) {
        return motion.updateTween(deltaSeconds, DURATION_SECONDS);
    }

    float get() {
        return motion.get();
    }
}
