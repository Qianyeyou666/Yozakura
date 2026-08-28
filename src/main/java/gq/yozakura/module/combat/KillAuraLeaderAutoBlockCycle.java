package gq.yozakura.module.combat;

/**
 * Pure schedulers mirroring Leader-Lite's Legit and
 * Hypixel(Without NoSlow) AutoBlock switch branches.
 */
final class KillAuraLeaderAutoBlockCycle {
    enum LegitStep {
        ATTACK_AND_BLOCK,
        RELEASE_AND_WAIT,
        WAIT,
        RESET
    }

    enum HypixelStep {
        FLUSH_ATTACK_AND_BLOCK,
        WAIT,
        BLINK_RELEASE_AND_ATTACK,
        RESET
    }

    enum HypixelLagStep {
        ATTACK_AND_BLOCK,
        RELEASE_AND_SUPPRESS_ATTACK,
        FLUSH_AND_WAIT,
        WAIT,
        RESET
    }

    private int legitPhase;
    private int hypixelPhase;
    private int hypixelLagPhase;

    LegitStep nextLegit(boolean shouldCycle, boolean blocking, long attackDelayMillis,
                         boolean attackReady) {
        if (!shouldCycle) {
            boolean wasActive = legitPhase != 0 || blocking;
            legitPhase = 0;
            return wasActive ? LegitStep.RESET : LegitStep.WAIT;
        }
        if (legitPhase == 0) {
            if (!attackReady) {
                return LegitStep.WAIT;
            }
            legitPhase = 1;
            return LegitStep.ATTACK_AND_BLOCK;
        }
        LegitStep step = blocking ? LegitStep.RELEASE_AND_WAIT : LegitStep.WAIT;
        if (attackDelayMillis <= 50L) {
            legitPhase = 0;
        }
        return step;
    }

    void onLegitInitialAttackResult(boolean attacked) {
        if (!attacked && legitPhase == 1) {
            legitPhase = 0;
        }
    }

    HypixelStep nextHypixel(boolean shouldCycle, boolean attackReady) {
        if (!shouldCycle) {
            boolean wasActive = hypixelPhase != 0;
            hypixelPhase = 0;
            return wasActive ? HypixelStep.RESET : HypixelStep.WAIT;
        }
        if (hypixelPhase == 0) {
            if (attackReady) {
                hypixelPhase = 1;
            }
            return HypixelStep.FLUSH_ATTACK_AND_BLOCK;
        }
        if (hypixelPhase == 1) {
            hypixelPhase = 2;
            return HypixelStep.WAIT;
        }
        hypixelPhase = 0;
        return HypixelStep.BLINK_RELEASE_AND_ATTACK;
    }

    void onHypixelInitialAttackResult(boolean attacked) {
        if (!attacked && hypixelPhase == 1) {
            hypixelPhase = 0;
        }
    }

    HypixelLagStep nextHypixelLag(boolean shouldCycle, boolean blocking,
                                        long attackDelayMillis, boolean attackReady) {
        if (!shouldCycle) {
            boolean wasActive = hypixelLagPhase != 0 || blocking;
            hypixelLagPhase = 0;
            return wasActive ? HypixelLagStep.RESET : HypixelLagStep.WAIT;
        }
        if (hypixelLagPhase == 0) {
            if (!attackReady) {
                return HypixelLagStep.WAIT;
            }
            hypixelLagPhase = 1;
            return HypixelLagStep.ATTACK_AND_BLOCK;
        }
        if (hypixelLagPhase == 1) {
            hypixelLagPhase = 2;
            return HypixelLagStep.RELEASE_AND_SUPPRESS_ATTACK;
        }
        if (attackDelayMillis <= 50L) {
            hypixelLagPhase = 0;
        }
        return HypixelLagStep.FLUSH_AND_WAIT;
    }

    void onHypixelLagInitialAttackResult(boolean attacked) {
        if (!attacked && hypixelLagPhase == 1) {
            hypixelLagPhase = 0;
        }
    }

    void reset() {
        legitPhase = 0;
        hypixelPhase = 0;
        hypixelLagPhase = 0;
    }
}
