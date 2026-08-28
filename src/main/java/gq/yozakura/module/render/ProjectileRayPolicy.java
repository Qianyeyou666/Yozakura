package gq.yozakura.module.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ProjectileRayPolicy {
    private static final double BOW_SPEED = 3.0D;
    private static final double THROWABLE_SPEED = 1.5D;
    private static final double DRAG = 0.99D;

    private ProjectileRayPolicy() {
    }

    static double bowPower(int useDurationTicks) {
        double charge = Math.max(0, useDurationTicks) / 20.0D;
        double power = (charge * charge + charge * 2.0D) / 3.0D;
        return Math.min(1.0D, power);
    }

    static LaunchSpec launchSpec(ProjectileKind kind, int useDurationTicks,
                                 float yawDegrees, float pitchDegrees) {
        if (kind == null) {
            return null;
        }
        double speed;
        double gravity;
        if (kind == ProjectileKind.BOW) {
            double power = bowPower(useDurationTicks);
            if (power < 0.1D) {
                return null;
            }
            speed = BOW_SPEED * power;
            gravity = 0.05D;
        } else {
            speed = THROWABLE_SPEED;
            gravity = 0.03D;
        }

        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        Point direction = new Point(
                -Math.sin(yaw) * Math.cos(pitch),
                -Math.sin(pitch),
                Math.cos(yaw) * Math.cos(pitch));
        return new LaunchSpec(kind, direction.normalize().scale(speed), gravity, DRAG);
    }

    static List<Point> trace(Point position, LaunchSpec spec, int ticks) {
        if (position == null || spec == null || ticks < 0) {
            return Collections.emptyList();
        }
        ArrayList<Point> points = new ArrayList<Point>(ticks + 1);
        double x = position.x;
        double y = position.y;
        double z = position.z;
        double motionX = spec.motion.x;
        double motionY = spec.motion.y;
        double motionZ = spec.motion.z;
        points.add(position);
        for (int tick = 0; tick < ticks; tick++) {
            x += motionX;
            y += motionY;
            z += motionZ;
            points.add(new Point(x, y, z));
            motionX *= spec.drag;
            motionY = motionY * spec.drag - spec.gravity;
            motionZ *= spec.drag;
        }
        return points;
    }

    enum ProjectileKind {
        BOW,
        SNOWBALL,
        EGG,
        ENDER_PEARL
    }

    static final class LaunchSpec {
        private final ProjectileKind kind;
        private final Point motion;
        private final double gravity;
        private final double drag;

        LaunchSpec(ProjectileKind kind, Point motion, double gravity, double drag) {
            this.kind = kind;
            this.motion = motion;
            this.gravity = gravity;
            this.drag = drag;
        }

        ProjectileKind getKind() {
            return kind;
        }

        Point getMotion() {
            return motion;
        }

        double getGravity() {
            return gravity;
        }

        double getDrag() {
            return drag;
        }
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

        Point scale(double scale) {
            return new Point(x * scale, y * scale, z * scale);
        }

        Point normalize() {
            double length = length();
            if (length <= 1.0E-8D) {
                return new Point(0.0D, 0.0D, 0.0D);
            }
            return scale(1.0D / length);
        }

        double length() {
            return Math.sqrt(x * x + y * y + z * z);
        }
    }
}
