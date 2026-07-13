package gq.yozakura.util.animation;

/**
 * Retained scalar UI animation state. Target changes start from the current rendered value, so a
 * component can retarget an in-flight animation without a visual jump.
 */
public final class MotionValue {
    private static final float REDUCED_DURATION_FACTOR = 0.35F;
    private static final float SETTLE_COEFFICIENT = 6.64F;
    private static final float REST_EPSILON = 0.00001F;

    public enum Mode {
        FULL,
        REDUCED,
        OFF
    }

    private float current;
    private float target;
    private float velocity;
    private float tweenStart;
    private float tweenElapsed;
    private boolean tweenActive;
    private Mode mode;

    public MotionValue(float initialValue) {
        this(initialValue, Mode.FULL);
    }

    public MotionValue(float initialValue, Mode mode) {
        requireFinite(initialValue, "initialValue");
        if (mode == null) {
            throw new IllegalArgumentException("mode must not be null");
        }
        current = initialValue;
        target = initialValue;
        tweenStart = initialValue;
        this.mode = mode;
    }

    public float get() {
        return current;
    }

    public float getTarget() {
        return target;
    }

    public Mode getMode() {
        return mode;
    }

    /**
     * Changes the target only when it differs. Repeating a target therefore preserves tween
     * progress instead of restarting its easing curve.
     */
    public void setTarget(float target) {
        requireFinite(target, "target");
        if (Float.compare(this.target, target) == 0) {
            return;
        }

        this.target = target;
        if (mode == Mode.OFF) {
            snapTo(target);
            return;
        }

        tweenStart = current;
        tweenElapsed = 0.0F;
        tweenActive = true;
    }

    /**
     * Applies the requested accessibility motion mode. Entering {@link Mode#OFF} immediately
     * resolves the current value to its target; a mode switch while tweening rebases at the
     * current value so it does not introduce a jump.
     */
    public void setMode(Mode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("mode must not be null");
        }
        if (this.mode == mode) {
            return;
        }

        this.mode = mode;
        if (mode == Mode.OFF) {
            snapTo(target);
        } else if (tweenActive) {
            tweenStart = current;
            tweenElapsed = 0.0F;
        }
    }

    public void snapTo(float value) {
        requireFinite(value, "value");
        current = value;
        target = value;
        tweenStart = value;
        tweenElapsed = 0.0F;
        velocity = 0.0F;
        tweenActive = false;
    }

    /**
     * Advances an out-cubic tween. Duration is its full-motion duration; reduced motion shortens
     * it to 35%, while off motion resolves immediately.
     */
    public float updateTween(float deltaSeconds, float durationSeconds) {
        requireDuration(durationSeconds);
        if (mode == Mode.OFF) {
            snapTo(target);
            return current;
        }
        if (!tweenActive) {
            return current;
        }

        tweenElapsed += UiClock.clampDelta(deltaSeconds);
        float progress = Math.min(1.0F, tweenElapsed / effectiveDuration(durationSeconds));
        current = tweenStart + (target - tweenStart) * easeOutCubic(progress);
        if (progress >= 1.0F) {
            current = target;
            velocity = 0.0F;
            tweenActive = false;
        }
        return current;
    }

    /**
     * Advances an analytic critically damped spring. {@code settleSeconds} is the approximate
     * full-motion time to settle within one percent of the target.
     */
    public float updateSpring(float deltaSeconds, float settleSeconds) {
        requireDuration(settleSeconds);
        if (mode == Mode.OFF) {
            snapTo(target);
            return current;
        }

        tweenActive = false;
        float delta = UiClock.clampDelta(deltaSeconds);
        if (delta == 0.0F) {
            return current;
        }

        float omega = SETTLE_COEFFICIENT / effectiveDuration(settleSeconds);
        float offset = current - target;
        if (offset == 0.0F) {
            velocity = 0.0F;
            return current;
        }
        float velocityOffset = velocity + omega * offset;
        float decay = (float) Math.exp(-omega * delta);
        float nextOffset = (offset + velocityOffset * delta) * decay;
        float nextVelocity = (velocity - omega * velocityOffset * delta) * decay;
        if ((offset > 0.0F && nextOffset < 0.0F) || (offset < 0.0F && nextOffset > 0.0F)) {
            snapTo(target);
            return current;
        }

        velocity = nextVelocity;
        current = target + nextOffset;

        if (Math.abs(current - target) <= REST_EPSILON && Math.abs(velocity) <= REST_EPSILON) {
            current = target;
            velocity = 0.0F;
        }
        return current;
    }

    private float effectiveDuration(float fullDurationSeconds) {
        return mode == Mode.REDUCED
                ? fullDurationSeconds * REDUCED_DURATION_FACTOR
                : fullDurationSeconds;
    }

    private static float easeOutCubic(float progress) {
        float inverse = 1.0F - progress;
        return 1.0F - inverse * inverse * inverse;
    }

    private static void requireDuration(float durationSeconds) {
        requireFinite(durationSeconds, "durationSeconds");
        if (durationSeconds <= 0.0F) {
            throw new IllegalArgumentException("durationSeconds must be greater than zero");
        }
    }

    private static void requireFinite(float value, String name) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
