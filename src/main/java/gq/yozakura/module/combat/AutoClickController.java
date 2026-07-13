package gq.yozakura.module.combat;

final class AutoClickController {
    private long nextClickAt;

    boolean shouldClick(long now, boolean leftButtonDown, boolean allowed, long delay) {
        if (!leftButtonDown || !allowed) {
            reset();
            return false;
        }

        long safeDelay = Math.max(1L, delay);
        if (nextClickAt == 0L) {
            nextClickAt = now + safeDelay;
            return false;
        }
        if (now < nextClickAt) {
            return false;
        }

        nextClickAt = now + safeDelay;
        return true;
    }

    void reset() {
        nextClickAt = 0L;
    }
}
