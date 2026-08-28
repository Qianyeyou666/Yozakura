package gq.yozakura.module.render;

/**
 * Small Java 8 port of Rise 6's time-based animation helper.
 * The destination can change while the animation is running without snapping.
 */
final class RiseTargetHudAnimation {
    enum Easing {
        EASE_OUT_SINE,
        EASE_OUT_CUBIC,
        EASE_OUT_QUINT
    }

    private Easing easing;
    private long duration;
    private long startTime;
    private double startValue;
    private double destinationValue;
    private double value;

    RiseTargetHudAnimation(Easing easing, long duration) {
        this.easing = easing;
        this.duration = Math.max(1L, duration);
        this.startTime = System.currentTimeMillis();
    }

    void run(double destinationValue) {
        long now = System.currentTimeMillis();
        if (Double.compare(this.destinationValue, destinationValue) != 0) {
            this.destinationValue = destinationValue;
            this.startValue = value;
            this.startTime = now;
        }
        double progress = Math.max(0.0D, Math.min(1.0D,
                (double) (now - startTime) / (double) Math.max(1L, duration)));
        if (progress >= 1.0D) {
            value = destinationValue;
            return;
        }
        double result = apply(easing, progress);
        value = startValue + (destinationValue - startValue) * result;
    }

    void snap(double value) {
        this.value = value;
        this.startValue = value;
        this.destinationValue = value;
        this.startTime = System.currentTimeMillis();
    }

    double getValue() {
        return value;
    }

    void setEasing(Easing easing) {
        if (easing != null) {
            this.easing = easing;
        }
    }

    void setDuration(long duration) {
        this.duration = Math.max(1L, duration);
    }

    private static double apply(Easing easing, double x) {
        switch (easing) {
            case EASE_OUT_SINE:
                return Math.sin(x * Math.PI / 2.0D);
            case EASE_OUT_CUBIC:
                double cubic = x - 1.0D;
                return 1.0D + cubic * cubic * cubic;
            case EASE_OUT_QUINT:
            default:
                double quint = x - 1.0D;
                return 1.0D + quint * quint * quint * quint * quint;
        }
    }
}
