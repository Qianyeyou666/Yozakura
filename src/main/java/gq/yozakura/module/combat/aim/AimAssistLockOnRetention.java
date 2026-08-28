package gq.yozakura.module.combat.aim;

/** Holds a Lock-on target through a short-lived eligibility or visibility gap. */
public final class AimAssistLockOnRetention {
    private static final long RETENTION_MILLIS = 350L;

    private long lastEligibleMillis = -1L;

    public void confirmEligible(long nowMillis) {
        lastEligibleMillis = Math.max(0L, nowMillis);
    }

    public boolean shouldRetain(long nowMillis) {
        return lastEligibleMillis >= 0L
                && Math.max(0L, nowMillis - lastEligibleMillis) < RETENTION_MILLIS;
    }

    public void reset() {
        lastEligibleMillis = -1L;
    }
}
