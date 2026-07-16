package gq.yozakura.module.world;

/** Pure raw-input planning utilities for BridgeAssist's next input frame. */
final class BridgeAssistMovementPrediction {
    private static final float VANILLA_MOVE_FLYING_FACTOR = 0.16277136F;
    private static final float VANILLA_GROUND_FRICTION = 0.91F;
    private static final double TICK_MILLIS = 50.0D;

    private BridgeAssistMovementPrediction() {
    }

    static float calculateInputAcceleration(boolean onGround, float aiMoveSpeed,
                                            float jumpMovementFactor, float groundSlipperiness) {
        if (!onGround) {
            return jumpMovementFactor;
        }
        float friction = groundSlipperiness * VANILLA_GROUND_FRICTION;
        return aiMoveSpeed * (VANILLA_MOVE_FLYING_FACTOR / (friction * friction * friction));
    }

    static double[] calculateInputMotion(float forward, float strafe, double speed, float yaw) {
        double input = forward * forward + strafe * strafe;
        if (input < 1.0E-4D) {
            return new double[]{0.0D, 0.0D};
        }
        input = Math.sqrt(input);
        if (input < 1.0D) {
            input = 1.0D;
        }
        input = speed / input;
        strafe *= input;
        forward *= input;
        double yawRadians = Math.toRadians(yaw);
        double sin = Math.sin(yawRadians);
        double cos = Math.cos(yawRadians);
        return new double[]{strafe * cos - forward * sin, forward * cos + strafe * sin};
    }

    static int ticksFromMillis(double millis) {
        return (int) Math.ceil(Math.max(0.0D, millis) / TICK_MILLIS);
    }
}
