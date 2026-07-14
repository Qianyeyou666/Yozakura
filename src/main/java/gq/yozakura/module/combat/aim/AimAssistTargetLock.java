package gq.yozakura.module.combat.aim;

public final class AimAssistTargetLock {
    private AimAssistTargetLock() {
    }

    public static boolean shouldSwitch(double currentScore, double challengerScore, double margin,
                                       long lockedForMillis, long minimumLockMillis) {
        // Keep this facade dependency-free for the pure selector policy tests.
        return lockedForMillis >= minimumLockMillis && challengerScore + margin < currentScore;
    }
}
