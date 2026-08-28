package gq.yozakura.ui.click.yozakura;

/** Keeps scroll bounds independent from rendering so wheel behavior is deterministic. */
public final class ClickGuiScrollModel {
    private float current;
    private float target;
    private float maximum;

    public void updateBounds(float contentHeight, float viewportHeight) {
        maximum = Math.max(0f, contentHeight - Math.max(0f, viewportHeight));
        current = clamp(current);
        target = clamp(target);
    }

    public void onWheel(int delta) {
        target = clamp(target - delta * 0.25f);
    }

    public void reset() {
        current = 0f;
        target = 0f;
        maximum = 0f;
    }

    public void animate(float frameScale) {
        current = AnimationUtilBridge.approach(current, target, frameScale);
    }

    public void snapToTarget() {
        current = target;
    }

    private float clamp(float value) {
        return Math.max(0f, Math.min(maximum, value));
    }

    public float current() { return current; }
    public float target() { return target; }
    public float maximum() { return maximum; }

    private static final class AnimationUtilBridge {
        private static float approach(float current, float target, float frameScale) {
            float amount = 1f - (float) Math.pow(1f - ClickGuiTheme.EASE_OUT_SPEED,
                    Math.max(0f, frameScale));
            return current + (target - current) * amount;
        }
    }
}
