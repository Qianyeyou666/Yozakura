package gq.yozakura.bridge.modern;

final class ModernAimMath {
    static final double DEFAULT_BOW_GRAVITY = 0.006D;

    private ModernAimMath() {
    }

    static double bowVelocity(int useTicks) {
        double charge = Math.max(0, useTicks) / 20.0D;
        double velocity = (charge * charge + charge * 2.0D) / 3.0D;
        return Math.min(velocity, 1.0D);
    }

    static PredictedPoint predict(double x, double y, double z,
                                  double previousX, double previousY, double previousZ,
                                  double eyeHeight, double prediction) {
        double safePrediction = Math.max(0.0D, prediction);
        return new PredictedPoint(
                x + (x - previousX) * safePrediction,
                y + (y - previousY) * Math.min(0.8D, safePrediction) + eyeHeight - 0.15D,
                z + (z - previousZ) * safePrediction);
    }

    static BallisticSolution solveLowArc(double deltaX, double deltaY, double deltaZ,
                                         double velocity, double gravity) {
        double horizontal = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        if (horizontal <= 1.0E-6D || velocity <= 0.0D || gravity <= 0.0D) {
            return BallisticSolution.UNREACHABLE;
        }
        double velocitySquared = velocity * velocity;
        double discriminant = velocitySquared * velocitySquared
                - gravity * (gravity * horizontal * horizontal
                + 2.0D * deltaY * velocitySquared);
        if (discriminant < 0.0D || Double.isNaN(discriminant)) {
            return BallisticSolution.UNREACHABLE;
        }
        double yaw = Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0D;
        double pitch = -Math.toDegrees(Math.atan(
                (velocitySquared - Math.sqrt(discriminant)) / (gravity * horizontal)));
        if (Double.isNaN(yaw) || Double.isInfinite(yaw)
                || Double.isNaN(pitch) || Double.isInfinite(pitch)) {
            return BallisticSolution.UNREACHABLE;
        }
        return new BallisticSolution(true, (float) yaw, (float) pitch);
    }

    static final class PredictedPoint {
        private final double x;
        private final double y;
        private final double z;

        PredictedPoint(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        double getX() {
            return x;
        }

        double getY() {
            return y;
        }

        double getZ() {
            return z;
        }
    }

    static final class BallisticSolution {
        private static final BallisticSolution UNREACHABLE =
                new BallisticSolution(false, 0.0F, 0.0F);

        private final boolean reachable;
        private final float yaw;
        private final float pitch;

        BallisticSolution(boolean reachable, float yaw, float pitch) {
            this.reachable = reachable;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        boolean isReachable() {
            return reachable;
        }

        float getYaw() {
            return yaw;
        }

        float getPitch() {
            return pitch;
        }
    }
}
