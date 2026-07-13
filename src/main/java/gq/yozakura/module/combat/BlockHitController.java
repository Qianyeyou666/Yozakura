package gq.yozakura.module.combat;

/**
 * Pure timing state for the vanilla-input BlockHit implementation.
 *
 * <p>A real attack only arms Auto; the corresponding use-key hold may begin
 * only after a later movement packet establishes a new input boundary.</p>
 */
final class BlockHitController {
    enum UseOwner {
        NONE,
        MANUAL,
        PREDICT,
        AUTO
    }

    private static final int NO_ATTACK_EPOCH = Integer.MIN_VALUE;

    private int movementEpoch;
    private int autoAttackEpoch = NO_ATTACK_EPOCH;
    private UseOwner useOwner = UseOwner.NONE;
    private long useUntil;

    synchronized void advanceMovementEpoch() {
        movementEpoch++;
    }

    synchronized void armAuto() {
        autoAttackEpoch = movementEpoch;
    }

    synchronized boolean isAutoReadyAfterMovement() {
        return autoAttackEpoch != NO_ATTACK_EPOCH && movementEpoch > autoAttackEpoch;
    }

    synchronized void consumeAutoArm() {
        autoAttackEpoch = NO_ATTACK_EPOCH;
    }

    synchronized boolean beginUse(UseOwner owner, long now, long durationMs) {
        if (owner == null || owner == UseOwner.NONE || durationMs <= 0L) {
            return false;
        }
        expireUse(now);
        if (priority(owner) < priority(useOwner)) {
            return false;
        }

        useOwner = owner;
        long requestedUntil = now + durationMs;
        useUntil = Math.max(useUntil, requestedUntil);
        return true;
    }

    synchronized boolean isUseActive(long now) {
        expireUse(now);
        return useOwner != UseOwner.NONE;
    }

    synchronized UseOwner activeOwner(long now) {
        expireUse(now);
        return useOwner;
    }

    synchronized void clearUse() {
        useOwner = UseOwner.NONE;
        useUntil = 0L;
    }

    synchronized void reset() {
        movementEpoch = 0;
        autoAttackEpoch = NO_ATTACK_EPOCH;
        clearUse();
    }

    private void expireUse(long now) {
        if (useOwner != UseOwner.NONE && now >= useUntil) {
            clearUse();
        }
    }

    private int priority(UseOwner owner) {
        switch (owner) {
            case MANUAL:
                return 3;
            case AUTO:
                return 2;
            case PREDICT:
                return 1;
            default:
                return 0;
        }
    }
}
