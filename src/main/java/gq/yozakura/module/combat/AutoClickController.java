package gq.yozakura.module.combat;

final class AutoClickController {
    private static final double[] SMOOTH_RHYTHM = new double[]{
            0.25D, 0.50D, 0.75D, 1.00D, 0.75D, 0.50D, 0.25D, 0.00D
    };

    private long nextClickAt;
    private int intervalIndex;

    boolean shouldClick(long now, boolean leftButtonDown, boolean allowed,
                        double minCps, double maxCps, boolean smoothRhythm) {
        if (!leftButtonDown || !allowed) {
            reset();
            return false;
        }

        if (nextClickAt == 0L) {
            nextClickAt = now + nextDelay(minCps, maxCps, smoothRhythm);
            return false;
        }
        if (now < nextClickAt) {
            return false;
        }

        nextClickAt = now + nextDelay(minCps, maxCps, smoothRhythm);
        return true;
    }

    private long nextDelay(double minCps, double maxCps, boolean smoothRhythm) {
        return calculateDelay(minCps, maxCps, smoothRhythm, intervalIndex++);
    }

    static long calculateDelay(double minCps, double maxCps, boolean smoothRhythm, int intervalIndex) {
        double first = clampCps(minCps);
        double second = clampCps(maxCps);
        double lower = Math.min(first, second);
        double upper = Math.max(first, second);
        double phase = smoothRhythm
                ? SMOOTH_RHYTHM[Math.floorMod(intervalIndex, SMOOTH_RHYTHM.length)]
                : 0.50D;
        double cps = lower + (upper - lower) * phase;
        return Math.max(50L, (long) Math.ceil(1000.0D / cps));
    }

    private static double clampCps(double cps) {
        if (Double.isNaN(cps) || cps <= 1.0D) {
            return 1.0D;
        }
        return Math.min(20.0D, cps);
    }

    void reset() {
        nextClickAt = 0L;
        intervalIndex = 0;
    }
}
