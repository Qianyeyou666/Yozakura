package gq.yozakura.module.world;

/** Tracks the real client dig boundary so packet finishing cannot leak across targets. */
final class SpeedMineDigSession {
    private Target target;

    void start(int x, int y, int z, int facingOrdinal) {
        target = new Target(x, y, z, facingOrdinal);
    }

    void abort(int x, int y, int z) {
        if (matches(x, y, z)) {
            reset();
        }
    }

    boolean canFinish(int x, int y, int z) {
        if (target == null) {
            return false;
        }
        if (!matches(x, y, z)) {
            reset();
            return false;
        }
        return true;
    }

    Target finish(int x, int y, int z) {
        if (!canFinish(x, y, z)) {
            return null;
        }
        Target finished = target;
        reset();
        return finished;
    }

    boolean isActive() {
        return target != null;
    }

    void reset() {
        target = null;
    }

    private boolean matches(int x, int y, int z) {
        return target != null && target.x == x && target.y == y && target.z == z;
    }

    static final class Target {
        final int x;
        final int y;
        final int z;
        final int facingOrdinal;

        Target(int x, int y, int z, int facingOrdinal) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.facingOrdinal = facingOrdinal;
        }
    }
}
