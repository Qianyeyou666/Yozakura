package gq.yozakura.module.world;

/**
 * Resolves the recorded Telly movement program into bounded input edges.
 * The controller is tick deterministic and intentionally independent of Minecraft state.
 */
final class TellyBridgeMotionCurve {
    private static final float AXIS_SLEW_PER_TICK = 0.55F;
    private static final float AXIS_RELEASE_RESPONSE = 0.45F;
    private static final float AXIS_SNAP_EPSILON = 0.01F;

    static final class Sample {
        final float forward;
        final float strafe;
        final boolean jump;

        Sample(float forward, float strafe, boolean jump) {
            this.forward = forward;
            this.strafe = strafe;
            this.jump = jump;
        }
    }

    private float forward;
    private float strafe;
    private boolean jumpWindowActive;
    private boolean wasOnGround;
    private int sampledTick = Integer.MIN_VALUE;
    private Sample sampled;

    void reset(float forward, float strafe) {
        this.forward = clampAxis(forward);
        this.strafe = clampAxis(strafe);
        jumpWindowActive = false;
        wasOnGround = false;
        sampledTick = Integer.MIN_VALUE;
        sampled = null;
    }

    Sample sample(float targetForward, float targetStrafe, boolean wantsJump,
                  boolean onGround, int tick) {
        if (sampled != null && sampledTick == tick) {
            return sampled;
        }
        forward = resolveAxis(forward, clampAxis(targetForward));
        strafe = resolveAxis(strafe, clampAxis(targetStrafe));

        boolean jump = false;
        if (wantsJump) {
            jump = !jumpWindowActive || onGround && !wasOnGround;
            jumpWindowActive = true;
        } else {
            jumpWindowActive = false;
        }
        wasOnGround = onGround;
        sampledTick = tick;
        sampled = new Sample(forward, strafe, jump);
        return sampled;
    }

    private static float resolveAxis(float current, float target) {
        if (Math.abs(target) <= AXIS_SNAP_EPSILON) {
            float released = current * (1.0F - AXIS_RELEASE_RESPONSE);
            return Math.abs(released) <= AXIS_SNAP_EPSILON ? 0.0F : released;
        }
        float delta = target - current;
        if (Math.abs(delta) <= AXIS_SLEW_PER_TICK) {
            return target;
        }
        return current + Math.copySign(AXIS_SLEW_PER_TICK, delta);
    }

    private static float clampAxis(float value) {
        return Math.max(-1.0F, Math.min(1.0F, value));
    }
}
