package gq.yozakura.module.world;

final class FastPlacePolicy {
    static final int MIN_DELAY_TICKS = 0;
    static final int MAX_DELAY_TICKS = 4;

    private FastPlacePolicy() {
    }

    static boolean shouldCapCooldown(boolean useKeyDown, boolean onlyBlocks, boolean holdingBlockItem) {
        return useKeyDown && (!onlyBlocks || holdingBlockItem);
    }

    static int normalizeDelayTicks(Number configuredDelay) {
        if (configuredDelay == null) {
            return MIN_DELAY_TICKS;
        }

        double delay = configuredDelay.doubleValue();
        if (Double.isNaN(delay) || Double.isInfinite(delay)) {
            return MIN_DELAY_TICKS;
        }

        return (int) Math.max(MIN_DELAY_TICKS, Math.min(MAX_DELAY_TICKS, Math.round(delay)));
    }
}
