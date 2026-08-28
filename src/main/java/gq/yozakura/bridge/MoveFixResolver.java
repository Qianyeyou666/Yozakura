package gq.yozakura.bridge;

/** Resolves one sampled movement input against the yaw used for movement physics. */
final class MoveFixResolver {
    private static final float INPUT_EPSILON = 0.0001F;

    private MoveFixResolver() {
    }

    static ResolvedInput resolve(float visualYaw, float physicsYaw, float forward, float strafe) {
        if (Math.abs(forward) < INPUT_EPSILON && Math.abs(strafe) < INPUT_EPSILON) {
            return new ResolvedInput(forward, strafe);
        }

        float desiredYaw = movementYaw(visualYaw, forward, strafe);
        int bestForward = 0;
        int bestStrafe = 0;
        float bestDelta = Float.MAX_VALUE;

        for (int candidateForward = -1; candidateForward <= 1; candidateForward++) {
            for (int candidateStrafe = -1; candidateStrafe <= 1; candidateStrafe++) {
                if (candidateForward == 0 && candidateStrafe == 0) {
                    continue;
                }
                float candidateYaw = movementYaw(physicsYaw, candidateForward, candidateStrafe);
                float delta = angleDelta(desiredYaw, candidateYaw);
                if (delta < bestDelta) {
                    bestDelta = delta;
                    bestForward = candidateForward;
                    bestStrafe = candidateStrafe;
                }
            }
        }
        float axisScale = resolveAxisScale(forward, strafe);
        return new ResolvedInput(bestForward * axisScale, bestStrafe * axisScale);
    }

    private static float resolveAxisScale(float forward, float strafe) {
        // Grim 1.8 enumerates physical -1/0/1 key axes and then applies the
        // shared sneak scale. Preserve that shared scale instead of creating
        // magnitude-preserving fractional axes that no real key state emits.
        return Math.min(1.0F, Math.max(Math.abs(forward), Math.abs(strafe)));
    }

    private static float movementYaw(float yaw, float forward, float strafe) {
        float result = yaw;
        if (forward < 0.0F) {
            result += 180.0F;
        }
        float strafeFactor = forward < 0.0F ? -0.5F : forward > 0.0F ? 0.5F : 1.0F;
        if (strafe > 0.0F) {
            result -= 90.0F * strafeFactor;
        }
        if (strafe < 0.0F) {
            result += 90.0F * strafeFactor;
        }
        return result;
    }

    private static float angleDelta(float first, float second) {
        float delta = (first - second) % 360.0F;
        if (delta >= 180.0F) {
            delta -= 360.0F;
        }
        if (delta < -180.0F) {
            delta += 360.0F;
        }
        return Math.abs(delta);
    }

    static final class ResolvedInput {
        private final float forward;
        private final float strafe;

        private ResolvedInput(float forward, float strafe) {
            this.forward = forward;
            this.strafe = strafe;
        }

        float getForward() {
            return forward;
        }

        float getStrafe() {
            return strafe;
        }
    }
}
