package gq.yozakura.ui.click.yozakura;

/** Time-based damped motion used by the direct OpenGL Panel host. */
public final class PanelClickGuiMotion {
    private PanelClickGuiMotion() {
    }

    public static float approach(float current, float target, float deltaMs, float responseMs) {
        if (Math.abs(target - current) <= 0.001f) {
            return target;
        }
        float response = Math.max(1.0f, responseMs);
        float delta = Math.max(0.0f, Math.min(100.0f, deltaMs));
        float factor = 1.0f - (float) Math.exp(-delta / response);
        float next = current + (target - current) * factor;
        return Math.abs(target - next) <= 0.001f ? target : next;
    }

    public static float addWheelImpulse(float velocity, float scrollY) {
        return velocity - scrollY * 24.0f;
    }

    public static ScrollFrame advanceScroll(float scroll, float velocity, float frameScale,
                                            float minScroll, float maxScroll) {
        float min = Math.min(minScroll, maxScroll);
        float max = Math.max(minScroll, maxScroll);
        float delta = Math.max(0.0f, Math.min(6.0f, frameScale));
        float decay = (float) Math.pow(0.86f, delta);
        float travel = (1.0f - decay) / (1.0f - 0.86f);
        float nextScroll = clamp(scroll + velocity * travel, min, max);
        float nextVelocity = velocity * decay;
        if (Math.abs(nextVelocity) < 0.3f) {
            nextVelocity = 0.0f;
        }
        return new ScrollFrame(nextScroll, nextVelocity);
    }

    public static float easeOutCubic(float progress) {
        float value = Math.max(0.0f, Math.min(1.0f, progress));
        float inverse = 1.0f - value;
        return 1.0f - inverse * inverse * inverse;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static final class ScrollFrame {
        private final float scroll;
        private final float velocity;

        private ScrollFrame(float scroll, float velocity) {
            this.scroll = scroll;
            this.velocity = velocity;
        }

        public float scroll() {
            return scroll;
        }

        public float velocity() {
            return velocity;
        }
    }
}
