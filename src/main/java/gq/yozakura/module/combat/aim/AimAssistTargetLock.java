package gq.yozakura.module.combat.aim;

public final class AimAssistTargetLock {
    private AimAssistTargetLock() {
    }

    public static boolean shouldSwitch(double currentScore, double challengerScore, double margin,
                                       long lockedForMillis, long minimumLockMillis) {
        return lockedForMillis >= minimumLockMillis && challengerScore + margin < currentScore;
    }
}
