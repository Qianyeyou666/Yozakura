package gq.yozakura.module.render;

import gq.yozakura.util.animation.MotionValue;

/**
 * Retains one Night Bloom ArrayList row while it enters, exits, and moves during a reorder.
 */
final class NightBloomModuleRowMotion {
    static final float VISIBILITY_DURATION_SECONDS = 0.16F;
    static final float REORDER_DURATION_SECONDS = 0.20F;

    private final MotionValue visibility = new MotionValue(0.0F);
    private final MotionValue y = new MotionValue(0.0F);
    private boolean positioned;

    void setVisible(boolean visible) {
        visibility.setTarget(visible ? 1.0F : 0.0F);
    }

    void setTargetY(float targetY) {
        if (!positioned) {
            y.snapTo(targetY);
            positioned = true;
            return;
        }
        y.setTarget(targetY);
    }

    Snapshot update(float deltaSeconds) {
        return new Snapshot(
                visibility.updateTween(deltaSeconds, VISIBILITY_DURATION_SECONDS),
                y.updateTween(deltaSeconds, REORDER_DURATION_SECONDS));
    }

    float getVisibility() {
        return visibility.get();
    }

    boolean isFinishedExit() {
        return visibility.getTarget() == 0.0F && visibility.get() <= 0.01F;
    }

    static final class Snapshot {
        private final float visibility;
        private final float y;

        private Snapshot(float visibility, float y) {
            this.visibility = visibility;
            this.y = y;
        }

        float getVisibility() {
            return visibility;
        }

        float getY() {
            return y;
        }
    }
}
