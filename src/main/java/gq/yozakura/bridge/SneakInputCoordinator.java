package gq.yozakura.bridge;

import gq.yozakura.event.bridge.SneakInputEvent;

/** Owns final per-frame sneak state before legacy movement listeners run. */
final class SneakInputCoordinator {
    static final float SNEAK_MULTIPLIER = 0.3F;

    private boolean safeWalkRequested;

    void setSafeWalkRequested(boolean requested) {
        safeWalkRequested = requested;
    }

    void clear() {
        safeWalkRequested = false;
    }

    ResolvedInput resolve(boolean sampledSneak, float rawForward, float rawStrafe,
                          SneakInputEvent.SneakIntent intent) {
        return resolve(sampledSneak, rawForward, rawStrafe, safeWalkRequested, intent);
    }

    static ResolvedInput resolve(boolean sampledSneak, float rawForward, float rawStrafe,
                                 boolean safeWalkRequested, SneakInputEvent.SneakIntent intent) {
        boolean sneak = resolveSneak(sampledSneak, safeWalkRequested, intent);
        float multiplier = sneak ? SNEAK_MULTIPLIER : 1.0F;
        return new ResolvedInput(sneak, rawForward * multiplier, rawStrafe * multiplier);
    }

    static float toRawAxis(float axis, boolean sampledSneak) {
        return sampledSneak ? axis / SNEAK_MULTIPLIER : axis;
    }

    private static boolean resolveSneak(boolean sampledSneak, boolean safeWalkRequested,
                                        SneakInputEvent.SneakIntent intent) {
        if (safeWalkRequested) {
            return true;
        }
        if (intent == SneakInputEvent.SneakIntent.FORCE_ON) {
            return true;
        }
        if (intent == SneakInputEvent.SneakIntent.FORCE_OFF) {
            return false;
        }
        return sampledSneak;
    }

    static final class ResolvedInput {
        private final boolean sneaking;
        private final float forward;
        private final float strafe;

        private ResolvedInput(boolean sneaking, float forward, float strafe) {
            this.sneaking = sneaking;
            this.forward = forward;
            this.strafe = strafe;
        }

        boolean isSneaking() {
            return sneaking;
        }

        float getForward() {
            return forward;
        }

        float getStrafe() {
            return strafe;
        }
    }
}
