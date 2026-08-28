package gq.yozakura.module.combat;

/** Selects a stable reachable point on a target collision box. */
final class KillAuraAimPoint {
    private static final double INSIDE_EPSILON_SQUARED = 0.000001D;

    private KillAuraAimPoint() {
    }

    static Point closest(double eyeX, double eyeY, double eyeZ,
                         double minX, double minY, double minZ,
                         double maxX, double maxY, double maxZ) {
        double x = clamp(eyeX, minX, maxX);
        double y = clamp(eyeY, minY, maxY);
        double z = clamp(eyeZ, minZ, maxZ);
        double dx = x - eyeX;
        double dy = y - eyeY;
        double dz = z - eyeZ;
        if (dx * dx + dy * dy + dz * dz < INSIDE_EPSILON_SQUARED) {
            return new Point((minX + maxX) * 0.5D, (minY + maxY) * 0.5D,
                    (minZ + maxZ) * 0.5D);
        }
        return new Point(x, y, z);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(value, max));
    }

    static final class Point {
        private final double x;
        private final double y;
        private final double z;

        private Point(double x, double y, double z) {
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
}
