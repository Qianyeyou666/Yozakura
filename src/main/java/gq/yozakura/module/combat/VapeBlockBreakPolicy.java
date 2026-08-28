package gq.yozakura.module.combat;

import java.util.Random;

final class VapeBlockBreakPolicy {
    private final Random random;
    private long activationStartedAt;

    VapeBlockBreakPolicy(long seed) {
        random = new Random(seed);
    }

    boolean shouldPause(long now, boolean activationHeld, boolean enabled,
                        boolean toolsOnly, boolean holdingAllowedTool,
                        boolean pointingAtBlock, boolean containerOpen,
                        double minimumDelay, double maximumDelay) {
        if (!activationHeld) {
            reset();
            return false;
        }
        if (!enabled) {
            return false;
        }
        if (activationStartedAt == 0L) {
            activationStartedAt = now;
        }
        if (now - activationStartedAt < randomDelay(minimumDelay, maximumDelay)) {
            return false;
        }
        if (containerOpen || toolsOnly && !holdingAllowedTool) {
            return false;
        }
        if (pointingAtBlock) {
            return true;
        }

        reset();
        return false;
    }

    void reset() {
        activationStartedAt = 0L;
    }

    private long randomDelay(double first, double second) {
        double minimum = Math.max(0.0D, Math.min(first, second));
        double maximum = Math.max(minimum, Math.max(first, second));
        if (maximum <= minimum) {
            return (long) minimum;
        }
        return (long) (minimum + random.nextDouble() * (maximum - minimum));
    }
}
