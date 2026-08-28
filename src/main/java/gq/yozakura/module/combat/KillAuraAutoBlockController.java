package gq.yozakura.module.combat;

/**
 * Single owner for KillAura's block, release, attack-window and reblock lifecycle.
 * Runtime packet callbacks confirm transitions; update() only requests actions.
 */
final class KillAuraAutoBlockController {
    private static final long PENDING_TIMEOUT_MILLIS = 300L;

    enum Phase {
        IDLE,
        BLOCK_STARTING,
        BLOCKING,
        RELEASE_PENDING,
        RELEASED_FOR_ATTACK,
        ATTACKING,
        REBLOCK_PENDING
    }

    enum Action {
        NONE,
        START_BLOCK,
        RELEASE_FOR_ATTACK,
        ATTACK,
        RELEASE
    }

    private Phase phase = Phase.IDLE;
    private boolean ownershipWanted;
    private boolean releaseForAttack;
    private long phaseStartedAt;
    private long nextAttackWindowAt;
    private long cycleIntervalMillis;

    synchronized Action update(long now, boolean shouldBlock, boolean canAttackTarget,
                               boolean attackReady, int aps) {
        ownershipWanted = shouldBlock;
        cycleIntervalMillis = intervalMillis(aps);

        recoverTimedOutTransition(now);

        if (!shouldBlock) {
            if (phase == Phase.BLOCKING || phase == Phase.BLOCK_STARTING) {
                releaseForAttack = false;
                phase = Phase.RELEASE_PENDING;
                phaseStartedAt = now;
                return Action.RELEASE;
            }
            if (phase != Phase.RELEASE_PENDING) {
                reset();
            }
            return Action.NONE;
        }

        if (phase == Phase.IDLE || phase == Phase.REBLOCK_PENDING) {
            phase = Phase.BLOCK_STARTING;
            phaseStartedAt = now;
            return Action.START_BLOCK;
        }
        if (phase == Phase.BLOCK_STARTING || phase == Phase.RELEASE_PENDING
                || phase == Phase.ATTACKING) {
            return Action.NONE;
        }
        if (phase == Phase.RELEASED_FOR_ATTACK) {
            if (canAttackTarget && attackReady) {
                phase = Phase.ATTACKING;
                phaseStartedAt = now;
                return Action.ATTACK;
            }
            phase = Phase.REBLOCK_PENDING;
            return Action.START_BLOCK;
        }

        boolean cadenceReady = now >= nextAttackWindowAt;
        if (phase == Phase.BLOCKING && canAttackTarget && attackReady && cadenceReady) {
            releaseForAttack = true;
            phase = Phase.RELEASE_PENDING;
            phaseStartedAt = now;
            return Action.RELEASE_FOR_ATTACK;
        }
        return Action.NONE;
    }

    synchronized void onBlockStarted() {
        if (phase != Phase.BLOCK_STARTING) {
            return;
        }
        phase = Phase.BLOCKING;
        nextAttackWindowAt = phaseStartedAt;
    }

    synchronized void onBlockStartFailed() {
        if (phase == Phase.BLOCK_STARTING) {
            phase = ownershipWanted ? Phase.REBLOCK_PENDING : Phase.IDLE;
        }
    }

    synchronized void onBlockWriteFailed() {
        if (phase == Phase.BLOCKING) {
            phase = ownershipWanted ? Phase.REBLOCK_PENDING : Phase.IDLE;
        }
    }

    synchronized void onBlockStopped() {
        if (phase != Phase.RELEASE_PENDING) {
            return;
        }
        phase = releaseForAttack && ownershipWanted
                ? Phase.RELEASED_FOR_ATTACK
                : Phase.IDLE;
        releaseForAttack = false;
    }

    synchronized void onBlockStopFailed() {
        if (phase == Phase.RELEASE_PENDING) {
            releaseForAttack = false;
            phase = Phase.BLOCKING;
        }
    }

    synchronized void onReleaseWriteFailed() {
        if (phase == Phase.RELEASED_FOR_ATTACK || phase == Phase.IDLE) {
            releaseForAttack = false;
            phase = Phase.BLOCKING;
        }
    }

    synchronized void requestReferenceBlockStart(long now) {
        ownershipWanted = true;
        releaseForAttack = false;
        phase = Phase.BLOCK_STARTING;
        phaseStartedAt = now;
    }

    synchronized boolean requestReferenceRelease(long now) {
        if (phase != Phase.BLOCKING) {
            return false;
        }
        ownershipWanted = false;
        releaseForAttack = false;
        phase = Phase.RELEASE_PENDING;
        phaseStartedAt = now;
        return true;
    }

    synchronized void prepareReferenceAttack() {
        if (phase == Phase.RELEASE_PENDING) {
            phase = Phase.IDLE;
            ownershipWanted = false;
            releaseForAttack = false;
        }
    }

    synchronized void onAttackResult(boolean attacked, long now, long attackDelayMillis) {
        if (phase != Phase.ATTACKING) {
            return;
        }
        phase = ownershipWanted ? Phase.REBLOCK_PENDING : Phase.IDLE;
        long cadence = Math.max(50L, cycleIntervalMillis);
        long attackDelay = Math.max(0L, attackDelayMillis);
        nextAttackWindowAt = safeAdd(now, Math.max(cadence, attackDelay));
    }

    synchronized void reset() {
        phase = Phase.IDLE;
        ownershipWanted = false;
        releaseForAttack = false;
        phaseStartedAt = 0L;
        nextAttackWindowAt = 0L;
        cycleIntervalMillis = 0L;
    }

    synchronized Phase getPhase() {
        return phase;
    }

    synchronized boolean isBlocking() {
        return phase == Phase.BLOCKING;
    }

    synchronized boolean isBlockPending() {
        return phase == Phase.BLOCK_STARTING;
    }

    synchronized boolean isReleasePending() {
        return phase == Phase.RELEASE_PENDING;
    }

    synchronized boolean isAttackWindowOpen() {
        return phase == Phase.RELEASED_FOR_ATTACK || phase == Phase.ATTACKING;
    }

    synchronized boolean shouldRenderBlockPose() {
        return ownershipWanted && phase != Phase.IDLE;
    }

    static long intervalMillis(int aps) {
        int boundedAps = Math.max(1, Math.min(20, aps));
        return Math.max(50L, Math.round(1000.0D / boundedAps));
    }

    private void recoverTimedOutTransition(long now) {
        if (phase != Phase.BLOCK_STARTING && phase != Phase.RELEASE_PENDING) {
            return;
        }
        if (now - phaseStartedAt < PENDING_TIMEOUT_MILLIS) {
            return;
        }
        releaseForAttack = false;
        phase = ownershipWanted ? Phase.REBLOCK_PENDING : Phase.IDLE;
    }

    private static long safeAdd(long value, long increment) {
        return value > Long.MAX_VALUE - increment ? Long.MAX_VALUE : value + increment;
    }
}
