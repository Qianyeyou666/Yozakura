package gq.yozakura.module.world;

import java.util.List;

public final class BedNukerTargetPolicy {
    private BedNukerTargetPolicy() {
    }

    public static Position selectNearest(List<Position> candidates, double eyeX, double eyeY, double eyeZ,
                                         double reach) {
        return selectNearest(candidates, eyeX, eyeY, eyeZ, reach, null, 0.0D);
    }

    public static Position selectMiningTarget(Position bed, Position firstHit, boolean throughWalls) {
        if (bed == null || throughWalls || firstHit == null || firstHit.equals(bed)) {
            return bed;
        }
        return firstHit;
    }

    public static double completionThreshold(double speedPercent) {
        double normalized = Math.max(0.0D, Math.min(100.0D, speedPercent)) / 100.0D;
        return 1.0D - 0.3D * normalized;
    }

    public static boolean isEligibleBed(Position bed, List<Position> whitelist, boolean whitelistEnabled) {
        return bed != null && (!whitelistEnabled || whitelist == null || !whitelist.contains(bed));
    }

    public static Position selectSurrounding(List<Surrounding> candidates,
                                             double eyeX, double eyeY, double eyeZ) {
        if (candidates == null) {
            return null;
        }
        Surrounding best = null;
        for (Surrounding candidate : candidates) {
            if (candidate == null || candidate.position == null) {
                continue;
            }
            if (best == null || candidate.breakStrength > best.breakStrength
                    || candidate.breakStrength == best.breakStrength
                    && candidate.position.distanceSqToCenter(eyeX, eyeY, eyeZ)
                    < best.position.distanceSqToCenter(eyeX, eyeY, eyeZ)) {
                best = candidate;
            }
        }
        return best == null ? null : best.position;
    }

    public static Position selectNearest(List<Position> candidates, double eyeX, double eyeY, double eyeZ,
                                         double reach, Position current, double lockMargin) {
        if (candidates == null || reach < 0.0D) {
            return null;
        }
        double maximumDistanceSq = reach * reach;
        Position best = null;
        double bestDistanceSq = Double.MAX_VALUE;
        double currentDistanceSq = Double.MAX_VALUE;
        for (Position candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            double distanceSq = candidate.distanceSqToCenter(eyeX, eyeY, eyeZ);
            if (distanceSq > maximumDistanceSq) {
                continue;
            }
            if (candidate.equals(current)) {
                currentDistanceSq = distanceSq;
            }
            if (distanceSq < bestDistanceSq) {
                best = candidate;
                bestDistanceSq = distanceSq;
            }
        }
        if (current != null && currentDistanceSq <= maximumDistanceSq) {
            double margin = Math.max(0.0D, lockMargin);
            if (Math.sqrt(currentDistanceSq) <= Math.sqrt(bestDistanceSq) + margin) {
                return current;
            }
        }
        return best;
    }

    public static final class Surrounding {
        private final Position position;
        private final double breakStrength;

        public Surrounding(Position position, double breakStrength) {
            this.position = position;
            this.breakStrength = breakStrength;
        }

        public Position getPosition() {
            return position;
        }

        public double getBreakStrength() {
            return breakStrength;
        }
    }

    public static final class Position {
        private final int x;
        private final int y;
        private final int z;

        public Position(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getZ() {
            return z;
        }

        private double distanceSqToCenter(double eyeX, double eyeY, double eyeZ) {
            double dx = x + 0.5D - eyeX;
            double dy = y + 0.5D - eyeY;
            double dz = z + 0.5D - eyeZ;
            return dx * dx + dy * dy + dz * dz;
        }

        @Override
        public boolean equals(Object object) {
            if (!(object instanceof Position)) {
                return false;
            }
            Position other = (Position) object;
            return x == other.x && y == other.y && z == other.z;
        }

        @Override
        public int hashCode() {
            int result = x;
            result = 31 * result + y;
            return 31 * result + z;
        }
    }
}
