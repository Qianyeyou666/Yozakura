package gq.yozakura.module.combat.aim;

/**
 * Player-model hit regions used by Lock-on. Coordinates are immutable for one
 * rendered entity frame and do not depend on Minecraft classes.
 */
public final class AimAssistLockOnGeometry {
    private static final double MODEL_WIDTH = 0.5D;
    private static final double HEAD_DEPTH = 0.5D;
    private static final double BODY_DEPTH = 0.25D;
    private static final double INNER_EPSILON = 0.001D;

    public enum Zone {
        HEAD,
        BODY,
        LEGS
    }

    private AimAssistLockOnGeometry() {
    }

    public static Frame create(double minX, double minY, double minZ,
                               double maxX, double maxY, double maxZ,
                               float bodyYaw, double horizontalMultipoint,
                               double verticalMultipoint) {
        if (!(maxX > minX) || !(maxY > minY) || !(maxZ > minZ)) {
            throw new IllegalArgumentException("Player bounds must have positive dimensions");
        }
        double horizontal = clamp01(horizontalMultipoint);
        double vertical = clamp01(verticalMultipoint);
        double centerX = (minX + maxX) * 0.5D;
        double centerZ = (minZ + maxZ) * 0.5D;
        double height = maxY - minY;
        double legsTop = minY + height * (12.0D / 32.0D);
        double bodyTop = minY + height * (24.0D / 32.0D);
        double radians = Math.toRadians(bodyYaw);
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        double collisionHalfDiagonal = Math.sqrt(square(maxX - minX) + square(maxZ - minZ)) * 0.5D;

        Region head = region(minX, minY, minZ, maxX, maxY, maxZ, centerX, centerZ,
                cosine, sine, MODEL_WIDTH, HEAD_DEPTH, bodyTop, maxY,
                collisionHalfDiagonal, horizontal, vertical);
        Region body = region(minX, minY, minZ, maxX, maxY, maxZ, centerX, centerZ,
                cosine, sine, MODEL_WIDTH, BODY_DEPTH, legsTop, bodyTop,
                collisionHalfDiagonal, horizontal, vertical);
        Region legs = region(minX, minY, minZ, maxX, maxY, maxZ, centerX, centerZ,
                cosine, sine, MODEL_WIDTH, BODY_DEPTH, minY, legsTop,
                collisionHalfDiagonal, horizontal, vertical);
        return new Frame(head, body, legs);
    }

    private static Region region(double minX, double minY, double minZ,
                                 double maxX, double maxY, double maxZ,
                                 double centerX, double centerZ, double cosine, double sine,
                                 double modelWidth, double modelDepth,
                                 double modelMinY, double modelMaxY,
                                 double collisionHalfDiagonal, double horizontal, double vertical) {
        double halfWidth = lerp(modelWidth * 0.5D, collisionHalfDiagonal, horizontal);
        double halfDepth = lerp(modelDepth * 0.5D, collisionHalfDiagonal, horizontal);
        double regionMinY = lerp(modelMinY, minY, vertical);
        double regionMaxY = lerp(modelMaxY, maxY, vertical);
        return new Region(minX, minY, minZ, maxX, maxY, maxZ,
                centerX, centerZ, cosine, sine, halfWidth, halfDepth,
                regionMinY, regionMaxY, modelWidth, modelDepth,
                horizontal >= 1.0D, vertical >= 1.0D);
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static double lerp(double start, double end, double amount) {
        return start + (end - start) * amount;
    }

    private static double square(double value) {
        return value * value;
    }

    public static final class Frame {
        private final Region head;
        private final Region body;
        private final Region legs;

        private Frame(Region head, Region body, Region legs) {
            this.head = head;
            this.body = body;
            this.legs = legs;
        }

        public Region get(Zone zone) {
            if (zone == Zone.HEAD) {
                return head;
            }
            return zone == Zone.BODY ? body : legs;
        }

        public Point nearestHeadPoint(double eyeX, double eyeY, double eyeZ) {
            return head.nearestInteriorPoint(eyeX, eyeY, eyeZ);
        }

        public Point nearestHeadPointToRay(double eyeX, double eyeY, double eyeZ,
                                           double directionX, double directionY, double directionZ) {
            return nearestPointToRay(Zone.HEAD, eyeX, eyeY, eyeZ,
                    directionX, directionY, directionZ);
        }

        public Point nearestPointToRay(Zone zone,
                                       double eyeX, double eyeY, double eyeZ,
                                       double directionX, double directionY, double directionZ) {
            Region region = get(zone);
            double directionLengthSq = square(directionX) + square(directionY) + square(directionZ);
            if (directionLengthSq < 1.0E-12D) {
                return region.nearestInteriorPoint(eyeX, eyeY, eyeZ);
            }
            double targetX = region.centerX;
            double targetY = (region.getMinimumY() + region.getMaximumY()) * 0.5D;
            double targetZ = region.centerZ;
            double distanceAlongRay = ((targetX - eyeX) * directionX
                    + (targetY - eyeY) * directionY
                    + (targetZ - eyeZ) * directionZ) / directionLengthSq;
            distanceAlongRay = Math.max(0.0D, distanceAlongRay);
            return region.nearestInteriorPoint(
                    eyeX + directionX * distanceAlongRay,
                    eyeY + directionY * distanceAlongRay,
                    eyeZ + directionZ * distanceAlongRay);
        }

        public boolean rayHits(Zone zone, double originX, double originY, double originZ,
                               double directionX, double directionY, double directionZ, double reach) {
            return get(zone).rayHits(originX, originY, originZ,
                    directionX, directionY, directionZ, reach);
        }

        public boolean rayHitsAny(double originX, double originY, double originZ,
                                  double directionX, double directionY, double directionZ, double reach) {
            return head.rayHits(originX, originY, originZ, directionX, directionY, directionZ, reach)
                    || body.rayHits(originX, originY, originZ, directionX, directionY, directionZ, reach)
                    || legs.rayHits(originX, originY, originZ, directionX, directionY, directionZ, reach);
        }
    }

    public static final class Region {
        private static final double DIRECTION_EPSILON = 1.0E-12D;

        private final double minX;
        private final double minY;
        private final double minZ;
        private final double maxX;
        private final double maxY;
        private final double maxZ;
        private final double centerX;
        private final double centerZ;
        private final double cosine;
        private final double sine;
        private final double halfWidth;
        private final double halfDepth;
        private final double regionMinY;
        private final double regionMaxY;
        private final double modelWidth;
        private final double modelDepth;
        private final boolean fullHorizontal;
        private final boolean fullVertical;

        private Region(double minX, double minY, double minZ,
                       double maxX, double maxY, double maxZ,
                       double centerX, double centerZ, double cosine, double sine,
                       double halfWidth, double halfDepth, double regionMinY, double regionMaxY,
                       double modelWidth, double modelDepth,
                       boolean fullHorizontal, boolean fullVertical) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.cosine = cosine;
            this.sine = sine;
            this.halfWidth = halfWidth;
            this.halfDepth = halfDepth;
            this.regionMinY = regionMinY;
            this.regionMaxY = regionMaxY;
            this.modelWidth = modelWidth;
            this.modelDepth = modelDepth;
            this.fullHorizontal = fullHorizontal;
            this.fullVertical = fullVertical;
        }

        public double getWidth() {
            return fullHorizontal ? maxX - minX : Math.min(modelWidth + (halfWidth * 2.0D - modelWidth),
                    halfWidth * 2.0D);
        }

        public double getDepth() {
            return fullHorizontal ? maxZ - minZ : Math.min(modelDepth + (halfDepth * 2.0D - modelDepth),
                    halfDepth * 2.0D);
        }

        public double getMinimumY() {
            return fullVertical ? minY : regionMinY;
        }

        public double getMaximumY() {
            return fullVertical ? maxY : regionMaxY;
        }

        public boolean contains(double x, double y, double z) {
            if (x < minX || x > maxX || y < getMinimumY() || y > getMaximumY()
                    || z < minZ || z > maxZ) {
                return false;
            }
            if (fullHorizontal) {
                return true;
            }
            double relativeX = x - centerX;
            double relativeZ = z - centerZ;
            double localX = relativeX * cosine + relativeZ * sine;
            double localZ = -relativeX * sine + relativeZ * cosine;
            return Math.abs(localX) <= halfWidth && Math.abs(localZ) <= halfDepth;
        }

        private Point nearestInteriorPoint(double x, double y, double z) {
            double relativeX = x - centerX;
            double relativeZ = z - centerZ;
            double localX = relativeX * cosine + relativeZ * sine;
            double localZ = -relativeX * sine + relativeZ * cosine;
            double innerHalfWidth = Math.max(0.0D, halfWidth - INNER_EPSILON);
            double innerHalfDepth = Math.max(0.0D, halfDepth - INNER_EPSILON);
            if (fullHorizontal) {
                innerHalfWidth = Math.max(0.0D, (maxX - minX) * 0.5D - INNER_EPSILON);
                innerHalfDepth = Math.max(0.0D, (maxZ - minZ) * 0.5D - INNER_EPSILON);
                localX = clamp(x, minX + INNER_EPSILON, maxX - INNER_EPSILON) - centerX;
                localZ = clamp(z, minZ + INNER_EPSILON, maxZ - INNER_EPSILON) - centerZ;
                return new Point(centerX + localX,
                        clamp(y, getMinimumY() + INNER_EPSILON, getMaximumY() - INNER_EPSILON),
                        centerZ + localZ);
            }
            localX = clamp(localX, -innerHalfWidth, innerHalfWidth);
            localZ = clamp(localZ, -innerHalfDepth, innerHalfDepth);
            double worldX = centerX + localX * cosine - localZ * sine;
            double worldZ = centerZ + localX * sine + localZ * cosine;
            worldX = clamp(worldX, minX + INNER_EPSILON, maxX - INNER_EPSILON);
            worldZ = clamp(worldZ, minZ + INNER_EPSILON, maxZ - INNER_EPSILON);
            double worldY = clamp(y, getMinimumY() + INNER_EPSILON,
                    getMaximumY() - INNER_EPSILON);
            for (int iteration = 0; iteration < 16 && !contains(worldX, worldY, worldZ); iteration++) {
                worldX = (worldX + centerX) * 0.5D;
                worldZ = (worldZ + centerZ) * 0.5D;
            }
            return new Point(worldX, worldY, worldZ);
        }

        private boolean rayHits(double originX, double originY, double originZ,
                                double directionX, double directionY, double directionZ, double reach) {
            if (reach < 0.0D) {
                return false;
            }
            double[] interval = new double[]{0.0D, reach};
            if (!clipAxis(originX, directionX, minX, maxX, interval)
                    || !clipAxis(originY, directionY, getMinimumY(), getMaximumY(), interval)
                    || !clipAxis(originZ, directionZ, minZ, maxZ, interval)) {
                return false;
            }
            if (fullHorizontal) {
                return interval[1] >= interval[0];
            }
            double relativeX = originX - centerX;
            double relativeZ = originZ - centerZ;
            double localOriginX = relativeX * cosine + relativeZ * sine;
            double localOriginZ = -relativeX * sine + relativeZ * cosine;
            double localDirectionX = directionX * cosine + directionZ * sine;
            double localDirectionZ = -directionX * sine + directionZ * cosine;
            return clipAxis(localOriginX, localDirectionX, -halfWidth, halfWidth, interval)
                    && clipAxis(localOriginZ, localDirectionZ, -halfDepth, halfDepth, interval)
                    && interval[1] >= interval[0];
        }

        private static boolean clipAxis(double origin, double direction, double minimum, double maximum,
                                        double[] interval) {
            if (Math.abs(direction) < DIRECTION_EPSILON) {
                return origin >= minimum && origin <= maximum;
            }
            double first = (minimum - origin) / direction;
            double second = (maximum - origin) / direction;
            if (first > second) {
                double swap = first;
                first = second;
                second = swap;
            }
            interval[0] = Math.max(interval[0], first);
            interval[1] = Math.min(interval[1], second);
            return interval[1] >= interval[0];
        }

        private static double clamp(double value, double minimum, double maximum) {
            return Math.max(minimum, Math.min(maximum, value));
        }
    }

    public static final class Point {
        private final double x;
        private final double y;
        private final double z;

        private Point(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public double getZ() {
            return z;
        }
    }
}
