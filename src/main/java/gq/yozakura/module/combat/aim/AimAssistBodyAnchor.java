package gq.yozakura.module.combat.aim;

/**
 * Keeps the initially selected hit point fixed in world space. The point moves
 * only after the target's attack box no longer contains it, and then only on
 * axes that actually left the box.
 */
public final class AimAssistBodyAnchor {
    private static final double HORIZONTAL_SAFE_INSET = 0.04D;
    private static final double VERTICAL_SAFE_INSET = 0.06D;
    private static final double MINIMUM_BOX_SIZE = 1.0E-9D;

    private double x;
    private double y;
    private double z;

    private AimAssistBodyAnchor(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static AimAssistBodyAnchor capture(double pointX, double pointY, double pointZ) {
        return new AimAssistBodyAnchor(pointX, pointY, pointZ);
    }

    public static AimAssistBodyAnchor captureLevelInnerBox(double minX, double minY, double minZ,
                                                            double maxX, double maxY, double maxZ,
                                                            double preferredY) {
        double height = Math.max(0.0D, maxY - minY);
        double inset = Math.min(VERTICAL_SAFE_INSET * 2.0D, height * 0.25D);
        double safeY = Math.max(minY + inset, Math.min(maxY - inset, preferredY));
        return new AimAssistBodyAnchor(
                (minX + maxX) * 0.5D,
                safeY,
                (minZ + maxZ) * 0.5D);
    }

    public double[] point() {
        return new double[]{x, y, z};
    }

    public double[] translatedPoint(double sourceMinX, double sourceMinY, double sourceMinZ,
                                    double sourceMaxX, double sourceMaxY, double sourceMaxZ,
                                    double targetMinX, double targetMinY, double targetMinZ,
                                    double targetMaxX, double targetMaxY, double targetMaxZ) {
        return new double[]{
                x + centerDelta(sourceMinX, sourceMaxX, targetMinX, targetMaxX),
                y + centerDelta(sourceMinY, sourceMaxY, targetMinY, targetMaxY),
                z + centerDelta(sourceMinZ, sourceMaxZ, targetMinZ, targetMaxZ)
        };
    }

    public boolean followTargetTranslation(double previousMinX, double previousMinY, double previousMinZ,
                                           double previousMaxX, double previousMaxY, double previousMaxZ,
                                           double currentMinX, double currentMinY, double currentMinZ,
                                           double currentMaxX, double currentMaxY, double currentMaxZ) {
        return followTargetTranslation(previousMinX, previousMinY, previousMinZ,
                previousMaxX, previousMaxY, previousMaxZ,
                currentMinX, currentMinY, currentMinZ,
                currentMaxX, currentMaxY, currentMaxZ, 1.0D);
    }

    public boolean followTargetTranslation(double previousMinX, double previousMinY, double previousMinZ,
                                           double previousMaxX, double previousMaxY, double previousMaxZ,
                                           double currentMinX, double currentMinY, double currentMinZ,
                                           double currentMaxX, double currentMaxY, double currentMaxZ,
                                           double verticalBlend) {
        double blend = Math.max(0.0D, Math.min(1.0D, verticalBlend));
        double nextX = x + centerDelta(previousMinX, previousMaxX, currentMinX, currentMaxX);
        double nextY = y + centerDelta(previousMinY, previousMaxY, currentMinY, currentMaxY) * blend;
        double nextZ = z + centerDelta(previousMinZ, previousMaxZ, currentMinZ, currentMaxZ);
        boolean moved = Double.compare(x, nextX) != 0
                || Double.compare(y, nextY) != 0
                || Double.compare(z, nextZ) != 0;
        if (moved) {
            x = nextX;
            y = nextY;
            z = nextZ;
        }
        return moved;
    }

    public boolean clampToBox(double minX, double minY, double minZ,
                              double maxX, double maxY, double maxZ) {
        return clampToRegion(minX, minY, minZ, maxX, maxY, maxZ,
                HORIZONTAL_SAFE_INSET, VERTICAL_SAFE_INSET, false);
    }

    public boolean clampToSafeBox(double minX, double minY, double minZ,
                                  double maxX, double maxY, double maxZ) {
        return clampToRegion(minX, minY, minZ, maxX, maxY, maxZ,
                HORIZONTAL_SAFE_INSET * 2.0D, VERTICAL_SAFE_INSET * 2.0D, true);
    }

    private boolean clampToRegion(double minX, double minY, double minZ,
                                  double maxX, double maxY, double maxZ,
                                  double horizontalInset, double verticalInset,
                                  boolean maintainInset) {
        double nextX = clampAxis(x, minX, maxX, horizontalInset, maintainInset);
        double nextY = clampAxis(y, minY, maxY, verticalInset, maintainInset);
        double nextZ = clampAxis(z, minZ, maxZ, horizontalInset, maintainInset);
        boolean moved = Double.compare(x, nextX) != 0
                || Double.compare(y, nextY) != 0
                || Double.compare(z, nextZ) != 0;
        if (moved) {
            x = nextX;
            y = nextY;
            z = nextZ;
        }
        return moved;
    }

    private static double centerDelta(double sourceMin, double sourceMax,
                                      double targetMin, double targetMax) {
        return (targetMin + targetMax - sourceMin - sourceMax) * 0.5D;
    }

    private static double clampAxis(double value, double minimum, double maximum,
                                    double safeInset, boolean maintainInset) {
        if (maximum - minimum <= MINIMUM_BOX_SIZE) {
            return value < minimum || value > maximum ? (minimum + maximum) * 0.5D : value;
        }
        double inset = Math.min(safeInset, (maximum - minimum) * 0.25D);
        double safeMinimum = minimum + inset;
        double safeMaximum = maximum - inset;
        if (maintainInset) {
            return Math.max(safeMinimum, Math.min(safeMaximum, value));
        }
        if (value < minimum) {
            return safeMinimum;
        }
        if (value > maximum) {
            return safeMaximum;
        }
        return value;
    }
}
