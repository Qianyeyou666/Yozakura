package gq.yozakura.module.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ProjectileWarningPolicy {
    private static final double EPSILON = 1.0E-7D;

    private ProjectileWarningPolicy() {
    }

    static List<Point> trace(Point position, Point motion, Point acceleration,
                             double gravity, double drag, int ticks) {
        if (position == null || motion == null || acceleration == null || ticks < 0) {
            return Collections.emptyList();
        }
        ArrayList<Point> points = new ArrayList<Point>(ticks + 1);
        double x = position.x;
        double y = position.y;
        double z = position.z;
        double motionX = motion.x;
        double motionY = motion.y;
        double motionZ = motion.z;
        points.add(new Point(x, y, z));
        for (int tick = 0; tick < ticks; tick++) {
            x += motionX;
            y += motionY;
            z += motionZ;
            points.add(new Point(x, y, z));
            motionX = (motionX + acceleration.x) * drag;
            motionY = (motionY + acceleration.y - gravity) * drag;
            motionZ = (motionZ + acceleration.z) * drag;
        }
        return points;
    }

    static Point referenceRayEnd(Point position, Point motion, double distance) {
        if (position == null || motion == null || distance < 0.0D) {
            return null;
        }
        double speed = motion.length();
        if (speed <= EPSILON) {
            return position;
        }
        return position.add(motion.scale(distance / speed));
    }

    static boolean hasReferenceFireballMotion(Point motion) {
        return motion != null && motion.lengthSquared() >= 0.0001D;
    }

    static boolean isInsideReferenceWarningBox(Point playerPosition, Point impactCenter,
                                               double halfExtent) {
        if (playerPosition == null || impactCenter == null || halfExtent < 0.0D) {
            return false;
        }
        return Math.abs(playerPosition.x - impactCenter.x) <= halfExtent
                && Math.abs(playerPosition.y - impactCenter.y) <= halfExtent
                && Math.abs(playerPosition.z - impactCenter.z) <= halfExtent;
    }

    static double referenceEtaSeconds(double impactDistance, double speedPerTick) {
        if (impactDistance < 0.0D || speedPerTick <= EPSILON) {
            return -1.0D;
        }
        return impactDistance / (speedPerTick * 20.0D);
    }

    static int referenceDistanceColor(double impactDistance) {
        final double nearDistance = 8.0D;
        final double mediumDistance = 24.0D;
        final double farDistance = 48.0D;
        if (impactDistance <= nearDistance) {
            return 0xFFFF0000;
        }
        if (impactDistance >= farDistance) {
            return 0xFF00FF00;
        }
        if (impactDistance <= mediumDistance) {
            double progress = (impactDistance - nearDistance) / (mediumDistance - nearDistance);
            return rgb(255, (int) Math.round(255.0D * progress), 0);
        }
        double progress = (impactDistance - mediumDistance) / (farDistance - mediumDistance);
        return rgb((int) Math.round(255.0D * (1.0D - progress)), 255, 0);
    }

    private static int rgb(int red, int green, int blue) {
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    static double inferredExplosionStrength(boolean largeFireball, int explosionPower) {
        if (!largeFireball) {
            return 0.0D;
        }
        return Math.max(1.0D, explosionPower);
    }

    static boolean isPredictedDestroyedBlock(double distanceToExplosion, double explosionStrength,
                                             float hardness, float explosionResistance, boolean immune) {
        if (immune || hardness < 0.0F || explosionStrength <= 0.0D) {
            return false;
        }
        double effectiveRadius = explosionStrength;
        if (distanceToExplosion > effectiveRadius) {
            return false;
        }
        double distanceFactor = 1.0D - distanceToExplosion / effectiveRadius;
        double blastBudget = explosionStrength * 80.0D * distanceFactor;
        return blastBudget > Math.max(0.0F, explosionResistance);
    }

    static double bedAlarmProgress(double distance, double warningRange) {
        if (warningRange <= 0.0D) {
            return 0.0D;
        }
        return clamp(distance / warningRange);
    }

    static float bedWarningY(int scaledHeight) {
        final float playerStatsHeight = 52.0F;
        final float warningHeight = 6.0F;
        final float warningBottomMargin = 4.0F;
        final float warningBorder = 2.0F;
        return scaledHeight - playerStatsHeight - warningBottomMargin
                - warningBorder - warningHeight;
    }

    static int bedWarsStatus(String sidebarTitle, List<String> sidebarLines) {
        if (sidebarTitle == null || !sidebarTitle.toLowerCase(java.util.Locale.ROOT).contains("bed wars")) {
            return -1;
        }
        if (sidebarLines == null) {
            return -1;
        }
        for (String line : sidebarLines) {
            String clean = line == null ? "" : line.trim();
            String[] parts = clean.split("  ");
            if (parts.length > 1 && parts[1].startsWith("L")) {
                return 0;
            }
            if (clean.equals("Waiting...") || clean.startsWith("Starting in")) {
                return 1;
            }
            if (clean.startsWith("R Red:") || clean.startsWith("B Blue:")) {
                return 2;
            }
        }
        return -1;
    }

    static BedThreat selectNearestBedThreat(List<BedThreat> candidates, double warningRange) {
        if (candidates == null || warningRange <= 0.0D) {
            return null;
        }
        BedThreat nearest = null;
        for (BedThreat candidate : candidates) {
            if (candidate == null || !candidate.eligible || candidate.distance < 0.0D
                    || candidate.distance > warningRange) {
                continue;
            }
            if (nearest == null || candidate.distance < nearest.distance) {
                nearest = candidate;
            }
        }
        return nearest;
    }

    private static double clamp(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    static final class Point {
        private final double x;
        private final double y;
        private final double z;

        Point(double x, double y, double z) {
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

        Point add(Point other) {
            return new Point(x + other.x, y + other.y, z + other.z);
        }

        Point subtract(Point other) {
            return new Point(x - other.x, y - other.y, z - other.z);
        }

        Point scale(double scale) {
            return new Point(x * scale, y * scale, z * scale);
        }

        double dot(Point other) {
            return x * other.x + y * other.y + z * other.z;
        }

        double lengthSquared() {
            return dot(this);
        }

        double length() {
            return Math.sqrt(lengthSquared());
        }
    }

    static final class FireballRisk {
        private final boolean dangerous;
        private final double closestDistance;
        private final double ticksToClosest;
        private final double currentDistance;

        FireballRisk(boolean dangerous, double closestDistance, double ticksToClosest, double currentDistance) {
            this.dangerous = dangerous;
            this.closestDistance = closestDistance;
            this.ticksToClosest = ticksToClosest;
            this.currentDistance = currentDistance;
        }

        static FireballRisk safe() {
            return new FireballRisk(false, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                    Double.POSITIVE_INFINITY);
        }

        boolean isDangerous() {
            return dangerous;
        }

        double getClosestDistance() {
            return closestDistance;
        }

        double getTicksToClosest() {
            return ticksToClosest;
        }

        double getCurrentDistance() {
            return currentDistance;
        }
    }

    static final class BedThreat {
        private final String name;
        private final double distance;
        private final boolean eligible;

        BedThreat(String name, double distance, boolean eligible) {
            this.name = name;
            this.distance = distance;
            this.eligible = eligible;
        }

        String getName() {
            return name;
        }

        double getDistance() {
            return distance;
        }

        boolean isEligible() {
            return eligible;
        }
    }
}
