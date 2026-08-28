package gq.yozakura.module.combat;

final class AutoClickController {
    private static final long ACTIVATION_DELAY_MILLIS = 50L;

    private long nextClickAt;
    private long activationStartedAt;
    private final VapeAutoClickTimingState timingState;

    AutoClickController() {
        this(System.nanoTime());
    }

    AutoClickController(long seed) {
        timingState = new VapeAutoClickTimingState(seed);
    }

    boolean shouldClick(long now, boolean active, boolean allowed,
                        double minCps, double maxCps, AutoClickRandomization randomization) {
        if (!active) {
            reset();
            return false;
        }
        if (activationStartedAt == 0L) {
            activationStartedAt = now;
            return false;
        }
        if (now - activationStartedAt < ACTIVATION_DELAY_MILLIS) {
            return false;
        }
        if (!allowed) {
            return false;
        }
        if (nextClickAt != 0L && now < nextClickAt) {
            return false;
        }

        nextClickAt = now + adjustVapeDelay(
                timingState.nextDelay(now, minCps, maxCps, randomization));
        return true;
    }

    static long adjustVapeDelay(long sampledDelay) {
        long adjustedDelay = sampledDelay - 5L;
        return adjustedDelay - 50L <= 0L ? 45L : adjustedDelay;
    }

    void reset() {
        nextClickAt = 0L;
        activationStartedAt = 0L;
    }
}
